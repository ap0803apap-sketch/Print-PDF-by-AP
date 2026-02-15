plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}



android {
    namespace = "com.ap.print.pdf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ap.print.pdf"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0(15-02-26)"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    // 🔥 REQUIRED FOR COMPOSE
    buildFeatures {
        compose = true
    }
}

dependencies {

    // CORE
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // ACTIVITY COMPOSE
    implementation("androidx.activity:activity-compose:1.8.2")

    // VIEWMODEL COMPOSE (YOU WERE MISSING THIS)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // COMPOSE BOM (STABLE — NOT 2026!)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // MATERIAL 3
    implementation("androidx.compose.material3:material3")
    implementation(libs.material)

    // ICONS
    implementation("androidx.compose.material:material-icons-extended")

    // DEBUG
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // TEST
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
}