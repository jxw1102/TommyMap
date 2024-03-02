plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val tomtomApiKey: String by project

android {
    namespace = "com.example.tommymap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tommymap"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes.configureEach {
        buildConfigField("String", "TOMTOM_API_KEY", "\"$tomtomApiKey\"")
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs.pickFirsts.add("lib/**/libc++_shared.so")
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    val tom2Version = "0.48.0"
    implementation("com.tomtom.sdk.maps:map-display:$tom2Version")
    implementation("com.tomtom.sdk.location:provider-android:$tom2Version")
    implementation("com.tomtom.sdk.location:provider-simulation:$tom2Version")
    implementation("com.tomtom.sdk.location:provider-map-matched:$tom2Version")
    implementation("com.tomtom.sdk.search:search-online:$tom2Version")
    implementation("com.tomtom.sdk.routing:route-planner-online:$tom2Version")
    implementation("com.tomtom.sdk.navigation:navigation-online:$tom2Version")
    implementation("com.tomtom.sdk.navigation:route-replanner-online:$tom2Version")
    implementation("com.tomtom.sdk.navigation:ui:$tom2Version")
}