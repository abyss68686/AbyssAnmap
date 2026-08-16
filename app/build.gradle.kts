plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.abyss.anmap"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.abyss.anmap"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

    packaging {
        jniLibs {
            // Nmap is an Android PIE executable placed in lib/<abi>/ so Android
            // extracts it with executable permission before ProcessBuilder runs it.
            useLegacyPackaging = true
            keepDebugSymbols += "**/libnmap.so"
        }
    }

    lint {
        abortOnError = true
    }
}

val nativeNmapBinary = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libnmap.so")

tasks.register("verifyNativeNmap") {
    group = "verification"
    description = "Checks that the NDK-built Nmap executable is packaged for arm64-v8a."
    inputs.file(nativeNmapBinary)
    doLast {
        check(nativeNmapBinary.asFile.isFile) {
            "Native Nmap binary missing. Run native/build-nmap.sh before building the APK."
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("verifyNativeNmap")
}

