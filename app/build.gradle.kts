import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.feishin.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.feishin.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "1.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only English is shipped: the app has no other locale content, and
        // this strips ~85 library locales (mostly AndroidX) from resources.
        resConfigs("en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Local, untracked keystore (see README). Falls back to the debug
            // config when the file/keys are absent, so CI/`assembleRelease`
            // still produce an installable APK.
            val ks = rootProject.file("keystore/vusic-release.jks")
            val props = rootProject.file("keystore/keystore.properties")
            if (ks.exists() && props.exists()) {
                val map = Properties()
                props.inputStream().use { map.load(it) }
                signingConfig = signingConfigs.create("release") {
                    storeFile = ks
                    storePassword = map.getProperty("storePassword")
                    keyAlias = map.getProperty("keyAlias")
                    keyPassword = map.getProperty("keyPassword")
                }
                println("Vusic release signing: ${ks.name}")
            } else {
                println("Vusic release signing: no keystore, APK will be unsigned")
            }
        }
        debug {
            applicationIdSuffix = ""
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    splits {
        // Phone APK carries only arm64-v8a native code; the emulator build
        // stays x86_64. No universal APK (each split drops the other ABIs'
        // .so files: only DataStore + Compose path libs ship any).
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.md"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.coil.compose)

    implementation(libs.datastore.preferences)
    implementation(libs.palette)
    implementation(libs.reorderable)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    implementation(libs.security.crypto)
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Part B: on-device MediaBrowser client verifying the Auto browse tree.
    androidTestImplementation(libs.media3.session)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
}
