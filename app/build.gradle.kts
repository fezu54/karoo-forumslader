import org.gradle.api.JavaVersion.VERSION_11
import org.happycode.karoo.forumslader.build.KarooAppManifestSpec
import org.happycode.karoo.forumslader.build.KarooManifestJsonSerializer
import org.happycode.karoo.forumslader.build.KarooManifestVersionNormalizer
import org.happycode.karoo.forumslader.build.ReleaseNotesFormatter
import java.io.File
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.happycode.karoo.forumslader"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.happycode.karoo.forumslader"
        minSdk = 26
        targetSdk = 37
        versionCode = (project.findProperty("versionCode") as? String)?.toInt() ?: 1
        versionName = KarooManifestVersionNormalizer.normalize(
            (project.findProperty("versionName") as? String) ?: "1.0"
        )
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
            it.systemProperty("project.rootDir", rootProject.projectDir.absolutePath)
            if (gradle.startParameter.taskNames.any { task -> task.endsWith("generateFieldScreenshots") }) {
                it.filter {
                    includeTestsMatching("org.happycode.karoo.forumslader.presentation.DataFieldSnapshotGeneratorTest")
                }
                it.outputs.upToDateWhen { false }
            }
        }
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

base.archivesName.set("karoo-forumslader-${android.defaultConfig.versionName}")

kover {
    reports {
        filters {
            excludes {
                classes("*.BuildConfig", "*.R", "*.R$*", "*_Impl", "*_Impl$*")
            }
        }
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.kable.core)
    implementation(libs.nordic.dfu)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.qrcode.kotlin)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.karoo.ext.testing)
    testImplementation(libs.karoo.ext.testing.robolectric)
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.junit.vintage.engine)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("generateFieldScreenshots") {
    description = "Generates high-resolution RemoteViews data field screenshots for documentation."
    group = "documentation"
    dependsOn("testDebugUnitTest")
}

tasks.register("generateManifest") {
    description = "Generates manifest.json for the Hammerhead Karoo Extension Library."
    group = "publishing"

    val outputFile = layout.buildDirectory.file("outputs/manifest/manifest.json")
    val appId = requireNotNull(android.defaultConfig.applicationId)
    val resolvedVersion = requireNotNull(android.defaultConfig.versionName)
    val resolvedVersionCode = requireNotNull(android.defaultConfig.versionCode)
    val baseUrl = (project.findProperty("manifestBaseUrl") as? String)
        ?: "https://github.com/fezu54/karoo-forumslader/releases/latest/download"
    val releaseNotesPath = project.findProperty("releaseNotesFile") as? String
    val inlineReleaseNotes = project.findProperty("releaseNotes") as? String
    val rootDir = rootProject.projectDir
    val resolvedNotesFile = releaseNotesPath?.let { path ->
        File(path).let { if (it.isAbsolute) it else rootDir.resolve(path) }
    }

    inputs.property("packageName", appId)
    inputs.property("versionName", resolvedVersion)
    inputs.property("versionCode", resolvedVersionCode)
    inputs.property("baseUrl", baseUrl)
    inputs.property("inlineReleaseNotes", inlineReleaseNotes ?: "")
    inputs.property("releaseNotesPath", releaseNotesPath ?: "")
    resolvedNotesFile?.let { file -> inputs.file(file).optional() }
    outputs.file(outputFile)

    doLast {
        val rawNotes = resolvedNotesFile?.takeIf { it.exists() }?.readText()
            ?: inlineReleaseNotes

        val screenshotFiles = listOf(
            "card-1.png",
            "card-2.png",
            "card-3.png",
            "card-4.png",
            "card-6.png",
            "card-7.png",
            "card-8.png"
        )

        val spec = KarooAppManifestSpec(
            label = "Karoo Forumslader",
            packageName = appId,
            latestApkUrl = "$baseUrl/karoo-forumslader-$resolvedVersion-release.apk",
            latestVersion = resolvedVersion,
            latestVersionCode = resolvedVersionCode,
            iconUrl = "$baseUrl/karoo-forumslader.png",
            developer = "fezu54",
            description = "Connect your Forumslader V5/V6 hub-dynamo charger to Hammerhead Karoo. " +
                "Provides 16 live telemetry data fields, climb-aware battery range estimation, " +
                "automated 12V light USB power management, safety alerts, FIT recording, " +
                "wireless log export, and V6 firmware updates.",
            releaseNotes = ReleaseNotesFormatter.format(rawNotes),
            screenshotUrls = screenshotFiles.map { fileName -> "$baseUrl/$fileName" },
            tags = emptyList()
        )

        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(KarooManifestJsonSerializer.toJson(spec))
        println("Generated ${target.absolutePath} for version $resolvedVersion ($resolvedVersionCode)")
    }
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
    jvmToolchain(21)
}

