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
 *
 */

package com.nannoq.tools.repository.models

import com.amazonaws.services.dynamodbv2.datamodeling.S3Link
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.OpenOptions
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.streams.Pump
import io.vertx.ext.web.FileUpload
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import java.util.UUID
import java.util.function.Supplier
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadata
import javax.imageio.stream.FileImageOutputStream

/**
 * This class defines an interface for models that have image upload functionality.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
interface ImageUploader {
    fun doUpload(vertx: Vertx, file: File?, s3LinkSupplier: Supplier<S3Link>, fut: Future<Boolean>) {
        vertx.executeBlocking<Boolean>({
            try {
                when {
                    !file!!.exists() -> it.fail(UnknownError("File does not exist!"))
                    else -> {

                        val convertedFile = imageToPng(file)
                        val location = s3LinkSupplier.get()
                        location.amazonS3Client.putObject(
                                location.bucketName, location.key, convertedFile)
                        convertedFile.delete()

                        logger.debug("Content stored for: " + file.path)

                        it.complete(java.lang.Boolean.TRUE)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failure in external storage!", e)

                file?.delete()

                it.tryFail(e)
            }
        }, false) {
            when {
                it.failed() -> {
                    logger.error("FAILED Storage for: " + file!!.path, it.cause())

                    fut.fail(it.cause())
                }
                else -> fut.complete(java.lang.Boolean.TRUE)
            }
        }
    }

    fun doUpload(vertx: Vertx, file: FileUpload, s3LinkSupplier: Supplier<S3Link>, fut: Future<Boolean>) {
        vertx.executeBlocking<Boolean>({
            try {
                val convertedFile = imageToPng(File(file.uploadedFileName()))
                val location = s3LinkSupplier.get()
                location.amazonS3Client.putObject(
                        location.bucketName, location.key, convertedFile)
                convertedFile.delete()

                logger.debug("Content stored for: " + file.uploadedFileName())

                it.complete(java.lang.Boolean.TRUE)
            } catch (e: Exception) {
                logger.error("Failure in external storage!", e)

                it.tryFail(e)
            }
        }, false) {
            when {
                it.failed() -> {
                    logger.error("FAILED Storage for: " + file.uploadedFileName(), it.cause())

                    fut.fail(it.cause())
                }
                else -> fut.complete(java.lang.Boolean.TRUE)
            }
        }
    }

    fun doUpload(vertx: Vertx, url: String, s3LinkSupplier: Supplier<S3Link>, fut: Future<Boolean>) {
        val options = HttpClientOptions()
        options.connectTimeout = 10000
        options.isSsl = true

        val req = vertx.createHttpClient(options).getAbs(url) {
            when {
                it.statusCode() == 200 -> {
                    val uuid = UUID.randomUUID().toString()
                    logger.debug("Response to: $uuid")

                    when {
                        it.statusCode() == 200 -> {
                            it.pause()

                            val asyncFile = arrayOfNulls<AsyncFile>(1)
                            val openOptions = OpenOptions()
                                    .setCreate(true)
                                    .setWrite(true)

                            it.endHandler { end ->
                                logger.debug("Reading image...")

                                when {
                                    asyncFile[0] != null -> asyncFile[0]?.flush {
                                        asyncFile[0]?.close {
                                            doUpload(vertx, File(uuid), s3LinkSupplier, fut)
                                        }
                                    }
                                    else -> {
                                        logger.error("File is missing!")

                                        fut.fail("File is missing!")
                                    }
                                }
                            }

                            vertx.fileSystem().open(uuid, openOptions) { file ->
                                logger.debug("File opened!")

                                when {
                                    file.succeeded() -> {
                                        asyncFile[0] = file.result()
                                        val pump = Pump.pump<Buffer>(it, asyncFile[0])
                                        pump.start()
                                    }
                                    else -> logger.error("Unable to open file for download!", file.cause())
                                }

                                logger.debug("Read response!")

                                it.resume()
                            }
                        }
                        else -> {
                            logger.error("Error reading external file (" + it.statusCode() + ") for: " + uuid)

                            fut.fail(it.statusMessage())
                        }
                    }
                }
                else -> {
                    logger.error("Error reading external file...")

                    fut.fail(UnknownError())
                }
            }
        }

        req.setFollowRedirects(true)
        req.exceptionHandler { fut.fail(it) }
        req.end()
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun imageToPng(file: File?): File {
        file!!.setReadable(true)

        try {
            val iis = ImageIO.createImageInputStream(file)
            val readers = ImageIO.getImageReaders(iis)
            var imageReader: ImageReader
            var metadata: IIOMetadata? = null
            var image: BufferedImage? = null

            while (readers.hasNext()) {
                try {
                    imageReader = readers.next()
                    imageReader.setInput(iis, true)
                    metadata = imageReader.getImageMetadata(0)
                    image = imageReader.read(0)

                    break
                } catch (e: Exception) {
                    logger.error("Error parsing image!", e)
                }
            }

            if (image == null) throw IOException()

            image = convertImageToRGB(image, BufferedImage.TYPE_INT_RGB)
            val jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next()
            val jpgWriteParam = jpgWriter.defaultWriteParam
            jpgWriteParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
            jpgWriteParam.compressionQuality = 1.0f

            val outputStream = FileImageOutputStream(file)
            jpgWriter.output = outputStream
            val outputImage = IIOImage(image, null, metadata)
            jpgWriter.write(null, outputImage, jpgWriteParam)
            jpgWriter.dispose()

            return file
        } catch (e: IOException) {
            logger.error("Error converting image, running backup!", e)

            try {
                var image = ImageIO.read(file)
                image = convertImageToRGB(image, BufferedImage.TYPE_INT_RGB)
                ImageIO.write(image, "jpg", file)

                return file
            } catch (ee: IOException) {
                logger.error("Error converting image!", ee)

                throw ee
            }
        }
    }

    fun convertImageToRGB(src: BufferedImage, typeIntRgb: Int): BufferedImage {
        val img = BufferedImage(src.width, src.height, typeIntRgb)
        val g2d = img.createGraphics()
        g2d.drawImage(src, 0, 0, null)
        g2d.dispose()

        return img
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ImageUploader::class.java.simpleName)
    }
}
