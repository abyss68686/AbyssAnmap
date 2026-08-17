import java.util.zip.ZipFile

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
        versionCode = 2
        versionName = "0.1.1"
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
val nativeCxxRuntime = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libc++_shared.so")

tasks.register("verifyNativeNmap") {
    group = "verification"
    description = "Checks that Nmap and its NDK C++ runtime are available for arm64-v8a."
    inputs.files(nativeNmapBinary, nativeCxxRuntime)
    doLast {
        check(nativeNmapBinary.asFile.isFile) {
            "Native Nmap binary missing. Run native/build-nmap.sh before building the APK."
        }
        check(nativeCxxRuntime.asFile.isFile) {
            "NDK C++ runtime missing. Run native/build-nmap.sh before building the APK."
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("verifyNativeNmap")
}

val debugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")

tasks.register("verifyDebugApkNativeLibraries") {
    group = "verification"
    description = "Verifies that the debug APK contains Nmap and its required C++ runtime."
    dependsOn("assembleDebug")
    inputs.file(debugApk)
    doLast {
        ZipFile(debugApk.get().asFile).use { apk ->
            listOf(
                "lib/arm64-v8a/libnmap.so",
                "lib/arm64-v8a/libc++_shared.so"
            ).forEach { entry ->
                check(apk.getEntry(entry) != null) {
                    "Required native library is missing from the debug APK: $entry"
                }
            }
        }
    }
}
