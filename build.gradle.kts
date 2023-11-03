import java.util.Properties
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform") version "1.8.0"
    id("org.jetbrains.kotlinx.kover") version "0.7.0-Alpha"
    id("com.vanniktech.maven.publish") version "0.25.3"
}

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        nodejs()
//        browser()
    }
    ios()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    mingwX64()
    mingwX86()
    linuxX64()
    linuxArm64()
    linuxArm32Hfp()
    linuxMips32()
    linuxMipsel32()

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
    publishToMavenCentral(SonatypeHost.S01)
    signAllPublications()
    coordinates(
        groupId = "moe.tlaster",
        artifactId = "twitter-parser",
        version = "0.3.4",
    )
    pom {
        name.set("twitter-parser")
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
