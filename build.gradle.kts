import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    implementation("org.eclipse.elk:org.eclipse.elk.core:0.9.1")
    implementation("org.eclipse.elk:org.eclipse.elk.alg.layered:0.9.1")

    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        pluginVerifier()
        zipSigner()
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    instrumentCode = false

    pluginConfiguration {
        id = "org.mozilla.firefox-redux-navigator"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description = "Navigate Firefox Android Redux actions from dispatch sites to middleware handlers and reducers."
        ideaVersion {
            sinceBuild = "251"
        }
    }
}

tasks.named("buildSearchableOptions").configure {
    // This task launches a full IDE instance, which conflicts with an already running Studio.
    enabled = false
}
