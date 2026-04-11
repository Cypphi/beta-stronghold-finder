import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "2.3.0"
}

group = "dev.cypphi"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val executableJar by tasks.registering(Jar::class) {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds a runnable jar in the project root."
    archiveFileName.set("${project.name}.jar")
    destinationDirectory.set(layout.projectDirectory)

    manifest {
        attributes(
            "Main-Class" to "dev.cypphi.betastrongholdfinder.MainKt",
        )
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
}

tasks.assemble {
    dependsOn(executableJar)
}
