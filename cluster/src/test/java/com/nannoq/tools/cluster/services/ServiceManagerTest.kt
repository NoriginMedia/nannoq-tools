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

package com.nannoq.tools.cluster.services

import com.nannoq.tools.cluster.apis.APIHostProducer
import com.nannoq.tools.cluster.apis.APIManager
import com.nannoq.tools.cluster.service.HeartBeatServiceImpl
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.http.HttpClient
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.unit.Async
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

import java.util.stream.IntStream

/**
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@RunWith(VertxUnitRunner::class)
class ServiceManagerTest {

    @Rule
    @JvmField
    val rule = RunTestOnContext()

    @Rule
    @JvmField
    val name = TestName()

    val apiManager: APIManager
        get() = APIManager(rule.vertx(), JsonObject()
                .put("publicHost", "localhost")
                .put("privateHost", "localhost"),
                object : APIHostProducer {
                    override fun getInternalHost(name: String): String {
                        return "localhost"
                    }

                    override fun getExternalHost(name: String): String {
                        return "localhost"
                    }
                })

    @Before
    @Throws(Exception::class)
    fun setUp(testContext: TestContext) {
        logger.info("Setup: " + name.methodName)
    }

    @After
    @Throws(Exception::class)
    fun tearDown(testContext: TestContext) {
        logger.info("Teardown " + name.methodName)
    }

    @Test
    @Throws(Exception::class)
    fun publishApi(testContext: TestContext) {
        ServiceManager.getInstance(rule.vertx()).publishApi(apiManager.createExternalApiRecord("SOME_API", "/api"))
        ServiceManager.getInstance(rule.vertx()).consumeApi("SOME_API", testContext.asyncAssertSuccess<HttpClient>())
    }

    @Test
    @Throws(Exception::class)
    fun unPublishApi(testContext: TestContext) {
        val async = testContext.async()

        ServiceManager.getInstance(rule.vertx()).publishApi(apiManager.createExternalApiRecord("SOME_API", "/api"), Handler {
            ServiceManager.getInstance(rule.vertx()).consumeApi("SOME_API", testContext.asyncAssertSuccess<HttpClient>())
            ServiceManager.getInstance(rule.vertx()).unPublishApi(it.result(), testContext.asyncAssertSuccess())
            ServiceManager.getInstance(rule.vertx()).consumeApi("SOME_API", testContext.asyncAssertFailure<HttpClient>())

            async.complete()
        })
    }

    @Test
    @Throws(Exception::class)
    fun consumeApi(testContext: TestContext) {
        ServiceManager.getInstance(rule.vertx()).publishApi(apiManager.createExternalApiRecord("SOME_API", "/api"))

        IntStream.range(0, 100).parallel().forEach { i ->
            val async = testContext.async()

            ServiceManager.getInstance(rule.vertx()).consumeApi("SOME_API", Handler { apiRes ->
                if (apiRes.failed()) {
                    testContext.fail(apiRes.cause())
                } else {
                    async.complete()
                }
            })
        }
    }

    @Test
    @Throws(Exception::class)
    fun publishService(testContext: TestContext) {
        ServiceManager.getInstance(rule.vertx()).publishService(HeartbeatService::class.java, HeartBeatServiceImpl())
        ServiceManager.getInstance(rule.vertx()).publishService(HeartbeatService::class.java, "SOME_ADDRESS", HeartBeatServiceImpl())
        ServiceManager.getInstance(rule.vertx()).consumeService(HeartbeatService::class.java, testContext.asyncAssertSuccess<HeartbeatService>())
        ServiceManager.getInstance(rule.vertx()).consumeService(HeartbeatService::class.java, "SOME_ADDRESS", testContext.asyncAssertSuccess<HeartbeatService>())
    }

    @Test
    @Throws(Exception::class)
    fun unPublishService(testContext: TestContext) {
        val async = testContext.async()

        ServiceManager.getInstance(rule.vertx()).publishService(HeartbeatService::class.java, HeartBeatServiceImpl(), Handler { record ->
            ServiceManager.getInstance(rule.vertx()).consumeService(HeartbeatService::class.java, Handler {
                ServiceManager.getInstance(rule.vertx()).unPublishService(HeartbeatService::class.java, record.result(), Handler {
                    ServiceManager.getInstance(rule.vertx()).consumeService(HeartbeatService::class.java, Handler { async.complete() })
                })
            })
        })
    }

    @Test
    @Throws(Exception::class)
    fun consumeService(testContext: TestContext) {
        ServiceManager.getInstance(rule.vertx()).publishService(HeartbeatService::class.java, HeartBeatServiceImpl())
        ServiceManager.getInstance(rule.vertx()).publishService(HeartbeatService::class.java, "SOME_ADDRESS", HeartBeatServiceImpl())

        IntStream.range(0, 100).parallel().forEach {
            val async = testContext.async()
            val secondAsync = testContext.async()

            ServiceManager.getInstance(rule.vertx()).consumeService(HeartbeatService::class.java, Handler { checkService(testContext, async, it) })
            ServiceManager.getInstance(rule.vertx()).consumeService(HeartbeatService::class.java, "SOME_ADDRESS", Handler { res -> checkService(testContext, secondAsync, res) })
        }
    }

    fun checkService(testContext: TestContext, async: Async, res: AsyncResult<HeartbeatService>) {
        if (res.failed()) {
            testContext.fail(res.cause())
        } else {
            res.result().ping(Handler {
                if (it.failed()) {
                    testContext.fail(it.cause())
                } else {
                    async.complete()
                }
            })
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceManagerTest::class.java.simpleName)
    }
}