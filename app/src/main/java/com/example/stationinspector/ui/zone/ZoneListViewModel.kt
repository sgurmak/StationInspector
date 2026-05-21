package com.example.stationinspector.ui.zone

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.domain.model.PhotoZone
import com.example.stationinspector.domain.model.StationStatus
import com.example.stationinspector.domain.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ── Domain data class exposed to the UI ───────────────────────────────────────

data class ZoneWithStats(
    val zone: PhotoZone,
    val displayName: String,
    /** Photos of type CLIENT_REPORT only */
    val photoCount: Int,
    /** Photos of type INTERNAL_DEFECT only */
    val issueCount: Int
)

// ── ViewModel ──────────────────────────────────────────────────────────────────

@HiltViewModel
class ZoneListViewModel @Inject constructor(
    private val stationRepository: StationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val stationId: Long =
        savedStateHandle.get<String>("stationId")?.toLongOrNull() ?: -1L

    // ── Station name (reactive from DB) ───────────────────────────────────────
    val stationName: StateFlow<String> =
        stationRepository.getStationById(stationId)
            .map { it?.name ?: "—" }
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.Eagerly,   // never stop observing
                initialValue = "…"
            )

    // ── Per-zone stats via independent zone-scoped DB queries ─────────────────
    // BUG FIX 1 — Counter logic:
    //   photoCount = CLIENT_REPORT count  (not total)
    //   issueCount = INTERNAL_DEFECT count
    //
    // BUG FIX 2 — Persistence:
    //   SharingStarted.Eagerly keeps the DB Flow alive even when the screen
    //   leaves composition briefly (e.g. navigating to Camera and back).
    //   WhileSubscribed(5000) was cancelling the upstream after 5 s away,
    //   making the counters appear to reset.
    val zonesWithCounts: StateFlow<List<ZoneWithStats>> = run {
        val zoneFlows = INSPECTION_ZONES.map { (zone, displayName) ->
            stationRepository
                .getPhotosByStationAndZone(stationId, zone.name)
                .map { photos ->
                    val safeList = photos ?: emptyList()
                    ZoneWithStats(
                        zone        = zone,
                        displayName = displayName,
                        // ← FIX: count only CLIENT_REPORT as "regular photos"
                        photoCount  = safeList.count { it.type == PhotoType.CLIENT_REPORT },
                        // ← FIX: count only INTERNAL_DEFECT as "issues"
                        issueCount  = safeList.count { it.type == PhotoType.INTERNAL_DEFECT }
                    )
                }
        }

        combine(zoneFlows[0], zoneFlows[1], zoneFlows[2]) { s0, s1, s2 ->
            listOf(s0, s1, s2)
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.Eagerly,   // never drop the subscription
            initialValue = INSPECTION_ZONES.map { (zone, displayName) ->
                ZoneWithStats(zone, displayName, 0, 0)
            }
        )
    }

    // ── Total photo count across all zones (all types, for the Confirm button) ──
    val totalPhotoCount: StateFlow<Int> =
        stationRepository.getPhotosForStation(stationId)
            .map { it?.size ?: 0 }
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.Eagerly,
                initialValue = 0
            )

    // ── Mark station as Completed ──────────────────────────────────────────────
    fun markStationCompleted() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val station = stationRepository.getStationById(stationId).first()
                station?.let {
                    stationRepository.saveStation(it.copy(status = StationStatus.COMPLETED))
                }
            }
        }
    }

    companion object {
        /**
         * The 3 inspection zones shown in the UI, in display order.
         * In companion object → class-load-time initialization → no NPE when
         * used as a StateFlow initialValue during instance construction.
         */
        val INSPECTION_ZONES: List<Pair<PhotoZone, String>> = listOf(
            PhotoZone.ENTRANCE to "Вокзал",
            PhotoZone.PLATFORM to "Зона очікування",
            PhotoZone.RESTROOM to "WC"
        )
    }
}