plugins {
    `java-library`
}

dependencies {
    // provided by Paper; shaded into the Fabric jar
    compileOnlyApi("net.kyori:adventure-api:5.2.0")
    compileOnlyApi("org.yaml:snakeyaml:2.2")
    compileOnlyApi("com.google.code.gson:gson:2.11.0")
    compileOnlyApi("de.maxhenkel.voicechat:voicechat-api:2.6.24")
    compileOnlyApi("org.jetbrains:annotations:26.0.2")

    api("dev.arbjerg:lavaplayer:2.2.7")
    api("dev.lavalink.youtube:common:1.18.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testImplementation("net.kyori:adventure-api:5.2.0")
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("com.google.code.gson:gson:2.11.0")
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
