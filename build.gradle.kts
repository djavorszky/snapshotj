plugins {
    id("java")
    id("maven-publish")
    id("signing")
    id("com.gradleup.nmcp") version "0.1.5"
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
                url.set("https://github.com/djavorszky/snapshotj")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("djavorszky")
                        name.set("Daniel Javorszky")
                        email.set("snapshotj@fastmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/djavorszky/snapshotj.git")
                    developerConnection.set("scm:git:ssh://github.com/djavorszky/snapshotj.git")
                    url.set("https://github.com/djavorszky/snapshotj")
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}

nmcp {
    centralPortal {
        username = providers.gradleProperty("sonatypeUsername")
            .orElse(providers.environmentVariable("SONATYPE_USERNAME"))
        password = providers.gradleProperty("sonatypePassword")
            .orElse(providers.environmentVariable("SONATYPE_PASSWORD"))
        publicationName = "maven"
        // USER_MANAGED = you click "Release" in the portal; switch to AUTOMATIC once confident
        publishingType = "USER_MANAGED"
    }
}
