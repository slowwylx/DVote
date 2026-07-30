package com.dvote.data.firebase.repository

import com.dvote.domain.coroutines.runSuspendCatching

internal suspend fun <T> authenticateAndProvision(
    authenticate: suspend () -> T,
    provision: suspend (T) -> Unit,
): Result<Unit> {
    return runSuspendCatching {
        provision(authenticate())
    }
}

internal suspend fun <T> repairAuthenticatedProfile(
    currentUser: () -> T?,
    provision: suspend (T) -> Unit,
): Result<Unit> {
    return runSuspendCatching {
        val user = currentUser()
            ?: error("No authenticated user is available to repair.")
        provision(user)
    }
}
