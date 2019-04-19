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
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import redis.embedded.RedisServer
import java.io.File
import java.io.FileReader
import java.util.Properties

/**
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
@Suppress("unused")
@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(VertxExtension::class)
class CheckFCMOnlineIT : ConfigSupport {
    private var redisServer: RedisServer? = null
    private var fcmServer: FcmServer? = null
    private var defaultDataMessageHandler: DefaultDataMessageHandler? = null

    @BeforeEach
    @Throws(Exception::class)
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        val redisPort = findFreePort()
        redisServer = RedisServer(redisPort)
        redisServer!!.start()

        defaultDataMessageHandler = DefaultDataMessageHandler()
        fcmServer = FcmCreator.createFcm(defaultDataMessageHandler!!)

        vertx.deployVerticle(fcmServer, fcmConfig(redisPort)) { if (it.succeeded()) testContext.completeNow() }
    }

    private fun fcmConfig(redisPort: Int): DeploymentOptions {
        val p = Properties()
        p.load(FileReader(File(this::class.java.classLoader.getResource("fcm.properties").toURI())))

        return DeploymentOptions()
                .setConfig(JsonObject()
                        .put("basePackageNameFcm", p.getProperty("fcm.api.app"))
                        .put("gcmSenderId", p.getProperty("fcm.api.id"))
                        .put("gcmApiKey", p.getProperty("fcm.api.key"))
                        .put("redis_host", "localhost")
                        .put("redis_port", redisPort))
    }

    @Disabled // requires valid keys
    @Test
    fun fcmRunning() {
        assertThat(fcmServer!!.isOnline).isTrue()
    }

    @AfterEach
    @Throws(Exception::class)
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        redisServer!!.stop()
        vertx.undeploy(fcmServer!!.deploymentID())
        testContext.completeNow()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CheckFCMOnlineIT::class.java.simpleName)
    }
}
