package com.example.stationinspector.data.repository

import com.example.stationinspector.data.local.dao.PoiDao
import com.example.stationinspector.data.mapper.toDomain
import com.example.stationinspector.data.mapper.toEntity
import com.example.stationinspector.domain.model.Poi
import com.example.stationinspector.domain.repository.PoiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoiRepositoryImpl @Inject constructor(
    private val dao: PoiDao
) : PoiRepository {

    override fun observePoisForDate(date: LocalDate): Flow<List<Poi>> =
        dao.getPoisForDate(date).map { list -> list.map { it.toDomain() } }

    override suspend fun getPoisForDate(date: LocalDate): List<Poi> =
        dao.getPoisForDateSync(date).map { it.toDomain() }

    override suspend fun insertPoi(poi: Poi) {
        dao.insertPoi(poi.toEntity())
    }

    override suspend fun updateOrder(id: String, orderIndex: Int) {
        dao.updatePoiOrder(id, orderIndex)
    }

    override suspend fun updateOrders(orders: List<Pair<String, Int>>) {
        dao.updatePoiOrders(orders)
    }

    override suspend fun deletePoi(id: String) {
        dao.deletePoi(id)
    }

    override suspend fun deletePoisByNameAndDate(name: String, date: LocalDate) {
        dao.deletePoisByNameAndDate(name, date)
    }
}
