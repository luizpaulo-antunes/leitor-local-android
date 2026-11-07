import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val diiModelUrl =
    "https://github.com/luizpaulo-antunes/leitor-local-android/releases/download/v0.8.0/pt_BR-dii-high.onnx"
val diiModelSha256 =
    "0238b83d2c6f4a6690a256303cccd34e35aa28f1ee08cb53af6addb70c0401a7"
val supertonicModelUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
val supertonicModelSha256 =
    "82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427"
val supertonicModelDirectory = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
val kokoroModelUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
val kokoroModelSha256 =
    "60703707bc05ccd48d60e1eb723f38b07d6e474795a0b6a588d7220400e0f7a4"
val kokoroModelDirectory = "kokoro-multi-lang-v1_0"

fun java.io.File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        DigestInputStream(input, digest).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (stream.read(buffer) >= 0) Unit
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

android {
    namespace = "com.leitorlocal"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.leitorlocal"
        minSdk = 26
        targetSdk = 37
        versionCode = 18
        versionName = "0.11.0"

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
    // This is a static build-directory path. The explicit preBuild dependency below creates its
    // contents; passing the Provider itself to SourceSet is rejected by recent AGP versions.
    android.sourceSets["main"].assets.srcDir(generatedDiiAssets.get().asFile)
    tasks.named("preBuild").configure { dependsOn(downloadDiiVoiceModel) }
}

fun registerSherpaTtsArchive(
    taskName: String,
    archiveName: String,
    modelUrl: String,
    expectedSha256: String,
    modelDirectory: String,
    requiredFiles: List<String>,
) {
    val generatedAssets = layout.buildDirectory.dir("generated/${taskName}Assets")
    val archiveProvider = layout.buildDirectory.file("downloads/$archiveName")
    val generatedFiles = requiredFiles.map { relativePath ->
        generatedAssets.map { directory ->
            directory.file("$modelDirectory/$relativePath").asFile
        }
    }
    val prepareModel = tasks.register(taskName) {
        inputs.property("modelUrl", modelUrl)
        inputs.property("modelSha256", expectedSha256)
        outputs.files(generatedFiles)
        doLast {
            val archive = archiveProvider.get().asFile
            if (!archive.isFile || archive.sha256() != expectedSha256) {
                archive.parentFile.mkdirs()
                val temporary = archive.resolveSibling(".${archive.name}.download")
                try {
                    URI(modelUrl).toURL().openConnection().apply {
                        connectTimeout = 30_000
                        readTimeout = 600_000
                        setRequestProperty("User-Agent", "Leitor-Local-Gradle")
                    }.getInputStream().use { input ->
                        temporary.outputStream().buffered().use(input::copyTo)
                    }
                    check(temporary.sha256() == expectedSha256) {
                        "O modelo $modelDirectory não passou na verificação de integridade."
                    }
                    archive.delete()
                    check(temporary.renameTo(archive)) {
                        "Não foi possível preparar o arquivo do modelo $modelDirectory."
                    }
                } finally {
                    temporary.delete()
                }
            }

            copy {
                from(tarTree(resources.bzip2(archive))) {
                    requiredFiles.forEach { include("$modelDirectory/$it") }
                }
                into(generatedAssets.get().asFile)
            }
            generatedFiles.forEach { output ->
                check(output.get().isFile) {
                    "O pacote $modelDirectory não contém ${output.get().name}."
                }
            }
        }
    }
    android.sourceSets["main"].assets.srcDir(generatedAssets.get().asFile)
    tasks.named("preBuild").configure { dependsOn(prepareModel) }
}

registerSherpaTtsArchive(
    taskName = "prepareSupertonicVoiceModel",
    archiveName = "supertonic-3-int8.tar.bz2",
    modelUrl = supertonicModelUrl,
    expectedSha256 = supertonicModelSha256,
    modelDirectory = supertonicModelDirectory,
    requiredFiles = listOf(
        "duration_predictor.int8.onnx",
        "text_encoder.int8.onnx",
        "vector_estimator.int8.onnx",
        "vocoder.int8.onnx",
        "tts.json",
        "unicode_indexer.bin",
        "voice.bin",
        "LICENSE",
        "README.md",
    ),
)

registerSherpaTtsArchive(
    taskName = "prepareKokoroVoiceModel",
    archiveName = "kokoro-multi-lang-v1_0.tar.bz2",
    modelUrl = kokoroModelUrl,
    expectedSha256 = kokoroModelSha256,
    modelDirectory = kokoroModelDirectory,
    requiredFiles = listOf(
        "model.onnx",
        "voices.bin",
        "tokens.txt",
        "LICENSE",
        "README.md",
    ),
)

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
