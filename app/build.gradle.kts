import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.diegoca.gpsmocker"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.diegoca.gpsmocker"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"
        val adsEnabled = providers.gradleProperty("adsEnabled").getOrElse("false").toBooleanStrict()
        val adsTest = providers.gradleProperty("adsTestMode").getOrElse("true").toBooleanStrict()
        val appId = if (adsTest) "ca-app-pub-3940256099942544~3347511713" else providers.gradleProperty("admobAppId").getOrElse("")
        val bannerId = if (adsTest) "ca-app-pub-3940256099942544/9214589741" else providers.gradleProperty("admobBannerId").getOrElse("")
        require(!adsEnabled || appId.matches(Regex("ca-app-pub-[0-9]{16}~[0-9]{10}")) && bannerId.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}"))) { "Configure valid AdMob IDs or disable ads." }
        buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
        buildConfigField("String", "ADMOB_APP_ID", "\"$appId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$bannerId\"")
        manifestPlaceholders["admobAppId"] = appId.ifBlank { "ca-app-pub-3940256099942544~3347511713" }
        val routingUrl = providers.gradleProperty("routingUrl").getOrElse("").trim()
        require(routingUrl.isBlank() || runCatching {
            val uri = URI(routingUrl)
            routingUrl.length <= 2048 && uri.scheme == "https" && uri.host != null &&
                uri.path == "/v1/route" && uri.query == null && uri.fragment == null && uri.userInfo == null
        }.getOrDefault(false)) { "routingUrl must be an HTTPS proxy URL ending in /v1/route, without credentials or query parameters." }
        require(!adsEnabled || adsTest || !appId.startsWith("ca-app-pub-3940256099942544")) { "Production ads require your own AdMob IDs." }
        buildConfigField("String", "ROUTING_URL", "\"$routingUrl\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "GPS Mocker Dev")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            it.forkEvery = 1
            it.testLogging {
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                events("failed")
            }
            providers.gradleProperty("legacyBackup").orNull?.let { path -> it.systemProperty("gps.backup.path", path); it.inputs.file(path) }
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.datastore:datastore:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

tasks.register("verifyLegacyBackup") {
    group = "verification"
    description = "Validates the USB rescue JSON using the Android backup codec."
    dependsOn("testDebugUnitTest")
    doFirst { require(providers.gradleProperty("legacyBackup").isPresent) { "Use -PlegacyBackup=/absolute/path.json" } }
}
