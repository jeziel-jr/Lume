package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalControlDataStoreTest {

    private class StoreHarness(scope: CoroutineScope) {
        val file = File.createTempFile("parental-test", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = ParentalControlDataStore(dataStore)
    }

    @Test
    fun `definePin accepts only four digits`() = runTest {
        val harness = StoreHarness(CoroutineScope(Dispatchers.IO + SupervisorJob()))
        try {
            assertFalse(harness.store.definePin("123"))
            assertFalse(harness.store.definePin("12345"))
            assertFalse(harness.store.definePin("12ab"))
            assertTrue(harness.store.definePin("1234"))
            assertTrue(harness.store.settings.first().pinSet)
        } finally {
            harness.file.delete()
        }
    }

    @Test
    fun `verifyPin matches only the stored pin`() = runTest {
        val harness = StoreHarness(CoroutineScope(Dispatchers.IO + SupervisorJob()))
        try {
            harness.store.definePin("1234")
            assertTrue(harness.store.verifyPin("1234"))
            assertFalse(harness.store.verifyPin("4321"))
            assertFalse(harness.store.verifyPin("12"))
        } finally {
            harness.file.delete()
        }
    }

    @Test
    fun `stored values never contain the plain pin`() = runTest {
        val harness = StoreHarness(CoroutineScope(Dispatchers.IO + SupervisorJob()))
        try {
            harness.store.definePin("4321")
            val values = harness.dataStore.data.first().asMap().values
            assertTrue(values.none { it.toString() == "4321" })
        } finally {
            harness.file.delete()
        }
    }

    @Test
    fun `clearPin removes pin state`() = runTest {
        val harness = StoreHarness(CoroutineScope(Dispatchers.IO + SupervisorJob()))
        try {
            harness.store.definePin("1234")
            harness.store.clearPin()
            assertFalse(harness.store.settings.first().pinSet)
            assertFalse(harness.store.verifyPin("1234"))
        } finally {
            harness.file.delete()
        }
    }

    @Test
    fun `adult content defaults hidden and can be toggled`() = runTest {
        val harness = StoreHarness(CoroutineScope(Dispatchers.IO + SupervisorJob()))
        try {
            assertTrue(harness.store.settings.first().adultContentHidden)
            harness.store.setAdultContentHidden(false)
            assertFalse(harness.store.settings.first().adultContentHidden)
            harness.store.setAdultContentHidden(true)
            assertTrue(harness.store.settings.first().adultContentHidden)
        } finally {
            harness.file.delete()
        }
    }
}
