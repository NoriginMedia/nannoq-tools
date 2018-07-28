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

package com.nannoq.tools.fcm.server.data

import com.nannoq.tools.fcm.server.FcmServer
import com.nannoq.tools.fcm.server.MessageSender
import com.nannoq.tools.fcm.server.messageutils.CcsMessage
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

/**
 * This class handles various scenarios for data messages retrieved from devices.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
interface DataMessageHandler : Handler<CcsMessage> {
    val registrationService: RegistrationService

    override fun handle(msg: CcsMessage) {
        logger.info("Message from: " + msg.from + "\n" +
                "Message category: " + msg.category + "\n" +
                "Message id: " + msg.messageId + "\n" +
                "Message data: " + msg.payload)

        val gcmId = msg.from
        val data = msg.payload
        val action = cleanData(data.getString(ACTION_NOTATION))

        @Suppress("SENSELESS_COMPARISON")
        when {
            msg.registrationId != null -> {
                logger.info("New token detected, performing update with canonical!")

                data.put(OLD_ID_NOTATION, gcmId)

                registrationService.update(msg.category, msg.registrationId, data)
            }
            else ->
                when (action) {
                    REGISTER_DEVICE -> registrationService.registerDevice(msg.category, gcmId, data)
                    UPDATE_ID -> registrationService.update(msg.category, gcmId, data)
                    PONG -> {
                        logger.info("Device is alive...")
                        setDeviceAlive(data)
                    }
                    else -> handleIncomingDataMessage(msg)
                }
        }
    }

    fun handleIncomingDataMessage(ccsMessage: CcsMessage)

    fun cleanData(input: String?): String? {
        return if (input != null) Jsoup.clean(input, Whitelist.basic()) else null

    }

    fun setDeviceAlive(data: JsonObject) {
        // TODO No implementation yet
    }

    @Fluent
    fun setServer(server: FcmServer): DataMessageHandler

    @Fluent
    fun setSender(sender: MessageSender): DataMessageHandler

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DataMessageHandler::class.java.simpleName)

        // data notations
        const val ACTION_NOTATION = "action"
        const val OLD_ID_NOTATION = "old_id"

        // constant actions
        const val REGISTER_DEVICE = "Register Device"
        const val UPDATE_ID = "Update Id"
        const val PONG = "Pong"
    }
}
