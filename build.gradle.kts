plugins {
    kotlin("jvm") version "1.5.10"
    id("com.google.cloud.tools.jib") version "3.2.1"
}

group = "no.haatveit"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation (group = "com.fasterxml.jackson.core", name = "jackson-core", version = "2.13.1")
    implementation (group = "com.fasterxml.jackson.core", name = "jackson-databind", version = "2.13.1")
    implementation("io.projectreactor:reactor-core:3.4.16")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.2")
    implementation("org.whispersystems:signal-protocol-java:2.8.1")
}

jib {
    from {
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm"
                os = "linux"
            }
        }
    }
    to {
        image = "ghcr.io/lhaatveit/bolia-com-outlet-bot:latest"
        auth.password = System.getenv("JIB_AUTH_PASSWORD")
        auth.username = System.getenv("JIB_AUTH_USERNAME")
    }
}
