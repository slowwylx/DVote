package com.dvote.data.firebase.security

import android.content.Context
import com.dvote.data.firebase.di.KeyStoreDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class InstallationIdProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:KeyStoreDispatcher private val storageDispatcher: CoroutineDispatcher,
) {
    private val lock = Any()

    suspend fun installationId(): String = withContext(storageDispatcher) {
        synchronized(lock) {
            val identityFile = File(context.noBackupFilesDir, IDENTITY_FILE_NAME)
            identityFile
                .takeIf(File::isFile)
                ?.readText(Charsets.UTF_8)
                ?.trim()
                ?.takeIf(::isCanonicalUuid)
                ?: UUID.randomUUID().toString().also { generatedId ->
                    FileOutputStream(identityFile).use { output ->
                        output.write(generatedId.toByteArray(Charsets.UTF_8))
                        output.fd.sync()
                    }
                }
        }
    }

    private fun isCanonicalUuid(value: String): Boolean {
        return try {
            UUID.fromString(value).toString() == value
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private companion object {
        const val IDENTITY_FILE_NAME = "dvote_installation_id"
    }
}
