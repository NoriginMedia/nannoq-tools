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

package com.nannoq.tools.cluster.apis

import com.nannoq.tools.cluster.services.ServiceManager
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(VertxExtension::class)
class APIManagerTest {
    @Test
    fun performRequestWithCircuitBreaker(vertx: Vertx, testContext: VertxTestContext) {
        ServiceManager.getInstance().publishApi(getApiManager("www.google.com", vertx)
                .createExternalApiRecord("TEST", "/"))

        ServiceManager.getInstance().consumeApi("TEST", Handler { res ->
            testContext.verify {
                assertThat(res.succeeded()).isTrue()
            }

            res.result().get("/").handler {
                testContext.verify {
                    assertThat(it.statusCode() == 200 || it.statusCode() == 302).isTrue()
                    testContext.completeNow()
                }
            }.end()
        })
    }

    @Test
    fun createInternalApiRecord(vertx: Vertx, testContext: VertxTestContext) {
        val record = getApiManager("localhost", vertx).createInternalApiRecord("TEST_API", "/api")

        testContext.verify {
            assertThat("TEST_API").isEqualTo(record.name)
        }
    }

    @Test
    fun createExternalApiRecord(vertx: Vertx, testContext: VertxTestContext) {
        val record = getApiManager("localhost", vertx).createExternalApiRecord("TEST_API", "/api")

        testContext.verify {
            assertThat("TEST_API").isEqualTo(record.name)
        }
    }

    private fun getApiManager(host: String, vertx: Vertx): APIManager {
        return APIManager(vertx, JsonObject()
                .put("publicHost", host)
                .put("privateHost", host),
                object : APIHostProducer {
                    override fun getInternalHost(name: String): String {
                        return host
                    }

                    override fun getExternalHost(name: String): String {
                        return host
                    }
                })
    }
}
