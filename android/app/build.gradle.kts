import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.memowallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.memowallpaper"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "2.4.0"
    }

    // 서명 키
    // - release: 배포용 비공개 키. keystore/release.properties + release.jks (저장소에 올리지 않음)
    // - shared: 개발·테스트용 공개 키(비밀 아님). 예전(2.3.0까지) 설치본 업데이트용으로도 씀(-PsharedKey)
    val releaseProps = rootProject.file("keystore/release.properties")
    val useRelease = releaseProps.exists() && !project.hasProperty("sharedKey")
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/memo-wallpaper.keystore")
            storePassword = "android"
            keyAlias = "memowallpaper"
            keyPassword = "android"
        }
        if (useRelease) {
            val p = Properties().apply { releaseProps.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (useRelease) "release" else "shared")
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    // 볼륨 버튼 카운트(미디어 세션)
    implementation("androidx.media:media:1.7.0")
    // 장소 알림(위치 감지)과 지도
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // 화면·결과 이미지 확인용 테스트(Robolectric으로 JVM에서 실행)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
