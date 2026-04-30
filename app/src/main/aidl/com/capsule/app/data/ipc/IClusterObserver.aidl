// IClusterObserver.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.ClusterCardParcel;

oneway interface IClusterObserver {
    void onClustersChanged(in List<ClusterCardParcel> clusters);
}
