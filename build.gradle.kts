// Every plugin is declared here (not applied) so the whole build shares one plugin
// classpath. The Compose and serialization compiler plugins also put the matching
// Kotlin Gradle plugin on that classpath, which AGP's built-in Kotlin then uses.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
