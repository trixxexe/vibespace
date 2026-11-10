pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VibeSpace"

// Application module
include(":app")

// Core modules
include(":core:common")
include(":core:designsystem")
include(":core:workspace")
include(":core:language")
include(":core:editor")
include(":core:runner")
include(":core:terminal")
include(":core:ai")

// Feature modules
include(":feature:home")
include(":feature:explorer")
include(":feature:editor")
include(":feature:terminal")
include(":feature:aichat")
include(":feature:search")
include(":feature:settings")
