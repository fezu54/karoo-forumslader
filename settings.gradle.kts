@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val localProperties = java.util.Properties().apply {
        val file = settingsDir.resolve("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    repositories {
        google()
        mavenCentral()
        // karoo-ext from Github Packages
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .getOrElse(localProperties.getProperty("gpr.user") ?: System.getenv("USERNAME") ?: "")
                password = providers.gradleProperty("gpr.key")
                    .getOrElse(localProperties.getProperty("gpr.key") ?: System.getenv("TOKEN") ?: "")
            }
        }
    }
}

rootProject.name = "Karoo Forumslader Extension"
include("app")
