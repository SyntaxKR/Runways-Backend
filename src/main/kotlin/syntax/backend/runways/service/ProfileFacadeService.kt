package syntax.backend.runways.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import syntax.backend.runways.dto.ResponseMyInfoDTO
import syntax.backend.runways.dto.UserProfileWithCoursesDTO
import syntax.backend.runways.entity.User

@Service
class ProfileFacadeService(
    private val userApiService: UserApiService,
    private val courseQueryService: CourseQueryService
) {
    fun getMyProfile(userId: String, pageable: Pageable): ResponseMyInfoDTO {
        val user: User = userApiService.getUserDataFromId(userId)
        val courses = courseQueryService.getCourseList(userId, pageable, false)

        return ResponseMyInfoDTO(
            id = user.id,
            name = user.name,
            email = user.email,
            platform = user.platform,
            profileImage = user.profileImageUrl,
            birthDate = user.birthdate,
            gender = user.gender,
            nickname = user.nickname,
            follow = user.follow,
            marketing = user.marketing,
            accountPrivate = user.accountPrivate,
            courses = courses,
            experience = user.experience
        )
    }

    fun getOtherUserProfile(senderId: String, receiverId: String, pageable: Pageable): UserProfileWithCoursesDTO {
        val user = userApiService.getUserDataFromId(receiverId)
        val isFollowing = user.follow.isFollower(senderId)
        val courses = if (user.accountPrivate) {
            org.springframework.data.domain.Page.empty(pageable)
        } else {
            courseQueryService.getCourseList(receiverId, pageable, true)
        }

        return UserProfileWithCoursesDTO(
            profileImage = user.profileImageUrl,
            nickname = user.nickname,
            follow = user.follow,
            accountPrivate = user.accountPrivate,
            courses = courses,
            isFollow = isFollowing,
            experience = user.experience
        )
    }
}
