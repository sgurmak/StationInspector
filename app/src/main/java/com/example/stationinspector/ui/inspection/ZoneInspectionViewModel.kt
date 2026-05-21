package com.example.stationinspector.ui.inspection

import android.graphics.Bitmap
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationinspector.camera.CameraXController
import com.example.stationinspector.data.storage.FileStorageManager
import com.example.stationinspector.domain.model.Photo
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.camera.ImageCompressor
import com.example.stationinspector.domain.model.PhotoZone
import com.example.stationinspector.domain.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  Per-zone photo count snapshot exposed to the UI
// ─────────────────────────────────────────────────────────────────────────────

data class ZonePhotoCount(
    val zone:         PhotoZone,
    val ordinaryCount: Int,
    val defectCount:   Int
)

@HiltViewModel
class ZoneInspectionViewModel @Inject constructor(
    private val stationRepository: StationRepository,
    private val fileStorageManager: FileStorageManager,
    private val cameraXController: CameraXController,
    private val imageCompressor: ImageCompressor,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ── Route arguments ───────────────────────────────────────────────────────
    val currentStationId: Long =
        savedStateHandle.get<String>("stationId")?.toLongOrNull() ?: -1L

    // ── Station name (loaded once for display in the top bar) ─────────────────
    val stationName: StateFlow<String> =
        stationRepository.getStationById(currentStationId)
            .map { station -> station?.name ?: "" }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // ── Selectable zone (UI-driven, defaults to ENTRANCE) ────────────────────
    private val _selectedZone = MutableStateFlow(PhotoZone.ENTRANCE)
    val selectedZone: StateFlow<PhotoZone> = _selectedZone

    fun selectZone(zone: PhotoZone) { _selectedZone.value = zone }

    // ── All photos for this station (cross-zone, drives per-zone counters) ────
    private val allPhotosFlow = stationRepository.getPhotosForStation(currentStationId)

    /** Per-zone counts for all zones — drives the zone selector strip. */
    val zoneCounts: StateFlow<List<ZonePhotoCount>> =
        allPhotosFlow
            .map { photos ->
                PhotoZone.entries.map { zone ->
                    val zonePhotos = photos.filter { it.zone == zone }
                    ZonePhotoCount(
                        zone          = zone,
                        ordinaryCount = zonePhotos.count { it.type != PhotoType.INTERNAL_DEFECT },
                        defectCount   = zonePhotos.count { it.type == PhotoType.INTERNAL_DEFECT }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Photos for the CURRENTLY selected zone (drives thumbnail + flash) ─────
    val photos: StateFlow<List<Photo>> =
        combine(allPhotosFlow, _selectedZone) { allPhotos, zone ->
            allPhotos.filter { it.zone == zone }
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // ── Camera ────────────────────────────────────────────────────────────────

    fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        viewModelScope.launch {
            cameraXController.startCamera(lifecycleOwner, surfaceProvider)
        }
    }

    fun onZoomChange(zoomChange: Float) {
        val info = cameraXController.getCameraInfo() ?: return
        val currentState = info.zoomState.value ?: return
        val currentZoom = currentState.zoomRatio
        val maxZoom = currentState.maxZoomRatio
        val minZoom = currentState.minZoomRatio
        val newZoom = (currentZoom * zoomChange).coerceIn(minZoom, maxZoom)
        cameraXController.setZoomRatio(newZoom)
    }

    fun setFlashMode(flashMode: Int) {
        cameraXController.setFlashMode(flashMode)
    }

    /** Capture a photo tagged to the CURRENTLY selected zone. */
    fun capturePhoto(type: PhotoType) {
        val zone = _selectedZone.value
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap         = cameraXController.takePicture() ?: return@launch
            val compressed     = imageCompressor.compress(bitmap, type)
            val absolutePath   = fileStorageManager.savePhoto(compressed) ?: return@launch

            val station        = stationRepository.getStationById(currentStationId).firstOrNull()
            val assignedDateStr = station?.inspectionDate?.toString()
                ?: java.time.LocalDate.now().toString()

            stationRepository.savePhoto(
                Photo(
                    id           = 0,
                    stationId    = currentStationId,
                    zone         = zone,   // ← always the UI-selected zone
                    type         = type,
                    localPath    = absolutePath,
                    timestamp    = System.currentTimeMillis(),
                    description  = "",
                    exported     = false,
                    assignedDate = assignedDateStr
                )
            )
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val photo = photos.value.find { it.id == photoId } ?: return@launch
            fileStorageManager.deletePhoto(photo.localPath)
            stationRepository.deletePhoto(photo)
        }
    }
}