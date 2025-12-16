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
import syntax.backend.runways.entity.Course
import syntax.backend.runways.entity.CourseDifficulty
import syntax.backend.runways.repository.CourseRepository
import syntax.backend.runways.repository.CourseSegmentMappingRepository
import syntax.backend.runways.repository.UserRepository
import syntax.backend.runways.util.*
import kotlin.math.ceil
import kotlin.system.measureTimeMillis

@SpringBootTest(properties = ["spring.profiles.active=test"])
class CourseMappingServicePerformanceTest @Autowired constructor(
    private val courseMappingService: CourseMappingService,
    private val courseRepository: CourseRepository,
    private val courseSegmentMappingRepository: CourseSegmentMappingRepository,
    private val userRepository: UserRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    private val geometryFactory = GeometryFactory()
    private lateinit var testCourse: Course
    private val testDataSizes = listOf(100, 500, 1000)
    private lateinit var availableGids: List<Int>

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
            title = "성능테스트 코스",
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

        availableGids = jdbcTemplate.queryForList(
            "SELECT gid FROM walkroads LIMIT 2000",
            Int::class.java
        )

        if (availableGids.size < 1000) {
            throw IllegalStateException("테스트를 위해 최소 1000개의 walkroads gid가 필요합니다. 현재: ${availableGids.size}개")
        }

        println("테스트 준비: Course ID = ${testCourse.id}, 사용 가능한 GID = ${availableGids.size}개")
    }

    @AfterEach
    fun cleanup() {
        courseSegmentMappingRepository.deleteByCourseId(testCourse.id)
        courseRepository.delete(testCourse)
        println("테스트 데이터 정리 완료")
    }

    @Test
    fun `성능 비교 테스트 - 모든 방식`() {
        println("\n" + "=".repeat(80))
        println("Bulk Insert 성능 비교 테스트")
        println("=".repeat(80))

        testDataSizes.forEach { size ->
            println("\n[ 테스트 데이터 크기: $size 개 ]")
            println("-".repeat(80))

            val testGids = availableGids.take(size)
            val results = mutableMapOf<String, Long>()

            // 1. JPA 방식
            courseSegmentMappingRepository.deleteByCourseId(testCourse.id)
            val jpaTime = measureTimeMillis {
                courseMappingService.bulkInsertWithJpa(testCourse.id, testGids)
            }
            results["JPA (saveAllAndFlush)"] = jpaTime
            verifyInsertCount(size)

            // 2. JdbcTemplate Batch
            courseSegmentMappingRepository.deleteByCourseId(testCourse.id)
            val jdbcTemplateTime = measureTimeMillis {
                courseMappingService.bulkInsertWithJdbcTemplate(testCourse.id, testGids)
            }
            results["JdbcTemplate Batch"] = jdbcTemplateTime
            verifyInsertCount(size)

            // 3. 순수 JDBC Batch
            courseSegmentMappingRepository.deleteByCourseId(testCourse.id)
            val jdbcTime = measureTimeMillis {
                courseMappingService.bulkInsertWithJdbc(testCourse.id, testGids)
            }
            results["Pure JDBC Batch"] = jdbcTime
            verifyInsertCount(size)

            // 4. Native Query
            courseSegmentMappingRepository.deleteByCourseId(testCourse.id)
            val nativeQueryTime = measureTimeMillis {
                courseMappingService.bulkInsertWithNativeQuery(testCourse.id, testGids)
            }
            results["Native Query (Single INSERT)"] = nativeQueryTime
            verifyInsertCount(size)

            // 5. PostgreSQL COPY
            courseSegmentMappingRepository.deleteByCourseId(testCourse.id)
            val copyTime = measureTimeMillis {
                courseMappingService.bulkInsertWithCopy(testCourse.id, testGids)
            }
            results["PostgreSQL COPY"] = copyTime
            verifyInsertCount(size)

            println()
            val sortedResults = results.entries.sortedBy { it.value }

            sortedResults.forEachIndexed { index, (method, time) ->
                val rank = index + 1
                val speedup = if (index > 0) {
                    String.format("(%.2fx faster)", sortedResults[0].value.toDouble() / time.toDouble())
                } else {
                    "(fastest)"
                }
                println("$rank. %-35s : %6d ms %s".format(method, time, speedup))
            }

            println()
            courseSegmentMappingRepository.deleteByCourseId(testCourse.id)
        }

        println("\n" + "=".repeat(80))
        println("테스트 완료")
        println("=".repeat(80) + "\n")
    }

    @Test
    fun `개별 성능 테스트 - JPA`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ JPA 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithJpa(testCourse.id, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    fun `개별 성능 테스트 - JdbcTemplate`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ JdbcTemplate 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithJdbcTemplate(testCourse.id, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    fun `개별 성능 테스트 - JDBC`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ 순수 JDBC 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithJdbc(testCourse.id, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    fun `개별 성능 테스트 - Native Query`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ Native Query 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithNativeQuery(testCourse.id, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    fun `개별 성능 테스트 - PostgreSQL COPY`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ PostgreSQL COPY 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithCopy(testCourse.id, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    fun `자원 사용량 상세 비교 테스트`() {
        val benchmark = PerformanceBenchmark(
            config = BenchmarkConfig(
                name = "Bulk Insert 자원 사용량 상세 비교",
                dataSizes = testDataSizes
            ),
            setupBeforeEach = { courseSegmentMappingRepository.deleteByCourseId(testCourse.id) }
        )

        benchmark.addMethods(
            BenchmarkMethod(
                name = "JPA (saveAllAndFlush)",
                queryCount = { size -> size },
                action = { size ->
                    courseMappingService.bulkInsertWithJpa(testCourse.id, availableGids.take(size))
                    verifyInsertCount(size)
                }
            ),
            BenchmarkMethod(
                name = "JdbcTemplate Batch",
                queryCount = { size -> ceil(size.toDouble() / 100).toInt() },
                action = { size ->
                    courseMappingService.bulkInsertWithJdbcTemplate(testCourse.id, availableGids.take(size))
                    verifyInsertCount(size)
                }
            ),
            BenchmarkMethod(
                name = "Pure JDBC Batch",
                queryCount = { 1 },
                action = { size ->
                    courseMappingService.bulkInsertWithJdbc(testCourse.id, availableGids.take(size))
                    verifyInsertCount(size)
                }
            ),
            BenchmarkMethod(
                name = "Native Query (Single INSERT)",
                queryCount = { size -> ceil(size.toDouble() / 1000).toInt() },
                action = { size ->
                    courseMappingService.bulkInsertWithNativeQuery(testCourse.id, availableGids.take(size))
                    verifyInsertCount(size)
                }
            ),
            BenchmarkMethod(
                name = "PostgreSQL COPY",
                queryCount = { 1 },
                action = { size ->
                    courseMappingService.bulkInsertWithCopy(testCourse.id, availableGids.take(size))
                    verifyInsertCount(size)
                }
            )
        )

        val results = benchmark.run()

        PerformanceResultFormatter.printTable(results)

        println("\n" + "=".repeat(100))
        println("간단한 성능 비교")
        println("=".repeat(100))
        PerformanceResultFormatter.printSimpleComparison(results)

        println("\n" + "=".repeat(100))
        println("성능 분석")
        println("=".repeat(100))
        PerformanceResultFormatter.printAnalysis(results)
    }


    @Test
    fun `프레임워크 사용 - 자원 사용량 비교`() {
        val benchmark = PerformanceBenchmark(
            config = BenchmarkConfig(
                name = "Bulk Insert 자원 사용량 상세 비교 (프레임워크 버전)",
                dataSizes = listOf(100, 500, 1000)
            ),
            setupBeforeEach = { courseSegmentMappingRepository.deleteByCourseId(testCourse.id) },
            cleanupAfterEach = { /* 추가 정리 작업 */ }
        )

        benchmark.addMethods(
            BenchmarkMethod(
                name = "JPA (saveAllAndFlush)",
                queryCount = { size -> size },
                action = { size ->
                    val testGids = availableGids.take(size)
                    courseMappingService.bulkInsertWithJpa(testCourse.id, testGids)
                    verifyInsertCount(size)
                }
            ),
            BenchmarkMethod(
                name = "JdbcTemplate Batch",
                queryCount = { size -> ceil(size.toDouble() / 100).toInt() },
                action = { size ->
                    val testGids = availableGids.take(size)
                    courseMappingService.bulkInsertWithJdbcTemplate(testCourse.id, testGids)
                    verifyInsertCount(size)
                }
            ),
            BenchmarkMethod(
                name = "Pure JDBC Batch",
                queryCount = { 1 },
                action = { size ->
                    val testGids = availableGids.take(size)
                    courseMappingService.bulkInsertWithJdbc(testCourse.id, testGids)
                    verifyInsertCount(size)
                }
            ),
            BenchmarkMethod(
                name = "Native Query (Single INSERT)",
                queryCount = { size -> ceil(size.toDouble() / 1000).toInt() },
                action = { size ->
                    val testGids = availableGids.take(size)
                    courseMappingService.bulkInsertWithNativeQuery(testCourse.id, testGids)
                    verifyInsertCount(size)
                }
            ),
            BenchmarkMethod(
                name = "PostgreSQL COPY",
                queryCount = { 1 },
                action = { size ->
                    val testGids = availableGids.take(size)
                    courseMappingService.bulkInsertWithCopy(testCourse.id, testGids)
                    verifyInsertCount(size)
                }
            )
        )

        val results = benchmark.run()

        println("\n" + "=".repeat(100))
        println("상세 표 출력")
        println("=".repeat(100))
        PerformanceResultFormatter.printTable(results)

        println("\n" + "=".repeat(100))
        println("간단한 비교 출력")
        println("=".repeat(100))
        PerformanceResultFormatter.printSimpleComparison(results)

        println("\n" + "=".repeat(100))
        println("분석 결과")
        println("=".repeat(100))
        PerformanceResultFormatter.printAnalysis(results)

    }

    private fun verifyInsertCount(expectedCount: Int) {
        val actualCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM course_segment_mapping WHERE course_id = ?",
            Int::class.java,
            testCourse.id
        ) ?: 0

        if (actualCount != expectedCount) {
            throw AssertionError("Expected $expectedCount rows, but found $actualCount")
        }
    }
}
