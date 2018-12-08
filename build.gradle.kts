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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val groupValue : String = "com.nannoq"
val versionValue : String = "1.1.0"
val jvmTargetValue : String = "1.8"

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
        classpath(kotlin("gradle-plugin", "1.3.11"))
    }
}

plugins {
    base

    kotlin("jvm") version "1.3.11" apply false
    id("com.github.ksoichiro.console.reporter") version("0.5.0")
    id("io.codearte.nexus-staging") version("0.11.0")
}

apply {
    plugin("kotlin")
    plugin("jacoco")
    plugin("idea")
}

allprojects {
    group = groupValue
    version = versionValue

    repositories {
        jcenter()
    }
}

subprojects {
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = jvmTargetValue
            incremental = true
            suppressWarnings = true
            freeCompilerArgs = listOf("-Xskip-runtime-version-check")
        }
    }
}

dependencies {
    subprojects.forEach {
        archives(it)
    }
}

nexusStaging {
    packageGroup = groupValue
}