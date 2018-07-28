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

import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_ACK_MESSAGE_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_COLLAPSE_KEY_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_DATA_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_DELAY_WHILE_IDLE_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_MESSAGE_ID_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_MESSAGE_TYPE_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_NACK_MESSAGE_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_TIME_TO_LIVE_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_TO_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.RESTRICTED_PACKAGE_NAME_KEY_NOTATION
import com.nannoq.tools.fcm.server.data.DataMessageHandler.Companion.REGISTER_DEVICE
import com.nannoq.tools.fcm.server.data.DataMessageHandler.Companion.UPDATE_ID
import com.nannoq.tools.fcm.server.messageutils.FcmNotification
import com.nannoq.tools.fcm.server.messageutils.FcmPacketExtension
import com.nannoq.tools.fcm.server.messageutils.MessageUtils.DELAY_WHILE_IDLE
import com.nannoq.tools.fcm.server.messageutils.MessageUtils.TIME_TO_LIVE
import com.nannoq.tools.fcm.server.messageutils.MessageUtils.createJsonMessage
import com.nannoq.tools.repository.repository.redis.RedisUtils
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.RedisClient
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * This class handles all sending functionality for the GCM server.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class MessageSender internal constructor(private val server: FcmServer) {
    private var redisClient: RedisClient? = null
    private val delayedSendingService: ExecutorService = Executors.newCachedThreadPool()

    internal fun setRedisClient(redisClient: RedisClient) {
        this.redisClient = redisClient
    }

    internal fun send(json: JsonObject) {
        val messageId = json.getString(GCM_PACKET_MESSAGE_ID_NOTATION)

        send(messageId, Json.encode(json))
    }

    internal fun send(messageId: String, jsonValue: String) {
        if (redisClient == null) return

        val retryKey = messageId + "_retry_count"

        RedisUtils.performJedisWithRetry(redisClient!!) {
            it.hset(REDIS_MESSAGE_HASH, messageId, jsonValue, { hSetResult ->
                if (hSetResult.failed()) {
                    logger.error("HSET Failed for id:$messageId")
                }

                val extension = FcmPacketExtension(jsonValue)
                val request = extension.toPacket()

                it.get(retryKey, { getResult ->
                    when {
                        getResult.failed() -> {
                            logger.error("SET Failed for id: $messageId")

                            server.sendingConnection!!.sendPacket(request)
                        }
                        else -> {
                            val getResultAsString = getResult.result()

                            val retryCountAsInt = if (getResultAsString == null)
                                1
                            else
                                Integer.parseInt(getResultAsString)

                            delayedSendingService.execute {
                                try {
                                    Thread.sleep((retryCountAsInt * 2 * 1000).toLong())
                                } catch (e: InterruptedException) {
                                    logger.fatal("Could not delay sending of: $messageId")
                                }

                                logger.info("Sending Extension to GCM (JSON): " + extension.json)
                                logger.info("Sending Extension to GCM (XML): " + extension.toXML())
                                logger.info("Sending Packet to GCM (XMLNS): " + request.xmlns)

                                server.sendingConnection!!.sendPacket(request)
                            }

                            val retryCount = "" + retryCountAsInt + 1

                            it.set(retryKey, retryCount, {
                                if (it.failed()) {
                                    logger.error("Re set of retry failed for $messageId")
                                }
                            })
                        }
                    }
                })
            })
        }
    }

    internal fun sendToNewRecipient(regId: String, messageAsJson: String) {
        val messageJson = JsonObject(messageAsJson)
        messageJson.put(GCM_PACKET_TO_NOTATION, regId)

        send(messageJson)
    }

    fun replyWithSuccessfullDeviceRegistration(packageName: String, gcmId: String) {
        logger.info("Returning message of correct registration...")

        var body = JsonObject()
        body = addSuccessCreate(body)

        send(createJsonMessage(gcmId, REGISTER_DEVICE, body, REGISTER_DEVICE, packageName))
    }

    fun replyWithDeviceAlreadyExists(packageName: String, gcmId: String) {
        logger.info("Device already exists...")

        val body = JsonObject()
        body.put(MESSAGE_STATUS, FAILURE)
        body.put(STATUS_CODE, ALREADY_EXISTS)

        send(createJsonMessage(gcmId, REGISTER_DEVICE, body, REGISTER_DEVICE, packageName))
    }

    fun replyWithNewDeviceIdSet(gcmId: String, packageName: String) {
        logger.info("Returning message of correct re-registration...")

        val body = JsonObject()
        body.put(MESSAGE_STATUS, SUCCESS)
        body.put(STATUS_CODE, UPDATED)

        send(createJsonMessage(gcmId, UPDATE_ID, body, UPDATE_ID, packageName))
    }

    private fun addSuccessCreate(message: JsonObject): JsonObject {
        message.put(MESSAGE_STATUS, SUCCESS)
        message.put(STATUS_CODE, CREATED)

        return message
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MessageSender::class.java.simpleName)

        // local values
        private const val GCM_PACKET_CONTENT_AVAILABLE_NOTATION = "content_available"
        private const val GCM_PACKET_NOTIFICATION_NOTATION = "notification"
        private const val GCM_PACKET_PRIORITY_NOTATION = "priority"
        private const val MESSAGE_STATUS = "message"
        private const val DELIVERY_RECEIPT_REQUESTED = "delivery_receipt_requested"
        private const val STATUS_CODE = "status"
        private const val SUCCESS = "Success"
        private const val FAILURE = "Failure"
        private const val ALREADY_EXISTS = 208
        private const val CREATED = 200
        private const val NO_CONTENT = 204
        private const val UPDATED = 204
        private const val INTERNAL_ERROR = 500

        // device group values
        private const val GCM_DEVICE_GROUP_OPERATION_NOTATION = "operation"
        private const val GCM_DEVICE_GROUP_CREATE_GROUP_NOTATION = "create"
        private const val GCM_DEVICE_GROUP_ADD_ACTION_NOTATION = "add"
        private const val GCM_DEVICE_GROUP_REMOVE_ACTION_NOTATION = "remove"
        private const val GCM_DEVICE_NOTIFICATION_KEY_NAME_NOTATION = "notification_key_name"
        private const val GCM_DEVICE_NOTIFICATION_KEY_NOTATION = "notification_key"
        private const val GCM_PACKET_NOTIFICATION_SOUND_NOTATION = "sound"
        private const val GCM_PACKET_NOTIFICATION_SOUND_DEFAULT = "default"

        private const val GCM_DEVICE_REG_IDS_NOTATION = "registration_ids"

        // redis message hash
        internal const val REDIS_MESSAGE_HASH = "MESSAGE_QUEUE"

        fun createJsonAck(from: String, messageId: String): JsonObject {
            logger.info("Responding with ack to: $from based on $messageId")

            return prepareJsonAckNack(from, messageId, GCM_PACKET_ACK_MESSAGE_NOTATION)
        }

        fun createJsonNack(from: String, messageId: String): JsonObject {
            logger.info("Responding with nack to: $from based on $messageId")

            return prepareJsonAckNack(from, messageId, GCM_PACKET_NACK_MESSAGE_NOTATION)
        }

        @Suppress("SENSELESS_COMPARISON")
        @JvmOverloads
        fun createCustomNotification(appPackageName: String?, to: String,
                                     customNotification: FcmNotification, dryRun: Boolean = false): JsonObject {
            val message = JsonObject()
            message.put(GCM_PACKET_TO_NOTATION, to)
            message.put(GCM_PACKET_MESSAGE_ID_NOTATION, UUID.randomUUID().toString())
            message.put(GCM_PACKET_CONTENT_AVAILABLE_NOTATION,
                    customNotification.data != null && customNotification.data.isNotEmpty())
            message.put(GCM_PACKET_PRIORITY_NOTATION, if (customNotification.priority != null)
                customNotification.priority
            else
                "high")
            message.put(GCM_PACKET_COLLAPSE_KEY_NOTATION, customNotification.collapseKey)
            val notification = customNotification.notification.toMutableMap()

            if (notification != null) {
                notification[GCM_PACKET_NOTIFICATION_SOUND_NOTATION] = GCM_PACKET_NOTIFICATION_SOUND_DEFAULT
            }

            customNotification.notification = notification.toMap()

            message.put(GCM_PACKET_NOTIFICATION_NOTATION, notification)
            message.put(GCM_PACKET_DATA_NOTATION, customNotification.data)
            message.put(DELIVERY_RECEIPT_REQUESTED, true)
            message.put(GCM_PACKET_TIME_TO_LIVE_NOTATION, TIME_TO_LIVE)
            message.put(GCM_PACKET_DELAY_WHILE_IDLE_NOTATION, DELAY_WHILE_IDLE)
            message.put(RESTRICTED_PACKAGE_NAME_KEY_NOTATION, appPackageName)
            if (dryRun) message.put("dry_run", true)

            return message
        }

        fun createDeviceGroupCreationJson(uniqueId: String, gcmId: String): JsonObject {
            val message = JsonObject()
            message.put(GCM_DEVICE_GROUP_OPERATION_NOTATION, GCM_DEVICE_GROUP_CREATE_GROUP_NOTATION)
            message.put(GCM_DEVICE_NOTIFICATION_KEY_NAME_NOTATION, uniqueId)
            message.put(GCM_DEVICE_REG_IDS_NOTATION, listOf(gcmId))

            return createJsonMessage(message)
        }

        fun createAddDeviceGroupJson(gcmId: String, notificationKeyName: String, key: String?): JsonObject {
            val message = buildDeviceGroupOperator(gcmId, notificationKeyName, key)
            message.put(GCM_DEVICE_GROUP_OPERATION_NOTATION, GCM_DEVICE_GROUP_ADD_ACTION_NOTATION)

            return createJsonMessage(message)
        }

        fun createRemoveDeviceGroupJson(gcmId: String, notificationKeyName: String, key: String): JsonObject {
            val message = buildDeviceGroupOperator(gcmId, notificationKeyName, key)
            message.put(GCM_DEVICE_GROUP_OPERATION_NOTATION, GCM_DEVICE_GROUP_REMOVE_ACTION_NOTATION)

            return createJsonMessage(message)
        }

        private fun buildDeviceGroupOperator(gcmId: String, notificationKeyName: String, key: String?): JsonObject {
            val message = JsonObject()
            message.put(GCM_DEVICE_NOTIFICATION_KEY_NAME_NOTATION, notificationKeyName)
            message.put(GCM_DEVICE_NOTIFICATION_KEY_NOTATION, key)
            message.put(GCM_DEVICE_REG_IDS_NOTATION, listOf(gcmId))

            return message
        }

        private fun prepareJsonAckNack(from: String, messageId: String, type: String): JsonObject {
            val message = JsonObject()
            message.put(GCM_PACKET_TO_NOTATION, from)
            message.put(GCM_PACKET_MESSAGE_ID_NOTATION, messageId)
            message.put(GCM_PACKET_MESSAGE_TYPE_NOTATION, type)

            return message
        }
    }
}
