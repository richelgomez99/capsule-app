// IEnvelopeObserver.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.DayPageParcel;

oneway interface IEnvelopeObserver {
    void onDayLoaded(in DayPageParcel page);
}
