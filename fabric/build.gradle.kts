plugins {
    id("net.fabricmc.fabric-loom")
    id("com.gradleup.shadow")
}

base {
    archivesName.set("JukeboxRadio-fabric")
}

val shade: Configuration by configurations.creating

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.3")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.24")

    implementation(project(":core"))
    implementation("net.kyori:adventure-api:5.2.0")
    implementation("net.kyori:adventure-text-serializer-gson:5.2.0")
    implementation("org.yaml:snakeyaml:2.2")

    shade(project(":core"))
    shade("net.kyori:adventure-api:5.2.0")
    shade("net.kyori:adventure-text-serializer-gson:5.2.0")
    shade("org.yaml:snakeyaml:2.2")
}

loom {
    runs {
        named("server") {
            runDir("run-server")
        }
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(shade)
    from(sourceSets.main.get().output)
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
    relocate("net.kyori", "$base.kyori")
    relocate("org.yaml.snakeyaml", "$base.snakeyaml")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    // Minecraft ships these
    dependencies {
        exclude(dependency("org.slf4j:.*"))
        exclude(dependency("com.google.code.gson:.*"))
        exclude(dependency("com.google.guava:.*"))
        exclude(dependency("org.jspecify:.*"))
        exclude(dependency("org.jetbrains:annotations"))
    }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
