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
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val buildUtils = BuildUtils()
val branchName = buildUtils.gitBranch()
val groupValue: String = "com.nannoq"
val versionValue: String = buildUtils.calculateVersion(properties["version"] as String, branchName)

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
        classpath(kotlin("gradle-plugin", Versions.kotlin_version))
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${Versions.dokka_version}")
        classpath("io.github.robwin:jgitflow-gradle-plugin:${Versions.gradle_gitflow_version}")
    }
}

plugins {
    base

    id("java")
    id("io.codearte.nexus-staging") version(Versions.nexus_staging_version)
    id("org.jetbrains.dokka") version(Versions.dokka_version)
    id("idea")
    id("com.adarshr.test-logger") version(Versions.gradle_test_logger_version)
    id("com.github.ben-manes.versions") version(Versions.gradle_versions_version)
    id("org.jlleitschuh.gradle.ktlint") version(Versions.gradle_ktlint_version)
    kotlin("jvm") version(Versions.kotlin_version)
    kotlin("kapt") version(Versions.kotlin_version)

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
        plugin("org.jlleitschuh.gradle.ktlint")
        plugin("com.github.ben-manes.versions")
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
        implementation(kotlin("stdlib", Versions.kotlin_version))
        implementation(kotlin("stdlib-jdk8", Versions.kotlin_version))
        implementation("org.jetbrains.kotlin:kotlin-reflect")

        // Vert.x
        implementation(Libs.vertx_core)
        implementation(Libs.vertx_web)
        implementation(Libs.vertx_hazelcast)
        implementation(Libs.vertx_codegen)
        implementation(Libs.vertx_lang_js)
        implementation(Libs.vertx_lang_ruby)
        implementation(Libs.vertx_lang_kotlin)
        implementation(Libs.vertx_service_proxy)
        implementation(Libs.vertx_sockjs_service_proxy)
        implementation(Libs.vertx_circuit_breaker)
        implementation(Libs.vertx_redis_client)
        implementation(Libs.vertx_mail_client)
        implementation(Libs.vertx_lang_kotlin_coroutines)

        // Kapt
        kapt("${Libs.vertx_codegen}:processor")
        kapt("${Libs.vertx_service_proxy}:processor")

        // Log4j2
        implementation(group = "org.apache.logging.log4j", name = "log4j-api", version = Versions.log4j_version)
        implementation(group = "org.apache.logging.log4j", name = "log4j-core", version = Versions.log4j_version)
        implementation(group = "com.lmax", name = "disruptor", version = Versions.com_lmax_version)

        // Jackson
        implementation(Libs.jackson_annotations)

        // Commons
        implementation(Libs.commons_lang3)
        implementation(Libs.commons_io)
        implementation(Libs.commons_validator)
        implementation(Libs.findbugs_annotations)
        implementation(Libs.guava_jdk5)

        // Test
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
        testImplementation(Libs.vertx_config)
        testImplementation(Libs.vertx_junit5)
        testImplementation(Libs.assertj_core)
        testImplementation(Libs.rest_assured)
        testImplementation(Libs.rest_assured_json_path)
        testImplementation(Libs.rest_assured_json_schema_validator)
        testImplementation(Libs.commons_io)
        testImplementation(Libs.commons_validator)
        testImplementation(Libs.findbugs_annotations)
        testImplementation(Libs.guava_jdk5)
        testRuntime(Libs.junit_jupiter_engine)

        // Kapt Test
        kaptTest("${Libs.vertx_codegen}:processor")
        kaptTest("${Libs.vertx_service_proxy}:processor")
    }

    testlogger {
        theme = ThemeType.STANDARD_PARALLEL
    }

    kapt {
        correctErrorTypes = true
        useBuildCache = true
        includeCompileClasspath = false

        javacOptions {
            option("-Xdoclint:none")
            option("-Xlint:none")
            option("-nowarn")
        }
    }

    val dokka by tasks.getting(DokkaTask::class) {
        logging.level = LogLevel.QUIET
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
            jvmTarget = Versions.jvmTargetValue
            suppressWarnings = true
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs = listOf("-Xdoclint:none", "-Xlint:none", "-nowarn")
    }

    tasks {
        "test"(Test::class) {
            @Suppress("UnstableApiUsage")
            useJUnitPlatform()
            systemProperties = mapOf(Pair("vertx.logger-delegate-factory-class-name", Versions.logger_factory_version))
        }

        "publish" {
            dependsOn(listOf("signSourcesJar", "signPackageJavadoc"))
            mustRunAfter(listOf("signSourcesJar", "signPackageJavadoc"))

            doLast {
                println("Published ${project.version as String}")
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

            if (branchName == "develop" && project.hasProperty("central")) {
                maven(url = "https://oss.sonatype.org/content/repositories/snapshots/") {
                    credentials {
                        username = System.getenv("OSSRH_USER")
                        password = System.getenv("OSSRH_PASS")
                    }
                }
            } else if (branchName == "master" && project.hasProperty("central")) {
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
}

configure(subprojects.filter { it.name == "repository" || it.name == "web" }) {
    val dynamodb by configurations.creating

    dependencies {
        // Cache
        implementation(Libs.jcache)

        // Redis
        testImplementation(Libs.embedded_redis)

        // DynamoDB Test
        testImplementation(Libs.dynamodb_local)
        testImplementation(Libs.sqlite4)
        testImplementation(Libs.sqlite4_win32_x86)
        testImplementation(Libs.sqlite4_win32_x64)
        testImplementation(Libs.sqlite4_osx)
        testImplementation(Libs.sqlite4_linux_i386)
        testImplementation(Libs.sqlite4_linux_amd64)
        testImplementation(Libs.dynamodb_local)
        dynamodb(fileTree("lib") { include(listOf("*.dylib", "*.so", "*.dll")) })
        dynamodb(Libs.dynamodb_local)
    }

    tasks {
        val dynamoDbDeps by registering(Copy::class) {
            dependsOn(dynamodb)

            from(dynamodb)
            into("$projectDir/build/tmp/dynamodb-libs")
        }

        val compileTestKotlin by existing(KotlinCompile::class) {
            dependsOn(dynamoDbDeps)
        }

        "test"(Test::class) {
            systemProperties = mapOf(
                    Pair("vertx.logger-delegate-factory-class-name", Versions.logger_factory_version),
                    Pair("java.library.path", file("$projectDir/build/tmp/dynamodb-libs").absolutePath))
        }
    }
}
