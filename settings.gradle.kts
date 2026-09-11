pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Jingliu"
include(":app", ":core")

// The optional server must not block the Android app import/model in Android Studio.
// Enable explicitly only when building/deploying your own official identity authorization service:
// ./gradlew -PincludeAuthBridge=true :auth-bridge:test :auth-bridge:installDist
val includeAuthBridge = providers.gradleProperty("includeAuthBridge").orNull
require(includeAuthBridge == null || includeAuthBridge == "true" || includeAuthBridge == "false") {
    "includeAuthBridge must be true or false"
}
if (includeAuthBridge == "true") include(":auth-bridge")
