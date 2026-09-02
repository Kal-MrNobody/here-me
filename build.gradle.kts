// Plugins are declared per module rather than here, so that :core stays buildable on a
// machine with no Android SDK — declaring the Android plugins at the root, even with
// `apply false`, forces Gradle to resolve them for every build.
