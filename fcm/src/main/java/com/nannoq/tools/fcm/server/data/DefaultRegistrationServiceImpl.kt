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

package com.nannoq.tools.fcm.server.data

import com.nannoq.tools.fcm.server.FcmServer
import com.nannoq.tools.fcm.server.MessageSender
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject

/**
 * This class handles various scenarios for data messages retrieved from devices.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class DefaultRegistrationServiceImpl : RegistrationService {
    override fun setServer(server: FcmServer): RegistrationService {
        return this
    }

    override fun setSender(sender: MessageSender): RegistrationService {
        return this
    }

    override fun registerDevice(appPackageName: String, fcmId: String, data: JsonObject): RegistrationService {
        return this
    }

    override fun update(appPackageName: String, fcmId: String, data: JsonObject): RegistrationService {
        return this
    }

    override fun handleDeviceRemoval(messageId: String, registrationId: String, resultHandler: Handler<AsyncResult<FcmDevice>>): RegistrationService {
        return this
    }
}
