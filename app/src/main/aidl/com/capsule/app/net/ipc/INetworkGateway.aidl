// INetworkGateway.aidl
package com.capsule.app.net.ipc;

import com.capsule.app.net.ipc.FetchResultParcel;
import com.capsule.app.net.ipc.LlmGatewayRequestParcel;
import com.capsule.app.net.ipc.LlmGatewayResponseParcel;

interface INetworkGateway {
    FetchResultParcel fetchPublicUrl(String url, long timeoutMs);

    /**
     * Spec 013 (FR-013-005) — sole AI Gateway entry point.
     * Sealed-class JSON-in-String round-trip; see
     * specs/013-cloud-llm-routing/data-model.md §3.
     */
    LlmGatewayResponseParcel callLlmGateway(in LlmGatewayRequestParcel request);
}
