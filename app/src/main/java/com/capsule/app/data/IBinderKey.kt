package com.capsule.app.data

/**
 * Equality-by-IBinder-identity wrapper used by repository observer maps so
 * that the Same callback registered twice is overwritten (instead of
 * leaking) and `RemoteException` cancels the binder's collection job.
 */
internal data class IBinderKey(val binder: android.os.IBinder)
