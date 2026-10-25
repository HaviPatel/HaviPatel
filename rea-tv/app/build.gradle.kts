import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from the environment (CI secrets or your shell). Without it the
// release APK is signed with the debug key so it still installs on a Fire TV.
val releaseKeystore: String? = System.getenv("REA_KEYSTORE")?.takeIf { file(it).exists() }

// Optional TMDB key for Top Shelf series and movies (CI secret TMDB_API_KEY, or -PtmdbApiKey=...).
// It can also be set on the TV later with scripts/rea.sh tmdb. Only key characters survive.
val tmdbKey: String = (System.getenv("TMDB_API_KEY") ?: providers.gradleProperty("tmdbApiKey").orNull ?: "")
    .filter { it.isLetterOrDigit() || it in "._-" }

android {
    namespace = "com.reatv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reatv.launcher"
        minSdk = 22
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en")
        buildConfigField("String", "TMDB_KEY", "\"$tmdbKey\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("REA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("REA_KEY_ALIAS")
                keyPassword = System.getenv("REA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/androidx/**",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
