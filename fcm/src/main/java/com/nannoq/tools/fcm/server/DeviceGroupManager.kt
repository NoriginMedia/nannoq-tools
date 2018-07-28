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

package com.nannoq.tools.fcm.server

import com.google.common.net.MediaType
import com.nannoq.tools.cluster.apis.APIManager
import com.nannoq.tools.fcm.server.FcmServer.Companion.GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE
import com.nannoq.tools.fcm.server.data.FcmDevice
import com.nannoq.tools.repository.repository.redis.RedisUtils
import io.vertx.core.AsyncResult
import io.vertx.core.Future.failedFuture
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.RedisClient
import java.util.function.Consumer

/**
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class DeviceGroupManager internal constructor(private val server: FcmServer, private val sender: MessageSender, private val redisClient: RedisClient,
                                              private val GCM_SENDER_ID: String, private val GCM_API_KEY: String) {
    private val logger = LoggerFactory.getLogger(DeviceGroupManager::class.java.simpleName)

    fun addDeviceToDeviceGroupForUser(device: FcmDevice, appPackageName: String,
                                      channelKeyName: String, fcmId: String) {
        addDeviceToDeviceGroup(device, channelKeyName, Handler {
            when {
                it.failed() -> logger.error("Could not add device to device group...")
                else -> {
                    logger.info("User updated, device added...")

                    sender.replyWithSuccessfullDeviceRegistration(appPackageName, fcmId)

                    logger.info("Sent message of correct device registration...")
                }
            }
        })
    }

    private fun addDeviceToDeviceGroup(device: FcmDevice, channelKeyName: String, resultHandler: Handler<AsyncResult<Boolean>>) {
        RedisUtils.performJedisWithRetry(redisClient) { redis ->
            redis.hgetall(channelKeyName) { hGetAllResult ->
                when {
                    hGetAllResult.failed() -> {
                        logger.error("Unable to get Channelmap...")

                        resultHandler.handle(failedFuture(hGetAllResult.cause()))
                    }
                    else -> {
                        val jsonMap = Json.decodeValue(hGetAllResult.result().encode(), Map::class.java)
                        val channelMap = mutableMapOf<String, String>()
                        @Suppress("UNCHECKED_CAST")
                        if (jsonMap != null) channelMap.putAll(jsonMap as Map<out String, String>)
                        val notificationKeyName = device.notificationKeyName
                        val key = channelMap[notificationKeyName]

                        when (key) {
                            null -> {
                                val creationJson = Json.encode(MessageSender.createDeviceGroupCreationJson(
                                        notificationKeyName, device.fcmId))
                                val finalChannelMap = channelMap

                                logger.info("Creation Json is: " + Json.encodePrettily(creationJson))

                                val url = GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE

                                APIManager.performRequestWithCircuitBreaker(resultHandler, Handler {
                                    val opt = HttpClientOptions()
                                            .setSsl(true)

                                    logger.info("Creation for: $url")

                                    val req = server.vertx.createHttpClient(opt).postAbs(url) { clientResponse ->
                                        val status = clientResponse.statusCode()
                                        logger.info("Create Group response: " + (status == 200))

                                        when (status) {
                                            200 -> clientResponse.bodyHandler { bodyBuffer ->
                                                logger.info("Device Group Created...")

                                                val body = bodyBuffer.toJsonObject()

                                                logger.info("Response from GCM: " + Json.encodePrettily(body))

                                                val notificationKey = body.getString("notification_key")

                                                it.complete(java.lang.Boolean.TRUE)

                                                doDeviceGroupResult(
                                                        notificationKey, finalChannelMap,
                                                        device, notificationKeyName, channelKeyName,
                                                        resultHandler)
                                            }
                                            else -> clientResponse.bodyHandler { body ->
                                                logger.error(clientResponse.statusMessage())
                                                logger.error(body.toString())

                                                logger.fatal("Could not create Device Group for " +
                                                        notificationKeyName + " with " + "id: " +
                                                        device.fcmId)
                                                logger.fatal("Attempting adding...")

                                                it.fail(UnknownError("Could not create Device Group for " +
                                                        notificationKeyName + " with " + "id: " +
                                                        device.fcmId))

                                                doDeviceGroupResult(null, finalChannelMap,
                                                        device, notificationKeyName, channelKeyName,
                                                        resultHandler)
                                            }
                                        }
                                    }.exceptionHandler { message ->
                                        logger.error("HTTP Error: $message")

                                        it.fail(message)
                                    }

                                    req.putHeader(HttpHeaders.AUTHORIZATION, "key=$GCM_API_KEY")
                                    req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                                    req.putHeader("project_id", GCM_SENDER_ID)
                                    req.end(creationJson)
                                }, { logger.error("Failed DeviceGroupAdd: $it") })
                            }
                            else -> addToGroup(device.fcmId, notificationKeyName, key, resultHandler)
                        }
                    }
                }
            }
        }
    }

    private fun doDeviceGroupResult(notificationKey: String?, channelMap: MutableMap<String, String>, device: FcmDevice,
                                    notificationKeyName: String, channelKeyName: String,
                                    resultHandler: Handler<AsyncResult<Boolean>>) {
        logger.info("New key for device group is: " + notificationKey!!)

        @Suppress("SENSELESS_COMPARISON")
        when (notificationKey) {
            null -> {
                val checkKey = Consumer<String> {
                    when {
                        it != null -> setNewKey(device, channelKeyName, channelMap, notificationKeyName, it, resultHandler)
                        else -> resultHandler.handle(failedFuture(IllegalArgumentException("Could not fetch key...")))
                    }
                }

                val httpResultHandler = Handler<AsyncResult<String>> {
                    when {
                        it.succeeded() ->
                            when {
                                it.result() != null -> logger.info("Completed Fetch key...")
                                else -> logger.error("Failed Fetch key...")
                            }
                        else -> logger.error("Failed Fetch key...")
                    }

                    checkKey.accept(it.result())
                }

                val url = "$GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE?notification_key_name=$notificationKeyName"

                APIManager.performRequestWithCircuitBreaker(httpResultHandler, Handler {
                    val options = HttpClientOptions()
                            .setSsl(true)

                    logger.info("Querying: $url")

                    val req = Vertx.currentContext().owner().createHttpClient(options).getAbs(url) { res ->
                        val status = res.statusCode()
                        logger.info("Fetch Notification key response: " + (status == 200))

                        when {
                            status != 200 -> {
                                res.bodyHandler { body ->
                                    logger.error(res.statusMessage())
                                    logger.error(body.toString())
                                }

                                it.fail(UnknownError(res.statusMessage()))
                            }
                            else -> res.bodyHandler { body ->
                                val bodyObject = body.toJsonObject()

                                logger.info("Response from GCM: " + Json.encodePrettily(bodyObject))

                                it.complete(bodyObject.getString("notification_key"))
                            }
                        }
                    }.exceptionHandler { message ->
                        logger.error("HTTP Auth ERROR: $message")

                        it.fail(message)
                    }

                    req.putHeader(HttpHeaders.AUTHORIZATION, "key=$GCM_API_KEY")
                    req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                    req.putHeader("project_id", GCM_SENDER_ID)
                    req.end()
                }) { logger.error("HttpFetchFailed: $it") }
            }
            else -> setNewKey(device, channelKeyName, channelMap,
                    notificationKeyName, notificationKey, resultHandler)
        }
    }

    private fun setNewKey(device: FcmDevice, channelKeyName: String, channelMap: MutableMap<String, String>,
                          notificationKeyName: String, newNotificationKey: String?,
                          resultHandler: Handler<AsyncResult<Boolean>>) {
        if (newNotificationKey != null) channelMap[notificationKeyName] = newNotificationKey

        val mapAsJson = JsonObject(Json.encode(channelMap))

        RedisUtils.performJedisWithRetry(redisClient) { redis ->
            redis.hmset(channelKeyName, mapAsJson) {
                when {
                    it.failed() -> logger.error("Failed to set hm for device group...")
                    else -> addToGroup(device.fcmId, notificationKeyName, newNotificationKey, resultHandler)
                }
            }
        }
    }

    private fun addToGroup(fcmId: String, keyName: String, key: String?,
                           resultHandler: Handler<AsyncResult<Boolean>>) {
        val addJson = Json.encode(MessageSender.createAddDeviceGroupJson(fcmId, keyName, key))
        val url = GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE

        APIManager.performRequestWithCircuitBreaker(resultHandler, Handler {
            val opts = HttpClientOptions()
                    .setSsl(true)

            val req = server.vertx.createHttpClient(opts).postAbs(url) { clientResponse ->
                val status = clientResponse.statusCode()
                logger.info("Add To Group response: " + (status == 200))

                if (status != 200) {
                    clientResponse.bodyHandler { body ->
                        logger.error(clientResponse.statusMessage())
                        logger.error(body.toString())
                    }
                }

                it.complete(status == 200)
            }.exceptionHandler { message ->
                logger.error(message)

                it.fail(message)
            }

            req.putHeader(HttpHeaders.AUTHORIZATION, "key=$GCM_API_KEY")
            req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
            req.putHeader("project_id", GCM_SENDER_ID)
            req.end(addJson)
        }, {
            logger.error("Failed Add to Group...")

            resultHandler.handle(failedFuture(IllegalArgumentException()))
        })
    }
}
