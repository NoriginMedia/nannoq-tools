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
    compile(project(":cluster"))
    compile(project(":version"))

    // AWS
    compile("com.amazonaws:aws-java-sdk-core:${extra["awssdk_version"] as String}")
    compile("com.amazonaws:aws-java-sdk-sts:${extra["awssdk_version"] as String}")
    compile("com.amazonaws:aws-java-sdk-dynamodb:${extra["awssdk_version"] as String}")
    compile("com.amazonaws:aws-java-sdk-cloudfront:${extra["awssdk_version"] as String}")
    compile("com.amazonaws:aws-dynamodb-encryption-java:${extra["awssdk_encryption_version"] as String}")

    // Image And File Detection
    compile("com.twelvemonkeys.imageio:imageio-core:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-jpeg:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-tiff:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-psd:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-metadata:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-pnm:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-icns:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-pdf:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-pcx:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-sgi:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-iff:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-tga:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-pict:${extra["imagei_version"] as String}")
    compile("com.twelvemonkeys.imageio:imageio-batik:${extra["imagei_version"] as String}")
    compile("com.drewnoakes:metadata-extractor:${extra["metadata_extractor_version"] as String}")
    compile("org.apache.tika:tika-core:${extra["tika_version"] as String}")

    // S3 Test
    testCompile("io.findify:s3mock_2.12:${extra["s3mock_version"] as String}")
}

publishing {
    publications {
        getByName<MavenPublication>("mavenJava") {
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
