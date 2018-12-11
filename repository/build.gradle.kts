/*
 * MIT License
 *
 * Copyright (c) 2017 Anders Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")

import com.wiredforcode.gradle.spawn.KillProcessTask
import com.wiredforcode.gradle.spawn.SpawnProcessTask
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.tasks.JavaExec
import org.gradle.internal.impldep.org.bouncycastle.pqc.crypto.gmss.GMSSKeyPairGenerator
import org.gradle.kotlin.dsl.*
import org.gradle.script.lang.kotlin.*
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KaptAnnotationProcessorOptions
import org.jetbrains.kotlin.gradle.plugin.KaptJavacOptionsDelegate
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import java.net.ServerSocket

val groupId = project.group!!
val projectName = project.name!!
val projectVersion = project.version!!
val nameOfArchive = "$projectName-$projectVersion.jar"

val vertxPort = findFreePort()

val kotlin_version: String by project
val vertx_version: String by project
val hazelcast_version: String by project
val log4j_version: String by project
val com_lmax_version: String by project
val junit_version: String by project
val rest_assured_version: String by project
val logger_factory_version: String by project
val nannoq_tools_version: String by project
val awssdk_version: String by project
val imagei_version: String by project
val sqlLiteVersion = "1.0.392"

buildscript {
    var kotlin_version: String by extra
    var dokka_version: String by extra
    kotlin_version = "1.3.11"
    dokka_version = "0.9.16"

    repositories {
        mavenCentral()
        jcenter()
        maven(url = "http://dl.bintray.com/vermeulen-mp/gradle-plugins")
    }

    dependencies {
        classpath("gradle.plugin.com.palantir.gradle.docker:gradle-docker:0.20.1")
        classpath("com.github.jengelman.gradle.plugins:shadow:4.0.3")
        classpath("com.wiredforcode:gradle-spawn-plugin:0.8.0")
        classpath(kotlin("gradle-plugin", kotlin_version))
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:$dokka_version")
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
    maven(url = "http://dynamodb-local.s3-website-us-west-2.amazonaws.com/release")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}

plugins {
    id("java")
    id("kotlin")
    id("application")
    id("com.wiredforcode.spawn") version("0.8.0")

    @Suppress("RemoveRedundantBackticks")
    `maven-publish`
    signing
}

apply {
    plugin("java")
    plugin("kotlin")
    plugin("org.jetbrains.dokka")
    plugin("kotlin-kapt")
    plugin("idea")
}

dependencies {
    // Kotlin
    compile(kotlin("stdlib", kotlin_version))
    compile(kotlin("stdlib-jdk8", kotlin_version))
    compile("org.jetbrains.kotlin:kotlin-reflect")

    // Vert.x
    compile("io.vertx:vertx-core:$vertx_version")
    compile("io.vertx:vertx-web:$vertx_version")
    compile("com.hazelcast:hazelcast-all:$hazelcast_version")
    compile("io.vertx:vertx-hazelcast:$vertx_version")
    compile("io.vertx:vertx-codegen:$vertx_version")
    compile("io.vertx:vertx-lang-js:$vertx_version")
    compile("io.vertx:vertx-lang-ruby:$vertx_version")
    compile("io.vertx:vertx-lang-kotlin:$vertx_version")
    compile("io.vertx:vertx-service-proxy:$vertx_version")
    compile("io.vertx:vertx-sockjs-service-proxy:$vertx_version")
    compile("io.vertx:vertx-circuit-breaker:$vertx_version")
    compile("io.vertx:vertx-redis-client:$vertx_version")
    compile("io.vertx:vertx-lang-kotlin-coroutines:$vertx_version")

    // Nannoq Tools
    compile(project(":cluster"))

    // Kapt
    kapt("io.vertx:vertx-codegen:$vertx_version:processor")
    kapt("io.vertx:vertx-service-proxy:$vertx_version:processor")
    kaptTest("io.vertx:vertx-codegen:$vertx_version:processor")
    kaptTest("io.vertx:vertx-service-proxy:$vertx_version:processor")

    // Log4j2
    compile(group = "org.apache.logging.log4j", name = "log4j-api", version = log4j_version)
    compile(group = "org.apache.logging.log4j", name = "log4j-core", version = log4j_version)
    compile(group = "com.lmax", name = "disruptor", version = com_lmax_version)

    // Cache
    compile("javax.cache:cache-api:1.1.0")

    // Commons
    compile("org.apache.commons:commons-lang3:3.4")
    compile("com.google.code.findbugs:annotations:3.0.1")
    compile("com.google.guava:guava-jdk5:17.0")

    // AWS

    compile("com.amazonaws:aws-java-sdk-core:$awssdk_version")
    compile("com.amazonaws:aws-java-sdk-sts:$awssdk_version")
    compile("com.amazonaws:aws-java-sdk-dynamodb:$awssdk_version")
    compile("com.amazonaws:aws-java-sdk-cloudfront:$awssdk_version")
    compile("com.amazonaws:aws-dynamodb-encryption-java:1.11.0")

    // Image And File Detection

    compile("com.twelvemonkeys.imageio:imageio-core:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-jpeg:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-tiff:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-psd:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-metadata:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-pnm:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-icns:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-pdf:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-pcx:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-sgi:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-iff:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-tga:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-pict:$imagei_version")
    compile("com.twelvemonkeys.imageio:imageio-batik:$imagei_version")
    compile("com.drewnoakes:metadata-extractor:2.7.2")
    compile("org.apache.tika:tika-core:1.14")

    // Test
    testCompile("junit:junit:$junit_version")
    testCompile("org.jetbrains.kotlin:kotlin-test")
    testCompile("org.jetbrains.kotlin:kotlin-test-junit")
    testCompile("io.vertx:vertx-config:$vertx_version")
    testCompile("io.vertx:vertx-unit:$vertx_version")
    testCompile("io.rest-assured:rest-assured:$rest_assured_version")
    testCompile("com.github.kstyrc:embedded-redis:0.6")

    // DynamoDB Test
    testCompile("com.amazonaws:DynamoDBLocal:[1.11.119,2.0]")
    testCompile("com.almworks.sqlite4java:sqlite4java:$sqlLiteVersion")
    testCompile("com.almworks.sqlite4java:sqlite4java-win32-x86:$sqlLiteVersion")
    testCompile("com.almworks.sqlite4java:sqlite4java-win32-x64:$sqlLiteVersion")
    testCompile("com.almworks.sqlite4java:libsqlite4java-osx:$sqlLiteVersion")
    testCompile("com.almworks.sqlite4java:libsqlite4java-linux-i386:$sqlLiteVersion")
    testCompile("com.almworks.sqlite4java:libsqlite4java-linux-amd64:$sqlLiteVersion")

    // S3 Test
    testCompile("io.findify:s3mock_2.12:0.2.4")
}

configure<KotlinProjectExtension> {
    experimental.coroutines = Coroutines.ENABLE
}

val dokka by tasks.getting(DokkaTask::class) {
    outputFormat = "html"
    outputDirectory = "$buildDir/docs"
    jdkVersion = 8
}

val packageJavadoc by tasks.creating(Jar::class) {
    dependsOn("dokka")
    classifier = "javadoc"
    from(dokka.outputDirectory)
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

tasks {
    "copyDynamoDBLibs"(Copy::class) {
        delete("$projectDir/build/dynamodb-libs")

        configurations.getByName("testCompile").resolvedConfiguration.resolvedArtifacts.forEach({
            if (isSqlite(it.id.componentIdentifier.displayName)) {
                copy {
                    from(it.file)
                    into(file("$buildDir/sqlite/${it.name}"))
                }

                copy {
                    from("$buildDir/sqlite/${it.name}") {
                        include(listOf("*.so", "*.dll", "*.dylib"))
                    }

                    into("$projectDir/build/dynamodb-libs")
                }
            }
        })
    }

    "test"(Test::class) {
        dependsOn("processResources")
        mustRunAfter("processResources")

        maxParallelForks = 4
        systemProperties = mapOf(
                Pair("vertx.logger-delegate-factory-class-name", logger_factory_version),
                Pair("java.library.path", file("$projectDir/build/dynamodb-libs").absolutePath))
    }

    "verify" {
        dependsOn(listOf("test"))
    }

    "publish" {
        dependsOn(listOf("verify", "signSourcesJar", "signPackageJavadoc"))
        mustRunAfter(listOf("verify", "signSourcesJar", "signPackageJavadoc"))
    }

    "install" {
        dependsOn(listOf("verify", "publish"))
        mustRunAfter("clean")

        doLast({
            println("$nameOfArchive installed!")
        })
    }
}

signing {
    useGpgCmd()
    sign(sourcesJar)
    sign(packageJavadoc)

    sign(publishing.publications)
}

publishing {
    repositories {
        mavenLocal()

        if (projectVersion.toString().contains("-SNAPSHOT") && project.hasProperty("central")) {
            maven(url = "https://oss.sonatype.org/content/repositories/snapshots/") {
                credentials {
                    username = System.getenv("OSSRH_USER")
                    password = System.getenv("OSSRH_PASS")
                }
            }
        } else if (project.hasProperty("central")) {
            maven(url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
                credentials {
                    username = System.getenv("OSSRH_USER")
                    password = System.getenv("OSSRH_PASS")
                }
            }
        }
    }

    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])

            artifact(sourcesJar) {
                classifier = "sources"
            }

            artifact(packageJavadoc) {
                classifier = "javadoc"
            }

            pom.withXml {
                asNode().appendNode("name", "Nannoq Tools Repository")
                asNode().appendNode("description", "Repository Layer of Nannoq Tools")
                asNode().appendNode("url", "https://github.com/NoriginMedia/nannoq-repository")

                val scmNode = asNode().appendNode("scm")

                scmNode.appendNode("url", "https://github.com/NoriginMedia/nannoq-repository")
                scmNode.appendNode("connection", "scm:git:git://github.com/NoriginMedia/nannoq-repository")
                scmNode.appendNode("developerConnection", "scm:git:ssh:git@github.com/NoriginMedia/nannoq-repository")

                val licenses = asNode().appendNode("licenses")
                val license = licenses.appendNode("license")
                license.appendNode("name", "MIT License")
                license.appendNode("url", "http://www.opensource.org/licenses/mit-license.php")
                license.appendNode("distribution", "repo")

                val developers = asNode().appendNode("developers")
                val developer = developers.appendNode("developer")
                developer.appendNode("id", "mikand13")
                developer.appendNode("name", "Anders Mikkelsen")
                developer.appendNode("email", "mikkelsen.anders@gmail.com")
            }
        }
    }
}

fun isSqlite(componentIdentifier: String) : Boolean {
    return componentIdentifier.contains("sqlite4java")
}

fun findFreePort() = ServerSocket(0).use {
    it.localPort
}

fun writeCustomConfToConf(vertxPort: Int): String {
    val config = JsonSlurper().parseText(File("$projectDir/src/test/resources/app-conf.json").readText())
    val outPutConfig = file("$buildDir/tmp/app-conf-test.json")
    outPutConfig.createNewFile()

    val builder = JsonBuilder(config)
    val openJson = builder.toPrettyString().removeSuffix("}")
    val newConfig = JsonBuilder(JsonSlurper().parseText("$openJson, \"gateway\":{\"bridgePort\":$vertxPort}}")).toPrettyString()

    outPutConfig.bufferedWriter().use { out ->
        out.write(newConfig)
        out.flush()
    }

    return outPutConfig.absolutePath
}