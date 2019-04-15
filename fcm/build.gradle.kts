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

dependencies {
    // Nannoq Tools
    compile(project(":repository"))
    compile(project(":cluster"))

    // XMPP
    compile("org.igniterealtime.smack:smack:${extra["smack_version"] as String}")

    // Sanitation
    compile("org.jsoup:jsoup:${extra["jsoup_version"] as String}")

    // Test
    testCompile("com.github.kstyrc:embedded-redis:${extra["embedded_redis_version"] as String}")
}

publishing {
    publications {
        getByName<MavenPublication>("mavenJava") {
            pom.withXml {
                asNode().appendNode("name", "Nannoq Tools FCM")
                asNode().appendNode("description", "FCM Layer of Nannoq Tools")
                asNode().appendNode("url", "https://github.com/NoriginMedia/nannoq-fcm")

                val scmNode = asNode().appendNode("scm")

                scmNode.appendNode("url", "https://github.com/NoriginMedia/nannoq-fcm")
                scmNode.appendNode("connection", "scm:git:git://github.com/NoriginMedia/nannoq-fcm")
                scmNode.appendNode("developerConnection", "scm:git:ssh:git@github.com/NoriginMedia/nannoq-fcm")

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