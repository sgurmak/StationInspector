import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.stationinspector"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.stationinspector"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        val mapyKey = localProperties.getProperty("MAPY_CZ_API_KEY", "KEY_NOT_FOUND")
        buildConfigField("String", "MAPY_CZ_API_KEY", "\"$mapyKey\"")
        val orsKey = localProperties.getProperty("ORS_API_KEY", "KEY_NOT_FOUND")
        buildConfigField("String", "ORS_API_KEY", "\"$orsKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Return default values (0/null/false) for un-mocked android.* calls
            // such as android.util.Log, so pure-logic unit tests don't crash.
            isReturnDefaultValues = true
            // Robolectric needs the merged Android resources/manifest on the
            // unit-test classpath to drive an in-memory Room (real SQLite) on the JVM.
            isIncludeAndroidResources = true
        }
    }
}

// Export the Room schema so future migrations can be verified with
// MigrationTestHelper and reviewed in version control.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Coil
    implementation(libs.coil.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Utilities
    implementation(libs.zip4j)
    implementation(libs.poi.ooxml)

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Map Engine
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Reorderable List
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    // Testing
    testImplementation(libs.junit)
    // Runtime verification on the JVM: Robolectric runs Room on real SQLite,
    // coroutines-test drives suspend/Flow code deterministically.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Forked unit-test JVMs don't inherit the daemon's SSL system properties, so
// Robolectric's runtime download of the android-all image fails behind a TLS-
// intercepting AV/proxy. If `testTrustStore` is set (kept in ~/.gradle, off the
// repo) point the test workers at that truststore so the download is trusted.
tasks.withType<Test>().configureEach {
    (project.findProperty("testTrustStore") as String?)?.let { store ->
        val pass = project.findProperty("testTrustStorePassword") as String? ?: "changeit"
        jvmArgs(
            "-Djavax.net.ssl.trustStore=$store",
            "-Djavax.net.ssl.trustStorePassword=$pass"
        )
    }
}
