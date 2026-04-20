// INetworkGateway.aidl
package com.capsule.app.net.ipc;

import com.capsule.app.net.ipc.FetchResultParcel;

interface INetworkGateway {
    FetchResultParcel fetchPublicUrl(String url, long timeoutMs);
}
