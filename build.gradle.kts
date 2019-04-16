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

import com.adarshr.gradle.testlogger.TestLoggerPlugin
import com.adarshr.gradle.testlogger.theme.ThemeType
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.ServerSocket

val groupValue : String = "com.nannoq"
val versionValue : String by project
val jvmTargetValue : String by project
val kotlin_version: String by project
val dokka_version: String by project
val vertx_version: String by project
val vertx_redis_version: String by project
val log4j_version: String by project
val com_lmax_version: String by project
val junit_version: String by project
val assertj_version: String by project
val rest_assured_version: String by project
val logger_factory_version: String by project
val jackson_annotations_version: String by project
val commons_validator_version: String by project
val google_findbugs_version: String by project
val jsoup_version: String by project
val embedded_redis_version: String by project
val jjwt_version: String by project
val google_api_client_version: String by project
val facebook4j_version: String by project
val jInstagram_version: String by project
val nexus_staging_version: String by project
val google_guava_jdk5_version: String by project
val smack_version: String by project
val s3mock_version: String by project
val sqlLite_version: String by project
val awssdk_encryption_version: String by project
val metadata_extractor_version: String by project
val tika_version: String by project
val dynamodb_local_version: String by project
val apache_commons_io_version: String by project
val jcache_version: String by project
val gradle_test_logger_version: String by project

repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
}

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", extra["kotlin_version"] as String))
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${extra["dokka_version"] as String}")
    }
}

plugins {
    base

    id("java")
    id("io.codearte.nexus-staging") version(extra["nexus_staging_version"] as String)
    id("org.jetbrains.dokka") version(extra["dokka_version"] as String)
    id("idea")
    id("com.adarshr.test-logger") version(extra["gradle_test_logger_version"] as String)
    kotlin("jvm") version(extra["kotlin_version"] as String)
    kotlin("kapt") version(extra["kotlin_version"] as String)

    @Suppress("RemoveRedundantBackticks")
    `maven-publish`
    signing
}

dependencies {
    subprojects.forEach {
        archives(it)
    }
}

nexusStaging {
    packageGroup = groupValue
    username = System.getenv("OSSRH_USER")
    password = System.getenv("OSSRH_PASS")
}

allprojects {
    group = groupValue
    version = versionValue

    extra {
        kotlin_version
        dokka_version
        vertx_version
        vertx_redis_version
        log4j_version
        com_lmax_version
        junit_version
        assertj_version
        rest_assured_version
        logger_factory_version
        jackson_annotations_version
        commons_validator_version
        google_findbugs_version
        jsoup_version
        embedded_redis_version
        jjwt_version
        google_api_client_version
        facebook4j_version
        jInstagram_version
        google_guava_jdk5_version
        smack_version
        s3mock_version
        sqlLite_version
        awssdk_encryption_version
        metadata_extractor_version
        tika_version
        dynamodb_local_version
        apache_commons_io_version
        jcache_version
        gradle_test_logger_version
    }
}

