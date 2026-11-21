plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val appVersionCode = providers.gradleProperty("appVersionCode").map(String::toInt).orElse(3)
val appVersionName = providers.gradleProperty("appVersionName").orElse("0.2.1")

val releaseKeystorePath = providers.gradleProperty("releaseKeystorePath").orNull
val releaseStorePassword = providers.gradleProperty("releaseStorePassword").orNull
val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias").orNull
val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "dz.meals.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dz.meals.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Public releases use the persistent private key supplied by CI.
            // Local builds remain installable with the machine's standard debug key.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/AL2.0", "/META-INF/LGPL2.1")
        }
    }
}


dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
