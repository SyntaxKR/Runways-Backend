package syntax.backend.runways.service

import jakarta.persistence.EntityNotFoundException
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.locationtech.jts.io.WKTReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.slf4j.LoggerFactory
import syntax.backend.runways.dto.*
import syntax.backend.runways.entity.*
import syntax.backend.runways.event.CourseCreatedEvent
import syntax.backend.runways.event.CourseUpdatedEvent
import syntax.backend.runways.exception.NotAuthorException
import syntax.backend.runways.mapper.CourseMapper
import syntax.backend.runways.repository.*
import syntax.backend.runways.util.DistanceUtil
import syntax.backend.runways.util.GeoJsonUtil
import java.time.LocalDateTime
import java.util.*

@Service
class CourseApiServiceImpl(
    private val courseRepository: CourseRepository,
    private val userApiService: UserApiService,
    private val locationApiService: LocationApiService,
    private val commentRepository: CommentRepository,
    private val courseQueryService: CourseQueryService,
    private val weatherService: WeatherService,
    private val tendencyApiService: TendencyApiService,
    private val attendanceApiService: AttendanceApiService,
    private val popularCourseRepository: PopularCourseRepository,
    private val courseTagRepository: CourseTagRepository,
    private val tagApiService: TagApiService,
    private val tagRepository: TagRepository,
    private val tagLogRepository: TagLogRepository,
    private val experienceService: ExperienceService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val bookmarkRepository: BookmarkRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : CourseApiService {

    @Value("\${llm-server-url}")
    private lateinit var llmServerUrl : String
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val wktReader = WKTReader()

    // 코스 데이터 호출
    override fun getCourseData(courseId: UUID): Course {
        val courseData = courseRepository.findById(courseId).orElse(null) ?: throw EntityNotFoundException("코스를 찾을 수 없습니다.")
        return courseData
    }

    // 댓글 개수 호출
    private fun getCommentCount(courseId: UUID): Int {
        val commentStatus = CommentStatus.PUBLIC
        return commentRepository.countByPost_IdAndStatus(courseId, commentStatus)
    }

    // 코스 생성
    @Transactional
    override fun createCourse(requestCourseDTO: RequestCourseDTO, userId: String) : UUID {
        val user = userApiService.getUserDataFromId(userId)

        val position = wktReader.read(requestCourseDTO.position) // Point
        val coordinate = wktReader.read(requestCourseDTO.coordinate) // LineString

        position.srid = 4326
        coordinate.srid = 4326

        if (position.geometryType != "Point" || coordinate.geometryType != "LineString") {
            throw IllegalArgumentException("유효하지 않은 WKT 형식: position은 Point여야 하고 coordinate는 LineString이어야 합니다.")
        }

        if (requestCourseDTO.sido == requestCourseDTO.sigungu || requestCourseDTO.sido=="Unknown" || requestCourseDTO.sigungu=="Unknown") {
            val x = position.coordinate.x
            val y = position.coordinate.y
            val nearestLocation = locationApiService.getNearestLocation(x, y)
                ?: throw IllegalArgumentException("가장 가까운 Location을 찾을 수 없습니다.")
            requestCourseDTO.sido = nearestLocation.sido
            requestCourseDTO.sigungu = nearestLocation.sigungu
        }

        val newCourse = Course(
            title = requestCourseDTO.title,
            maker = user,
            distance = requestCourseDTO.distance,
            position = position as Point,
            coordinate = coordinate as LineString,
            mapUrl = requestCourseDTO.mapUrl,
            status = requestCourseDTO.status,
            usageCount = 0,
            sido = requestCourseDTO.sido,
            sigungu = requestCourseDTO.sigungu
        )

        courseRepository.save(newCourse)

        val tags = tagRepository.findAllById(requestCourseDTO.tag).map { tag ->
            tag.apply {
                usageCount += 1
            }
        }

        val courseTags = tags.map { tag -> CourseTag(course = newCourse, tag = tag) }
        val tagLogs = tags.map { tag -> TagLog(tag = tag, user = user, actionType = ActionType.USED) }

        tagRepository.saveAll(tags)
        tagLogRepository.saveAll(tagLogs)
        courseTagRepository.saveAll(courseTags)

        experienceService.addExperience(user, 50)

        eventPublisher.publishEvent(CourseCreatedEvent(newCourse.id))

        return newCourse.id
    }

    // 코스 업데이트
    @Transactional
    override fun updateCourse(requestUpdateCourseDTO: RequestUpdateCourseDTO, userId : String): UUID {
        val courseData = courseRepository.findById(requestUpdateCourseDTO.courseId)
            .orElseThrow { EntityNotFoundException("코스를 찾을 수 없습니다.") }

        if (courseData.maker.id != userId) {
            throw NotAuthorException("코스 제작자가 아닙니다.")
        }

        val position = wktReader.read(requestUpdateCourseDTO.position)
        val coordinate = wktReader.read(requestUpdateCourseDTO.coordinate)

        if (position.geometryType != "Point" || coordinate.geometryType != "LineString") {
            throw IllegalArgumentException("유효하지 않은 WKT 형식: position은 Point여야 하고 coordinate는 LineString이어야 합니다.")
        }

        if (requestUpdateCourseDTO.sido == requestUpdateCourseDTO.sigungu || requestUpdateCourseDTO.sido=="Unknown" || requestUpdateCourseDTO.sigungu=="Unknown") {
            val x = position.coordinate.x
            val y = position.coordinate.y
            val nearestLocation = locationApiService.getNearestLocation(x, y)
                ?: throw IllegalArgumentException("가장 가까운 Location을 찾을 수 없습니다.")
            requestUpdateCourseDTO.sido = nearestLocation.sido
            requestUpdateCourseDTO.sigungu = nearestLocation.sigungu
        }

        courseData.title = requestUpdateCourseDTO.title
        courseData.distance = requestUpdateCourseDTO.distance
        courseData.position = position as Point
        courseData.coordinate = coordinate as LineString
        courseData.mapUrl = requestUpdateCourseDTO.mapUrl
        courseData.status = requestUpdateCourseDTO.status
        courseData.updatedAt = LocalDateTime.now()
        courseData.sido = requestUpdateCourseDTO.sido
        courseData.sigungu = requestUpdateCourseDTO.sigungu

        courseRepository.save(courseData)

        val existingTags = courseData.courseTags.map { it.tag.id }
        val newTags = requestUpdateCourseDTO.tag

        val tagsToAdd = newTags.filterNot { it in existingTags }.distinct()
        val tagsToAddEntities = tagRepository.findAllById(tagsToAdd).map { tag ->
            tag.apply { usageCount += 1 }
        }
        val courseTagsToAdd = tagsToAddEntities.map { tag -> CourseTag(course = courseData, tag = tag) }
        val tagLogsToAdd = tagsToAddEntities.map { tag ->
            TagLog(tag = tag, user = courseData.maker, actionType = ActionType.USED)
        }
        courseTagRepository.saveAll(courseTagsToAdd)
        tagLogRepository.saveAll(tagLogsToAdd)
        tagRepository.saveAll(tagsToAddEntities)

        // 삭제해야 할 태그
        val tagsToRemove = existingTags.filterNot { it in newTags }.distinct()
        val tagsToRemoveEntities = tagRepository.findAllById(tagsToRemove).map { tag ->
            tag.apply { usageCount = (usageCount - 1).coerceAtLeast(0) }
        }
        courseTagRepository.deleteAllByCourseIdAndTagIdIn(courseData.id, tagsToRemove)
        tagRepository.saveAll(tagsToRemoveEntities)

        eventPublisher.publishEvent(CourseUpdatedEvent(courseData.id))
        return courseData.id
    }

    // 코스 상세정보
    override fun getCourseById(courseId: UUID, userId: String): ResponseCourseDetailDTO {
        val courseStatus = listOf(CourseStatus.PUBLIC, CourseStatus.PRIVATE, CourseStatus.FILTERED)
        val optCourseData = courseRepository.findCourseWithTagsByIdAndStatuses(courseId, courseStatus)

        if (optCourseData.isPresent) {
            val course = optCourseData.get()

            if (course.maker.id != userId && course.status == CourseStatus.PRIVATE) {
                throw NotAuthorException("비공개 코스는 제작자만 조회할 수 있습니다.")
            }

            val isBookmarked = bookmarkRepository.existsByCourseIdAndUserId(courseId, userId)

            return CourseMapper.toResponseCourseDetailDTO(
                course = course,
                userId = userId,
                isBookmarked = isBookmarked,
                commentCount = getCommentCount(course.id)
            )
        } else {
            throw EntityNotFoundException("존재하지 않거나 삭제된 코스입니다.")
        }
    }

    // 코스 삭제
    @Transactional
    override fun deleteCourse(courseId: UUID, userId: String): String {
        val optCourseData = courseRepository.findById(courseId)
        if (optCourseData.isPresent) {
            val course = optCourseData.get()
            if (course.maker.id != userId) {
                throw NotAuthorException("코스 제작자가 아닙니다.")
            }
            course.status = CourseStatus.DELETED
            courseRepository.save(course)

            popularCourseRepository.findByCourseId(courseId).forEach {
                popularCourseRepository.delete(it)
            }

            bookmarkRepository.deleteByCourseId(courseId)

            return "코스 삭제 성공"
        } else {
            throw EntityNotFoundException("코스를 찾을 수 없습니다.")
        }
    }

    // 북마크 추가
    @Transactional
    override fun addBookmark(courseId: UUID, userId:String): String {
        val course = courseRepository.findById(courseId).orElse(null) ?: throw EntityNotFoundException("코스를 찾을 수 없습니다")
        val user = userApiService.getUserDataFromId(userId)

        if (course.maker.id == userId) {
            return "자신의 코스는 북마크할 수 없습니다."
        }

        val isBookmark = bookmarkRepository.existsByCourseIdAndUserId(courseId, userId)

        if (isBookmark) {
            return "이미 북마크된 코스입니다."
        }

        val tags = course.courseTags.map { it.tag }
        val tagLogs = tags.map { tag ->
            TagLog(tag = tag, user = user, actionType = ActionType.BOOKMARKED)
        }
        tagLogRepository.saveAll(tagLogs)

        bookmarkRepository.save(Bookmark(course = course, user = user))

        return "북마크 추가 성공"
    }

    // 북마크 삭제
    @Transactional
    override fun removeBookmark(courseId: UUID, userId:String): String {
        if (!bookmarkRepository.existsByCourseIdAndUserId(courseId, userId))
            throw EntityNotFoundException("북마크를 찾을 수 없습니다.")

        bookmarkRepository.deleteByCourseIdAndUserId(courseId, userId)

        return "북마크 삭제 성공"
    }


    // 코스 조회수 증가
    @Transactional
    override fun increaseHits(courseId: UUID): String {
        val course = courseRepository.findById(courseId).orElse(null) ?: throw EntityNotFoundException("코스를 찾을 수 없습니다.")
        course.hits.increaseHits()
        logger.debug("코스 ID: {}, 증가된 조회수: {}", courseId, course.hits)
        courseRepository.save(course)
        return "조회수 증가 성공"
    }

    // LLM 서버에 요청하여 코스 생성, 세션 ID를 통해 상태 메시지 전송
    override fun createCourseByLLM(llmRequestDTO: LlmRequestDTO, userId: String): List<AutoGeneratedCourseDTO> {
        val distanceUtil = DistanceUtil()
        val session = "/topic/status/${llmRequestDTO.statusSessionId}"

        val weather = weatherService.getWeatherByCity(llmRequestDTO.city, llmRequestDTO.nx, llmRequestDTO.ny)

        val condition = attendanceApiService.getAttendance(userId)?.bodyState
            ?: tendencyApiService.getTendency(userId)?.exerciseFrequency
            ?: "사용자 컨디션을 찾을 수 없습니다."

        val requestData = mapOf(
            "question" to llmRequestDTO.request,
            "lon" to llmRequestDTO.nx,
            "lat" to llmRequestDTO.ny,
            "weather" to weather.sky,
            "temperature" to weather.temperature,
            "condition" to condition
        )

        val restTemplate = RestTemplate()

        repeat(5) { attempt -> // 최대 5번 시도
            try {
                val response = restTemplate.postForEntity(llmServerUrl, requestData, Map::class.java)

                if (response.statusCode.is2xxSuccessful) {
                    val responseBody = response.body as Map<*, *>
                    val courses = responseBody["data"] as? List<Map<String, Any>> ?: emptyList()

                    return courses.map { data ->
                        val lon = (data["position"] as List<Double>)[0]
                        val lat = (data["position"] as List<Double>)[1]
                        val positionNode = GeoJsonUtil.point(lon, lat)
                        val coordinateNode = GeoJsonUtil.lineString(data["coordinate"] as List<List<Double>>)

                        // 좌표 리스트로 거리 계산, km 단위로 변환을 위해 1000으로 나눔
                        val coordinates = data["coordinate"] as List<List<Double>>
                        val totalDistance = coordinates.zipWithNext { start, end ->
                            distanceUtil.haversine(start[1], start[0], end[1], end[0])
                        }.sum() / 1000.0

                        val location = locationApiService.getNearestLocation(lon, lat)
                        val sido = location?.sido ?: "Unknown"
                        val sigungu = location?.sigungu ?: "Unknown"

                        val tags = (data["tags"] as List<String>).map { tagName ->
                            tagRepository.findByName(tagName) ?: tagRepository.save(Tag(name = tagName))
                        }

                        AutoGeneratedCourseDTO(
                            id = UUID.randomUUID(),
                            title = data["title"] as String,
                            distance = totalDistance.toFloat(),
                            position = positionNode,
                            coordinate = coordinateNode,
                            tag = tags,
                            sido = sido,
                            sigungu = sigungu
                        )
                    }
                }
            } catch (e: HttpServerErrorException) {
                if (e.statusCode.is5xxServerError) {
                    messagingTemplate.convertAndSend(
                        session,
                        StatusMessageDTO("RETRY", "서버 오류 발생, 재시도 중...", null)
                    )
                } else {
                    throw RuntimeException("LLM 요청 실패", e)
                }
            } catch (e: Exception) {
                if (attempt == 4) {
                    throw RuntimeException("LLM 요청 중 오류 발생", e)
                }
            }
        }
       throw IllegalStateException("LLM 요청이 실패하여 코스를 생성할 수 없습니다.")
    }

    // 사용자 관심 태그 기반 코스 추천
    fun getUserInterestedTags(userId: String): ResponseRecommendCourseDTO? {
        val interestTags = tagApiService.getPersonalizedTags(userId)
            .sortedByDescending { it.score } // score 기준으로 정렬

        if (interestTags.isEmpty()) return null

        val coursesByTags = mutableListOf<ResponseCourseDTO>()
        var startIndex = 0

        // 최소 3개의 코스를 찾을 때까지 반복
        while (coursesByTags.size < 3 && startIndex < interestTags.size) {
            val tag = interestTags[startIndex]
            val tagEntity = tagRepository.findByName(tag.name)
                ?: throw EntityNotFoundException("태그를 찾을 수 없습니다: ${tag.name}")

            val courseIds = courseRepository.findCourseIdsByTagIdExcludingUser(
                tagEntity.id, CourseStatus.PUBLIC, userId, PageRequest.of(0, 3)
            ).content

            if (courseIds.isNotEmpty()) {
                val courses = courseRepository.findCoursesWithTagsByIds(courseIds)
                val bookmarkedCourseIds = bookmarkRepository.findBookmarkedCourseIdsByUserIdAndCourseIds(userId, courseIds)

                coursesByTags.addAll(
                    courses.map { course ->
                        CourseMapper.toResponseCourseDTO(
                            course = course,
                            userId = userId,
                            isBookmarked = course.id in bookmarkedCourseIds,
                            commentCount = getCommentCount(course.id)
                        )
                    }
                )
            }
            startIndex++
        }

        if (coursesByTags.size < 3) return null

        val uniqueCourse = coursesByTags.shuffled().distinctBy { it.id }

        val courseSummaries = uniqueCourse.map { course ->
            CourseMapper.toCourseSummaryDTO(dto = course)
        }

        return ResponseRecommendCourseDTO(
            title = "🎯 이런 코스들은 어때요?",
            item = courseSummaries
        )
    }


    // 홈에 아무 것도 안 뜰때를 대비한 전체 코스 반환
    fun getAllCoursesForHome(userId: String): ResponseRecommendCourseDTO {
        val allCourse = courseQueryService.getAllCourses(userId, PageRequest.of(0, 10))
        val courseSummaries = allCourse.content.map { course ->
            CourseMapper.toCourseSummaryDTO(dto = course)
        }

        return ResponseRecommendCourseDTO(
            title = "🗺️ 추천 코스에요!",
            item = courseSummaries
        )
    }

    // 추천 코스 리스트
    override fun getCombinedRecommendCourses(nx: Double, ny:Double, city: String, userId: String): List<ResponseRecommendCourseDTO> {
        val nearCourseByDifficulty = courseQueryService.getNearbyCoursesByDifficulty(nx, ny, city, userId)
        val recentCourse = courseQueryService.getRecentCourses(userId)
        val popularCourse = courseQueryService.getPopularCourses()
        val risingCourse = courseQueryService.getRisingCourse()
        val userInterestedTags = getUserInterestedTags(userId)

        // 최근 코스, 인기 코스, 급상승 코스, 관심 태그 코스가 모두 null인 경우
        if (recentCourse == null && popularCourse == null && risingCourse == null && userInterestedTags == null) {
            return listOf(getAllCoursesForHome(userId), courseQueryService.getRecentCreatedCourses())
        }

        // 필요한 코스 데이터를 리스트로 추가
        return listOfNotNull( nearCourseByDifficulty, userInterestedTags, recentCourse, popularCourse, risingCourse)
            .distinctBy { it.title } // 제목 기준으로 중복 제거
    }
}
