package syntax.backend.runways.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import syntax.backend.runways.dto.ResponseCourseDTO
import syntax.backend.runways.dto.ResponseMyCourseDTO
import syntax.backend.runways.dto.ResponseRecommendCourseDTO

interface CourseQueryService {
    fun getCourseList(userId: String, pageable: Pageable, status: Boolean): Page<ResponseMyCourseDTO>
    fun getAllCourses(userId: String, pageable: Pageable): Page<ResponseCourseDTO>
    fun getBookmarkedCourses(userId: String, pageable: Pageable): Page<ResponseMyCourseDTO>
    fun searchCoursesByTitle(title: String, userId: String, pageable: Pageable): Page<ResponseCourseDTO>
    fun searchCoursesByTag(tagName: String, userId: String, pageable: Pageable): Page<ResponseCourseDTO>
    fun getRecentCourses(userId: String): ResponseRecommendCourseDTO?
    fun getPopularCourses(): ResponseRecommendCourseDTO?
    fun getRisingCourse(): ResponseRecommendCourseDTO?
    fun getRecentCreatedCourses(): ResponseRecommendCourseDTO
    fun getNearbyCoursesByDifficulty(nx: Double, ny: Double, city: String, userId: String, ): ResponseRecommendCourseDTO?
}
