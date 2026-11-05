import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val diiModelUrl =
    "https://github.com/luizpaulo-antunes/leitor-local-android/releases/download/v0.8.0/pt_BR-dii-high.onnx"
val diiModelSha256 =
    "0238b83d2c6f4a6690a256303cccd34e35aa28f1ee08cb53af6addb70c0401a7"

android {
    namespace = "com.leitorlocal"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.leitorlocal"
        minSdk = 26
        targetSdk = 37
        versionCode = 14
        versionName = "0.10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "**/libsherpa-onnx-c-api.so",
                "**/libsherpa-onnx-cxx-api.so",
            )
        }
    }
}

val localDiiModel = layout.projectDirectory
    .file("src/main/assets/vits-piper-pt_BR-dii-high/pt_BR-dii-high.onnx")
    .asFile

if (!localDiiModel.isFile) {
    val generatedDiiAssets = layout.buildDirectory.dir("generated/diiVoiceAssets")
    val generatedDiiModel = generatedDiiAssets.map { directory ->
        directory.file("vits-piper-pt_BR-dii-high/pt_BR-dii-high.onnx").asFile
    }
    val downloadDiiVoiceModel = tasks.register("downloadDiiVoiceModel") {
        outputs.file(generatedDiiModel)
        doLast {
            val destination = generatedDiiModel.get()
            destination.parentFile.mkdirs()
            val temporary = destination.resolveSibling(".${destination.name}.download")
            try {
                URI(diiModelUrl).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 180_000
                    setRequestProperty("User-Agent", "Leitor-Local-Gradle")
                }.getInputStream().use { input ->
                    temporary.outputStream().use(input::copyTo)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(temporary.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
                check(digest == diiModelSha256) {
                    "O modelo Dii baixado não passou na verificação de integridade."
                }
                check(temporary.renameTo(destination)) {
                    "Não foi possível preparar o modelo Dii para o build."
                }
            } finally {
                temporary.delete()
            }
        }
    }
    android.sourceSets["main"].assets.srcDir(generatedDiiAssets)
    tasks.named("preBuild").configure { dependsOn(downloadDiiVoiceModel) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    implementation(files("libs/piper-phonemize-v1.5.9.aar"))

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
