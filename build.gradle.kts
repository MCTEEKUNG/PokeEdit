plugins {
    id("fabric-loom") version "1.9.2"
}

version = "${property("mod_version")}"
group = property("maven_group") as String

base {
    archivesName = property("archives_base_name") as String
}

repositories {
    // Modrinth maven - serves mod jars by version id
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}")

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Cobblemon 1.7.3 (Fabric) - the mod we extend
    modImplementation("maven.modrinth:cobblemon:${property("cobblemon_version")}")

    // Cobblemon is written in Kotlin, the Kotlin runtime has to be on the class path
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect:2.2.0")

    // Optional integrations
    modImplementation("maven.modrinth:modmenu:${property("modmenu_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.jar {
    from("LICENSE")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.processResources {
    val modVersion = project.version.toString()
    val minecraftVersion = project.property("minecraft_version").toString()
    val loaderVersion = project.property("loader_version").toString()
    val fabricVersion = project.property("fabric_version").toString()
    inputs.property("version", modVersion)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    inputs.property("fabric_version", fabricVersion)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to modVersion,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
            "fabric_version" to fabricVersion,
        )
    }
}
