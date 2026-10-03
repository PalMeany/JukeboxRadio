plugins {
    java
    id("com.gradleup.shadow")
}

base {
    archivesName.set("JukeboxRadio")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.49-alpha")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.24")
    implementation(project(":core"))
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

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
