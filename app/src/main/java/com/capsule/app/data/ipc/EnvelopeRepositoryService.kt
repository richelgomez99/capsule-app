package com.capsule.app.data.ipc

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Bound service running in :ml process. Owns the encrypted Room database
 * and exposes IEnvelopeRepository + IAuditLog AIDL surfaces.
 *
 * Callers: :capture (seal path), :ui (read + mutate paths).
 */
class EnvelopeRepositoryService : Service() {

    // Stub will be fleshed out when EnvelopeRepositoryImpl is built (US1)
    private val binder = object : IEnvelopeRepository.Stub() {

        override fun seal(draft: IntentEnvelopeDraftParcel, state: StateSnapshotParcel): String {
            TODO("Implemented in US1 — T033")
        }

        override fun observeDay(isoDate: String, observer: IEnvelopeObserver) {
            TODO("Implemented in US1 — T033")
        }

        override fun stopObserving(observer: IEnvelopeObserver) {
            TODO("Implemented in US1 — T033")
        }

        override fun getEnvelope(envelopeId: String): EnvelopeViewParcel {
            TODO("Implemented in US1 — T033")
        }

        override fun reassignIntent(envelopeId: String, newIntentName: String, reasonOpt: String?) {
            TODO("Implemented in US1 — T033")
        }

        override fun archive(envelopeId: String) {
            TODO("Implemented in US1 — T033")
        }

        override fun delete(envelopeId: String) {
            TODO("Implemented in US1 — T033")
        }

        override fun undo(envelopeId: String): Boolean {
            TODO("Implemented in US1 — T033")
        }

        override fun restoreFromTrash(envelopeId: String) {
            TODO("Implemented in US1 — T033b")
        }

        override fun listSoftDeletedWithinDays(days: Int): List<EnvelopeViewParcel> {
            TODO("Implemented in US1 — T033b")
        }

        override fun countSoftDeletedWithinDays(days: Int): Int {
            TODO("Implemented in US1 — T033b")
        }

        override fun countAll(): Int {
            TODO("Implemented in US1 — T033")
        }

        override fun countArchived(): Int {
            TODO("Implemented in US1 — T033")
        }

        override fun countDeleted(): Int {
            TODO("Implemented in US1 — T033")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
