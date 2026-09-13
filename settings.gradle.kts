pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
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

rootProject.name = "MyHabitApp"
include(":app")

// M14.9 — generates the Baseline Profile and ships nothing. Approved as this project's second module
// (ADR-113); `CLAUDE.md` §5's single-module rule still holds for everything the APK contains.
include(":baselineprofile")
