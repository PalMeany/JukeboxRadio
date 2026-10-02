plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "su.nuv"
version = "1.1"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
    maven("https://maven.lavalink.dev/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.49-alpha")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.24")

    implementation("dev.arbjerg:lavaplayer:2.2.7")
    implementation("dev.lavalink.youtube:common:1.18.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testImplementation("io.papermc.paper:paper-api:26.3.build.49-alpha")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Lavaplayer's JNI natives bind to com.sedmelluq.* class names: never relocate lavaplayer itself.
    val base = "su.nuv.radio.libs"
    relocate("org.apache.http", "$base.apache.http")
    relocate("org.apache.commons", "$base.apache.commons")
    relocate("com.fasterxml.jackson", "$base.jackson")
    relocate("org.json", "$base.json")
    relocate("com.grack.nanojson", "$base.nanojson")
    relocate("org.mozilla", "$base.mozilla")
    relocate("org.jsoup", "$base.jsoup")
    relocate("net.iharder", "$base.iharder")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    // Paper ships SLF4J; bundling a second copy only produces binding warnings
    dependencies {
        exclude(dependency("org.slf4j:.*"))
    }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.named<Test>("test") {
    systemProperty("radio.preview.font", (findProperty("previewFont") as String?) ?: "")
    systemProperty("radio.live.url", (findProperty("liveUrl") as String?) ?: "")
    systemProperty("radio.live.ytdlp", (findProperty("ytdlp") as String?) ?: "")
    systemProperty("radio.live.proxy", (findProperty("proxy") as String?) ?: "")
    useJUnitPlatform {
        if (!project.hasProperty("live")) {
            excludeTags("live")
        }
    }
    testLogging {
        showStandardStreams = project.hasProperty("live")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
