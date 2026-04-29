package com.capsule.app.net

/**
 * Spec 014 T014-018 — test fake for [AuthStateBinder]. Records the
 * number of times [currentJwt] was queried and returns whatever JWT the
 * test sets via the constructor or [setJwt]. Used by
 * `LlmGatewayClientAuthTest` and any future unit test that exercises
 * the auth seam.
 */
class FakeAuthStateBinder(initialJwt: String? = null) : AuthStateBinder {

    @Volatile
    private var jwt: String? = initialJwt

    @Volatile
    var callCount: Int = 0
        private set

    fun setJwt(value: String?) {
        jwt = value
    }

    override suspend fun currentJwt(): String? {
        callCount += 1
        return jwt
    }
}
