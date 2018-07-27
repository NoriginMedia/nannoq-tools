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
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@RunWith(VertxUnitRunner::class)
class APIManagerTest {
    @Rule
    @JvmField
    val rule = RunTestOnContext()

    val apiManager: APIManager
        get() = getApiManager("localhost")

    @Test
    fun performRequestWithCircuitBreaker(testContext: TestContext) {
        ServiceManager.getInstance().publishApi(getApiManager("www.google.com").createExternalApiRecord("TEST", "/"))
        val async = testContext.async()

        ServiceManager.getInstance().consumeApi("TEST", Handler { res ->
            testContext.assertTrue(res.succeeded())

            res.result().get("/").handler({ resRes ->
                testContext.assertTrue(resRes.statusCode() == 200 || resRes.statusCode() == 302)
                async.complete()
            }).end()
        })
    }

    @Test
    fun createInternalApiRecord(testContext: TestContext) {
        val record = apiManager.createInternalApiRecord("TEST_API", "/api")

        testContext.assertEquals("TEST_API", record.name)
    }

    @Test
    fun createExternalApiRecord(testContext: TestContext) {
        val record = apiManager.createExternalApiRecord("TEST_API", "/api")

        testContext.assertEquals("TEST_API", record.name)
    }

    fun getApiManager(host: String): APIManager {
        return APIManager(rule.vertx(), JsonObject()
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