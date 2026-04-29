// IAuditLog.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.AuditEntryParcel;

interface IAuditLog {
    List<AuditEntryParcel> entriesForDay(String isoDate);
    List<AuditEntryParcel> entriesForEnvelope(String envelopeId);
    int countForDay(String isoDate, String actionName);
}
