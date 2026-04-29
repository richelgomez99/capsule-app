package com.capsule.app.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T095 — Onboarding flow test (US3 / FR-022 / FR-023).
 *
 * Scope is intentionally narrow:
 *   (a) A fresh install lands on `OnboardingActivity` step 1 (the
 *       "Enable notifications" rationale Compose sheet — _not_ the system
 *       dialog, which Espresso/Compose cannot drive on a real device).
 *   (b) `OnboardingPreferences` round-trips `completed` and `reducedMode`,
 *       which are the booleans the launcher routes off (T103a/b).
 *   (c) Setting `reducedMode = true` is observable on a fresh
 *       `OnboardingPreferences` instance — the contract for the
 *       launcher → `ReducedModeActivity` redirect.
 *
 * Out of scope (and intentionally excluded — would require Espresso UI
 * Automator and root device tweaks):
 *   • Granting/denying the actual `POST_NOTIFICATIONS` system dialog.
 *   • The `SYSTEM_ALERT_WINDOW` settings deep-link round-trip.
 *   • The end-to-end "decline twice → reducedMode" path through Compose,
 *     which is exercised manually in `quickstart.md §3`.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<OnboardingActivity>()

    private lateinit var prefs: OnboardingPreferences

    @Before
    fun setUp() {
        prefs = OnboardingPreferences(ApplicationProvider.getApplicationContext())
        // Wipe to fresh state so these tests are independent of prior runs.
        prefs.completed = false
        prefs.reducedMode = false
    }

    @After
    fun tearDown() {
        prefs.completed = false
        prefs.reducedMode = false
    }

    /** (a) Fresh launch shows the notification rationale step (step 1 of 4). */
    @Test
    fun freshLaunch_showsNotificationStepFirst() {
        composeRule.onNodeWithText("Enable notifications").assertIsDisplayed()
    }

    /**
     * (b) `completed` is false on a fresh install and survives a round-trip.
     * The launcher (`DiaryActivity.onCreate`) reads this to decide whether
     * to route to `OnboardingActivity`.
     */
    @Test
    fun preferences_completedRoundTrips() {
        val fresh = OnboardingPreferences(ApplicationProvider.getApplicationContext())
        assertFalse("fresh install must not be marked completed", fresh.completed)

        fresh.completed = true
        val reread = OnboardingPreferences(ApplicationProvider.getApplicationContext())
        assertTrue("completed=true must persist across instances", reread.completed)
    }

    /**
     * (c) `reducedMode = true` is observable from a fresh prefs instance —
     * this is the signal `OnboardingActivity.complete()` reads when routing
     * to `ReducedModeActivity` instead of `DiaryActivity` (T103a/b).
     */
    @Test
    fun preferences_reducedModeRoundTrips() {
        val fresh = OnboardingPreferences(ApplicationProvider.getApplicationContext())
        assertFalse("fresh install must not be in reduced mode", fresh.reducedMode)

        fresh.reducedMode = true
        val reread = OnboardingPreferences(ApplicationProvider.getApplicationContext())
        assertTrue("reducedMode=true must persist across instances", reread.reducedMode)
    }

    /**
     * (c-cont) Defensive sanity: clearing reducedMode back to false reverts
     * the route. This catches a regression where someone removes the setter
     * branch in `OnboardingPreferences`.
     */
    @Test
    fun preferences_reducedMode_canBeCleared() {
        prefs.reducedMode = true
        prefs.reducedMode = false
        val reread = OnboardingPreferences(ApplicationProvider.getApplicationContext())
        assertEquals(false, reread.reducedMode)
    }
}
