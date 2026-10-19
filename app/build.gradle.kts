plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.doormate.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.doormate.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 3
        versionName = "0.3-research"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
