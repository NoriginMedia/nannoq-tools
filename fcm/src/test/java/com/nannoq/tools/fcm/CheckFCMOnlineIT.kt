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
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import redis.embedded.RedisServer
import java.io.File
import java.io.FileReader
import java.util.*

/**
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
@Suppress("unused")
@RunWith(VertxUnitRunner::class)
class CheckFCMOnlineIT : ConfigSupport {
    private var redisServer: RedisServer? = null
    private var fcmServer: FcmServer? = null
    private var defaultDataMessageHandler: DefaultDataMessageHandler? = null

    @Rule
    @JvmField
    val rule = RunTestOnContext()

    @Before
    @Throws(Exception::class)
    fun setUp(testContext: TestContext) {
        val redisPort = findFreePort()
        redisServer = RedisServer(redisPort)
        redisServer!!.start()

        defaultDataMessageHandler = DefaultDataMessageHandler()
        fcmServer = FcmCreator.createFcm(defaultDataMessageHandler!!)

        rule.vertx().deployVerticle(fcmServer, fcmConfig(redisPort), testContext.asyncAssertSuccess())
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

    @Test
    fun fcmRunning() {
        assertTrue(fcmServer!!.isOnline)
    }

    @After
    @Throws(Exception::class)
    fun tearDown(testContext: TestContext) {
        redisServer!!.stop()
        rule.vertx().undeploy(fcmServer!!.deploymentID(), testContext.asyncAssertSuccess())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CheckFCMOnlineIT::class.java.simpleName)
    }
}
