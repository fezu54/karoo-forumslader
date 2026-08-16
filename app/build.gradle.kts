import org.gradle.api.JavaVersion.VERSION_11
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

android {
    namespace = "org.happycode.karoo.forumslader"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.happycode.karoo.forumslader"
        minSdk = 23
        targetSdk = 37
        versionCode = (project.findProperty("versionCode") as? String)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "1.0"
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
    }
    signingConfigs {
        create("release") {
            storeFile = file("${System.getProperty("user.home")}/.keystore/upload-keystore.jks")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = VERSION_11
        targetCompatibility = VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kover {
    reports {
        filters {
            excludes {
                classes("*.BuildConfig", "*.R", "*.R$*")
            }
        }
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.kable.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.compose.icons.extended)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}


tasks.register("checkCoverageBaseline") {
    description = "Checks if the current code coverage meets or exceeds the baseline."
    group = "verification"
    dependsOn("koverXmlReport")
    doLast {
        val reportFile = layout.buildDirectory.file("reports/kover/report.xml").get().asFile
        if (!reportFile.exists()) throw GradleException("Coverage report not found at ${reportFile.absolutePath}")

        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(reportFile)
        val lineCounter = (0 until doc.getElementsByTagName("counter").length)
            .map { doc.getElementsByTagName("counter").item(it) }
            .firstOrNull { it.parentNode.nodeName == "report" && it.attributes.getNamedItem("type")?.nodeValue == "LINE" }

        val currentCoverage = lineCounter?.attributes?.run {
            val covered = getNamedItem("covered")?.nodeValue?.toDouble() ?: 0.0
            val missed = getNamedItem("missed")?.nodeValue?.toDouble() ?: 0.0
            if (covered + missed > 0) (covered / (covered + missed)) * 100 else 0.0
        } ?: 0.0

        val baselineFile = rootProject.file(".github/coverage-baseline.txt")
        val baselineCoverage = if (baselineFile.exists()) baselineFile.readText().trim().toDoubleOrNull() ?: 0.0 else 0.0

        val formattedCurrent = "%.2f".format(Locale.US, currentCoverage)
        val formattedBaseline = "%.2f".format(Locale.US, baselineCoverage)

        println("Current Line Coverage: $formattedCurrent%")
        println("Baseline Line Coverage: $formattedBaseline%")

        val currentRounded = formattedCurrent.toDouble()
        val baselineRounded = formattedBaseline.toDouble()

        val shouldUpdate = project.hasProperty("updateBaseline") || System.getenv("CI") == "true"
        if (shouldUpdate) {
            baselineFile.apply {
                parentFile.mkdirs()
                writeText(formattedCurrent)
            }
        }

        if (currentRounded < baselineRounded) {
            throw GradleException("FAIL: Code coverage decreased from $formattedBaseline% to $formattedCurrent%!")
        }
        println("SUCCESS: Code coverage is equal to or higher than the baseline.")
    }
}

kotlin {
    jvmToolchain(17)
}

