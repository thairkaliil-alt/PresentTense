plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.allinone.blocker"
    compileSdk = 34

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.allinone.blocker"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        debug {
            // Enabling R8 on debug makes Compose lists dramatically smoother —
            // the compiler optimizations that eliminate unnecessary recompositions
            // only kick in when the code goes through the R8/D8 pipeline.
            isMinifyEnabled = true
            isShrinkResources = false   // keep all resources so nothing breaks
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    buildFeatures {
        compose = true
        // Lets us read the app version in code via BuildConfig.VERSION_NAME
        // (used on the new Settings screen, "About" section).
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // DataStore (Preferences) — used by the new Settings screen to permanently
    // save notification toggle states. This is Google's modern replacement
    // for SharedPreferences; the rest of the app still uses SharedPreferences
    // directly and that is untouched by this change.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Material Components for Android — needed for the XML theme parents
    // (Theme.Material3.Light.NoActionBar / Theme.Material3.Dark.NoActionBar)
    // used in values/themes.xml and values-night/themes.xml.
    implementation("com.google.android.material:material:1.12.0")

    // Bumped from 2024.06.00 so we can use Modifier.animateItem() — the
    // official Compose API for smooth "slide into place" list reordering
    // (added in Compose Foundation 1.7, first shipped in BOM 2024.09.00).
    // Picked the last patch release in that same 1.7.x family (2024.09.03)
    // to get the feature with the smallest possible version jump — same
    // Kotlin compiler line (1.5.14 / Kotlin 1.9.24) as before, so nothing
    // else in the app should be affected.
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
