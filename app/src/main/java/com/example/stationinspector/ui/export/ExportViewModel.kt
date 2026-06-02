package com.example.stationinspector.ui.export

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.stationinspector.domain.repository.StationRepository
import com.example.stationinspector.worker.ExportZipWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.example.stationinspector.domain.model.Photo

enum class ExportState {
    IDLE,
    RUNNING,
    SUCCESS,
    ERROR
}

data class ExportViewState(
    val unexportedCount: Int = 0,
    val exportState: ExportState = ExportState.IDLE,
    val zipUri: String? = null,
    val photos: List<Photo> = emptyList()
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val stationRepository: StationRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private var workObserverJob: kotlinx.coroutines.Job? = null
    private val _exportState = MutableStateFlow<Pair<ExportState, String?>>(ExportState.IDLE to null)
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)

    private val unexportedPhotosFlow = stationRepository.getAllPhotos()

    fun setSelectedDate(dateStr: String) {
        try {
            _selectedDate.value = LocalDate.parse(dateStr)
        } catch (e: Exception) {
            Log.w("ExportViewModel", "Invalid export date '$dateStr'", e)
        }
    }

    val state: StateFlow<ExportViewState> = combine(
        unexportedPhotosFlow,
        _exportState,
        _selectedDate
    ) { allPhotos, exportData, selectedDate ->
        val filteredPhotos = if (selectedDate != null) {
            allPhotos.filter { it.assignedDate == selectedDate.toString() }
        } else {
            emptyList()
        }
        ExportViewState(
            unexportedCount = filteredPhotos.size,
            exportState     = exportData.first,
            zipUri          = exportData.second,
            photos          = filteredPhotos
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExportViewState()
    )

    fun resetState() {
        _exportState.value = ExportState.IDLE to null
    }

    fun startExport() {
        val exportDateStr = _selectedDate.value?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            ?: return  // no date selected — nothing to export

        _exportState.value = ExportState.RUNNING to null
        workObserverJob?.cancel()

        viewModelScope.launch {
            val workRequest = OneTimeWorkRequestBuilder<ExportZipWorker>()
                .setInputData(workDataOf("export_date" to exportDateStr))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                "ExportZipWork",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            // Strictly observe only the actively triggered request avoiding history bleeds
            workObserverJob = workManager.getWorkInfoByIdFlow(workRequest.id)
                .onEach { workInfo ->
                    when (workInfo?.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                            _exportState.value = ExportState.RUNNING to null
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val uri = workInfo.outputData.getString("zip_uri")
                            _exportState.value = ExportState.SUCCESS to uri
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _exportState.value = ExportState.ERROR to null
                        }
                        else -> {}
                    }
                }
                .launchIn(viewModelScope)
        }
    }
}