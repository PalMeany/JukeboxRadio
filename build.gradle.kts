plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("net.fabricmc.fabric-loom") version "1.14-SNAPSHOT" apply false
}

allprojects {
    group = "su.nuv"
    version = "1.2"
}

subprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.maxhenkel.de/repository/public")
        maven("https://maven.lavalink.dev/releases")
        maven("https://jitpack.io")
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(25)
        }
    }
}
