plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baseline.profile)
}

android {
    namespace = "dz.meals.app.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}

baselineProfile {
    // Run profile capture and macrobenchmarks on an attached Android device.
    useConnectedDevices = true
}
