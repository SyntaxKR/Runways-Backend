package syntax.backend.runways.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import syntax.backend.runways.entity.Course
import syntax.backend.runways.entity.CourseDifficulty
import syntax.backend.runways.repository.CourseRepository
import syntax.backend.runways.repository.CourseSegmentMappingRepository
import syntax.backend.runways.repository.UserRepository
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class CourseMappingServiceIntegrationTest @Autowired constructor(
    private val courseMappingService: CourseMappingService,
    private val courseRepository: CourseRepository,
    private val courseSegmentMappingRepository: CourseSegmentMappingRepository,
    private val userRepository: UserRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    private val geometryFactory = GeometryFactory()
    private lateinit var testCourse: Course

    @BeforeEach
    fun setup() {
        val testUser = userRepository.findAll().firstOrNull()
            ?: throw IllegalStateException("테스트를 위해 최소 1명의 유저가 필요합니다.")

        val coordinates = arrayOf(
            Coordinate(126.9780, 37.5665),
            Coordinate(126.9810, 37.5670),
            Coordinate(126.9840, 37.5675)
        )

        val lineString: LineString = geometryFactory.createLineString(coordinates)

        testCourse = Course(
            title = "테스트 코스",
            maker = testUser,
            coordinate = lineString,
            distance = 1000.0f,
            difficulty = CourseDifficulty.EASY,
            position = geometryFactory.createPoint(Coordinate(126.9780, 37.5665)),
            mapUrl = "https://test.com/map",
            sido = "경기도",
            sigungu = "파주시"
        )

        testCourse = courseRepository.save(testCourse)
        println("테스트 코스 생성: ${testCourse.id}")
    }

    @AfterEach
    fun cleanup() {
        courseSegmentMappingRepository.deleteByCourseId(testCourse.id)
        courseRepository.delete(testCourse)
        println("테스트 데이터 정리 완료")
    }

    @Test
    fun `코스 세그먼트 매핑 - 정상 동작 확인`() {
        // Given: 테스트 코스가 준비됨

        // When: 세그먼트 매핑 실행
        val executionTime = measureTimeMillis {
            courseMappingService.mapSegmentsToCourse(testCourse)
        }

        // Then: 매핑 결과 검증
        val mappings = courseSegmentMappingRepository.findByCourseId(testCourse.id)

        println("=".repeat(80))
        println("테스트 결과")
        println("=".repeat(80))
        println("실행 시간: ${executionTime}ms")
        println("매핑된 세그먼트 수: ${mappings.size}")
        println("=".repeat(80))

        assertTrue(mappings.isNotEmpty(), "최소 1개 이상의 세그먼트가 매핑되어야 함")
        assertEquals(testCourse.id, mappings.first().course.id, "코스 ID가 일치해야 함")
    }

    @Test
    fun `중복 매핑 방지 확인`() {
        // Given: 첫 번째 매핑 실행
        courseMappingService.mapSegmentsToCourse(testCourse)
        val firstMappingCount = courseSegmentMappingRepository.findByCourseId(testCourse.id).size

        // When: 같은 코스에 대해 다시 매핑 실행
        courseMappingService.mapSegmentsToCourse(testCourse)
        val secondMappingCount = courseSegmentMappingRepository.findByCourseId(testCourse.id).size

        // Then: 매핑 수가 동일해야 함 (중복 방지)
        println("첫 번째 매핑: $firstMappingCount 개")
        println("두 번째 매핑: $secondMappingCount 개")

        assertEquals(firstMappingCount, secondMappingCount, "중복 매핑이 발생하지 않아야 함")
    }

    @Test
    fun `COPY 방식과 JPA 방식 결과 비교`() {
        val availableGids = jdbcTemplate.queryForList(
            "SELECT gid FROM walkroads LIMIT 100",
            Int::class.java
        )

        if (availableGids.size < 50) {
            println("테스트를 위해 최소 50개의 walkroads가 필요합니다. 스킵합니다.")
            return
        }

        val testGids = availableGids.take(50)

        // 1. COPY 방식
        courseSegmentMappingRepository.deleteAll()
        val copyTime = measureTimeMillis {
            courseMappingService.bulkInsertWithCopy(testCourse.id, testGids)
        }
        val copyCount = courseSegmentMappingRepository.findByCourseId(testCourse.id).size

        // 2. JPA 방식
        courseSegmentMappingRepository.deleteAll()
        val jpaTime = measureTimeMillis {
            courseMappingService.bulkInsertWithJpa(testCourse.id, testGids)
        }
        val jpaCount = courseSegmentMappingRepository.findByCourseId(testCourse.id).size

        println("=".repeat(80))
        println("성능 비교")
        println("=".repeat(80))
        println("COPY 방식: ${copyTime}ms, 삽입 수: $copyCount")
        println("JPA 방식: ${jpaTime}ms, 삽입 수: $jpaCount")
        println("속도 향상: ${jpaTime.toDouble() / copyTime.toDouble()}배")
        println("=".repeat(80))

        assertEquals(testGids.size, copyCount, "COPY 방식이 모든 데이터를 삽입해야 함")
        assertEquals(testGids.size, jpaCount, "JPA 방식이 모든 데이터를 삽입해야 함")
        assertEquals(copyCount, jpaCount, "두 방식의 결과가 동일해야 함")
        assertTrue(copyTime < jpaTime, "COPY 방식이 더 빨라야 함")
    }

    @Test
    fun `대용량 데이터 처리 테스트 - 5000개 이상`() {
        val availableGids = jdbcTemplate.queryForList(
            "SELECT gid FROM walkroads LIMIT 5500",
            Int::class.java
        )

        if (availableGids.size < 5000) {
            println("대용량 테스트를 위해 최소 5000개의 walkroads가 필요합니다. 현재: ${availableGids.size}개")
            println("테스트를 스킵합니다.")
            return
        }

        val testGids = availableGids.take(5500)

        val executionTime = measureTimeMillis {
            courseMappingService.bulkInsertWithCopy(testCourse.id, testGids)
        }

        val insertedCount = courseSegmentMappingRepository.findByCourseId(testCourse.id).size

        println("=".repeat(80))
        println("대용량 데이터 처리 결과")
        println("=".repeat(80))
        println("데이터 크기: ${testGids.size}개")
        println("실행 시간: ${executionTime}ms")
        println("삽입된 데이터: $insertedCount 개")
        println("초당 처리량: ${(testGids.size.toDouble() / executionTime * 1000).toInt()}개/초")
        println("=".repeat(80))

        assertEquals(testGids.size, insertedCount, "모든 데이터가 삽입되어야 함")
        assertTrue(executionTime < 1000, "5500개 데이터가 1초 이내에 삽입되어야 함")
    }

    @Test
    fun `빈 GID 리스트 처리 테스트`() {
        // Given: 빈 리스트
        val emptyGids = emptyList<Int>()

        // When: 빈 리스트로 매핑 시도
        val executionTime = measureTimeMillis {
            courseMappingService.bulkInsertWithCopy(testCourse.id, emptyGids)
        }

        // Then: 에러 없이 정상 처리
        val count = courseSegmentMappingRepository.findByCourseId(testCourse.id).size

        println("빈 리스트 처리 시간: ${executionTime}ms")
        assertEquals(0, count, "빈 리스트는 아무것도 삽입하지 않아야 함")
        assertTrue(executionTime < 100, "빈 리스트 처리는 즉시 완료되어야 함")
    }

    @Test
    fun `실제 walkroads 데이터와 매핑 확인`() {
        // Given: 실제 walkroads 테이블에 데이터가 있는 상태

        // When: 실제 공간 쿼리로 매핑
        courseMappingService.mapSegmentsToCourse(testCourse)

        // Then: 매핑 결과 검증
        val mappings = courseSegmentMappingRepository.findByCourseId(testCourse.id)
        val segmentGids = mappings.map { it.segmentGid }

        println("=".repeat(80))
        println("실제 walkroads 매핑 결과")
        println("=".repeat(80))
        println("매핑된 세그먼트 수: ${mappings.size}")
        println("GID 목록: $segmentGids")
        println("=".repeat(80))

        segmentGids.forEach { gid ->
            val exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM walkroads WHERE gid = ?)",
                Boolean::class.java,
                gid
            )
            assertTrue(exists == true, "GID $gid 가 walkroads에 존재해야 함")
        }
    }
}
