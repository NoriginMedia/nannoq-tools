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

import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_ELEMENT_NAME
import com.nannoq.tools.fcm.server.XMPPPacketListener.Companion.GCM_NAMESPACE
import com.nannoq.tools.fcm.server.data.DataMessageHandler
import com.nannoq.tools.fcm.server.data.DefaultDataMessageHandlerImpl
import com.nannoq.tools.fcm.server.data.DefaultRegistrationServiceImpl
import com.nannoq.tools.fcm.server.data.RegistrationService
import com.nannoq.tools.fcm.server.messageutils.FcmNotification
import com.nannoq.tools.fcm.server.messageutils.FcmPacketExtension
import com.nannoq.tools.repository.repository.redis.RedisUtils
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.RedisClient
import org.jivesoftware.smack.Connection
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.PacketInterceptor
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.filter.PacketTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.provider.PacketExtensionProvider
import org.jivesoftware.smack.provider.ProviderManager
import javax.net.ssl.SSLSocketFactory

/**
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
class FcmServer private constructor(dev: Boolean) : AbstractVerticle() {

    private val logger = LoggerFactory.getLogger(FcmServer::class.java.simpleName)

    private var PACKAGE_NAME_BASE: String? = null
    private var GCM_SENDER_ID: String? = null
    private var GCM_API_KEY: String? = null
    private val GCM_PORT: Int = if (dev) 5236 else 5235
    private var messageSender: MessageSender? = null
    private var dataMessageHandler: DataMessageHandler? = null
    private var registrationService: RegistrationService? = null

    private var redisClient: RedisClient? = null
        private set

    private var connectionConfiguration: ConnectionConfiguration? = null

    private var primaryConnection: Connection? = null
    private var secondaryConnection: Connection? = null

    private var primaryIsDraining: Boolean = false
    private var primaryConnecting: Boolean = false
    private var secondaryConnecting: Boolean = false

    internal val sendingConnection: Connection?
        get() {
            logger.info("GCM " +
                    (if (primaryIsDraining) "is" else "is not") +
                    " draining primary connection" +
                    if (primaryIsDraining) "!" else "...")

            return when {
                primaryIsDraining -> secondaryConnection
                else -> {
                    if (primaryConnection != null && primaryConnection!!.isConnected &&
                            secondaryConnection != null && secondaryConnection!!.isConnected) {
                        primaryIsDraining = false
                        secondaryConnection!!.disconnect()
                    }

                    primaryConnection
                }
            }
        }

    val isOnline: Boolean
        get() = when {
            primaryConnection != null -> primaryConnection!!.isConnected && primaryConnection!!.isAuthenticated
            else -> secondaryConnection != null &&
                    secondaryConnection!!.isConnected &&
                    secondaryConnection!!.isAuthenticated
        }

    class FcmServerBuilder {
        private var dev = false
        private var dataMessageHandler: DataMessageHandler? = null
        private var registrationService: RegistrationService? = null

        @Fluent
        fun withDev(dev: Boolean): FcmServerBuilder {
            this.dev = dev
            return this
        }

        @Fluent
        fun withDataMessageHandler(dataMessageHandler: DataMessageHandler): FcmServerBuilder {
            this.dataMessageHandler = dataMessageHandler

            return this
        }

        @Fluent
        fun withRegistrationService(registrationService: RegistrationService): FcmServerBuilder {
            this.registrationService = registrationService

            return this
        }

        fun build(): FcmServer {
            val fcmServer = FcmServer(dev)
            val messageSender = MessageSender(fcmServer)
            if (dataMessageHandler == null) dataMessageHandler = DefaultDataMessageHandlerImpl()
            if (registrationService == null) registrationService = DefaultRegistrationServiceImpl()
            dataMessageHandler!!.setServer(fcmServer)
            registrationService!!.setServer(fcmServer)

            dataMessageHandler!!.setSender(messageSender)
            registrationService!!.setSender(messageSender)

            fcmServer.setDataMessageHandler(dataMessageHandler)
            fcmServer.setRegistrationService(registrationService)
            fcmServer.setMessageSender(messageSender)

            return fcmServer
        }
    }

    private fun setDataMessageHandler(dataMessageHandler: DataMessageHandler?) {
        this.dataMessageHandler = dataMessageHandler
    }

    private fun setRegistrationService(registrationService: RegistrationService?) {
        this.registrationService = registrationService
    }

    private fun setMessageSender(messageSender: MessageSender) {
        this.messageSender = messageSender
    }

    @Throws(Exception::class)
    override fun start(startFuture: Future<Void>) {
        logger.info("Starting GCM Server: $this")

        PACKAGE_NAME_BASE = config().getString("basePackageNameFcm")
        GCM_SENDER_ID = config().getString("gcmSenderId")
        GCM_API_KEY = config().getString("gcmApiKey")

        val errors = JsonObject()

        if (PACKAGE_NAME_BASE == null) errors.put("packageNameBase_error", "Cannot be null!")
        if (GCM_SENDER_ID == null) errors.put("gcmSenderId_error", "Cannot be null!")
        if (GCM_API_KEY == null) errors.put("gcmApiKey_error", "Cannot be null!")

        when {
            errors.isEmpty -> vertx.executeBlocking(Handler {
                connectionConfiguration = ConnectionConfiguration(GCM_ENDPOINT, GCM_PORT)
                redisClient = RedisUtils.getRedisClient(vertx, config())
                if (redisClient != null) this.messageSender?.setRedisClient(redisClient!!)
                setConfiguration()

                try {
                    if (primaryConnection == null || !primaryConnection!!.isConnected) {
                        primaryConnection = connect()
                        addPacketListener(primaryConnection!!)
                        auth(primaryConnection!!)

                        logger.info("GCM Connection established...")

                        it.complete()
                    }
                } catch (e: XMPPException) {
                    logger.error("GCM Connection could not be established!", e)

                    it.fail(e)
                }
            }, false, startFuture)
            else -> startFuture.fail(errors.encodePrettily())
        }
    }

    @Throws(Exception::class)
    override fun stop(stopFuture: Future<Void>) {
        logger.info("Shutting down GCM Server: $this...")

        vertx.executeBlocking(Handler {
            primaryConnection!!.disconnect()

            if (secondaryConnection != null && secondaryConnection!!.isConnected) {
                secondaryConnection!!.disconnect()
            }

            it.complete()
        }, false, stopFuture)
    }

    fun checkForDeadConnections() {
        when {
            primaryConnection != null && !primaryConnection!!.isConnected &&
                    secondaryConnection != null && secondaryConnection!!.isConnected -> {
                try {
                    logger.info("Draining on primary resolved, reconnecting...")

                    primaryConnecting = true
                    primaryIsDraining = false
                    primaryConnection = connect()
                    addPacketListener(primaryConnection!!)
                    auth(primaryConnection!!)

                    logger.info("Disconnecting secondary...")
                    secondaryConnection!!.disconnect()
                } catch (e: XMPPException) {
                    logger.error("GCM Connection could not be established!")
                }

                primaryConnecting = false
            }
            primaryConnection != null && primaryConnection!!.isConnected ->
                logger.debug("Primary: " + primaryConnection!!.isConnected + ", Sec is null")
            primaryConnection != null && !primaryConnection!!.isConnected &&
                    secondaryConnection != null && !secondaryConnection!!.isConnected ||
                    primaryConnection == null && secondaryConnection == null ->
                when {
                    primaryConnecting || secondaryConnecting ->
                        logger.info("${if (!primaryConnecting) "Secondary" else "Primary"} already attempting connection...")
                    else -> {
                        logger.info("No connection, reconnecting...")

                        try {
                            primaryConnecting = true
                            primaryIsDraining = false
                            primaryConnection = connect()
                            addPacketListener(primaryConnection!!)
                            auth(primaryConnection!!)
                        } catch (e: XMPPException) {
                            e.printStackTrace()

                            logger.error("GCM Connection could not be established!")
                        }

                        primaryConnecting = false
                    }
                }
            else -> logger.error("UNKNOWN STATE: $primaryConnection : $secondaryConnection")
        }
    }

    fun sendNotification(to: String, notification: FcmNotification): Boolean {
        val packageNameExtension = notification.packageNameExtension
        val appPackageName = if (packageNameExtension == "devApp")
            PACKAGE_NAME_BASE
        else
            "$PACKAGE_NAME_BASE.$packageNameExtension"

        messageSender!!.send(MessageSender.createCustomNotification(appPackageName, to, notification))

        logger.info("Passing notification to: $to")

        return true
    }

    private fun setConfiguration() {
        connectionConfiguration!!.isReconnectionAllowed = true
        connectionConfiguration!!.isRosterLoadedAtLogin = false
        connectionConfiguration!!.setSendPresence(false)
        connectionConfiguration!!.socketFactory = SSLSocketFactory.getDefault()

        ProviderManager.getInstance().addExtensionProvider(GCM_ELEMENT_NAME, GCM_NAMESPACE,
                PacketExtensionProvider {
                    val json = it.nextText()
                    FcmPacketExtension(json)
                })
    }

    @Throws(XMPPException::class)
    private fun connect(): Connection {
        logger.info("Connecting to GCM...")

        val connection = XMPPConnection(connectionConfiguration)
        connection.connect()

        logger.info("Adding connectionlistener...")

        connection.addConnectionListener(object : ConnectionListener {
            override fun reconnectionSuccessful() {
                logger.info("Reconnected!")
            }

            override fun reconnectionFailed(e: Exception) {
                logger.info("Reconnection failed: $e")
            }

            override fun reconnectingIn(seconds: Int) {
                logger.info(String.format("Reconnecting in %d secs", seconds))
            }

            override fun connectionClosedOnError(e: Exception) {
                logger.info("Connection closed on error: $e")

                if (!primaryConnection!!.isConnected) {
                    primaryIsDraining = false
                }
            }

            override fun connectionClosed() {
                logger.info("Connection closed")
            }
        })

        return connection
    }

    @Throws(XMPPException::class)
    private fun auth(connection: Connection) {
        logger.info("Authenticating to GCM...")

        connection.login(GCM_SENDER_ID!! + "@gcm.googleapis.com", GCM_API_KEY)
    }

    private fun addPacketListener(connection: Connection) {
        logger.info("Adding packetlistener and packetinterceptor...")

        connection.addPacketListener(XMPPPacketListener(
                this, redisClient!!, dataMessageHandler, registrationService, GCM_SENDER_ID, GCM_API_KEY),
                PacketTypeFilter(Message::class.java))

        connection.addPacketInterceptor(PacketInterceptor {
            logger.info("Sent: " + it.toXML().replace("&quot;".toRegex(), "'"))
        }, PacketTypeFilter(Message::class.java))
    }

    fun setDraining() {
        try {
            if (secondaryConnection == null || !secondaryConnection!!.isConnected) {
                secondaryConnecting = true

                for (i in 0..9) {
                    secondaryConnection = connect()
                    addPacketListener(secondaryConnection!!)
                    auth(secondaryConnection!!)

                    if (secondaryConnection!!.isConnected) {
                        break
                    } else {
                        secondaryConnection!!.disconnect()
                    }
                }

                secondaryConnecting = false
            }

            primaryIsDraining = true
        } catch (e: XMPPException) {
            logger.error("Could not connect secondary on draining!")
        }
    }

    companion object {
        private const val GCM_ENDPOINT = "fcm-xmpp.googleapis.com"
        const val GCM_HTTP_ENDPOINT = "https://fcm.googleapis.com/fcm/send"
        private const val GCM_DEVICE_GROUP_BASE = "android.googleapis.com"
        private const val GCM_DEVICE_GROUP_HTTP_ENDPOINT = "/gcm/notification"
        const val GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE =
                "https://$GCM_DEVICE_GROUP_BASE$GCM_DEVICE_GROUP_HTTP_ENDPOINT"
    }
}
