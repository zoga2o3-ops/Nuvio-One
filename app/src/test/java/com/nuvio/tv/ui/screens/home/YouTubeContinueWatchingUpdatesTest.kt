package com.nuvio.tv.ui.screens.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shape of the chain Home uses to keep its OneTube Continue Watching row current.
 *
 * Reported: the row only caught up after restarting the app. Watching on bumped
 * YouTubeProgress.changes, but Home's value was only "is the row on" — identical on every bump —
 * so distinctUntilChanged() dropped it and the row was never rebuilt from the new position.
 *
 * These tests pin the flow semantics that caused it, so the "drop the counters early" shape isn't
 * reintroduced.
 */
class YouTubeContinueWatchingUpdatesTest {

    /** Counters dropped before the filter: every bump looks the same, so it's swallowed. */
    private fun chainWithoutCounters(settings: MutableStateFlow<Int>, progress: MutableStateFlow<Int>): Flow<Boolean> =
        combine(settings, progress, MutableStateFlow(true)) { _, _, on -> on }
            .distinctUntilChanged()

    /** Counters kept in the value: every bump is distinct, so Home rebuilds the row. */
    private fun chainWithCounters(settings: MutableStateFlow<Int>, progress: MutableStateFlow<Int>): Flow<Boolean> =
        combine(settings, progress, MutableStateFlow(true)) { s, p, on -> Triple(s, p, on) }
            .distinctUntilChanged()
            .let { upstream -> flow { upstream.collect { emit(it.third) } } }

    /** Collects while bumping each counter, and returns what Home would have seen. */
    private fun TestScope.emissionsOf(chain: (MutableStateFlow<Int>, MutableStateFlow<Int>) -> Flow<Boolean>): List<Boolean> {
        val settings = MutableStateFlow(0)
        val progress = MutableStateFlow(0)
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { chain(settings, progress).toList(seen) }
        runCurrent()
        progress.value = 1
        runCurrent()
        progress.value = 2
        runCurrent()
        settings.value = 1
        runCurrent()
        job.cancel()
        return seen
    }

    @Test
    fun `keeping the counters lets every change through`() = runTest {
        assertEquals(listOf(true, true, true, true), emissionsOf(::chainWithCounters))
    }

    @Test
    fun `dropping the counters swallows them and needs a restart`() = runTest {
        assertEquals(listOf(true), emissionsOf(::chainWithoutCounters))
    }
}
