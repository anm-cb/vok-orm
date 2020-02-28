import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

val slf4jVersion = "1.7.30"

val local = Properties()
val localProperties: java.io.File = rootProject.file("local.properties")
if (localProperties.exists()) {
    localProperties.inputStream().use { local.load(it) }
}

plugins {
    kotlin("jvm") version "1.3.61"
    id("com.jfrog.bintray") version "1.8.3"
    `maven-publish`
    id("org.jetbrains.dokka") version "0.9.17"
}

defaultTasks("clean", "build")

group = "com.github.mvysny.vokorm"
version = "1.3-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("com.github.mvysny.vokdataloader:vok-dataloader:0.6")
    compile("com.gitlab.mvysny.jdbiorm:jdbi-orm:0.5")

    // logging
    compile("org.slf4j:slf4j-api:$slf4jVersion")

    // validation support
    testCompile("org.hibernate.validator:hibernate-validator:6.1.0.Final")
    // EL is required: http://hibernate.org/validator/documentation/getting-started/
    testCompile("org.glassfish:javax.el:3.0.1-b08")

    // tests
    testCompile("com.github.mvysny.dynatest:dynatest-engine:0.16")
    testCompile("com.google.code.gson:gson:2.8.5")
    testCompile("org.slf4j:slf4j-simple:$slf4jVersion")
    testCompile("com.h2database:h2:1.4.200")
    testCompile("com.zaxxer:HikariCP:3.4.2")

    testCompile("org.apache.lucene:lucene-analyzers-common:5.5.5") // for H2 Full-Text search
    testCompile("org.apache.lucene:lucene-queryparser:5.5.5") // for H2 Full-Text search

    testCompile("org.postgresql:postgresql:42.2.5")
    testCompile("org.zeroturnaround:zt-exec:1.10")
    testCompile("mysql:mysql-connector-java:5.1.48")
    testCompile("org.mariadb.jdbc:mariadb-java-client:2.4.0")

    testCompile("org.testcontainers:testcontainers:1.12.3")
    testCompile("org.testcontainers:postgresql:1.12.3")
    testCompile("org.testcontainers:mysql:1.12.3")
    testCompile("org.testcontainers:mariadb:1.12.3")

    // IDEA language injections
    testCompile("com.intellij:annotations:12.0")
}

val sourceJar = task("sourceJar", Jar::class) {
    dependsOn(tasks["classes"])
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar = task("javadocJar", Jar::class) {
    val javadoc = tasks["dokka"] as DokkaTask
    javadoc.outputFormat = "javadoc"
    javadoc.outputDirectory = "$buildDir/javadoc"
    dependsOn(javadoc)
    archiveClassifier.set("javadoc")
    from(javadoc.outputDirectory)
}

publishing {
    publications {
        create("mavenJava", MavenPublication::class.java).apply {
            groupId = project.group.toString()
            this.artifactId = "vok-orm"
            version = project.version.toString()
            pom {
                description.set("A very simple persistence framework, built on top of Sql2o")
                name.set("VoK-ORM")
                url.set("https://github.com/mvysny/vok-orm")
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("mavi")
                        name.set("Martin Vysny")
                        email.set("martin@vysny.me")
                    }
                }
                scm {
                    url.set("https://github.com/mvysny/vok-orm")
                }
            }
            from(components["java"])
            artifact(sourceJar)
            artifact(javadocJar)
        }
    }
}

bintray {
    user = local.getProperty("bintray.user")
    key = local.getProperty("bintray.key")
    pkg(closureOf<BintrayExtension.PackageConfig> {
        repo = "github"
        name = "com.github.mvysny.vokorm"
        setLicenses("MIT")
        vcsUrl = "https://github.com/mvysny/vok-orm"
        publish = true
        setPublications("mavenJava")
        version(closureOf<BintrayExtension.VersionConfig> {
            this.name = project.version.toString()
            released = Date().toString()
        })
    })
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        // to see the exceptions of failed tests in Travis-CI console.
        exceptionFormat = TestExceptionFormat.FULL
    }
}
