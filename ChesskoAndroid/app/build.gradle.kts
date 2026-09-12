plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.veljkoni.chessko"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.veljkoni.chessko"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // `puzzles.sqlite` mora ostati NEKOMPRESOVAN u APK-u: AAPT po
        // podrazumevanom spisku ekstenzija kompresuje sve sto nije vec
        // kompresovan format (slike, audio...), a ".sqlite" nije na tom
        // spisku. `PuzzleRepository` cita velicinu preko
        // `AssetManager.openFd()` da bi znao da li je kopija u `filesDir`
        // potpuna — `openFd()` puca sa `FileNotFoundException` na
        // kompresovanom asset-u ("it is probably compressed"). Dokazano na
        // emulatoru pre ovog dodatka: svih 8 instrumentisanih testova je
        // padalo na tacno toj gresci u konstruktoru.
        noCompress += "sqlite"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Coil for SVG loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}