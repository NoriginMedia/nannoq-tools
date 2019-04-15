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
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.util.stream.IntStream

/**
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(VertxExtension::class)
class ServiceManagerTest {

    @Test
    @Throws(Exception::class)
    fun publishApi(vertx: Vertx, testContext: VertxTestContext) {
        ServiceManager.getInstance(vertx).publishApi(getAPIManager(vertx).createExternalApiRecord("SOME_API", "/api"))
        ServiceManager.getInstance(vertx).consumeApi("SOME_API", testContext.completing())
    }

    @Test
    @Throws(Exception::class)
    fun unPublishApi(vertx: Vertx, testContext: VertxTestContext) {
        val checkpoint = testContext.checkpoint(3)

        ServiceManager.getInstance(vertx).publishApi(getAPIManager(vertx).createExternalApiRecord("SOME_API", "/api"), Handler { res ->
            ServiceManager.getInstance(vertx).consumeApi("SOME_API", Handler { if (it.succeeded()) checkpoint.flag() })
            ServiceManager.getInstance(vertx).unPublishApi(res.result(), Handler { if (it.succeeded()) checkpoint.flag() })
            ServiceManager.getInstance(vertx).consumeApi("SOME_API", Handler { if (it.succeeded()) checkpoint.flag() })

            testContext.completeNow()
        })
    }

    @Test
    @Throws(Exception::class)
    fun consumeApi(vertx: Vertx, testContext: VertxTestContext) {
        ServiceManager.getInstance(vertx).publishApi(getAPIManager(vertx).createExternalApiRecord("SOME_API", "/api"))

        IntStream.range(0, 100).parallel().forEach {
            ServiceManager.getInstance(vertx).consumeApi("SOME_API", Handler { apiRes ->
                when {
                    apiRes.failed() -> testContext.failNow(apiRes.cause())
                    else -> testContext.completeNow()
                }
            })
        }
    }

    @Test
    @Throws(Exception::class)
    fun publishService(vertx: Vertx, testContext: VertxTestContext) {
        val checkpoint = testContext.checkpoint(2)

        ServiceManager.getInstance(vertx).publishService(HeartbeatService::class.java, HeartBeatServiceImpl())
        ServiceManager.getInstance(vertx).publishService(HeartbeatService::class.java, "SOME_ADDRESS", HeartBeatServiceImpl())
        ServiceManager.getInstance(vertx).consumeService(HeartbeatService::class.java, Handler { if (it.succeeded()) checkpoint.flag() })
        ServiceManager.getInstance(vertx).consumeService(HeartbeatService::class.java, "SOME_ADDRESS", Handler { if (it.succeeded()) checkpoint.flag() })
    }

    @Test
    @Throws(Exception::class)
    fun unPublishService(vertx: Vertx, testContext: VertxTestContext) {
        ServiceManager.getInstance(vertx).publishService(HeartbeatService::class.java, HeartBeatServiceImpl(), Handler { record ->
            ServiceManager.getInstance(vertx).consumeService(HeartbeatService::class.java, Handler {
                ServiceManager.getInstance(vertx).unPublishService(HeartbeatService::class.java, record.result(), Handler {
                    ServiceManager.getInstance(vertx).consumeService(HeartbeatService::class.java, Handler { testContext.completeNow() })
                })
            })
        })
    }

    @Test
    @Throws(Exception::class)
    fun consumeService(vertx: Vertx, testContext: VertxTestContext) {
        ServiceManager.getInstance(vertx).publishService(HeartbeatService::class.java, HeartBeatServiceImpl())
        ServiceManager.getInstance(vertx).publishService(HeartbeatService::class.java, "SOME_ADDRESS", HeartBeatServiceImpl())

        IntStream.range(0, 100).parallel().forEach {
            ServiceManager.getInstance(vertx).consumeService(HeartbeatService::class.java, Handler { checkService(testContext, it) })
            ServiceManager.getInstance(vertx).consumeService(HeartbeatService::class.java, "SOME_ADDRESS", Handler { res -> checkService(testContext, res) })
        }
    }

    private fun checkService(testContext: VertxTestContext, res: AsyncResult<HeartbeatService>) {
        if (res.failed()) {
            testContext.failNow(res.cause())
        } else {
            res.result().ping(Handler {
                if (it.failed()) {
                    testContext.failNow(it.cause())
                } else {
                    testContext.completeNow()
                }
            })
        }
    }

    private fun getAPIManager(vertx: Vertx) = APIManager(vertx, JsonObject()
            .put("publicHost", "localhost")
            .put("privateHost", "localhost"), object : APIHostProducer {
                override fun getInternalHost(name: String): String {
                    return "localhost"
                }

                override fun getExternalHost(name: String): String {
                    return "localhost"
                }
            })

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceManagerTest::class.java.simpleName)
    }
}