import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

val appId = "io.github.kdroidwin.suicanfc"
val verCode = 128
val verId = "1.5.7"
val signingProperties = Properties().apply {
    val signingFile = file("keystore.properties")
    require(signingFile.exists()) { "Missing app/keystore.properties for release signing" }
    signingFile.inputStream().use(::load)
}

android {
    namespace = "com.example.suicanfcreader"
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        versionCode = verCode
        versionName = verId

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(signingProperties.getProperty("storeFile"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // R8 remains enabled. Resource shrinking is disabled because it is not
            // reliable on the target Windows build environment.
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("version")

    productFlavors {
        create("default") {
            dimension = "version"
            val timestamp = releaseTime()
            val newFileName = "suicanfc-kd-externaldb-v${verId}_${timestamp}.apk"

            buildOutputs.all {
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                    .outputFileName = newFileName
            }
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = true
        abortOnError = true
        warningsAsErrors = true
    }
}

fun releaseTime(): String {
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
    return dateFormat.format(Date())
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.runtime.livedata)
    implementation(files("libs/security-crypto-1.1.0-alpha06.aar"))
    implementation(files("libs/tink-android-1.8.0.jar"))
    implementation(files("libs/gson-2.8.9.jar"))
    implementation(files("libs/error_prone_annotations-2.18.0.jar"))
    implementation(files("libs/jsr305-3.0.2.jar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Avoid ZipFileSystem handle conflicts on Windows while compiling Java sources.
tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.compilerArgs.add("-XDuseOptimizedZip=false")
}
