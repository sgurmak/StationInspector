package com.example.stationinspector.data.repository

import com.example.stationinspector.domain.model.PoiLocation
import javax.inject.Inject
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.UUID

data class MapyCzResponse(val items: List<MapyCzItem>)
data class MapyCzItem(
    val name: String,
    val label: String?,
    val position: MapyCzPosition,
    val regionalStructure: List<MapyCzRegion>? = null
)
data class MapyCzRegion(val name: String, val type: String)
data class MapyCzPosition(val lon: Double, val lat: Double)

interface MapyCzApi {
    @GET("v1/geocode")
    suspend fun geocode(
        @Query("query") query: String
    ): MapyCzResponse
}

interface MapyCzRepository {
    suspend fun searchLocation(query: String): List<PoiLocation>
}

class MapyCzRepositoryImpl @Inject constructor(
    private val api: MapyCzApi
) : MapyCzRepository {
    override suspend fun searchLocation(query: String): List<PoiLocation> {
        if (query.isBlank()) return emptyList()
        val response = api.geocode(query)
        return response.items.map { item ->
            val structure = item.regionalStructure
            val city = structure?.find { it.type == "regional.municipality" }?.name
            val street = structure?.find { it.type == "regional.street" }?.name
            val addressNum = structure?.find { it.type == "regional.address" }?.name
            val region = structure?.find { it.type == "regional.region" || it.type == "regional.district" }?.name

            val addr = listOfNotNull(street, addressNum).joinToString(" ").takeIf { it.isNotBlank() }

            PoiLocation(
                id = UUID.randomUUID().toString(),
                name = item.name,
                city = city,
                address = addr,
                region = region,
                latitude = item.position.lat,
                longitude = item.position.lon
            )
        }
    }
}
