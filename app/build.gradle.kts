import java.util.Properties

plugins {
    alias(libs.plugins.android.application) // AGP 9: Kotlin 내장 (kotlin-android 플러그인 불필요)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// release 서명 정보는 저장소에 올리지 않는다 (spec.md 12절).
// local.properties 또는 환경 변수로 받는다:
// GACHIGA_STORE_FILE, GACHIGA_STORE_PASSWORD, GACHIGA_KEY_ALIAS, GACHIGA_KEY_PASSWORD
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String? = localProps.getProperty(name) ?: System.getenv(name)

android {
    namespace = "com.gachiga.app"
    compileSdk = 37 // 최신 AndroidX 요구. 실행 대상(targetSdk)은 Pleos 에뮬레이터와 같은 34

    defaultConfig {
        applicationId = "com.gachiga.app" // 가칭. 앱 이름 확정 후 변경
        minSdk = 28                       // 차량 보안 요건
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            secret("GACHIGA_STORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = secret("GACHIGA_STORE_PASSWORD")
                keyAlias = secret("GACHIGA_KEY_ALIAS")
                keyPassword = secret("GACHIGA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (secret("GACHIGA_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Pleos Connect SDK: Vehicle (spec.md 3.1), SpeechToText·TextToSpeech (F8, ON_DEVICE 모드)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.tts)

    testImplementation(libs.junit)
}
