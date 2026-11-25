plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "dz.meals.app.shared"
        compileSdk = 37
        minSdk = 26

        // Required with the Android-KMP library plugin so Compose Multiplatform
        // generated composeResources assets are copied into the Android AAR/APK.
        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "MealsShared"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            // WindowCompat / WindowInsetsControllerCompat are used by the
            // Android modal-window system-bar configuration.
            implementation(libs.androidx.core.ktx)
            // Stable AndroidX Material 3 exposes the public motion-scheme API.
            implementation("androidx.compose.material3:material3:1.5.0")
            implementation("com.github.styropyr0:PrismalAGSL:v1.0.7")
        }

        commonMain.dependencies {
            api(compose.animation)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api(compose.components.resources)
            implementation(libs.kotlinx.datetime)
            // On-device inverted-index search for the 4k+ food catalogue.
            implementation("io.github.karloti:typeahead-kmp:2.0.4")
            api(libs.haze.core)
            implementation(libs.haze.glass)
            implementation(libs.haze.blur)
            // Remote recipe photography is loaded on demand and cached by Coil.
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
            implementation("io.ktor:ktor-client-engine-defaults:3.6.1")
        }
    }
}


compose.resources {
    publicResClass = true
    packageOfResClass = "dz.meals.app.shared.generated.resources"
}
