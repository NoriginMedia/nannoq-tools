package com.nannoq.tools.fcm

import com.nannoq.tools.fcm.server.FcmServer
import com.nannoq.tools.fcm.server.MessageSender
import com.nannoq.tools.fcm.server.data.FcmDevice
import com.nannoq.tools.fcm.server.data.RegistrationService
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject

/**
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class DefaultRegistrationService : RegistrationService {
    override fun setServer(server: FcmServer): RegistrationService {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun setSender(sender: MessageSender): RegistrationService {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun registerDevice(appPackageName: String, fcmId: String, data: JsonObject): RegistrationService {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun update(appPackageName: String, fcmId: String, data: JsonObject): RegistrationService {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun handleDeviceRemoval(messageId: String, registrationId: String, resultHandler: Handler<AsyncResult<FcmDevice>>): RegistrationService {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}