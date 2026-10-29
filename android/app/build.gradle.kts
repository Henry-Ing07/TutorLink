plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tutorlink.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tutorlink.app"
        // API 26: cubre los equipos de gama baja que ya tienen docentes y estudiantes.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // Backend local vía `adb reverse tcp:5000 tcp:5000`. Se usa el loopback y no
            // 10.0.2.2 porque Android 17 bloquea por defecto el acceso a la red local.
            buildConfigField("String", "API_URL", "\"http://127.0.0.1:5000\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Producción solo por HTTPS (RNF-03). Ajustar al dominio real del backend.
            buildConfigField("String", "API_URL", "\"https://api.tutorlink.example\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Las versiones se actualizan de forma deliberada, revisando licencia y cambios
        // (acuerdo ético de IA, FPI-01), no cada vez que sale una nueva.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
        warningsAsErrors = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
}
