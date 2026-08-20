import java.io.FileInputStream
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is loaded from keystore.properties (gitignored, never in VCS).
// When the file is absent (CI, fresh clone), the release build is left unsigned.
val keystorePropsFile = listOf(
    rootProject.file("keystore.properties"),
    rootProject.file("../keystore.properties"),
    rootProject.file("../正式/keystore.properties"),
    rootProject.file("../签名密钥/keystore.properties"),
).firstOrNull { it.exists() }
val keystoreProps = Properties().apply {
    keystorePropsFile?.let { load(FileInputStream(it)) }
}
fun resolveKeystoreFile(path: String): File {
    val store = File(path)
    if (store.isAbsolute) return store
    return listOf(
        rootProject.file(path),
        File(keystorePropsFile?.parentFile ?: rootProject.projectDir, path),
    ).firstOrNull { it.exists() } ?: rootProject.file(path)
}
val updateApiUrl: String =
    providers.gradleProperty("superToyBoxUpdateApiUrl").orNull
        ?: "https://api.689588.xyz/api/v1/update/latest"
val soundApiBaseUrl: String =
    providers.gradleProperty("superToyBoxSoundApiBaseUrl").orNull
        ?: "https://api.689588.xyz/api/v1/sounds"
val activationApiUrl: String =
    providers.gradleProperty("superToyBoxActivationApiUrl").orNull
        ?: "https://api.689588.xyz/api/v1/license"
val serviceQrApiUrl: String =
    providers.gradleProperty("superToyBoxServiceQrApiUrl").orNull
        ?: "https://api.689588.xyz/api/v1/service/qrcodes"
val logUploadApiUrl: String =
    providers.gradleProperty("superToyBoxLogUploadApiUrl").orNull
        ?: "https://api.689588.xyz/api/v1/logs/upload"
android {
    namespace = "com.byd.wjx"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.byd.wjx"
        minSdk = 29
        // targetSdk 29 matches TripInfo and preserves direct shared-storage access
        // on DiLink Android 12 through requestLegacyExternalStorage.
        // targetSdk 30+ would break listFiles() on /storage/emulated/0/energydata/
        targetSdk = 29
        versionCode = 44
        versionName = "2.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPDATE_API_URL", "\"$updateApiUrl\"")
        buildConfigField("String", "SOUND_API_BASE_URL", "\"$soundApiBaseUrl\"")
        buildConfigField("String", "ACTIVATION_API_URL", "\"$activationApiUrl\"")
        buildConfigField("String", "SERVICE_QR_API_URL", "\"$serviceQrApiUrl\"")
        buildConfigField("String", "LOG_UPLOAD_API_URL", "\"$logUploadApiUrl\"")
    }

    signingConfigs {
        if (keystorePropsFile != null) {
            create("release") {
                storeFile = resolveKeystoreFile(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "超级玩具箱-V${variant.versionName}.apk"
        }
    }

    lint {
        // Not targeting Google Play -- DiLink sideload only
        disable += "ExpiredTargetSdkVersion"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // android.util.Log is unavailable in pure JVM unit tests without Robolectric.
            // returnDefaultValues = true makes all unmocked Android API methods return 0/null/false
            // instead of throwing RuntimeException — this keeps AutoserviceChargingDetector
            // testable without requiring Robolectric for every test class.
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // osmdroid (maps)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // JSON parsing
    implementation("org.json:json:20231013")

    // QR rendering for server-created activation payment orders
    implementation("com.google.zxing:core:3.5.3")

    // Sun/moon position calculation for time_of_day trigger
    implementation("org.shredzone.commons:commons-suncalc:3.7")

    // AppCompat (required for AppCompatDelegate.setApplicationLocales per-app language support)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Hidden-API bypass: allows in-process ServiceManager.getService() on Android 9+
    // to reach the helper binder without UnsatisfiedLinkError / NoSuchMethodError.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // On-device ADB is implemented in-tree and is limited to helper bootstrap and
    // narrow shell diagnostics. Production vehicle FID reads use Helper Binder.
    // com.cgutman:adblib does not exist on MavenCentral (only com.tananaev:adblib does).
    // Task 4 will use a hand-rolled ADB client fallback.
    // implementation("com.cgutman:adblib:1.0.0")
}
