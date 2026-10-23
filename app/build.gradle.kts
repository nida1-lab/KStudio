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
        versionCode = 4
        versionName = "0.4.0"
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

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/local-tools/assets"))
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/local-tools/jniLibs"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("examples"))
    packaging.jniLibs.useLegacyPackaging = true
    androidResources.noCompress += "jar"
}

dependencies {
    implementation("com.android.tools:r8:8.7.18")
    implementation("com.android.tools.build:apksig:8.7.3")
    testImplementation("junit:junit:4.13.2")
}

val prepareLocalToolchain by tasks.registering(Exec::class) {
    commandLine("python3", rootProject.file("tools/build_local_toolchain.py"),
        "--sdk", android.sdkDirectory, "--out", layout.buildDirectory.dir("generated/local-tools").get().asFile)
}
tasks.named("preBuild") { dependsOn(prepareLocalToolchain) }

