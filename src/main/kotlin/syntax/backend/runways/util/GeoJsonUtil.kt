package syntax.backend.runways.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.geojson.GeoJsonWriter

object GeoJsonUtil {
    private val objectMapper = ObjectMapper()
    private val geoJsonWriter = GeoJsonWriter()

    // GeoJSON 변환
    fun point(lon: Double, lat: Double): ObjectNode {
        return objectMapper.createObjectNode().apply {
            put("type", "Point")
            putArray("coordinates").add(lon).add(lat)
        }
    }

    // GeoJSON LineString 변환
    fun lineString(coords: List<List<Double>>): ObjectNode {
        return objectMapper.createObjectNode().apply {
            put("type", "LineString")
            val array = putArray("coordinates")
            coords.forEach { coord ->
                array.addArray().add(coord[0]).add(coord[1])
            }
        }
    }

    // CRS 필드 제거
    fun removeCrs(geoJson: String): ObjectNode {
        val objectMapper = ObjectMapper()
        val node = objectMapper.readTree(geoJson) as ObjectNode
        node.remove("crs")
        return node
    }

    fun writeAndRemoveCrs(geometry: Geometry): ObjectNode {
        val json = geoJsonWriter.write(geometry)
        return removeCrs(json)
    }
}
