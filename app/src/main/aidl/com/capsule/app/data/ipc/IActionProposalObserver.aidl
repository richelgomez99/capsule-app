// IActionProposalObserver.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.ActionProposalParcel;

/**
 * UI-side callback delivered to {@link IEnvelopeRepository#observeProposalsForEnvelope}.
 *
 * The repository emits the full current set on every change so observers
 * never need to reconstruct state from deltas (mirrors IEnvelopeObserver).
 */
oneway interface IActionProposalObserver {
    void onProposals(in List<ActionProposalParcel> proposals);
}
