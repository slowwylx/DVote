import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
    alias(libs.plugins.compose.compiler)
}

val secretProperties = Properties().apply {
    val defaults = rootProject.file("local.defaults.properties")
    val secrets = rootProject.file("secret.properties")
    if (defaults.exists()) defaults.inputStream().use { load(it) }
    if (secrets.exists()) secrets.inputStream().use { load(it) }
}

android {
    namespace = "com.dvote.feature.auth"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "WEB_CLIENT_ID",
            "\"${secretProperties.getProperty("WEB_CLIENT_ID", "")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(project(":domain"))

    implementation(composeBom)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material3)
    implementation(libs.googleid)
    implementation(libs.hilt)
    implementation(libs.hiltNavigationCompose)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.hiltCompiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
