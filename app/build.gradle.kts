import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Ключ підпису релізу. Лежить у keystore.properties, якого НЕМАЄ в git.
 * Якщо файла немає — release збереться без підпису, а не впаде.
 *
 * Навіщо це взагалі: APK, підписаний debug-ключем, для Play Захисту —
 * застосунок від невідомого розробника, і він блокує встановлення.
 * Свій постійний ключ ще й робить наступні версії ОНОВЛЕННЯМ,
 * а не окремим застосунком поруч.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * Перевіряємо не лише наявність налаштувань, а й самого файла ключа.
 * Інакше, коли keystore.properties уже створено, а MAKE-KEY.bat ще не
 * запускали, збірка падає з невиразним «Keystore file not found».
 */
val keystoreFile = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it) }
val hasKeystore = keystoreFile != null &&
    keystoreFile.exists() &&
    keystoreProperties.getProperty("storePassword").isNullOrBlank().not() &&
    keystoreProperties.getProperty("keyAlias").isNullOrBlank().not()

if (keystoreFile != null && !keystoreFile.exists()) {
    logger.warn(
        "keystore.properties є, але файла ключа ${keystoreFile.name} немає. " +
            "Запустіть MAKE-KEY.bat — інакше release вийде непідписаним."
    )
}

android {
    namespace = "com.bell.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bell.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v2 + v3: без них Android 11+ ставить APK неохоче.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            // Мінімізацію не вмикаємо: Compose + рефлексія в темах, а виграш
            // у розмірі тут нікому не потрібен.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
}
