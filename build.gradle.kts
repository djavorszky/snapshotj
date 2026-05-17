plugins {
    id("java")
    id("maven-publish")
}

group = "dev.jdan"
version = "0.1.0"

val javaVersion = (findProperty("javaVersion") as String?)?.toInt() ?: 17

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.18.2"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("org.apache.commons:commons-csv:1.12.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.15")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("snapshotj")
                description.set("JUnit-agnostic Java 17 library for inline snapshot testing")
                // TODO(Phase 11): fill in url, licenses, scm, developers before first release
            }
        }
    }
}