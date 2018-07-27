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

package com.nannoq.tools.fcm.server.messageutils

import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_COLLAPSE_KEY_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_DATA_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_DELAY_WHILE_IDLE_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_MESSAGE_ID_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_TIME_TO_LIVE_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_PACKET_TO_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.IOS_MUTABLE_NOTATION
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.RESTRICTED_PACKAGE_NAME_KEY_NOTATION
import com.nannoq.tools.fcm.server.data.DataMessageHandler.Companion.ACTION_NOTATION
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import java.util.*

/**
 * This class handles various utilities for messages.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
object MessageUtils {
    // message default
    const val TIME_TO_LIVE = 86400
    const val DELAY_WHILE_IDLE = false

    private const val GCM_PACKET_CONTENT_AVAILABLE_NOTATION = "content_available"
    private const val GCM_PACKET_PRIORITY_NOTATION = "priority"
    private const val DELIVERY_RECEIPT_REQUESTED = "delivery_receipt_requested"

    private fun generateNewMessageId(): String {
        return UUID.randomUUID().toString()
    }

    fun createJsonMessage(to: String, action: String,
                          payload: JsonObject, collapseKey: String, packageName: String): JsonObject {
        return createJsonMessage(createAttributeMap(
                to, action, generateNewMessageId(), payload, collapseKey, packageName))
    }

    fun createJsonMessage(map: JsonObject): JsonObject {
        return map
    }

    private fun createAttributeMap(to: String?, action: String, messageId: String?,
                                   payload: JsonObject?, collapseKey: String?, packageName: String): JsonObject {
        val message = HashMap<String, Any>()

        if (to != null) message[GCM_PACKET_TO_NOTATION] = to
        if (collapseKey != null) message[GCM_PACKET_COLLAPSE_KEY_NOTATION] = collapseKey
        if (messageId != null) message[GCM_PACKET_MESSAGE_ID_NOTATION] = messageId

        message[GCM_PACKET_TIME_TO_LIVE_NOTATION] = TIME_TO_LIVE
        message[GCM_PACKET_DELAY_WHILE_IDLE_NOTATION] = DELAY_WHILE_IDLE
        message[GCM_PACKET_CONTENT_AVAILABLE_NOTATION] = payload != null

        if (payload != null) {
            payload.put(ACTION_NOTATION, action)
            message[GCM_PACKET_DATA_NOTATION] = payload
        }

        message[RESTRICTED_PACKAGE_NAME_KEY_NOTATION] = packageName
        message[IOS_MUTABLE_NOTATION] = true
        message[GCM_PACKET_PRIORITY_NOTATION] = "high"
        message[DELIVERY_RECEIPT_REQUESTED] = true

        return JsonObject(Json.encode(message))
    }
}
