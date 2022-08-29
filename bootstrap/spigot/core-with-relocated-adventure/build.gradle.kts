plugins {
    id("com.github.johnrengelman.shadow")
}

dependencies {
    api(projects.core)
    api("net.kyori", "adventure-text-serializer-bungeecord", Versions.adventurePlatformVersion)
}

tasks {
    shadowJar {
        relocate("net.kyori", "org.geysermc.geyser.shaded.kyori")
    }
    build {
        dependsOn(shadowJar)
    }
}