package com.dvote.domain.repository

import com.dvote.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeCurrentUser(): Flow<UserProfile?>
}
