package com.example.stationinspector.domain.repository

import com.example.stationinspector.domain.model.Poi
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Single source of truth for Points of Interest persisted in the daily route.
 */
interface PoiRepository {

    fun observePoisForDate(date: LocalDate): Flow<List<Poi>>

    suspend fun getPoisForDate(date: LocalDate): List<Poi>

    suspend fun insertPoi(poi: Poi)

    suspend fun updateOrder(id: String, orderIndex: Int)

    /** Bulk-update orderIndex for many POIs in one transaction (see PoiDao). */
    suspend fun updateOrders(orders: List<Pair<String, Int>>)

    suspend fun deletePoi(id: String)

    suspend fun deletePoisByNameAndDate(name: String, date: LocalDate)
}
