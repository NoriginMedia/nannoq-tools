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

package com.nannoq.tools.fcm

import com.nannoq.tools.fcm.server.FcmServer
import com.nannoq.tools.fcm.server.MessageSender
import com.nannoq.tools.fcm.server.data.DataMessageHandler
import com.nannoq.tools.fcm.server.data.RegistrationService
import com.nannoq.tools.fcm.server.messageutils.CcsMessage
import io.vertx.core.Future

/**
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class DefaultDataMessageHandler : DataMessageHandler {
    private var messageFuture: Future<CcsMessage>? = null

    override val registrationService: RegistrationService = DefaultRegistrationService()

    override fun handleIncomingDataMessage(ccsMessage: CcsMessage) {
        if (messageFuture == null) throw IllegalArgumentException("Preload a future before receiving messages!")
        if (messageFuture!!.isComplete) throw IllegalStateException("This future is already complete!")
        messageFuture!!.complete(ccsMessage)
    }

    override fun setServer(server: FcmServer): DataMessageHandler {
        return this
    }

    override fun setSender(sender: MessageSender): DataMessageHandler {
        return this
    }
}
