package com.example.stationinspector.utils

import org.osmdroid.util.GeoPoint

object PolylineUtils {
    /**
     * Decodes an encoded polyline string (precision 5) to a list of GeoPoints.
     */
    fun decode(encodedPath: String): List<GeoPoint> {
        val len = encodedPath.length
        val path = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < len) {
            var result = 1
            var shift = 0
            var b: Int
            do {
                b = encodedPath[index++].code - 63 - 1
                result += b shl shift
                shift += 5
            } while (b >= 0x1f)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 1
            shift = 0
            do {
                b = encodedPath[index++].code - 63 - 1
                result += b shl shift
                shift += 5
            } while (b >= 0x1f)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            path.add(GeoPoint(lat / 1E5, lng / 1E5))
        }

        return path
    }
}
