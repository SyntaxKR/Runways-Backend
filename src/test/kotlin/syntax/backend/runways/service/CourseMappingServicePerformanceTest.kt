package syntax.backend.runways.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.system.measureTimeMillis

@SpringBootTest(properties = ["spring.profiles.active=test"])
class CourseMappingServicePerformanceTest @Autowired constructor(
    private val courseMappingService: CourseMappingService,
    private val jdbcTemplate: JdbcTemplate
) {

    private lateinit var testCourseId: UUID
    private val testDataSizes = listOf(100, 500, 1000)
    private lateinit var availableGids: List<Int>

    @BeforeEach
    fun setup() {
        testCourseId = jdbcTemplate.queryForObject(
            """
            SELECT c.id FROM courses c
            LEFT JOIN course_segment_mapping csm ON c.id = csm.course_id
            WHERE csm.id IS NULL
            LIMIT 1
            """,
            UUID::class.java
        ) ?: run {
            println("매핑이 없는 코스가 없습니다. 전체 매핑 데이터를 삭제합니다.")
            jdbcTemplate.update("DELETE FROM course_segment_mapping")
            jdbcTemplate.queryForObject("SELECT id FROM courses LIMIT 1", UUID::class.java)
                ?: throw IllegalStateException("테스트용 Course가 없습니다.")
        }

        availableGids = jdbcTemplate.queryForList(
            "SELECT gid FROM walkroads LIMIT 2000",
            Int::class.java
        )

        if (availableGids.size < 1000) {
            throw IllegalStateException("테스트를 위해 최소 1000개의 walkroads gid가 필요합니다. 현재: ${availableGids.size}개")
        }

        val deleted = javax.sql.DataSource::class.java.cast(jdbcTemplate.dataSource).connection.use { conn ->
            conn.autoCommit = true
            conn.prepareStatement("DELETE FROM course_segment_mapping WHERE course_id = ?").use { stmt ->
                stmt.setObject(1, testCourseId)
                stmt.executeUpdate()
            }
        }

        // 시퀀스를 현재 최대값 이후로 설정 (중복 방지)
        jdbcTemplate.execute("""
            SELECT setval('course_segment_mapping_id_seq',
                COALESCE((SELECT MAX(id) FROM course_segment_mapping), 0) + 1000,
                false)
        """)

        println("테스트 준비: Course ID = $testCourseId, 삭제된 매핑 = $deleted 개, 사용 가능한 GID = ${availableGids.size}개")
    }

    @AfterEach
    fun cleanup() {
        cleanupTestData()
        println("테스트 데이터 정리 완료")
    }

    private fun cleanupTestData() {
        javax.sql.DataSource::class.java.cast(jdbcTemplate.dataSource).connection.use { conn ->
            conn.autoCommit = true
            val deleted = conn.prepareStatement("DELETE FROM course_segment_mapping WHERE course_id = ?").use { stmt ->
                stmt.setObject(1, testCourseId)
                stmt.executeUpdate()
            }
            if (deleted > 0) {
                println("  → $deleted 개 데이터 삭제됨")
            }
        }
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
            cleanupTestData()
            val jpaTime = measureTimeMillis {
                courseMappingService.bulkInsertWithJpa(testCourseId, testGids)
            }
            results["JPA (saveAllAndFlush)"] = jpaTime
            verifyInsertCount(size)

            // 2. JdbcTemplate Batch
            cleanupTestData()
            val jdbcTemplateTime = measureTimeMillis {
                courseMappingService.bulkInsertWithJdbcTemplate(testCourseId, testGids)
            }
            results["JdbcTemplate Batch"] = jdbcTemplateTime
            verifyInsertCount(size)

            // 3. 순수 JDBC Batch
            cleanupTestData()
            val jdbcTime = measureTimeMillis {
                courseMappingService.bulkInsertWithJdbc(testCourseId, testGids)
            }
            results["Pure JDBC Batch"] = jdbcTime
            verifyInsertCount(size)

            // 4. Native Query
            cleanupTestData()
            val nativeQueryTime = measureTimeMillis {
                courseMappingService.bulkInsertWithNativeQuery(testCourseId, testGids)
            }
            results["Native Query (Single INSERT)"] = nativeQueryTime
            verifyInsertCount(size)

            // 5. PostgreSQL COPY
            cleanupTestData()
            val copyTime = measureTimeMillis {
                courseMappingService.bulkInsertWithCopy(testCourseId, testGids)
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
            cleanupTestData()
        }

        println("\n" + "=".repeat(80))
        println("테스트 완료")
        println("=".repeat(80) + "\n")
    }

    @Test
    @Transactional
    fun `개별 성능 테스트 - JPA`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ JPA 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithJpa(testCourseId, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    @Transactional
    fun `개별 성능 테스트 - JdbcTemplate`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ JdbcTemplate 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithJdbcTemplate(testCourseId, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    @Transactional
    fun `개별 성능 테스트 - JDBC`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ 순수 JDBC 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithJdbc(testCourseId, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    @Transactional
    fun `개별 성능 테스트 - Native Query`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ Native Query 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithNativeQuery(testCourseId, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    @Test
    @Transactional
    fun `개별 성능 테스트 - PostgreSQL COPY`() {
        val size = 1000
        val testGids = availableGids.take(size)

        println("\n[ PostgreSQL COPY 방식 성능 테스트: $size 개 ]")

        val time = measureTimeMillis {
            courseMappingService.bulkInsertWithCopy(testCourseId, testGids)
        }

        println("실행 시간: $time ms")
        verifyInsertCount(size)
    }

    private fun verifyInsertCount(expectedCount: Int) {
        val actualCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM course_segment_mapping WHERE course_id = ?",
            Int::class.java,
            testCourseId
        ) ?: 0

        if (actualCount != expectedCount) {
            throw AssertionError("Expected $expectedCount rows, but found $actualCount")
        }
    }
}
