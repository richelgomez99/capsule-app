package com.capsule.app.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Spec 014 T014-019b — Supabase [SessionManager] backed by
 * [EncryptedSharedPreferences].
 *
 * Constitution Principle II: this class lives in the `:net` process and is
 * the **only** place that ever touches the SDK's session storage. The session
 * JSON (which contains the access + refresh tokens) is encrypted at rest using
 * Android Keystore-derived AES256-GCM (values) + AES256-SIV (keys).
 *
 * The on-disk pref name is fixed so refresh-token rotation across process
 * restarts works (FR-014-017). The serialised payload format is the SDK's
 * own [UserSession] serializer with `encodeDefaults = true` (matches the
 * upstream `SettingsSessionManager` exactly so we stay format-compatible if
 * we ever swap implementations).
 *
 * Tests use the [SessionStorage] seam to exercise round-trip behaviour
 * without requiring the Android Keystore (which is not available under the
 * mockable android.jar; Robolectric is not on the classpath either — see
 * `EncryptedSessionManagerTest`).
 */
class EncryptedSessionManager internal constructor(
    private val storage: SessionStorage,
    private val json: Json = SESSION_JSON,
) : SessionManager {

    constructor(appContext: Context) : this(EncryptedPrefsStorage(appContext))

    override suspend fun saveSession(session: UserSession) {
        storage.put(json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession {
        val raw = storage.get() ?: error("No supabase session stored")
        return json.decodeFromString(UserSession.serializer(), raw)
    }

    override suspend fun deleteSession() {
        storage.clear()
    }

    /**
     * Tiny seam over the encrypted pref so we can unit-test
     * [EncryptedSessionManager] without an Android device. Production wiring
     * uses [EncryptedPrefsStorage] which is backed by
     * `EncryptedSharedPreferences`.
     */
    interface SessionStorage {
        fun put(json: String)
        fun get(): String?
        fun clear()
    }

    private class EncryptedPrefsStorage(appContext: Context) : SessionStorage {
        private val prefs: SharedPreferences = run {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        override fun put(json: String) {
            prefs.edit().putString(KEY_SESSION, json).apply()
        }

        override fun get(): String? = prefs.getString(KEY_SESSION, null)

        override fun clear() {
            prefs.edit().remove(KEY_SESSION).apply()
        }
    }

    companion object {
        const val PREF_NAME: String = "supabase_auth_session"
        const val KEY_SESSION: String = "session"

        // `encodeDefaults = true` matches the SDK's SettingsSessionManager and
        // is REQUIRED — the SDK relies on default fields (e.g. `type`) being
        // present in the serialised payload.
        internal val SESSION_JSON: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
