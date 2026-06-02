package com.example.stationinspector.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens the device's default maps application centered on [lat]/[lon], dropping a
 * pin labelled [label]. If no maps app is installed, shows [notFoundMessage].
 *
 * Single source of truth for the `geo:` deep-link previously duplicated in
 * NavGraph and StationListScreen.
 */
fun Context.openInMaps(
    lat: Double,
    lon: Double,
    label: String,
    notFoundMessage: String = "No map application found"
) {
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, notFoundMessage, Toast.LENGTH_SHORT).show()
    }
}
