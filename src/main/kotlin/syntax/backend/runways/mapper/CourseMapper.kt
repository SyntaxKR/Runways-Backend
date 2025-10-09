package syntax.backend.runways.mapper

import syntax.backend.runways.dto.*
import syntax.backend.runways.entity.*
import syntax.backend.runways.util.GeoJsonUtil

object CourseMapper {

    fun toResponseCourseDTO(
        course: Course,
        userId: String,
        isBookmarked: Boolean,
        commentCount: Int
    ): ResponseCourseDTO {
        return ResponseCourseDTO(
            id = course.id,
            title = course.title,
            maker = course.maker,
            bookmark = isBookmarked,
            hits = course.hits,
            distance = course.distance,
            position = GeoJsonUtil.writeAndRemoveCrs(course.position),
            coordinate = GeoJsonUtil.writeAndRemoveCrs(course.coordinate),
            mapUrl = course.mapUrl,
            createdAt = course.createdAt,
            updatedAt = course.updatedAt,
            author = course.maker.id == userId,
            status = course.status,
            tag = course.courseTags.map { it.tag },
            sido = course.sido,
            sigungu = course.sigungu,
            commentCount = commentCount,
            usageCount = course.usageCount
        )
    }

    fun toResponseMyCourseDTO(
        course: Course,
        userId: String,
        bookmarkCount: Int,
        commentCount: Int
    ): ResponseMyCourseDTO {
        return ResponseMyCourseDTO(
            id = course.id,
            title = course.title,
            maker = course.maker,
            bookmark = true,
            bookmarkCount = bookmarkCount,
            hits = course.hits,
            position = GeoJsonUtil.writeAndRemoveCrs(course.position),
            coordinate = GeoJsonUtil.writeAndRemoveCrs(course.coordinate),
            distance = course.distance,
            mapUrl = course.mapUrl,
            createdAt = course.createdAt,
            updatedAt = course.updatedAt,
            author = course.maker.id == userId,
            status = course.status,
            tag = course.courseTags.map { it.tag },
            sido = course.sido,
            sigungu = course.sigungu,
            commentCount = commentCount,
            usageCount = course.usageCount
        )
    }

    fun toResponseCourseDetailDTO(
        course: Course,
        userId: String,
        isBookmarked: Boolean,
        commentCount: Int
    ): ResponseCourseDetailDTO {
        return ResponseCourseDetailDTO(
            id = course.id,
            title = course.title,
            maker = course.maker,
            bookmark = isBookmarked,
            hits = course.hits,
            distance = course.distance,
            position = GeoJsonUtil.writeAndRemoveCrs(course.position),
            coordinate = GeoJsonUtil.writeAndRemoveCrs(course.coordinate),
            mapUrl = course.mapUrl,
            createdAt = course.createdAt,
            updatedAt = course.updatedAt,
            author = course.maker.id == userId,
            status = course.status,
            tag = course.courseTags.map { it.tag },
            sido = course.sido,
            sigungu = course.sigungu,
            commentCount = commentCount,
            usageCount = course.usageCount
        )
    }

    fun toCourseSummaryDTO(course: Course): CourseSummary {
        return CourseSummary (
            id = course.id,
            title = course.title,
            distance = course.distance,
            mapUrl = course.mapUrl,
            sido = course.sido,
            sigungu = course.sigungu,
            tags = course.courseTags.map { it.tag.name },
            usageCount = course.usageCount
        )
    }

    fun toCourseSummaryDTO(dto: ResponseCourseDTO): CourseSummary {
        return CourseSummary(
            id = dto.id,
            title = dto.title,
            distance = dto.distance,
            mapUrl = dto.mapUrl,
            sido = dto.sido,
            sigungu = dto.sigungu,
            tags = dto.tag.map { it.name },
            usageCount = dto.usageCount
        )
    }

}
