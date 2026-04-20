// IEnvelopeRepository.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel;
import com.capsule.app.data.ipc.StateSnapshotParcel;
import com.capsule.app.data.ipc.EnvelopeViewParcel;
import com.capsule.app.data.ipc.DayPageParcel;
import com.capsule.app.data.ipc.IEnvelopeObserver;

interface IEnvelopeRepository {

    // ---- Seal path (called by :capture) ----
    String seal(
        in IntentEnvelopeDraftParcel draft,
        in StateSnapshotParcel state
    );

    // ---- Read path (called by :ui) ----
    void observeDay(String isoDate, IEnvelopeObserver observer);
    void stopObserving(IEnvelopeObserver observer);
    EnvelopeViewParcel getEnvelope(String envelopeId);

    // ---- Mutate path (called by :ui) ----
    void reassignIntent(String envelopeId, String newIntentName, String reasonOpt);
    void archive(String envelopeId);
    void delete(String envelopeId);
    boolean undo(String envelopeId);

    // ---- Soft-delete / trash ----
    void restoreFromTrash(String envelopeId);
    List<EnvelopeViewParcel> listSoftDeletedWithinDays(int days);
    int countSoftDeletedWithinDays(int days);

    // ---- Diagnostics ----
    int countAll();
    int countArchived();
    int countDeleted();
}
