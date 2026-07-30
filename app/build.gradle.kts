plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose-stability.conf")
    )
}

val packageName = providers.gradleProperty("packageName").get()
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()
val appVersionName = providers.gradleProperty("appVersionName").get()
fun propertyOrEnv(name: String) = providers.gradleProperty(name).orElse(providers.environmentVariable(name))

val releaseStoreFilePath = propertyOrEnv("DVOTE_RELEASE_STORE_FILE").orNull
val releaseStorePassword = propertyOrEnv("DVOTE_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = propertyOrEnv("DVOTE_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = propertyOrEnv("DVOTE_RELEASE_KEY_PASSWORD").orNull
val releaseCertificateSha256 = propertyOrEnv("DVOTE_RELEASE_CERT_SHA256").orNull
val releaseSigningValues = linkedMapOf(
    "DVOTE_RELEASE_STORE_FILE" to releaseStoreFilePath,
    "DVOTE_RELEASE_STORE_PASSWORD" to releaseStorePassword,
    "DVOTE_RELEASE_KEY_ALIAS" to releaseKeyAlias,
    "DVOTE_RELEASE_KEY_PASSWORD" to releaseKeyPassword,
    "DVOTE_RELEASE_CERT_SHA256" to releaseCertificateSha256,
)
val providedReleaseSigningValues = releaseSigningValues
    .filterValues { value -> !value.isNullOrBlank() }
val missingReleaseSigningValues = releaseSigningValues
    .filterValues { value -> value.isNullOrBlank() }
    .keys
val hasReleaseSigning = missingReleaseSigningValues.isEmpty()
if (providedReleaseSigningValues.isNotEmpty() && !hasReleaseSigning) {
    throw GradleException(
        "Incomplete DVote distribution signing configuration. Missing: " +
            missingReleaseSigningValues.joinToString()
    )
}

val normalizedReleaseCertificateSha256 = releaseCertificateSha256
    ?.takeUnless { value -> value.isBlank() }
    ?.replace(":", "")
    ?.lowercase()
if (
    normalizedReleaseCertificateSha256 != null &&
    !normalizedReleaseCertificateSha256.matches(Regex("^[0-9a-f]{64}$"))
) {
    throw GradleException(
        "DVOTE_RELEASE_CERT_SHA256 must contain exactly 64 hexadecimal characters."
    )
}

val uploadCrashlyticsMapping = when (
    val value = propertyOrEnv("DVOTE_UPLOAD_CRASHLYTICS_MAPPING").orNull
        ?.trim()
        ?.lowercase()
) {
    null, "", "false" -> false
    "true" -> true
    else -> throw GradleException(
        "DVOTE_UPLOAD_CRASHLYTICS_MAPPING must be true or false."
    )
}

android {
    namespace = packageName
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = packageName
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            register("distribution") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("distribution")
            } else {
                null
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = uploadCrashlyticsMapping
            }
        }
        create("unsignedRelease") {
            initWith(getByName("release"))
            signingConfig = null
            matchingFallbacks += listOf("release")
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "uk")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

tasks.matching { task -> task.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "DVote distribution signing is not configured. " +
                    "Provide all DVOTE_RELEASE_* values or build " +
                    ":app:assembleUnsignedRelease for the unsigned minified artifact."
            )
        }
        val configuredStoreFile = file(requireNotNull(releaseStoreFilePath))
        if (!configuredStoreFile.isFile) {
            throw GradleException(
                "DVOTE_RELEASE_STORE_FILE does not point to a readable keystore file."
            )
        }
    }
}

val verifyReleaseSigningCertificate = tasks.register("verifyReleaseSigningCertificate") {
    group = "verification"
    description = "Verifies the distribution APK signer against DVOTE_RELEASE_CERT_SHA256."

    doLast {
        if (!hasReleaseSigning) {
            throw GradleException("Distribution signing is required before certificate verification.")
        }

        val releaseApks = layout.buildDirectory
            .dir("outputs/apk/release")
            .get()
            .asFile
            .listFiles { file -> file.isFile && file.extension == "apk" }
            .orEmpty()
        if (releaseApks.size != 1) {
            throw GradleException(
                "Expected exactly one distribution APK, found ${releaseApks.size}."
            )
        }

        val apksigner = androidComponents.sdkComponents.sdkDirectory
            .get()
            .asFile
            .resolve("build-tools/${android.buildToolsVersion}/apksigner")
        if (!apksigner.isFile) {
            throw GradleException(
                "Could not find apksigner for Android Build Tools ${android.buildToolsVersion}."
            )
        }

        val verificationOutput = providers.exec {
            commandLine(
                apksigner.absolutePath,
                "verify",
                "--print-certs",
                releaseApks.single().absolutePath,
            )
        }.standardOutput.asText.get()
        val actualDigest = Regex(
            "Signer #1 certificate SHA-256 digest: ([0-9a-fA-F:]+)"
        ).find(verificationOutput)
            ?.groupValues
            ?.get(1)
            ?.replace(":", "")
            ?.lowercase()
            ?: throw GradleException("apksigner did not report a SHA-256 signer digest.")

        if (actualDigest != normalizedReleaseCertificateSha256) {
            throw GradleException(
                "Distribution certificate mismatch. Expected " +
                    "$normalizedReleaseCertificateSha256 but apksigner reported $actualDigest."
            )
        }
        logger.lifecycle("Verified DVote distribution certificate SHA-256: $actualDigest")
    }
}

tasks.matching { task -> task.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyReleaseSigningCertificate)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":domain"))
    implementation(project(":data:firebase"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:voting"))

    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    implementation(libs.hilt)
    implementation(libs.hiltNavigationCompose)
    ksp(libs.hiltCompiler)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
