plugins {
    id("java")
    id("jacoco")
    id("org.sonarqube") version "7.0.1.6134"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugin("com.intellij.java")
        pluginModule(implementation("io.github.sibmaks.jjtemplate:jjtemplate:0.5.3"))
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }
    }
}

val versionFromProperty = "${project.property("version")}"
val versionFromEnv: String? = System.getenv("VERSION")

version = versionFromEnv ?: versionFromProperty
group = "${project.property("group")}"

val targetJavaVersion = (project.property("jdk_version") as String).toInt()

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(targetJavaVersion)
    }
}