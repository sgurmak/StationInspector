package com.example.stationinspector.ui.screens

import com.example.stationinspector.domain.repository.PoiRepository
import com.example.stationinspector.domain.repository.PreferencesRepository
import com.example.stationinspector.domain.repository.RouteRepository
import com.example.stationinspector.domain.repository.ShortcutRepository
import com.example.stationinspector.domain.repository.StationRepository
import com.example.stationinspector.domain.repository.TransactionRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Deterministic unit tests for [RouteViewModel]. Enabled by two refactors:
 * the dependency-cycle break (repos are domain interfaces, easily faked) and
 * the injected IO dispatcher (all VM work runs on the single test scheduler).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val stationRepository = mockk<StationRepository>(relaxed = true)
    private val routeRepository = mockk<RouteRepository>(relaxed = true)
    private val poiRepository = mockk<PoiRepository>(relaxed = true)
    private val preferencesRepository = mockk<PreferencesRepository>(relaxed = true)
    private val shortcutRepository = mockk<ShortcutRepository>(relaxed = true)
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { stationRepository.getAllStationsWithSplitCounts() } returns flowOf(emptyList())
        every { poiRepository.observePoisForDate(any()) } returns flowOf(emptyList())
        every { preferencesRepository.isRoundTripEnabled } returns flowOf(false)
        every { shortcutRepository.observeShortcuts() } returns flowOf(emptyList())
        coEvery { stationRepository.seedCoordinatesIfMissing() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = RouteViewModel(
        stationRepository, routeRepository, poiRepository,
        preferencesRepository, shortcutRepository, transactionRunner, dispatcher
    )

    @Test
    fun `onDateSelected updates the selected date`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val date = LocalDate.of(2026, 2, 1)
        vm.onDateSelected(date)

        assertEquals(date, vm.selectedDate.value)
    }

    @Test
    fun `setRoundTripEnabled persists the preference`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.setRoundTripEnabled(true)
        advanceUntilIdle()

        coVerify { preferencesRepository.setRoundTripEnabled(true) }
    }

    @Test
    fun `optimizeRoute with too few points warns and never calls the optimizer`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<UiEvent>()
        val collector = launch { vm.uiEvent.collect { events.add(it) } }
        advanceUntilIdle()

        vm.optimizeRoute() // routeItems is empty -> fewer than 3 valid points
        advanceUntilIdle()
        collector.cancel()

        assertTrue(
            "expected a 'too few points' snackbar",
            events.any { it is UiEvent.ShowSnackbar && it.message.contains("3 valid") }
        )
        coVerify(exactly = 0) { routeRepository.optimizeAndFetchGeometry(any()) }
    }
}
