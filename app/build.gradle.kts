import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync

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

val generatedTajawalRes = layout.buildDirectory.dir("generated/tajawal-res")
val localRecipeCorpusDir = rootProject.file("local-data/open-recipe-corpus")
val generatedRecipeCorpusAssets = layout.buildDirectory.dir("generated/recipe-corpus/debug-assets")
val generatedFoodIdentityAssets = layout.buildDirectory.dir("generated/food-identity-migration-assets")
val prepareFoodIdentityMigrationAssets = tasks.register<Sync>("prepareFoodIdentityMigrationAssets") {
    from(rootProject.file("data/products/recipe-ingredient-map.json")) {
        into("migration")
    }
    from(rootProject.file("data/products/catalogue.json")) {
        into("food")
    }
    from(rootProject.file("data/products/categories.json")) {
        into("food")
    }
    into(generatedFoodIdentityAssets)
}
val prepareRecipeCorpusDebugAssets = tasks.register<Sync>("prepareRecipeCorpusDebugAssets") {
    from(localRecipeCorpusDir) {
        include(
            "data/recipes.jsonl",
            "data/sources.json",
            "data/text/en.jsonl",
            "data/text/pl.jsonl"
        )
    }
    into(generatedRecipeCorpusAssets)
    doLast {
        val requiredFiles = listOf(
            "data/recipes.jsonl",
            "data/text/en.jsonl",
            "data/text/pl.jsonl"
        )
        val missingFiles = requiredFiles.filterNot {
            generatedRecipeCorpusAssets.get().file(it).asFile.isFile
        }
        if (missingFiles.isNotEmpty()) {
            logger.lifecycle(
                "Midati local recipe corpus is not installed. Run: python tools/download_recipe_corpus.py"
            )
        }
    }
}
val copyTajawalFonts = tasks.register<Copy>("copyTajawalFonts") {
    from(rootProject.file("shared/src/commonMain/composeResources/font")) {
        include(
            "tajawal_regular.ttf",
            "tajawal_medium.ttf",
            "tajawal_bold.ttf",
            "tajawal_extra_bold.ttf"
        )
    }
    into(generatedTajawalRes.map { it.dir("font") })
}

android {
    // Generate the release instrumentation-test variant for the minified APK smoke test.
    testBuildType = "release"

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

    // The same shared Tajawal assets are exposed as Android font resources
    // for a deterministic native Typeface fallback in MainActivity.
    sourceSets.getByName("main").res.srcDir(generatedTajawalRes.get().asFile)
    // Database upgrades need the generated legacy-ID crosswalk to work offline.
    sourceSets.getByName("main").assets.srcDir(generatedFoodIdentityAssets.get().asFile)
    // Local corpus assets are attached to Debug only, never to release APKs.
    sourceSets.getByName("debug").assets.srcDir(generatedRecipeCorpusAssets.get().asFile)

    room {
        schemaDirectory("$projectDir/schemas")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's default repo1.maven.org endpoint is frequently rate-limited
            // in CI when fetching android-all-instrumented at test runtime.
            // Use Maven Central's canonical endpoint for the runtime SDK artifacts.
            it.systemProperty(
                "robolectric.dependency.repo.url",
                "https://repo.maven.apache.org/maven2"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/AL2.0", "/META-INF/LGPL2.1")
        }
    }
}


// Every Android resource path that can read the generated res/font folder
// must wait for the copy task. AGP 9.5 validates these dependencies explicitly.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(copyTajawalFonts, prepareFoodIdentityMigrationAssets)
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareFoodIdentityMigrationAssets)
}
tasks.configureEach {
    val isFontResourceConsumer =
        (name.startsWith("map") && name.endsWith("SourceSetPaths")) ||
        (name.startsWith("process") && name.endsWith("NavigationResources")) ||
        (name.startsWith("package") && name.endsWith("Resources")) ||
        (name.startsWith("generate") && name.endsWith("Resources")) ||
        (name.startsWith("merge") && name.endsWith("Resources"))

    if (isFontResourceConsumer && name != "copyTajawalFonts") {
        dependsOn(copyTajawalFonts)
    }
}

tasks.matching { it.name == "mergeDebugAssets" }.configureEach {
    dependsOn(prepareRecipeCorpusDebugAssets)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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
    testImplementation("org.robolectric:robolectric:4.16.1") {
        // This migration test is pinned to API 35 (V). Robolectric uses
        // android-all for the native runtime from V onward, so its 151 MB
        // O-U compatibility distribution is not needed on this test classpath.
        exclude(group = "org.robolectric", module = "nativeruntime-dist-compat")
    }
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    // AndroidX Test references these compile-time annotations during R8 of release tests.
    androidTestImplementation("com.google.errorprone:error_prone_annotations:2.53.0")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
