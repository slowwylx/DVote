package com.dvote.domain.security

import java.security.KeyPair

interface KeyStoreService {
    fun generateKeyPairIfNeeded(): KeyPair
}