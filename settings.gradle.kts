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
        // Epublib releases are hosted on a GitHub raw repo
        maven { url = uri("https://github.com/psiegman/mvn-repo/raw/master/releases") }
        mavenCentral()
    }
}

rootProject.name = "BOOKS"
include(":app")
 