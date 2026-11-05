buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.1.1")
        classpath(
            "org.jetbrains.kotlin.plugin.compose:" +
                "org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.2.10",
        )
    }
}
