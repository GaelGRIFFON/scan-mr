plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "fr.gael.scanclient"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.gael.scanclient"
        minSdk = 26
        targetSdk = 35
        // Numéro de build GitHub : augmente à chaque exécution (1 en local)
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1"
    }

    // Clé de signature fixe déposée dans le dépôt (voir workflow "Générer la clé de signature").
    // Sans le fichier, Gradle retombe sur la clé debug habituelle.
    val fixedKeystore = file("keystore/scanclient.jks")
    val keystorePass = System.getenv("SCANCLIENT_KS_PASS")?.takeIf { it.isNotBlank() } ?: "scanclient2026"
    signingConfigs {
        if (fixedKeystore.exists()) {
            create("fixed") {
                storeFile = fixedKeystore
                storePassword = keystorePass
                keyAlias = "scanclient"
                keyPassword = keystorePass
            }
        }
    }

    buildTypes {
        debug {
            if (fixedKeystore.exists()) signingConfig = signingConfigs.getByName("fixed")
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val cameraX = "1.4.0"
    val room = "2.6.1"

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // OCR embarqué (hors ligne, pas de téléchargement de modèle)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
}
