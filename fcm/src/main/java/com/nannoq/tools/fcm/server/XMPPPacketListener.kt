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
import com.nannoq.tools.fcm.server.MessageSender.Companion.REDIS_MESSAGE_HASH
import com.nannoq.tools.fcm.server.control.ControlMessageHandler
import com.nannoq.tools.fcm.server.data.DataMessageHandler
import com.nannoq.tools.fcm.server.data.FcmDevice
import com.nannoq.tools.fcm.server.data.RegistrationService
import com.nannoq.tools.fcm.server.messageutils.CcsMessage
import com.nannoq.tools.fcm.server.messageutils.FcmPacketExtension
import com.nannoq.tools.repository.repository.redis.RedisUtils
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.RedisClient
import org.jivesoftware.smack.PacketListener
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Packet

/**
 * This class handles reception of all messages received from the CCS and devices.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class XMPPPacketListener internal constructor(private val server: FcmServer,
                                              private val redisClient: RedisClient,
                                              private val dataMessageHandler: DataMessageHandler?,
                                              private val registrationService: RegistrationService?,
                                              private val GCM_SENDER_ID: String?, private val GCM_API_KEY: String?) : PacketListener {
    private val logger = LoggerFactory.getLogger(XMPPPacketListener::class.java.simpleName)
    private val sender: MessageSender = MessageSender(server)

    init {
        sender.setRedisClient(redisClient)
    }

    override fun processPacket(packet: Packet) {
        logger.info("Packet received..")

        val incomingMessage = packet as Message
        val gcmPacket = incomingMessage.getExtension(GCM_NAMESPACE) as FcmPacketExtension
        val json = gcmPacket.json

        val jsonMap = JsonObject(json)

        logger.info("Packet contents: $jsonMap")

        handleMessage(jsonMap)
    }

    private fun handleMessage(jsonMap: JsonObject) {
        val messageType = jsonMap.getString(GCM_PACKET_MESSAGE_TYPE_NOTATION)
        logger.info("Received a message of type: " + messageType!!)

        @Suppress("SENSELESS_COMPARISON")
        when {
            messageType == null -> {
                logger.info("Received a datamessage...")

                val msg = getMessage(jsonMap)

                try {
                    sender.send(MessageSender.createJsonAck(msg.from, msg.messageId))
                } catch (e: Exception) {
                    sender.send(MessageSender.createJsonNack(msg.from, msg.messageId))
                }

                dataMessageHandler?.handle(msg)
            }
            GCM_PACKET_ACK_MESSAGE_NOTATION == messageType -> {
                logger.info("Received ACK ...")

                handleAck(jsonMap)
            }
            GCM_PACKET_NACK_MESSAGE_NOTATION == messageType -> {
                logger.warn("Received NACK...")

                handleNack(jsonMap)
            }
            GCM_PACKET_RECEIPT_MESSAGE_NOTATION == messageType -> {
                logger.warn("Received Receipt...")

                handleReceipt(jsonMap)
            }
            GCM_PACKET_CONTROL_MESSAGE_NOTATION == messageType -> {
                logger.warn("Received CONTROL...")

                ControlMessageHandler(server).handleControl(jsonMap)
            }
            else -> logger.error("Could not parse message: $messageType")
        }
    }

    private fun getMessage(jsonMap: JsonObject): CcsMessage {
        return CcsMessage(
                jsonMap.getString(GCM_PACKET_FROM_NOTATION),
                jsonMap.getString(GCM_PACKET_CATEGORY_NOTATION),
                jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION),
                jsonMap.getString(GCM_PACKET_REGISTRATION_ID_NOTATION),
                jsonMap.getJsonObject(GCM_PACKET_DATA_NOTATION))
    }

    private fun handleAck(jsonMap: JsonObject) {
        val messageId = jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION)
        val from = jsonMap.getString(GCM_PACKET_FROM_NOTATION)
        val registrationId = jsonMap.getString(GCM_PACKET_REGISTRATION_ID_NOTATION)

        if (registrationId != null) {
            logger.info("Received canonical, updating device!")
        }

        logger.info("CCS reports ACK for: $messageId from: $from")

        val success = jsonMap.getInteger("success")
        val failure = jsonMap.getInteger("failure")

        when {
            success != null && failure != null -> {
                logger.info("CCS reports ACK for Device Group message...")

                if (failure > 0) {
                    RedisUtils.performJedisWithRetry(redisClient) { redisClient ->
                        redisClient.get(messageId) { result ->
                            when {
                                result.failed() -> logger.error("Failed to process message...", result.cause())
                                else -> {
                                    val messageAsJson = result.result()

                                    when {
                                        messageAsJson != null -> {
                                            val failedIds = jsonMap.getJsonArray("failed_registration_ids")

                                            logger.info("Failed send to following ids: " + failedIds.encodePrettily())

                                            failedIds.forEach {
                                                logger.info("Resending to failed id: $it")

                                                sender.sendToNewRecipient(it.toString(), messageAsJson)
                                            }
                                        }
                                        else -> logger.error("Message Json is null for: $messageId")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> RedisUtils.performJedisWithRetry(redisClient) {
                val transaction = it.transaction()

                transaction.multi {
                    transaction.hdel(REDIS_MESSAGE_HASH, messageId) {
                        if (it.failed()) {
                            logger.error("Could not remove message hash...")
                        }
                    }

                    transaction.del(messageId + "_retry_count") {
                        if (it.failed()) {
                            logger.error("Could not remove reply count...")
                        }
                    }
                }

                transaction.exec {
                    when {
                        it.failed() -> logger.error("Could not execute redis transaction...")
                        else -> logger.info("Message sent successfully, purged from redis...")
                    }
                }
            }
        }
    }

    private fun handleNack(jsonMap: JsonObject) {
        val from = jsonMap.getString(GCM_PACKET_FROM_NOTATION)
        val messageId = jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION)
        val registrationId = jsonMap.getString(GCM_PACKET_REGISTRATION_ID_NOTATION)

        logger.info("CCS reports NACK for: " + jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION) + " from: " + from)

        val errorCode = jsonMap.getString(GCM_PACKET_ERROR_CODE_NOTATION)

        if (registrationId != null) {
            logger.info("Received canonical, updating device!")
        }

        when (errorCode) {
            GCM_ERROR_CODE_BAD_REGISTRATION, GCM_ERROR_CODE_DEVICE_UNREGISTERED -> {
                logger.error("Registration ID does not exist, deleting device...")

                registrationService?.handleDeviceRemoval(messageId, registrationId, Handler {
                    when {
                        it.failed() -> logger.error("No FcmDevice received for device group removal...")
                        else -> RedisUtils.performJedisWithRetry(redisClient) { inner ->
                            inner.get(messageId) { result ->
                                when {
                                    result.failed() -> logger.error("Failed to process message...", result.cause())
                                    else -> {
                                        val messageAsJson = result.result()

                                        when {
                                            messageAsJson != null -> {
                                                val packageName = JsonObject(messageAsJson)
                                                        .getString(RESTRICTED_PACKAGE_NAME_KEY_NOTATION)
                                                val channelKey = packageName.substring(packageName.lastIndexOf(".") + 1)

                                                deleteDeviceFromFCM(it.result(), inner, channelKey)
                                            }
                                            else -> logger.error("Message Json is null for: $messageId")
                                        }
                                    }
                                }
                            }
                        }
                    }
                })
            }
            GCM_ERROR_CODE_SERVICE_UNAVAILABLE -> {
                logger.fatal("SERVICE UNAVAILABLE!")

                sendReply(messageId)
            }
            GCM_ERROR_CODE_INTERNAL_SERVER_ERROR -> {
                logger.fatal("INTERNAL SERVER ERROR!")

                sendReply(messageId)
            }
            GCM_ERROR_CODE_INVALID_JSON -> logger.fatal("WRONG JSON FROM APP SERVER: " + jsonMap.getString(GCM_PACKET_ERROR_DESCRIPTION_NOTATION))
            GCM_ERROR_CODE_DEVICE_MESSAGE_RATE_EXCEEDED -> logger.error("Exceeded message limit for device: $from")
            else -> logger.error("Could not handle error: " + errorCode + " for: " + jsonMap.encodePrettily())
        }
    }

    private fun sendReply(messageId: String) {
        RedisUtils.performJedisWithRetry(redisClient) {
            it.hget(REDIS_MESSAGE_HASH, messageId) {
                when {
                    it.failed() -> logger.error("Unable to get map for message...")
                    else -> sender.send(messageId, it.result())
                }
            }
        }
    }

    private fun deleteDeviceFromFCM(device: FcmDevice, redisClient: RedisClient, channelKey: String) {
        val from = device.fcmId
        val notificationKeyName = device.notificationKeyName

        RedisUtils.performJedisWithRetry(redisClient) {
            redisClient.hget(channelKey, notificationKeyName) {
                when {
                    it.failed() -> logger.error("Unable to fetch notificationkey...")
                    else -> {
                        val removeJson = Json.encode(MessageSender.createRemoveDeviceGroupJson(
                                from, notificationKeyName, it.result()))

                        removeFromGroup(removeJson)
                    }
                }
            }
        }
    }

    private fun removeFromGroup(removeJson: String) {
        val resultHandler = Handler<AsyncResult<Boolean>> {
            when {
                it.succeeded() ->
                    when {
                        it.result() -> logger.error("Failed Remove from Group...")
                        else -> logger.info("Completed Remove from Group...")
                    }
                else -> logger.error("Failed Remove from Group...")
            }
        }

        val url = GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE

        APIManager.performRequestWithCircuitBreaker(resultHandler, Handler {
            val opts = HttpClientOptions()
                    .setSsl(true)

            val req = server.vertx.createHttpClient(opts).postAbs(url) {
                val status = it.statusCode()
                logger.info("Delete From Group response: " + (status == 200))

                if (status != 200) {
                    it.bodyHandler { body ->
                        logger.error(it.statusMessage())
                        logger.error(body.toString())
                    }
                }
            }.exceptionHandler { message ->
                logger.error(message)

                it.fail(message)
            }

            req.putHeader(HttpHeaders.AUTHORIZATION, "key=$GCM_API_KEY")
            req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
            req.putHeader("project_id", GCM_SENDER_ID)
            req.end(removeJson)
        }) { logger.error("Remove From Group Failed: $it") }
    }

    private fun handleReceipt(jsonMap: JsonObject) {
        val data = jsonMap.getJsonObject(GCM_PACKET_DATA_NOTATION)
        val category = jsonMap.getString(GCM_PACKET_CATEGORY_NOTATION)
        val from = jsonMap.getString(GCM_PACKET_FROM_NOTATION)
        val messageStatus = data.getString(GCM_PACKET_RECEIPT_MESSAGE_STATUS_NOTATION)
        val originalMessageId = data.getString(GCM_PACKET_RECEIPT_ORIGINAL_MESSAGE_ID_NOTATION)
        val gcmId = data.getString(GCM_PACKET_RECEIPT_GCM_ID_NOTATION)

        logger.info("CCS reports RECEIPT for: $category from: $from with: $data")

        when (messageStatus) {
            GCM_RECEIPT_MESSAGE_DELIVERED_CODE -> {
                logger.info("Message ID: $originalMessageId delivered to: $gcmId")

                sender.send(MessageSender.createJsonAck(from, jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION)))
            }
            else -> logger.error("Unknown receipt message...")
        }
    }

    companion object {
        const val GCM_ELEMENT_NAME = "gcm"
        const val GCM_NAMESPACE = "google:mobile:data"

        // gcm notations
        const val GCM_PACKET_TO_NOTATION = "to"
        const val GCM_PACKET_MESSAGE_ID_NOTATION = "message_id"
        const val GCM_PACKET_REGISTRATION_ID_NOTATION = "registration_id"
        const val GCM_PACKET_DATA_NOTATION = "data"
        const val RESTRICTED_PACKAGE_NAME_KEY_NOTATION = "restricted_package_name"
        const val IOS_MUTABLE_NOTATION = "mutable_content"
        const val GCM_PACKET_COLLAPSE_KEY_NOTATION = "collapse_key"
        const val GCM_PACKET_TIME_TO_LIVE_NOTATION = "time_to_live"
        const val GCM_PACKET_DELAY_WHILE_IDLE_NOTATION = "delay_while_idle"
        internal const val GCM_PACKET_MESSAGE_TYPE_NOTATION = "message_type"
        internal const val GCM_PACKET_ACK_MESSAGE_NOTATION = "ack"
        internal const val GCM_PACKET_NACK_MESSAGE_NOTATION = "nack"
        private const val GCM_PACKET_RECEIPT_MESSAGE_NOTATION = "receipt"
        private const val GCM_PACKET_CONTROL_MESSAGE_NOTATION = "control"
        private const val GCM_PACKET_FROM_NOTATION = "from"
        private const val GCM_PACKET_CATEGORY_NOTATION = "category"
        private const val GCM_PACKET_ERROR_CODE_NOTATION = "error"
        private const val GCM_PACKET_ERROR_DESCRIPTION_NOTATION = "error_description"
        private const val GCM_PACKET_RECEIPT_MESSAGE_STATUS_NOTATION = "message_status"
        private const val GCM_PACKET_RECEIPT_ORIGINAL_MESSAGE_ID_NOTATION = "original_message_id"
        private const val GCM_PACKET_RECEIPT_GCM_ID_NOTATION = "device_registration_id"

        // gcm error codes
        private const val GCM_ERROR_CODE_BAD_REGISTRATION = "BAD_REGISTRATION"
        private const val GCM_ERROR_CODE_DEVICE_UNREGISTERED = "DEVICE_UNREGISTERED"
        private const val GCM_ERROR_CODE_INVALID_JSON = "INVALID_JSON"
        private const val GCM_ERROR_CODE_DEVICE_MESSAGE_RATE_EXCEEDED = "DEVICE_MESSAGE_RATE_EXCEEDED"
        private const val GCM_ERROR_CODE_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"
        private const val GCM_ERROR_CODE_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR"

        // gcm receipt codes
        private const val GCM_RECEIPT_MESSAGE_DELIVERED_CODE = "MESSAGE_SENT_TO_DEVICE"
    }
}
