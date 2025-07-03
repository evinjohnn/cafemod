// Add this import at the top for the Java Toolchain
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.judini.cafemode"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.judini.cafemode"
        minSdk = 29 // Custom AudioEffects are most stable from API 29+
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // This block fixes the Java version warning and makes your build more reliable.
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    }

    buildFeatures {
        viewBinding = true
    }

    // This block correctly links your native code via CMake.
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}