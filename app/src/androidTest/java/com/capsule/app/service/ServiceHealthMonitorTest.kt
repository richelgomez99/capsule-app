package com.capsule.app.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceHealthMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("capsule_overlay_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun refreshTreatsStaleActiveStatusAsKilledWhenServiceIsNotRunning() {
        prefs.edit()
            .putString("service_health_status", ServiceHealthStatus.ACTIVE.name)
            .putBoolean("service_is_running", false)
            .commit()

        val monitor = ServiceHealthMonitor(context)

        assertEquals(ServiceHealthStatus.KILLED, monitor.health.value.status)
        monitor.dispose()
    }

    @Test
    fun startAndStopPersistRunningTruthState() {
        val monitor = ServiceHealthMonitor(context)

        monitor.onServiceStarted()
        assertTrue(prefs.getBoolean("service_is_running", false))
        assertEquals(ServiceHealthStatus.ACTIVE, monitor.health.value.status)

        monitor.onServiceStopped()
        assertFalse(prefs.getBoolean("service_is_running", true))
        assertEquals(ServiceHealthStatus.KILLED, monitor.health.value.status)

        val freshMonitor = ServiceHealthMonitor(context)
        assertEquals(ServiceHealthStatus.KILLED, freshMonitor.health.value.status)

        monitor.dispose()
        freshMonitor.dispose()
    }
}