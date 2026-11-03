package com.nuvio.tv.livetv.ondemand

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandLookupTest {
    @Test
    fun `autoplay waits for the provider result`() = runTest {
        val results = mutableListOf<List<String>>()
        val job = launch { onDemandLookup(true) { delay(1_000); listOf("provider") }.collect { results += it } }
        runCurrent()
        assertEquals(emptyList<List<String>>(), results)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(listOf("provider")), results)
        job.join()
    }

    @Test
    fun `normal browsing receives addon results immediately`() = runTest {
        val results = mutableListOf<List<String>>()
        val job = launch { onDemandLookup(false) { delay(1_000); listOf("provider") }.collect { results += it } }
        runCurrent()
        assertEquals(listOf(emptyList<String>()), results)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(emptyList<String>(), listOf("provider")), results)
        job.join()
    }

    @Test
    fun `autoplay falls back after provider timeout`() = runTest {
        val results = mutableListOf<List<String>>()
        val job = launch { onDemandLookup<String>(true) { delay(20_000); listOf("provider") }.collect { results += it } }
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(listOf(emptyList<String>()), results)
        job.join()
    }
}
