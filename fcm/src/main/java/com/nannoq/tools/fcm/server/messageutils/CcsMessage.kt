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

import io.vertx.core.json.JsonObject

/**
 * Represents a message for CCS based massaging.
 *
 * Sourced from: https://github.com/writtmeyer/gcm_server/blob/master/src/com/grokkingandroid/sampleapp/samples/gcm/ccs/server/CcsMessage.java
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class CcsMessage(
    /**
     * Recipient-ID.
     */
    val from: String,
    /**
     * Sender app's package.
     */
    val category: String,
    /**
     * Unique id for this message.
     */
    val messageId: String,
    val registrationId: String,
    /**
     * Payload data. A String in Json format.
     */
    val payload: JsonObject
) {
    companion object {
        const val GCM_CONFIGURATION_NOTATION = "configuration"
        const val GCM_PRIORITY_NOTATION = "priority"
        const val GCM_CONTENT_AVAILABLE_NOTATION = "contentAvailable"
        const val GCM_COLLAPSE_KEY_NOTATION = "collapseKey"

        const val GCM_NOTIFICATION_NOTATION = "notification"
        const val GCM_NOTIFICATION_TITLE_NOTATION = "title"
        const val GCM_NOTIFICATION_BODY_NOTATION = "body"
        const val GCM_NOTIFICATION_CONTENT_NOTATION = "content"
        const val GCM_DATA_NOTATION = "data"
        const val GCM_DATA_ACTION_NOTATION = "action"
    }
}