subprojects {
    apply {
        plugin<BasePlugin>()
        plugin<DokkaPlugin>()
        plugin<KotlinPluginWrapper>()
        plugin<SigningPlugin>()
        plugin<PublishingPlugin>()
        plugin<IdeaPlugin>()
        plugin<MavenPublishPlugin>()
        plugin<TestLoggerPlugin>()
        plugin("java")
        plugin("org.jetbrains.kotlin.kapt")
    }

    repositories {
        mavenCentral()
        mavenLocal()
        jcenter()
        maven(url = "http://dynamodb-local.s3-website-us-west-2.amazonaws.com/release")
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
    }

    dependencies {
        // Kotlin
        implementation(kotlin("stdlib", extra["kotlin_version"] as String))
        implementation(kotlin("stdlib-jdk8", extra["kotlin_version"] as String))
        compile("org.jetbrains.kotlin:kotlin-reflect")

        // Vert.x
        compile("io.vertx:vertx-core:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-web:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-hazelcast:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-codegen:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-lang-js:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-lang-ruby:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-lang-kotlin:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-service-proxy:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-sockjs-service-proxy:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-circuit-breaker:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-redis-client:${extra["vertx_redis_version"] as String}")
        compile("io.vertx:vertx-mail-client:${extra["vertx_version"] as String}")
        compile("io.vertx:vertx-lang-kotlin-coroutines:${extra["vertx_version"] as String}")

        // Kapt
        kapt("io.vertx:vertx-codegen:${extra["vertx_version"] as String}:processor")
        kapt("io.vertx:vertx-service-proxy:${extra["vertx_version"] as String}:processor")

        // Log4j2
        compile(group = "org.apache.logging.log4j", name = "log4j-api", version = extra["log4j_version"] as String)
        compile(group = "org.apache.logging.log4j", name = "log4j-core", version = extra["log4j_version"] as String)
        compile(group = "com.lmax", name = "disruptor", version = extra["com_lmax_version"] as String)

        // Jackson
        compile("com.fasterxml.jackson.core:jackson-annotations:${extra["jackson_annotations_version"] as String}")

        // Commons
        compile("org.apache.commons:commons-lang3:${extra["apache_commons_lang_version"] as String}")
        compile("commons-io:commons-io:${extra["apache_commons_io_version"] as String}")
        compile("commons-validator:commons-validator:${extra["commons_validator_version"] as String}")
        compile("com.google.code.findbugs:annotations:${extra["google_findbugs_version"] as String}")
        compile("com.google.guava:guava-jdk5:${extra["google_guava_jdk5_version"] as String}")

        // Test
        testCompile("org.jetbrains.kotlin:kotlin-test")
        testCompile("org.jetbrains.kotlin:kotlin-test-junit")
        testCompile("io.vertx:vertx-config:${extra["vertx_version"] as String}")
        testCompile("io.vertx:vertx-junit5:${extra["vertx_version"] as String}")
        testCompile("org.assertj:assertj-core:${extra["assertj_version"] as String}")
        testCompile("io.rest-assured:rest-assured:${extra["rest_assured_version"] as String}")
        testCompile("io.rest-assured:json-path:${extra["rest_assured_version"] as String}")
        testCompile("io.rest-assured:json-schema-validator:${extra["rest_assured_version"] as String}")
        testRuntime("org.junit.jupiter:junit-jupiter-engine:${extra["junit_version"] as String}")

        // Kapt Test
        kaptTest("io.vertx:vertx-codegen:${extra["vertx_version"] as String}:processor")
        kaptTest("io.vertx:vertx-service-proxy:${extra["vertx_version"] as String}:processor")
    }

    testlogger {
        theme = ThemeType.STANDARD_PARALLEL
    }

    kapt {
        correctErrorTypes = true
        useBuildCache = true
        includeCompileClasspath = false
    }

    val dokka by tasks.getting(DokkaTask::class) {
        outputFormat = "html"
        outputDirectory = "$buildDir/docs"
        jdkVersion = 8
        reportUndocumented = false
    }

    val packageJavadoc by tasks.creating(Jar::class) {
        dependsOn("dokka")
        archiveClassifier.set("javadoc")
        from(dokka.outputDirectory)
    }

    val sourcesJar by tasks.creating(Jar::class) {
        archiveClassifier.set("sources")
        from(kotlin.sourceSets["main"].kotlin)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = project.extra["jvmTargetValue"] as String
            suppressWarnings = true
        }
    }

    tasks {
        "test"(Test::class) {
            useJUnitPlatform()
            systemProperties = mapOf(Pair(
                    "vertx.logger-delegate-factory-class-name", project.extra["logger_factory_version"] as String))
        }

        "publish" {
            dependsOn(listOf("signSourcesJar", "signPackageJavadoc"))
            mustRunAfter(listOf("signSourcesJar", "signPackageJavadoc"))

            doLast {
                println("Published ${project.extra["versionValue"] as String}")
            }
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

            if ((project.extra["versionValue"] as String).contains("-SNAPSHOT") &&
                    project.hasProperty("central")) {
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

        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                artifact(sourcesJar) {
                    classifier = "sources"
                }

                artifact(packageJavadoc) {
                    classifier = "javadoc"
                }
            }
        }
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
}

configure(subprojects.filter { it.name == "repository" || it.name == "web" }) {
    val dynamodb by configurations.creating

    dependencies {
        // Cache
        compile("javax.cache:cache-api:${extra["jcache_version"] as String}")
        
        // Redis
        testCompile("com.github.kstyrc:embedded-redis:${extra["embedded_redis_version"] as String}")

        // DynamoDB Test
        testCompile("com.amazonaws:DynamoDBLocal:${extra["dynamodb_local_version"] as String}")
        testCompile("com.almworks.sqlite4java:sqlite4java:${extra["sqlLite_version"] as String}")
        testCompile("com.almworks.sqlite4java:sqlite4java-win32-x86:${extra["sqlLite_version"] as String}")
        testCompile("com.almworks.sqlite4java:sqlite4java-win32-x64:${extra["sqlLite_version"] as String}")
        testCompile("com.almworks.sqlite4java:libsqlite4java-osx:${extra["sqlLite_version"] as String}")
        testCompile("com.almworks.sqlite4java:libsqlite4java-linux-i386:${extra["sqlLite_version"] as String}")
        testCompile("com.almworks.sqlite4java:libsqlite4java-linux-amd64:${extra["sqlLite_version"] as String}")
        testImplementation("com.amazonaws:DynamoDBLocal:${extra["dynamodb_local_version"] as String}")
        dynamodb(fileTree("lib") { include(listOf("*.dylib", "*.so", "*.dll")) })
        dynamodb("com.amazonaws:DynamoDBLocal:${extra["dynamodb_local_version"] as String}")
    }

    tasks {
        val dynamoDbDeps by registering(Copy::class) {
            dependsOn(dynamodb)

            from(dynamodb)
            into("$projectDir/build/tmp/dynamodb-libs")
        }

        "test"(Test::class) {
            dependsOn(dynamoDbDeps)

            systemProperties = mapOf(
                    Pair("vertx.logger-delegate-factory-class-name", project.extra["logger_factory_version"] as String),
                    Pair("java.library.path", file("$projectDir/build/tmp/dynamodb-libs").absolutePath))
        }
    }
}
