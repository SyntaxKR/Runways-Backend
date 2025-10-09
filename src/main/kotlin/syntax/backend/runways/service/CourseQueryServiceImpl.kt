package syntax.backend.runways.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import syntax.backend.runways.dto.AttendanceDTO
import syntax.backend.runways.dto.FineDustDataDTO
import syntax.backend.runways.dto.ResponseCourseDTO
import syntax.backend.runways.dto.ResponseMyCourseDTO
import syntax.backend.runways.dto.ResponseRecommendCourseDTO
import syntax.backend.runways.dto.WeatherDataDTO
import syntax.backend.runways.entity.ActionType
import syntax.backend.runways.entity.CommentStatus
import syntax.backend.runways.entity.Course
import syntax.backend.runways.entity.CourseDifficulty
import syntax.backend.runways.entity.CourseStatus
import syntax.backend.runways.entity.TagLog
import syntax.backend.runways.mapper.CourseMapper
import syntax.backend.runways.repository.BookmarkRepository
import syntax.backend.runways.repository.CommentRepository
import syntax.backend.runways.repository.CourseRepository
import syntax.backend.runways.repository.PopularCourseRepository
import syntax.backend.runways.repository.RunningLogRepository
import syntax.backend.runways.repository.TagLogRepository
import syntax.backend.runways.repository.TagRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Service
class CourseQueryServiceImpl(
    private val courseRepository: CourseRepository,
    private val commentRepository: CommentRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val tagRepository: TagRepository,
    private val tagLogRepository: TagLogRepository,
    private val runningLogRepository: RunningLogRepository,
    private val popularCourseRepository: PopularCourseRepository,
    private val userApiService: UserApiService,
    private val attendanceApiService: AttendanceApiService,
    private val weatherService: WeatherService,
    private val fineDustService: FineDustService
) : CourseQueryService {

    // 댓글 개수 호출
    private fun getCommentCount(courseId: UUID): Int {
        val commentStatus = CommentStatus.PUBLIC
        return commentRepository.countByPost_IdAndStatus(courseId, commentStatus)
    }

    override fun getCourseList(userId: String, pageable: Pageable, status: Boolean): Page<ResponseMyCourseDTO> {
        val statuses = if (status) {
            listOf(CourseStatus.PUBLIC)
        } else {
            listOf(CourseStatus.PUBLIC, CourseStatus.FILTERED, CourseStatus.PRIVATE)
        }

        val courseIdsPage = courseRepository.findCourseIdsByMakerAndStatuses(userId, statuses, pageable)
        val courseIds = courseIdsPage.content
        if (courseIds.isEmpty()) return PageImpl(emptyList(), pageable, 0)

        val bookmarkedIds = bookmarkRepository.findBookmarkedCourseIdsByUserIdAndCourseIds(userId, courseIds)
        val bookmarkCountMap = bookmarkRepository.countBookmarksByCourseIds(courseIds)
            .associate { it.courseId to it.bookmarkCount.toInt() }

        val commentCountMap = commentRepository.countCommentsByCourseIdsAndStatus(courseIds, CommentStatus.PUBLIC)
            .associate { it.courseId to it.commentCount.toInt() }

        val courses = courseRepository.findCoursesWithTagsByIds(courseIds)

        val responseCourses = courses.map { course ->
            CourseMapper.toResponseMyCourseDTO(
                course = course,
                userId = userId,
                bookmarkCount = bookmarkCountMap[course.id] ?: 0,
                commentCount = commentCountMap[course.id] ?: 0
            ).copy(bookmark = course.id in bookmarkedIds)
        }

        return PageImpl(responseCourses, pageable, courseIdsPage.totalElements)
    }

    // 전체 코스 리스트
    override fun getAllCourses(userId: String, pageable: Pageable): Page<ResponseCourseDTO> {
        val statuses = CourseStatus.PUBLIC

        // 코스 ID 조회
        val courseIdsPage = courseRepository.findCourseIdsByStatus(statuses, pageable)
        val courseIds = courseIdsPage.content

        // 북마크된 courseIds 조회
        val bookmarkedCourseIds = bookmarkRepository.findBookmarkedCourseIdsByUserIdAndCourseIds(userId, courseIds)

        // 코스 데이터 조회
        val courses = courseRepository.findCoursesWithTagsByIds(courseIds)

        // ResponseCourseDTO로 매핑
        val responseCourses = courses.map { course ->
            CourseMapper.toResponseCourseDTO(
                course = course,
                userId = userId,
                isBookmarked = course.id in bookmarkedCourseIds,
                commentCount = getCommentCount(course.id)
            )
        }

        // 페이징 결과 반환
        return PageImpl(responseCourses, pageable, courseIdsPage.totalElements)
    }


    // 북마크된 코스 조회
    @Transactional
    override fun getBookmarkedCourses(userId: String, pageable: Pageable): Page<ResponseMyCourseDTO> {
        val bookmarkedCourseIdsPage = bookmarkRepository.findCourseIdsByUserId(userId, pageable)
        val bookmarkedCourseIds = bookmarkedCourseIdsPage.content

        if (bookmarkedCourseIds.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }

        val courses = courseRepository.findCoursesWithTagsByIds(bookmarkedCourseIds)

        // 북마크 수 조회
        val bookmarkCounts = bookmarkRepository.countBookmarksByCourseIds(bookmarkedCourseIds)
        val bookmarkCountMap = bookmarkCounts.associateBy({ it.courseId }, { it.bookmarkCount })

        val responseCourses = courses.map { course ->
            val bookmarkCount = (bookmarkCountMap[course.id] ?: 0L).toInt()
            val commentCount = getCommentCount(course.id)

            CourseMapper.toResponseMyCourseDTO(
                course = course,
                userId = userId,
                bookmarkCount = bookmarkCount,
                commentCount = commentCount
            )
        }

        return PageImpl(responseCourses, pageable, bookmarkedCourseIdsPage.totalElements)
    }

    // 코스 검색
    override fun searchCoursesByTitle(title: String, userId: String, pageable: Pageable): Page<ResponseCourseDTO> {
        val statuses = CourseStatus.PUBLIC

        // 코스 ID 조회
        val courseIdsPage = courseRepository.findCourseIdsByTitleContainingAndStatus(title, statuses, pageable)
        val courseIds = courseIdsPage.content

        // 북마크된 courseIds 조회
        val bookmarkedCourseIds = bookmarkRepository.findBookmarkedCourseIdsByUserIdAndCourseIds(userId, courseIds)

        // 코스 데이터 조회
        val courses = courseRepository.findCoursesWithTagsByIds(courseIds)

        val responseCourses = courses.map { course ->
            CourseMapper.toResponseCourseDTO(
                course = course,
                userId = userId,
                isBookmarked = course.id in bookmarkedCourseIds,
                commentCount = getCommentCount(course.id)
            )
        }

        // 페이징 결과 반환
        return PageImpl(responseCourses, pageable, courseIdsPage.totalElements)
    }

    // 태그로 코스 검색
    override fun searchCoursesByTag(tagName: String, userId: String, pageable: Pageable): Page<ResponseCourseDTO> {
        // 태그 이름으로 태그 ID 조회
        val tag = tagRepository.findByName(tagName)
            ?: throw EntityNotFoundException("태그를 찾을 수 없습니다: $tagName")

        // 코스 ID만 조회 (PUBLIC 상태 필터링)
        val courseIdsPage = courseRepository.findCourseIdsByTagIdExcludingUser(tag.id, CourseStatus.PUBLIC, userId, pageable)
        val courseIds = courseIdsPage.content

        // 북마크된 courseIds 조회
        val bookmarkedCourseIds = bookmarkRepository.findBookmarkedCourseIdsByUserIdAndCourseIds(userId, courseIds)

        // Fetch Join으로 코스와 관련 데이터를 한 번에 조회
        val courses = courseRepository.findCoursesWithTagsByIds(courseIds)

        // `user` 객체를 한 번만 조회
        val user = userApiService.getUserDataFromId(userId)

        // ID 순서를 유지하도록 수동 정렬
        val sortedCourses = courseIds.mapNotNull { id -> courses.find { it.id == id } }

        val responseCourses = sortedCourses.map { course ->
            CourseMapper.toResponseCourseDTO(
                course = course,
                userId = userId,
                isBookmarked = course.id in bookmarkedCourseIds,
                commentCount = getCommentCount(course.id)
            )
        }

        // 태그 로그 생성
        val tagLog = TagLog(
            tag = tag,
            user = user,
            actionType = ActionType.SEARCHED
        )
        tagLogRepository.save(tagLog)

        return PageImpl(responseCourses, pageable, courseIdsPage.totalElements)
    }


    // 최근 사용 코스 조회
    override fun getRecentCourses(userId: String): ResponseRecommendCourseDTO? {
        // RunningLog에서 유효한 코스 ID만 Top 5 조회
        val courseIds = runningLogRepository.findTop5CourseIdsByUserIdAndCourseStatusNotOrderByEndTimeDesc(userId, CourseStatus.DELETED)

        if (courseIds.isEmpty()) {
            return null
        }

        // 코스 정보를 한 번에 조회
        val courses = courseRepository.findCoursesWithTagsByIds(courseIds)

        // 코스 정보를 CourseSummary로 매핑
        val courseSummaries = courses.map { course ->
            CourseMapper.toCourseSummaryDTO(course = course)
        }

        return ResponseRecommendCourseDTO(
            title = "🕓 최근에 이용하셨어요!",
            item = courseSummaries
        )
    }

    // 인기 코스 조회
    override fun getPopularCourses(): ResponseRecommendCourseDTO? {
        val now = LocalDateTime.now()

        // 00:00 ~ 04:29 사이인지 확인
        val isEarlyMorning = now.toLocalTime().isBefore(LocalTime.of(4, 30))

        // 04시 초기화기 때문에 어제 날짜로 설정
        val targetDate = if (isEarlyMorning) LocalDate.now().minusDays(2) else LocalDate.now().minusDays(1)

        // 스케줄러에서 저장된 인기 코스 조회
        val popularCourses = popularCourseRepository.findByDate(targetDate)

        if (popularCourses.isEmpty()) {
            return null
        }

        val courseIds = popularCourses.map { it.courseId }
        val courses = courseRepository.findCoursesWithTagsByIds(courseIds)

        // 순서 유지를 위한 맵 생성
        val courseMap = courses.associateBy { it.id }

        val courseSummaries = popularCourses
            .sortedByDescending { it.usageCount }
            .map { popularCourse ->
                val course = courseMap[popularCourse.courseId]
                    ?: throw EntityNotFoundException("코스 ID ${popularCourse.courseId}를 찾을 수 없습니다.")

                CourseMapper.toCourseSummaryDTO(course = course)
            }

        return ResponseRecommendCourseDTO(
            title = "🌟 어제 많이 이용한 코스에요!",
            item = courseSummaries
        )
    }

    // 급상승 코스 조회
    override fun getRisingCourse() : ResponseRecommendCourseDTO? {
        val now = LocalDateTime.now()

        // 00:00 ~ 04:29 사이인지 확인
        val isEarlyMorning = now.toLocalTime().isBefore(LocalTime.of(4, 30))

        // 조회할 날짜 설정
        val targetDate = if (isEarlyMorning) LocalDate.now().minusDays(1) else LocalDate.now()

        // 스케줄러에서 저장된 인기 코스 조회
        val risingCourses = popularCourseRepository.findByDate(targetDate)

        if (risingCourses.isEmpty()) {
            return null
        }

        // 코스 ID 리스트 추출
        val courseIds = risingCourses.map { it.courseId }

        // 코스 데이터 한 번에 조회
        val courses = courseRepository.findCoursesWithTagsByIds(courseIds)
        val courseMap = courses.associateBy { it.id }

        // 코스 정보를 CourseSummary로 매핑
        val courseSummaries = risingCourses
            .sortedByDescending { it.usageCount } // usageCount 기준 내림차순 정렬
            .map { risingCourse ->
                val course = courseMap[risingCourse.courseId]
                    ?: throw EntityNotFoundException("코스 ID ${risingCourse.courseId}를 찾을 수 없습니다.")

                CourseMapper.toCourseSummaryDTO(course = course)
            }

        return ResponseRecommendCourseDTO(
            title = "📈 실시간으로 급상승중이에요!",
            item = courseSummaries
        )
    }

    // 최근 생성된 코스 조회
    override fun getRecentCreatedCourses(): ResponseRecommendCourseDTO {
        // 최근 생성된 PUBLIC 코스 조회
        val recentCreatedCourseIds = courseRepository.findTop10ByStatusOrderByCreatedAtDesc(CourseStatus.PUBLIC)

        val recentCreatedCourse = courseRepository.findCoursesWithTagsByIds(recentCreatedCourseIds)

        // ID 순서를 유지하며 정렬
        val sortedCourses = recentCreatedCourseIds.mapNotNull { id ->
            recentCreatedCourse.find { it.id == id }
        }

        // 코스 정보를 CourseSummary로 매핑
        val courseSummaries = sortedCourses.map { course ->
            CourseMapper.toCourseSummaryDTO(course = course)
        }

        return ResponseRecommendCourseDTO(
            title = "🍞 따끈따끈 갓 나온 코스에요!",
            item = courseSummaries
        )

    }

    // 난이도로 코스 검색
    override fun getNearbyCoursesByDifficulty(
        nx: Double,
        ny: Double,
        city: String,
        userId: String,
    ): ResponseRecommendCourseDTO? {

        val radius = 2000.0 // 2km 반경

        val weather = weatherService.getWeatherByCity(city, nx, ny)

        val attendance = attendanceApiService.getAttendance(userId)
            ?: return null

        val fineDust = fineDustService.getFineDustData(nx, ny)

        val weatherScore = getEnvironmentalScore(weather, fineDust)

        val difficulties = determineDifficulties(attendance, weatherScore)

        val courses = fetchCoursesByDifficulty(nx, ny, difficulties, radius)
        if (courses.isEmpty()) return null

        val courseSummaries = courses.map { course ->
            CourseMapper.toCourseSummaryDTO(course = course)
        }.shuffled()

        // 날씨 + 난이도 기반 추천 제목 설정
        val title = generateRecommendationTitle(weather, fineDust, difficulties)

        return ResponseRecommendCourseDTO(
            title = title,
            item = courseSummaries
        )
    }

    private fun getEnvironmentalScore(weather: WeatherDataDTO, fineDust: FineDustDataDTO) : Int {
        val (temperature, humidity) = weather.normalized()
        val sky = weather.sky
        val pm10 = fineDust.pm10value.toIntOrNull() ?: 0
        val pm25 = fineDust.pm25value.toIntOrNull() ?: 0

        val tempScore = when {
            temperature >= 30.0 -> -2
            temperature in 25.0..29.9 -> -1
            temperature < 10.0 -> -1
            else -> 0
        }

        val humidityScore = when {
            humidity >= 80 -> -1
            humidity in 60..79 -> 0
            else -> 1
        }

        val skyScore = when (sky) {
            "맑음" -> 1
            "구름 많음", "흐림" -> 0
            "비", "소나기", "눈" -> -1
            else -> 0
        }

        val fineDustScore = when {
            pm10 > 150 || pm25 > 75 -> -2
            pm10 in 81..150 || pm25 in 36..75 -> -1
            pm10 in 31..80 || pm25 in 16..35 -> 0
            else -> 1
        }

        return tempScore + humidityScore + skyScore + fineDustScore
    }

    private fun fetchCoursesByDifficulty(
        lon: Double,
        lat: Double,
        difficulties: List<CourseDifficulty>,
        radius: Double
    ): List<Course> {
        val courseIds = courseRepository.findNearbyCourseIdsByDifficulty(
            lon = lon,
            lat = lat,
            difficulties = difficulties.map { it.name },
            radius = radius
        )
        return if (courseIds.isEmpty()) emptyList()
        else courseRepository.findCoursesWithTagsByIds(courseIds)
    }


    private fun determineDifficulties(attendance: AttendanceDTO, weatherScore: Int): List<CourseDifficulty>{
        val preference = attendance.courseDifficultyPreference?.toIntOrNull()

        return when (preference) {
            1 -> listOf(CourseDifficulty.EASY)
            2 -> listOf(CourseDifficulty.NORMAL)
            3 -> listOf(CourseDifficulty.HARD)
            else -> {
                val conditionScore = (attendance.bodyState?.toIntOrNull() ?: 0) +
                        (attendance.feeling?.toIntOrNull() ?: 0)
                val totalScore = conditionScore + weatherScore

                when {
                    totalScore <= 1 -> listOf(CourseDifficulty.EASY)
                    totalScore in 2..4 -> listOf(CourseDifficulty.EASY, CourseDifficulty.NORMAL)
                    totalScore in 5..6 -> listOf(CourseDifficulty.NORMAL, CourseDifficulty.HARD)
                    else -> listOf(CourseDifficulty.HARD)
                }
            }
        }
    }

    private fun generateRecommendationTitle(
        weather: WeatherDataDTO,
        fineDust: FineDustDataDTO,
        difficulties: List<CourseDifficulty>
    ): String {
        val sky = weather.sky
        val (temperature, humidity) = weather.normalized()
        val pm10 = fineDust.pm10value.toIntOrNull() ?: 0
        val pm25 = fineDust.pm25value.toIntOrNull() ?: 0

        return when {
            sky.contains("비", ignoreCase = true) || sky.contains("소나기", ignoreCase = true) ->
                "☔ 비 오는 날엔 가볍게 걷는 코스 어때요?"
            temperature >= 30.0 ->
                "🥵 무더운 날엔 짧고 쉬운 코스로 안전하게!"
            temperature < 10.0 ->
                "❄️ 추운 날씨엔 몸이 덜 무리가는 코스를 추천해요"
            humidity >= 85 ->
                "💧 습한 날씨엔 숨쉬기 편한 코스가 좋아요"
            pm10 in 81..150 || pm25 in 36..75 ->
                "🌫️ 미세먼지가 나쁜 날엔 쉬운 코스를 추천해요."
            difficulties.containsAll(listOf(CourseDifficulty.EASY, CourseDifficulty.NORMAL)) ->
                "🌤️ 오늘은 조금 가볍게 뛰어볼까요?"
            difficulties.containsAll(listOf(CourseDifficulty.NORMAL, CourseDifficulty.HARD)) ->
                "🔥 오늘은 조금 열심히 달려볼까요!!"
            difficulties.contains(CourseDifficulty.EASY) ->
                "😊 오늘 지친 당신을 위한 힐링 코스"
            difficulties.contains(CourseDifficulty.NORMAL) ->
                "🏃‍♂️ 오늘은 기분 좋게 달려볼까요?"
            difficulties.contains(CourseDifficulty.HARD) ->
                "💪 기운 넘치는 당신! 한계에 도전해볼까요?"
            else -> "📍 지금 날씨에 어울리는 추천 코스를 골라봤어요!"
        }
    }

    private fun WeatherDataDTO.normalized(): Pair<Double, Int> {
        val temp = temperature.toDoubleOrNull() ?: 20.0
        val hum = humidity.replace("%", "").toIntOrNull() ?: 50
        return Pair(temp, hum)
    }

}
