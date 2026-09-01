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

rootProject.name = "here-me"
include(":core")

// :core is plain Kotlin and builds anywhere, which is deliberate: the parser and the
// money arithmetic are the parts most likely to be wrong, so their tests must be
// runnable without an Android toolchain (CI, a container, a laptop without Studio).
//
// :app needs a real SDK, so it is only included when one is actually present. Android
// Studio always sets this up; a bare checkout runs `./gradlew :core:test` and skips it.
fun sdkDirFromLocalProperties(): String? {
    val localProperties = file("local.properties")
    if (!localProperties.exists()) return null
    val properties = java.util.Properties()
    localProperties.inputStream().use { stream -> properties.load(stream) }
    return properties.getProperty("sdk.dir")
}

val androidSdkDir: String? = listOfNotNull(
    sdkDirFromLocalProperties(),
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
).firstOrNull { File(it).isDirectory }

if (androidSdkDir != null) {
    include(":app")
} else {
    logger.lifecycle(
        "here-me: no Android SDK found, so the :app module is not part of this build. " +
            "Open the project in Android Studio, or set ANDROID_HOME, to build the app. " +
            "`./gradlew :core:test` covers the parser and balance logic without it.",
    )
}
