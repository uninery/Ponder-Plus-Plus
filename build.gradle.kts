plugins {
    id("net.neoforged.moddev")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

version = property("mod_version")
group = property("maven_group")

val commonJava = File(projectDir, "common-src/src/main/java")
val commonResources = File(projectDir, "common-src/src/main/resources")

neoForge {
    version = "21.1.206"

    parchment {
        minecraftVersion = "1.21.1"
        mappingsVersion = "2024.11.17"
    }

    runs {
        create("client") {
            client()
            gameDirectory = file("run/client")
        }
        create("server") {
            server()
            gameDirectory = file("run/server")
        }
        configureEach {
            jvmArgument("-XX:+AllowEnhancedClassRedefinition")
            jvmArgument("-XX:+IgnoreUnrecognizedVMOptions")
            jvmArgument("-Dmixin.debug.export=true")
        }
    }

    mods {
        create("mod_id") {
            sourceSet(sourceSets["main"])
        }
    }
}

// Compile against the shared source directory that is version-agnostic. Both the
// 1.20.1 (Forge) and the 1.21.1 (NeoForge) builds point at this same folder, so a
// single copy of the mixins / logic is kept.
sourceSets {
    main {
        java {
            srcDir(commonJava)
        }
        resources {
            srcDir(commonResources)
        }
    }
}

dependencies {
    compileOnly("net.createmod.ponder:Ponder-Common-1.21.1:${property("ponder_version")}")
    // The NeoForge build of Ponder is a full (shadowed) jar bundling the common code.
    compileOnly("net.createmod.ponder:Ponder-NeoForge-1.21.1:${property("ponder_version")}")
    runtimeOnly("net.createmod.ponder:Ponder-NeoForge-1.21.1:${property("ponder_version")}")

    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:${property("flywheel_version")}")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-1.21.1:${property("flywheel_version")}")
}

repositories {
    maven { url = uri("https://maven.createmod.net") }
    maven { url = uri("https://maven.neoforged.net/releases") }
}

tasks.withType(ProcessResources::class).configureEach {
    filteringCharset = "UTF-8"
    val expandProps = mapOf(
        "version" to project.version.toString(),
        "mod_id" to "ponderpp",
        "mod_name" to "Ponder++ (思索++)",
        "mod_author" to "Ponder++ contributors",
        "mod_license" to "GPL-3.0",
        "minecraft_version" to "1.21.1",
        "neo_version" to "21.1.206",
        "pack_format" to 18,
    )
    inputs.properties(expandProps)
    filesMatching(listOf("*.mixins.json", "pack.mcmeta", "META-INF/neoforge.mods.toml")) {
        expand(expandProps)
    }
}
