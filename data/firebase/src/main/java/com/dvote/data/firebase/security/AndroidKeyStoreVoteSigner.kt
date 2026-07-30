package com.dvote.data.firebase.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.dvote.core.model.VotingKey
import com.dvote.data.firebase.di.ComputationDispatcher
import com.dvote.data.firebase.di.KeyStoreDispatcher
import com.dvote.domain.security.VoteSigner
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AndroidKeyStoreVoteSigner @Inject constructor(
    private val installationIdProvider: InstallationIdProvider,
    @param:KeyStoreDispatcher private val keyStoreDispatcher: CoroutineDispatcher,
    @param:ComputationDispatcher private val computationDispatcher: CoroutineDispatcher,
) : VoteSigner {
    private val keyStoreMutex = Mutex()

    override suspend fun votingKey(userId: String): VotingKey {
        val installationId = installationIdProvider.installationId()
        return keyStoreMutex.withLock {
            withContext(keyStoreDispatcher) {
                keyPair(
                    VotingKeyIdentity.alias(userId, installationId)
                ).toVotingKey(userId, installationId)
            }
        }
    }

    override suspend fun replaceVotingKey(userId: String): VotingKey {
        val installationId = installationIdProvider.installationId()
        return keyStoreMutex.withLock {
            withContext(keyStoreDispatcher) {
                val alias = VotingKeyIdentity.alias(userId, installationId)
                val keyStore = loadKeyStore()
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                }
                keyPair(alias).toVotingKey(userId, installationId)
            }
        }
    }

    override suspend fun signBase64(
        userId: String,
        payload: String,
    ): String {
        val installationId = installationIdProvider.installationId()
        return keyStoreMutex.withLock {
            withContext(keyStoreDispatcher) {
                val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
                signature.initSign(
                    keyPair(VotingKeyIdentity.alias(userId, installationId)).private
                )
                signature.update(payload.toByteArray(Charsets.UTF_8))
                Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
            }
        }
    }

    override suspend fun sha256Base64(payload: String): String = withContext(computationDispatcher) {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun keyPair(alias: String): KeyPair {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE,
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(KEY_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.initialize(spec)
            keyGenerator.generateKeyPair()
        }

        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: error("The voting key entry has an unexpected type.")
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    private fun loadKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    private fun KeyPair.toVotingKey(
        userId: String,
        installationId: String,
    ): VotingKey {
        val publicKeyBytes = public.encoded
        return VotingKey(
            keyId = VotingKeyIdentity.keyId(publicKeyBytes),
            deviceId = VotingKeyIdentity.deviceId(userId, installationId),
            publicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP),
        )
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_CURVE = "secp256r1"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
