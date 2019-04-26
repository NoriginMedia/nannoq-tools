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
    api(project(":cluster"))
    api(project(":version"))

    // AWS
    implementation(Libs.aws_core)
    implementation(Libs.aws_sts)
    implementation(Libs.aws_dynamodb)
    implementation(Libs.aws_cloudfront)
    implementation(Libs.aws_encryption)

    // Image And File Detection
    implementation(Libs.imageio_core)
    implementation(Libs.imageio_jpeg)
    implementation(Libs.imageio_tiff)
    implementation(Libs.imageio_psd)
    implementation(Libs.imageio_metadata)
    implementation(Libs.imageio_pnm)
    implementation(Libs.imageio_icns)
    implementation(Libs.imageio_pdf)
    implementation(Libs.imageio_pcx)
    implementation(Libs.imageio_sgi)
    implementation(Libs.imageio_iff)
    implementation(Libs.imageio_tga)
    implementation(Libs.imageio_pict)
    implementation(Libs.imageio_batik)
    implementation(Libs.metadata_extractor)
    implementation(Libs.tika_core)

    // S3 Test
    testImplementation(Libs.s3mock)
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
