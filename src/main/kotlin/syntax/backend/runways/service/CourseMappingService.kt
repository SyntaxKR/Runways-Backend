package syntax.backend.runways.service

import jakarta.persistence.EntityManager
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import syntax.backend.runways.entity.Course
import syntax.backend.runways.entity.CourseSegmentMapping
import syntax.backend.runways.repository.CourseSegmentMappingRepository
import java.util.UUID
import javax.sql.DataSource

@Service
class CourseMappingService(
    private val entityManager: EntityManager,
    private val courseSegmentMappingRepository: CourseSegmentMappingRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val dataSource: DataSource
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun mapSegmentsToCourse(course: Course) {
        logger.info("코스 세그먼트 매핑 시작: courseId=${course.id}")

        val sql = """
            SELECT gid
            FROM walkroads
            WHERE ST_DWithin(coordinate, ST_SetSRID(:line, 4326), 5)
            ORDER BY ST_Distance(coordinate, ST_SetSRID(:line, 4326))
            LIMIT 100
        """.trimIndent()


        val gIDs: List<Int> = entityManager.createNativeQuery(sql)
            .setParameter("line", course.coordinate)
            .resultList
            .map { (it as Number).toInt() }

        logger.info("쿼리 결과 GID 개수: ${gIDs.size}")

        val existingGids = courseSegmentMappingRepository.findSegmentGidsByCourseId(course.id)
        val newGIDs = gIDs.distinct().filterNot { it in existingGids }

        logger.info("신규 매핑할 GID 개수: ${newGIDs.size}")

        if (newGIDs.isEmpty()) {
            logger.info("신규 GID 없음 - 매핑 작업 종료")
            return
        }

        try {
            bulkInsertWithCopy(course.id, newGIDs)
            logger.info("코스 세그먼트 매핑 완료: courseId=${course.id}, 삽입된 GID 수=${newGIDs.size}")
        } catch (e: Exception) {
            logger.error("PostgreSQL COPY 실패, JPA 방식으로 폴백: courseId=${course.id}", e)
            bulkInsertWithJpa(course.id, newGIDs)
        }
    }

    // 방법 1: 현재 방식 (JPA IDENTITY + saveAllAndFlush)
    fun bulkInsertWithJpa(courseId: UUID, gids: List<Int>) {
        val course = entityManager.find(Course::class.java, courseId)
            ?: throw IllegalArgumentException("Course not found: $courseId")

        val mappings = gids.map { gid -> CourseSegmentMapping(course = course, segmentGid = gid) }

        mappings.chunked(50).forEach { chunk ->
            courseSegmentMappingRepository.saveAllAndFlush(chunk)
            entityManager.clear()
        }
    }

    // 방법 2: JdbcTemplate Batch
    fun bulkInsertWithJdbcTemplate(courseId: UUID, gids: List<Int>) {
        val sql = "INSERT INTO course_segment_mapping (course_id, segment_gid) VALUES (?, ?)"

        jdbcTemplate.batchUpdate(sql, gids, 100) { ps, gid ->
            ps.setObject(1, courseId)
            ps.setInt(2, gid)
        }
    }

    // 방법 3: 순수 JDBC Batch
    fun bulkInsertWithJdbc(courseId: UUID, gids: List<Int>) {
        val sql = "INSERT INTO course_segment_mapping (course_id, segment_gid) VALUES (?, ?)"

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                gids.forEach { gid ->
                    stmt.setObject(1, courseId)
                    stmt.setInt(2, gid)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    // 방법 4: Native Query Single Insert
    @Transactional
    fun bulkInsertWithNativeQuery(courseId: UUID, gids: List<Int>) {
       gids.chunked(1000).forEach { chunk ->
            val values = chunk.joinToString(",") { gid ->
                "('$courseId'::uuid, $gid)"
            }

            val sql = """
                INSERT INTO course_segment_mapping (course_id, segment_gid)
                VALUES $values
            """

            entityManager.createNativeQuery(sql).executeUpdate()
        }
    }

    // 방법 5: PostgreSQL COPY
    fun bulkInsertWithCopy(courseId: UUID, gids: List<Int>) {
        if (gids.isEmpty()) {
            logger.debug("삽입할 GID가 없음")
            return
        }

        try {
            val chunkSize = 5000
            gids.chunked(chunkSize).forEachIndexed { index, chunk ->
                dataSource.connection.use { conn ->
                    val pgConnection = conn.unwrap(PGConnection::class.java)
                    val copyManager = pgConnection.copyAPI

                    val data = chunk.joinToString("\n") { gid ->
                        "$courseId\t$gid"
                    }

                    val sql = "COPY course_segment_mapping (course_id, segment_gid) FROM STDIN"
                    copyManager.copyIn(sql, data.byteInputStream())
                }
            }
            logger.info("courseId=$courseId - PostgreSQL COPY 완료: 총 ${gids.size}개 삽입")
        } catch (e: Exception) {
            logger.error("PostgreSQL COPY 실행 중 오류 발생: courseId=$courseId, gids.size=${gids.size}", e)
            throw e
        }
    }
}
