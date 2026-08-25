plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.company.vehiclevoice"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.edu.chd.yuxingvoice"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "v1.2"

        testInstrumentationRunner = "android.app.Instrumentation"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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
}

dependencies {
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.6.aar"))
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")

    testImplementation("junit:junit:4.13.2")
}
