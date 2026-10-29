plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    // Existing findings; CI fails only on new ones. Regenerate: ./gradlew detektBaseline
    baseline = rootProject.file("config/detekt-baseline.xml")
}

base {
    archivesName.set("katapult-v1.6")
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

android {
    namespace = "com.gezimos.katapult"
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "com.gezimos.katapult"
        minSdk = 30
        targetSdk = 36
        versionCode = 7
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The repo whose releases the in-app updater follows: the one CI built from, so a
        // fork's builds update from the fork (it signs with its own key).
        buildConfigField(
            "String",
            "RELEASES_REPO",
            "\"${System.getenv("GITHUB_REPOSITORY") ?: "richardkrikler/Katapult"}\"",
        )

        // Release CI sets these from the tag, so the values above stay upstream's.
        System.getenv("VERSION_NAME")?.let { versionName = it }
        System.getenv("VERSION_CODE")?.let { versionCode = it.toInt() }
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // Built for the Mudita Kompakt, a phone; ChromeOS compatibility doesn't apply.
        disable += "PermissionImpliesUnsupportedChromeOsHardware"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
