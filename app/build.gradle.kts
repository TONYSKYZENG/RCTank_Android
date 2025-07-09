plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.rctankw"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.rctankw"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.play.services.wearable)
  //  implementation(libs.appcompat)
}