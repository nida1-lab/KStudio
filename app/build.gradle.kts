plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nida.kstudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nida.kstudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        testApplicationId = "com.nida.kstudio.test"
        testInstrumentationRunner = "com.nida.kstudio.RunDeviceChecks"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

