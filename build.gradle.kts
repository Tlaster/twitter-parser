import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("multiplatform") version "1.9.23"
    id("org.jetbrains.kotlinx.kover") version "0.7.0-Alpha"
    id("com.vanniktech.maven.publish") version "0.34.0"
}

val libName = "twitter-parser"
val libGroup = "moe.tlaster"
val libVersion = "0.5.5"

group = libGroup
version = libVersion

repositories {
    mavenCentral()
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    mingwX64()
    linuxX64()
    linuxArm64()
    wasmJs()
    wasmWasi()

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}


mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates(
        groupId = libGroup,
        artifactId = libName,
        version = libVersion,
    )
    pom {
        name.set(libName)
        description.set("Twitter parser")
        url.set("https://github.com/Tlaster/twitter-parser")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("Tlaster")
                name.set("James Tlaster")
                email.set("tlaster@outlook.com")
            }
        }
        scm {
            url.set("https://github.com/Tlaster/twitter-parser")
            connection.set("scm:git:git://github.com/Tlaster/twitter-parser.git")
            developerConnection.set("scm:git:git://github.com/Tlaster/twitter-parser.git")
        }
    }
}
