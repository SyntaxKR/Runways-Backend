package syntax.backend.runways.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import syntax.backend.runways.dto.RoadDataDTO
import syntax.backend.runways.service.RoadApiService

@RestController
@RequestMapping("api/road")
class RoadApiController(
    private val roadApiService: RoadApiService
) {

    @GetMapping("/{id}")
    fun getRoadData(@PathVariable id:Long): ResponseEntity<RoadDataDTO> {
        val roadData = roadApiService.getRoadDataById(id)
        return ResponseEntity.ok(roadData)
    }

    @PostMapping("/save")
    fun saveRoadData(@RequestBody roadDataDTO: RoadDataDTO): ResponseEntity<String> {
        roadApiService.saveRoadData(roadDataDTO)
        return ResponseEntity.ok("Road data saved successfully")
    }
}