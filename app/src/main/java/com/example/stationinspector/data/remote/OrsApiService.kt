package com.example.stationinspector.data.remote

import com.example.stationinspector.data.remote.dto.DirectionsRequest
import com.example.stationinspector.data.remote.dto.DirectionsResponse
import com.example.stationinspector.data.remote.dto.OptimizationRequest
import com.example.stationinspector.data.remote.dto.OptimizationResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import com.example.stationinspector.data.remote.dto.GeocodeResponse

interface OrsApiService {
    @POST("v2/directions/driving-car")
    suspend fun getDirections(@Body request: DirectionsRequest): DirectionsResponse

    @POST("optimization")
    suspend fun getOptimization(@Body request: OptimizationRequest): OptimizationResponse

    @GET("v2/geocode/search")
    suspend fun searchGeocode(@Query("text") text: String): GeocodeResponse
}
