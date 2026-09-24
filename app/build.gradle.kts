import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 从 keystore.properties 读取签名信息（该文件不入库，避免泄露密钥/密码）
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasKeystore) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.bbdroid.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bbdroid.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile") ?: "release.keystore")
                storePassword = keystoreProperties.getProperty("storePassword") ?: ""
                keyAlias = keystoreProperties.getProperty("keyAlias") ?: "bbdroid"
                keyPassword = keystoreProperties.getProperty("keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.foundation:foundation:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 持久化：Room（历史/队列）+ DataStore（设置）
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 网络：B站接口请求
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 二维码生成（扫码登录用）
    implementation("com.google.zxing:core:3.5.1")

    // FFmpeg 引擎：本地 AAR（需手动下载后放入 libs/ 目录）
    implementation(files("libs/ffmpeg-kit-full-gpl-8.1.7.aar"))
    // FFmpegKit 运行依赖（提供 com.arthenica.smartexception.java.Exceptions）
    implementation("com.arthenica:smart-exception-java:0.2.1")
}
