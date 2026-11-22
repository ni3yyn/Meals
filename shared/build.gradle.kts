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
        commonMain.dependencies {
            api(compose.animation)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api(compose.components.resources)
            implementation(libs.kotlinx.datetime)
            implementation(libs.compose.icons.lucide)
            api(libs.haze.core)
            implementation(libs.haze.glass)
            implementation(libs.haze.blur)
        }
    }
}


compose.resources {
    publicResClass = true
    packageOfResClass = "dz.meals.app.shared.generated.resources"
}
