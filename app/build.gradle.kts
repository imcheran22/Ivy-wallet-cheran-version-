plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.android")
    org.jetbrains.kotlin.plugin.compose
    id("dagger.hilt.android.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.ivy.wallet"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        // The app's own identity. google-services.json registers this id, so
        // Firebase resolves against it. `namespace` stays com.ivy.wallet: that
        // only decides where R is generated and renaming it would touch every
        // module for no user-visible gain.
        applicationId = "com.cheran.tracker"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.compile.sdk.get().toInt()
        versionName = libs.versions.version.name.get()
        versionCode = libs.versions.version.code.get().toInt()
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.jks")
            storePassword = "IVY7834!DEbug"
            keyAlias = "debug"
            keyPassword = "IVY7834!DEbug"
        }

        create("release") {
            storeFile = file("../sign.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }

        create("local") {
            storeFile = file("../local-release.jks")
            storePassword = System.getenv("LOCAL_STORE_PASSWORD") ?: "ivywallet2025"
            keyAlias = System.getenv("LOCAL_KEY_ALIAS") ?: "ivy-local"
            keyPassword = System.getenv("LOCAL_KEY_PASSWORD") ?: "ivywallet2025"
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

            isDebuggable = false
            isDefault = false
            isCrunchPngs = true

            signingConfig = signingConfigs.getByName("release")

            resValue("string", "app_name", "Tracker")

            ndk {
                debugSymbolLevel = "NONE"
            }
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            isDebuggable = true
            isDefault = true

            signingConfig = signingConfigs.getByName("debug")

            resValue("string", "app_name", "Tracker Debug")
        }

        create("local") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            matchingFallbacks.add("release")

            isDebuggable = false
            isDefault = false
            isCrunchPngs = true

            signingConfig = signingConfigs.getByName("local")

            resValue("string", "app_name", "Tracker")

            ndk {
                debugSymbolLevel = "NONE"
            }
        }

        create("demo") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            matchingFallbacks.add("release")

            isDebuggable = false
            isDefault = false

            signingConfig = signingConfigs.getByName("debug")

            // No applicationIdSuffix: a suffixed id has no Firebase client, and
            // the Google Services task fails before anything compiles.
            resValue("string", "app_name", "Tracker")
        }
    }

    val javaVersion = libs.versions.jvm.target.get()
    kotlinOptions {
        jvmTarget = javaVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
        targetCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "ComposeViewModelInjection"
        checkDependencies = true
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
        htmlOutput = file("${project.rootDir}/build/reports/lint/lint.html")
        xmlReport = true
        xmlOutput = file("${project.rootDir}/build/reports/lint/lint.xml")
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(projects.feature.balance)
    implementation(projects.feature.budgets)
    implementation(projects.feature.categories)
    implementation(projects.feature.editTransaction)
    implementation(projects.feature.exchangeRates)
    implementation(projects.feature.features)
    implementation(projects.feature.home)
    implementation(projects.feature.importData)
    implementation(projects.feature.loans)
    implementation(projects.feature.main)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.piechart)
    implementation(projects.feature.plannedPayments)
    implementation(projects.feature.reports)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)
    implementation(projects.feature.transactions)
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    implementation(projects.temp.legacyCode)
    implementation(projects.temp.oldDesign)
    implementation(projects.widget.addTransaction)
    implementation(projects.widget.balance)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.kotlin.android)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.activity)
    implementation(libs.bundles.google)
    implementation(libs.bundles.firebase)
    implementation(libs.datastore)
    implementation(libs.androidx.security)
    implementation(libs.androidx.biometrics)

    implementation(libs.bundles.hilt)
    implementation(libs.material)
    ksp(libs.hilt.compiler)

    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(libs.timber)
    implementation(libs.keval)
    implementation(libs.bundles.opencsv)
    implementation(libs.androidx.work)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.androidx.work.testing)

    lintChecks(libs.slack.lint.compose)
}
