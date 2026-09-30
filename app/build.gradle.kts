import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Данные для подписи релизной сборки лежат в keystore.properties (в репозиторий не попадает, см. RELEASE.md).
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.headachediary.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.headachediary.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Более новые версии (1.1.0-beta02 и выше) требуют compileSdk 36 и AGP 8.9.1; beta01 подходит к текущей сборке.
    implementation("androidx.health.connect:connect-client:1.1.0-beta01")

    // Разблокировка отпечатком или кодом экрана блокировки.
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric 1.1.0 тянет fragment 1.2.5, а со старым fragment любой системный диалог (выбор файла, запрос разрешения)
    // падает с «Can only use lower 16 bits for requestCode». Поэтому версия fragment задана явно.
    implementation("androidx.fragment:fragment:1.8.5")
    // Запись автоматических копий в выбранную папку.
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    // Настоящая реализация org.json для локальных тестов (в android.jar только заглушки).
    testImplementation("org.json:json:20240303")
}
