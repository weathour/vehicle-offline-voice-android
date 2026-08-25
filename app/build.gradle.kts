import java.util.zip.GZIPInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val kokoroModelId = "kokoro-int8-multi-lang-v1_1"
val kokoroSourceDir = layout.projectDirectory.dir("src/main/tts-model/$kokoroModelId")
val kokoroAssetsDir = layout.buildDirectory.dir("generated/kokoroAssets")
val prepareKokoroAssets by tasks.registering(Sync::class) {
    inputs.file(kokoroSourceDir.file("model.int8.onnx.gz"))
    from(kokoroSourceDir) { exclude("model.int8.onnx.gz", "SHA256SUMS") }
    into(kokoroAssetsDir.map { it.dir("tts/$kokoroModelId") })
    doLast {
        val output = kokoroAssetsDir.get().file("tts/$kokoroModelId/model.int8.onnx").asFile
        output.parentFile.mkdirs()
        GZIPInputStream(kokoroSourceDir.file("model.int8.onnx.gz").asFile.inputStream().buffered()).use { input ->
            output.outputStream().buffered().use(input::copyTo)
        }
    }
}

android {
    namespace = "com.company.vehiclevoice"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.edu.chd.yuxingvoice"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "v1.3"

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

    sourceSets["main"].assets.srcDir(kokoroAssetsDir)
}

tasks.named("preBuild").configure { dependsOn(prepareKokoroAssets) }

dependencies {
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.6.aar"))
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")

    testImplementation("junit:junit:4.13.2")
}
