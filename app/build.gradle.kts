plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ninjahabits.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ninjahabits.app"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://35-76-200-8.nip.io\"")
            buildConfigField("String", "API_KEY", "\"0900ecad2310fcf51187abac6d537ed623770c7db4297e1ceed923403f288eac\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "API_BASE_URL", "\"https://35-76-200-8.nip.io\"")
            buildConfigField("String", "API_KEY", "\"0900ecad2310fcf51187abac6d537ed623770c7db4297e1ceed923403f288eac\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
