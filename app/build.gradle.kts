import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 每次构建自动递增 versionCode（按 UTC 分钟数取余 Int 范围内安全），
// versionName 带本次构建时间戳，避免「相同版本号必须卸载重装」。
val buildTimeUtc: Date = Date()
val buildVersionCode: Int = ((buildTimeUtc.time / 60_000L) % Int.MAX_VALUE).toInt()
val buildVersionName: String = run {
    val fmt = SimpleDateFormat("yyMMdd-HHmm", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    "0.2.0+${fmt.format(buildTimeUtc)}"
}

android {
    namespace = "com.tkpang.tvstriptest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tkpang.tvstriptest"
        minSdk = 26
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
