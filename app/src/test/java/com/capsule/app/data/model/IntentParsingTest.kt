package com.capsule.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentParsingTest {

    @Test
    fun toIntentOrAmbiguous_preservesKnownValues() {
        Intent.entries.forEach { intent ->
            assertEquals(intent, intent.name.toIntentOrAmbiguous())
        }
    }

    @Test
    fun toIntentOrAmbiguous_unknownValueFallsBackToAmbiguous() {
        assertEquals(Intent.AMBIGUOUS, "REMIND_ME".toIntentOrAmbiguous())
        assertEquals(Intent.AMBIGUOUS, "INSPIRATION".toIntentOrAmbiguous())
        assertEquals(Intent.AMBIGUOUS, "NOT_A_REAL_INTENT".toIntentOrAmbiguous())
    }
}
