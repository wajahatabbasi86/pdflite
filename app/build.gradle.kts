import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing — reads from keystore.properties (gitignored, never committed) at the repo
// root; see keystore.properties.example for the format. Absent entirely on a machine that
// hasn't set this up (CI without secrets, a fresh checkout) — assembleRelease/bundleRelease
// just aren't signed there rather than failing the whole build.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.trendoc.pdflite"
    35.also { compileSdk = it }

    defaultConfig {
        applicationId = "com.trendoc.pdflite"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // lintVitalRelease (which assembleRelease/bundleRelease depend on by default) crashes
        // in this environment with "Could not initialize class org.jetbrains.uast.UastFacade"
        // — a JVM/lint-tooling class-loading issue, not a real lint finding, and unrelated to
        // any code in this project. Run `./gradlew lint` on its own (non-fatal) to actually see
        // lint results; release builds shouldn't be blocked by a broken analyzer.
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"

            // Bouncy Castle (pulled in transitively by PdfBox-Android, for its standard
            // PDF encryption support — RC4/AES password handlers) bundles algorithm data
            // for every scheme it implements, including post-quantum ones TrenDoc has no
            // path to ever reach: PdfBox-Android only exercises BC's conventional
            // symmetric/RSA primitives for PDF passwords, never Picnic or SIKE. These
            // four property files alone account for ~7.5MB of dead weight in the APK.
            excludes += "org/bouncycastle/pqc/crypto/picnic/lowmc.properties"
            excludes += "org/bouncycastle/pqc/crypto/sike/*.properties"
        }
    }

    // Source lives under src/main/kotlin rather than the default src/main/java.
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    var compileSdkMinor = 0
    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    // PDF manipulation (merge/split/compress/create) — see docs/TECH_STACK.md
    implementation(libs.pdfbox.android)

    // Monetization (build step 8, docs/REQUIREMENTS.md §7) — AdMob banner + one-time
    // "Remove Ads" purchase via Play Billing.
    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
