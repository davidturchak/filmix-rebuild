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

rootProject.name = "filmix-client"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:network")
include(":core:data")
include(":feature:home")
include(":feature:profile")
include(":feature:detail")
include(":feature:player")
include(":feature:search")
include(":feature:library")
include(":feature:catalog")
include(":feature:config")
