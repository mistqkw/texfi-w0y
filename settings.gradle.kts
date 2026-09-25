pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // innertubex публикуется только через JitPack. Фильтр по группе
        // обязателен: без него Gradle пойдёт искать на JitPack вообще всё,
        // что не нашлось в mavenCentral, и холодная сборка растянется.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.MetrolistGroup.innertubex") }
        }
    }
}

rootProject.name = "w0y"
include(":app")
