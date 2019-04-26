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
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.OpenOptions
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.streams.Pump
import org.apache.http.HttpHeaders

import java.io.File

/**
 * This class defines an interface for models that store content.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
interface CachedContent {
    var contentLocation: S3Link

    fun storeContent(
        vertx: Vertx,
        urlToContent: String,
        bucketName: String,
        bucketPath: String,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        storeContent(vertx, 0, urlToContent, bucketName, bucketPath, resultHandler)
    }

    fun storeContent(
        vertx: Vertx,
        attempt: Int,
        urlToContent: String,
        bucketName: String,
        bucketPath: String,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        val startTime = System.currentTimeMillis()
        val opts = HttpClientOptions()
                .setConnectTimeout(10000)
                .setMaxRedirects(100)
        val httpClient = vertx.createHttpClient(opts)

        doRequest(vertx, httpClient, attempt, urlToContent, bucketName, bucketPath, Handler {
            when {
                it.failed() -> if (System.currentTimeMillis() < startTime + 60000L * 15L) {
                    when {
                        attempt < 30 -> {
                            logger.warn("Failed on: " + urlToContent + " at attempt " + attempt + " ::: " +
                                    it.cause().message)

                            vertx.setTimer((if (attempt == 0) 1 else attempt) * 1000L) {
                                storeContent(vertx, attempt + 1, urlToContent, bucketName, bucketPath, resultHandler)
                            }
                        }
                        else -> {
                            logger.error("Complete failure on: $urlToContent after $attempt attempts!")

                            httpClient.close()
                            resultHandler.handle(it)
                        }
                    }
                } else {
                    logger.error("Timeout failure (15 mins) on: $urlToContent after $attempt attempts!")

                    httpClient.close()
                    resultHandler.handle(it)
                }
                else -> {
                    when (attempt) {
                        0 -> logger.debug("Succeed on: $urlToContent at attempt $attempt")
                        else -> logger.warn("Back to normal on: $urlToContent at attempt $attempt")
                    }

                    httpClient.close()
                    resultHandler.handle(it)
                }
            }
        })
    }

    fun doRequest(
        vertx: Vertx,
        httpClient: HttpClient,
        attempt: Int,
        urlToContent: String,
        bucketName: String,
        bucketPath: String,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        val dynamoDBMapper = DynamoDBRepository.s3DynamoDbMapper
        contentLocation = DynamoDBRepository.createS3Link(dynamoDBMapper, bucketName, bucketPath)
        val finished = booleanArrayOf(false)

        try {
            val req = httpClient.getAbs(urlToContent) { response ->
                logger.debug("Response to: $urlToContent")

                when {
                    response.statusCode() == 200 -> {
                        response.pause()

                        val asyncFile = arrayOfNulls<AsyncFile>(1)
                        val openOptions = OpenOptions()
                                .setCreate(true)
                                .setWrite(true)

                        vertx.fileSystem().open("" + ModelUtils.returnNewEtag(bucketPath.hashCode().toLong()), openOptions) {
                            when {
                                it.succeeded() -> {
                                    asyncFile[0] = it.result()
                                    val pump = Pump.pump<Buffer>(response, asyncFile[0])
                                    pump.start()
                                    response.resume()
                                }
                                else -> logger.error("Unable to open file for download!", it.cause())
                            }

                            finished[0] = true
                        }

                        response.endHandler {
                            if (asyncFile[0] != null) {
                                asyncFile[0]?.flush {
                                    asyncFile[0]?.close {
                                        vertx.executeBlocking<Boolean>({
                                            var file: File? = null

                                            try {
                                                file = File("" + ModelUtils.returnNewEtag(bucketPath.hashCode().toLong()))
                                                val location = contentLocation
                                                contentLocation.amazonS3Client.putObject(location.bucketName, location.key, file)
                                                file.delete()

                                                logger.debug("Content stored for: $urlToContent, attempt: $attempt")

                                                it.complete(java.lang.Boolean.TRUE)
                                            } catch (e: Exception) {
                                                logger.error("Failure in external storage!", e)

                                                file?.delete()

                                                it.tryFail(e)
                                            }
                                        }, false) {
                                            when {
                                                it.failed() -> {
                                                    logger.error("FAILED Storage for: $urlToContent, attempt: $attempt",
                                                            it.cause())

                                                    resultHandler.handle(Future.failedFuture(it.cause()))
                                                }
                                                else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                                            }
                                        }
                                    }
                                }
                            }

                            finished[0] = true
                        }
                    }
                    else -> {
                        finished[0] = true

                        logger.error("Error reading external file (" + response.statusCode() + ") for: " +
                                urlToContent + ", attempt: " + attempt)

                        resultHandler.handle(Future.failedFuture(response.statusMessage()))
                    }
                }
            }.exceptionHandler { e ->
                finished[0] = true

                resultHandler.handle(Future.failedFuture(e))
            }

            req.putHeader(HttpHeaders.ACCEPT, "application/octet-stream")
            req.isChunked = true
            req.setFollowRedirects(true)
            req.end()

            vertx.setTimer(60000L * 10L) {
                if (!finished[0]) {
                    logger.error("Content has been downloading for 10 mins, killing connection...")

                    req.connection().close()
                }
            }

            logger.debug("Fetching: $urlToContent")
        } catch (e: Exception) {
            logger.fatal("Critical error in content storage!", e)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CachedContent::class.java.simpleName)
    }
}
