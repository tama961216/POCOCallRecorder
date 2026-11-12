plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "tw.pococallrecorder"
    compileSdk = 36
    defaultConfig {
        applicationId = "tw.pococallrecorder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
