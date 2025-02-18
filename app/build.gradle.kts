plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}


val packageName = providers.gradleProperty("packageName").get()
val name = providers.gradleProperty("name").get()
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()
val appVersionName = providers.gradleProperty("appVersionName").get()

android {
    namespace = packageName
    compileSdk = 35

    defaultConfig {
        applicationId = packageName
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions{
        kotlinCompilerExtensionVersion = "1.15.5"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=com.google.accompanist.pager.ExperimentalPagerApi",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi"
        )
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    //default
    implementation(libs.androidx.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //compose
    implementation(composeBom)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.navigation)
    implementation(libs.androidx.foundation)
    androidTestImplementation(composeBom)

    //retrofit
    implementation (libs.retrofit)
    implementation (libs.converter.gson)
    
    //hilt
    implementation(libs.hilt)
    implementation(libs.hiltNavigationCompose)
    ksp(libs.hiltCompiler)
}