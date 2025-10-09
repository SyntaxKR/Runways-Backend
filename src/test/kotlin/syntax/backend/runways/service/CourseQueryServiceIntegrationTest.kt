package syntax.backend.runways.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties = ["spring.profiles.active=test"])
@Transactional(readOnly = true)
@AutoConfigureMockMvc(addFilters = false)
class CourseQueryServiceIntegrationTest @Autowired constructor(
    private val courseQueryService: CourseQueryService
) {

    @Test
    fun `코스 목록 조회`() {
        val userId = "test-user-id"
        val pageable = PageRequest.of(0, 5)

        val result = courseQueryService.getAllCourses(userId, pageable)

        assertThat(result.content).isNotEmpty
        result.content.forEach {
            println("코스명: ${it.title}, 태그: ${it.tag.map { tag -> tag.name }}")
        }
    }
}
