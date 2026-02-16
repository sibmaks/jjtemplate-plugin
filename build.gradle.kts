plugins {
    id("java")
    id("jacoco")
    id("org.sonarqube") version "7.0.1.6134"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

repositories {
    mavenLocal()
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("io.github.sibmaks.jjtemplate:jjtemplate:unspecified")

    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(21)
    }

}
