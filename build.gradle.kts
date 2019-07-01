import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.40"
}

group = "club.issizler.okyanus"
version = "0.1.0"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.40")
    implementation("com.beust:klaxon:5.0.1")
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to "club.issizler.okyanus.installer.Installer"
        )
    }

    from(configurations.compileClasspath.map { if (it.isDirectory) it else zipTree(it) })
    exclude("**/*.kotlin*", "META-INF/maven/**/*")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}