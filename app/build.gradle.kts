plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.dokka)
}

// Release signing comes from the environment (CI secrets); without it, release builds are unsigned.
val keystorePath: String? = System.getenv("TTAC_KEYSTORE_PATH")

android {
    namespace = "io.gh.kdbrian.ttac"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.gh.kdbrian.ttac"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("TTAC_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("TTAC_VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("TTAC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TTAC_KEY_ALIAS")
                keyPassword = System.getenv("TTAC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric needs real resources (fonts, strings) to render screenshots.
        unitTests.isIncludeAndroidResources = true
    }
}

// Screenshot tests write straight into the docs so the guide always shows current rendering.
roborazzi {
    outputDir.set(rootProject.layout.projectDirectory.dir("docs/docs/images"))
}

dokka {
    moduleName.set("TTac")
    dokkaSourceSets.configureEach {
        includes.from("../docs/dokka-module.md")
    }
    dokkaPublications.html {
        outputDirectory.set(rootProject.layout.projectDirectory.dir("docs/docs/api"))
    }
}

baselineProfile {
    // Profiles are generated on a device and committed; release builds just consume them.
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // QR: zxing encodes/decodes, CameraX feeds frames to the scanner.
    implementation(libs.zxing.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    debugImplementation(libs.androidx.compose.ui.tooling)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
