package com.example.stationinspector.data.repository

import com.example.stationinspector.domain.model.RouteWaypoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [RouteRepositoryImpl.reconstructOptimizedOrder] — the pure
 * order/round-trip reconstruction extracted from the ORS optimize flow.
 */
class RouteRepositoryImplTest {

    private fun wp(id: String, lat: Double, lon: Double) =
        RouteWaypoint(id = id, isStation = false, latitude = lat, longitude = lon)

    @Test
    fun `linear route keeps start and end, applies optimizer order to the middle`() {
        // A,B,C,D with A != D -> not a round trip; B,C,D are jobs 1,2,3.
        val a = wp("A", 50.0, 14.0)
        val b = wp("B", 50.1, 14.1)
        val c = wp("C", 50.2, 14.2)
        val d = wp("D", 50.3, 14.3)
        val result = RouteRepositoryImpl.reconstructOptimizedOrder(listOf(a, b, c, d), listOf(3, 1, 2))

        // start A fixed; jobs reordered to D,B,C.
        assertEquals(listOf(a, d, b, c), result)
    }

    @Test
    fun `round trip keeps both home anchors and reorders only the middle`() {
        // Home start + Home end share coordinates -> round trip.
        val homeStart = wp("home-start", 50.0, 14.0)
        val b = wp("B", 50.1, 14.1)
        val c = wp("C", 50.2, 14.2)
        val homeEnd = wp("home-end", 50.0, 14.0)
        val waypoints = listOf(homeStart, b, c, homeEnd)

        val result = RouteRepositoryImpl.reconstructOptimizedOrder(waypoints, listOf(2, 1))

        // first & last home stay anchored; intermediate becomes C,B.
        assertEquals(listOf(homeStart, c, b, homeEnd), result)
    }

    @Test
    fun `round trip identity order is unchanged`() {
        val homeStart = wp("home-start", 49.0, 16.0)
        val b = wp("B", 49.1, 16.1)
        val c = wp("C", 49.2, 16.2)
        val homeEnd = wp("home-end", 49.0, 16.0)
        val waypoints = listOf(homeStart, b, c, homeEnd)

        val result = RouteRepositoryImpl.reconstructOptimizedOrder(waypoints, listOf(1, 2))

        assertEquals(waypoints, result)
    }

    @Test
    fun `out-of-range job ids are dropped, not crashing`() {
        val a = wp("A", 50.0, 14.0)
        val b = wp("B", 50.1, 14.1)
        val c = wp("C", 50.2, 14.2)
        // not a round trip (A != C); jobs are B=1, C=2; id 9 is invalid.
        val result = RouteRepositoryImpl.reconstructOptimizedOrder(listOf(a, b, c), listOf(2, 9, 1))

        assertEquals(listOf(a, c, b), result)
    }

    @Test
    fun `fewer than two waypoints returns input unchanged`() {
        val a = wp("A", 50.0, 14.0)
        assertEquals(listOf(a), RouteRepositoryImpl.reconstructOptimizedOrder(listOf(a), emptyList()))
        assertEquals(emptyList<RouteWaypoint>(), RouteRepositoryImpl.reconstructOptimizedOrder(emptyList(), emptyList()))
    }
}
