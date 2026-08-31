import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Single source of truth for the app version.
 *
 * versionCode is derived from the commit count so it always increases without
 * anyone having to remember to bump it; versionName stays hand-managed because
 * it is the number users read.
 */
val appVersionName = "0.6.26"

fun git(vararg args: String): String = runCatching {
    providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

val gitSha: String = git("rev-parse", "--short", "HEAD").ifEmpty { "unknown" }
val gitDirty: Boolean = git("status", "--porcelain").isNotEmpty()
val commitCount: Int = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1

/**
 * Release signing. Loaded from keystore/release.properties, which is not in
 * version control; without it a release build falls back to the debug key and
 * cannot be used to update an existing install.
 */
val releaseProps = Properties().apply {
    val f = rootProject.file("keystore/release.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = releaseProps.getProperty("storeFile") != null

android {
    namespace = "net.filmix.client"
    compileSdk = 35

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        // Deliberately outside net.filmix.*, which is the namespace of the
        // app Play Protect flags. The Kotlin namespace stays put — Play
        // Protect matches on the manifest package, not the R class package.
        applicationId = "dev.turchak.filmixng"
        minSdk = 26
        targetSdk = 35
        versionCode = commitCount
        versionName = appVersionName

        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("boolean", "GIT_DIRTY", "$gitDirty")
    }

    buildTypes {
        release {
            // R8 full mode: debug dex is ~20MB of unshrunk Compose/Media3/
            // Retrofit; shrinking and resource stripping is what makes the
            // shippable size honest.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":feature:home"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:player"))
    implementation(project(":feature:search"))
    implementation(project(":feature:library"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:config"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.window.core)
    implementation(libs.androidx.adaptive)
    implementation(libs.coil.compose)
    implementation(libs.paging.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
