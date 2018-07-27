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

import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_ELEMENT_NAME
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_NAMESPACE
import org.jivesoftware.smack.packet.DefaultPacketExtension
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Packet
import org.jivesoftware.smack.util.StringUtils

/**
 * XMPP Packet Extension for GCM Cloud Connection Server.
 *
 * Sourced: https://github.com/writtmeyer/gcm_server/blob/master/src/com/grokkingandroid/sampleapp/samples/gcm/ccs/server/CcsClient.java
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class FcmPacketExtension(val json: String) : DefaultPacketExtension(GCM_ELEMENT_NAME, GCM_NAMESPACE) {

    override fun toXML(): String {
        return String.format("<%s xmlns=\"%s\">%s</%s>",
                GCM_ELEMENT_NAME, GCM_NAMESPACE,
                StringUtils.escapeForXML(json), GCM_ELEMENT_NAME)
    }

    fun toPacket(): Packet {
        val message = Message()
        message.addExtension(this)
        return message
    }
}
