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

package com.nannoq.tools.web.controllers

import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.repository.results.CreateResult
import com.nannoq.tools.repository.utils.PageTokens
import com.nannoq.tools.web.RoutingHelper.routeWithBodyAndLogger
import com.nannoq.tools.web.RoutingHelper.routeWithLogger
import com.nannoq.tools.web.controllers.gen.models.TestModel
import com.nannoq.tools.web.controllers.utils.DynamoDBTestClass
import io.restassured.RestAssured.given
import io.restassured.response.Response
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import junit.framework.TestCase.assertEquals
import org.apache.http.HttpHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertNotEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.LocalDate
import java.util.Date
import java.util.Random
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors.toList
import java.util.stream.IntStream

/**
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(VertxExtension::class)
class RestControllerImplTestIT : DynamoDBTestClass() {
    private var controller: RestControllerImpl<TestModel>? = null
    private var crossModelController: CrossModelAggregationController? = null

    @BeforeEach
    @Throws(Exception::class)
    fun setUp(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        @Suppress("UNCHECKED_CAST")
        val repo: DynamoDBRepository<TestModel> = contextObjects["${testInfo.testMethod.get().name}-repo"] as DynamoDBRepository<TestModel>
        val config: JsonObject = contextObjects["${testInfo.testMethod.get().name}-config"] as JsonObject
        val port: Int = contextObjects["${testInfo.testMethod.get().name}-http-port"] as Int

        controller = RestControllerImpl(vertx, TestModel::class.java, config, repo)
        crossModelController = CrossModelAggregationController({ repo }, arrayOf(TestModel::class.java))
        val router = createRouter(vertx, controller!!, crossModelController!!)

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, context.completing<HttpServer>())
    }

    private fun createRouter(
        vertx: Vertx,
        controller: RestControllerImpl<TestModel>,
        crossModelcontroller: CrossModelAggregationController
    ): Router {
        val router = Router.router(vertx)

        routeWithLogger(Supplier { router.get("/parent/:hash/testModels/:range") }, Consumer { route -> route.get().handler { controller.show(it) } })
        routeWithLogger(Supplier { router.get("/parent/:hash/testModels") }, Consumer { route -> route.get().handler { controller.index(it) } })
        routeWithBodyAndLogger(Supplier { router.post("/parent/:hash/testModels") }, Consumer { route -> route.get().handler { controller.create(it) } })
        routeWithBodyAndLogger(Supplier { router.put("/parent/:hash/testModels/:range") }, Consumer { route -> route.get().handler { controller.update(it) } })
        routeWithLogger(Supplier { router.delete("/parent/:hash/testModels/:range") }, Consumer { route -> route.get().handler { controller.destroy(it) } })
        routeWithLogger(Supplier { router.get("/aggregations") }, Consumer { route -> route.get().handler(crossModelcontroller) })

        return router
    }

    @AfterEach
    fun tearDown(context: VertxTestContext) {
        controller = null
        context.completeNow()
    }

    private fun createXItems(
        testInfo: TestInfo,
        count: Int,
        resultHandler: Handler<AsyncResult<List<CreateResult<TestModel>>>>
    ) {
        val items = ArrayList<TestModel>()
        val futures = CopyOnWriteArrayList<Future<*>>()
        @Suppress("UNCHECKED_CAST")
        val repo: DynamoDBRepository<TestModel> = contextObjects["${testInfo.testMethod.get().name}-repo"] as DynamoDBRepository<TestModel>

        IntStream.range(0, count).forEach {
            val testModel = nonNullTestModel()
            testModel.range = (UUID.randomUUID().toString())

            val startDate = LocalDate.of(1990, 1, 1)
            val endDate = LocalDate.now()
            val start = startDate.toEpochDay()
            val end = endDate.toEpochDay()

            val randomEpochDay = ThreadLocalRandom.current().longs(start, end).findAny().asLong

            testModel.someDate = Date(randomEpochDay + 1000L)
            testModel.someDateTwo = Date(randomEpochDay)
            testModel.someLong = Random().nextLong()

            items.add(testModel)
        }

        items.forEach { item ->
            val future = Future.future<CreateResult<TestModel>>()

            repo.create(item, future)

            futures.add(future)

            try {
                Thread.sleep(10)
            } catch (ignored: InterruptedException) {}
        }

        CompositeFuture.all(futures).setHandler { res ->
            if (res.failed()) {
                resultHandler.handle(Future.failedFuture(res.cause()))
            } else {
                @Suppress("UNCHECKED_CAST")
                val collect = futures.stream()
                        .map { it.result() }
                        .map { o -> o as CreateResult<TestModel> }
                        .collect(toList())

                Thread.sleep(2000) // compensate for eventual consistency in testing

                resultHandler.handle(Future.succeededFuture(collect))
            }
        }
    }

    @Test
    fun show(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        val port: Int = contextObjects["${testInfo.testMethod.get().name}-http-port"] as Int

        vertx.executeBlocking<Void>({
            context.verify {
                var response = createByRest(port, nonNullTestModel())
                val testModel = Json.decodeValue<TestModel>(response.asString(), TestModel::class.java)
                response = getResponse(port, testModel, null, 200)
                getResponse(port, testModel, response.header(HttpHeaders.ETAG), 304)
            }

            it.complete()
        }, false, {
            if (it.failed()) context.failNow(it.cause())

            context.completeNow()
        })
    }

    private fun getResponse(port: Int, testModel: TestModel, eTag: String?, statusCode: Int): Response {
        return given()
                .port(port)
                .header(HttpHeaders.IF_NONE_MATCH, eTag ?: "NoEtag")
            .`when`()
                .get("/parent/" + testModel.hash + "/testModels/" + testModel.range)
            .then()
                .statusCode(statusCode)
                    .extract()
                        .response()
    }

    @Test
    fun index(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        val port: Int = contextObjects["${testInfo.testMethod.get().name}-http-port"] as Int

        createXItems(testInfo, 25, Handler {
            vertx.executeBlocking<Any>({
                try {
                    context.verify {
                        val response = getIndex(port, null, null, 200)
                        getIndex(port, response.header(HttpHeaders.ETAG), null, 304)
                        getIndex(port, response.header(HttpHeaders.ETAG), null, 200, "?limit=50")
                        getPage(port, null, it)
                    }
                } catch (e: Exception) {
                    it.fail(e)
                }
            }, false, {
                if (it.failed()) {
                    context.failNow(it.cause())
                } else {
                    context.completeNow()
                }
            })
        })
    }

    private fun getPage(port: Int, pageToken: String?, async: Future<Any>) {
        val response = getIndex(port, null, pageToken, 200)
        val etag = response.header(HttpHeaders.ETAG)
        getIndex(port, etag, pageToken, 304)
        val newPageToken = PageTokens(response.jsonPath().getString("paging")).next

        logger.info("New Token is: $newPageToken")

        if (newPageToken == "END_OF_LIST") {
            async.complete()
        } else {
            getPage(port, newPageToken, async)
        }
    }

    private fun getIndex(port: Int, eTag: String?, pageToken: String?, statusCode: Int, query: String? = null): Response {
        val url = "/parent/testString/testModels" + (query ?: "") +
                if (pageToken != null) (if (query != null) "&" else "?") + "pageToken=" + pageToken else ""

        return given()
                .port(port)
                .urlEncodingEnabled(false)
                .header(HttpHeaders.IF_NONE_MATCH, eTag ?: "NoEtag")
            .`when`()
                .get(url)
            .then()
                .statusCode(statusCode)
                    .extract()
                        .response()
    }

    @Test
    fun aggregateIndex(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        val port: Int = contextObjects["${testInfo.testMethod.get().name}-http-port"] as Int

        createXItems(testInfo, 100, Handler {
            vertx.executeBlocking<Any>({
                try {
                    context.verify {
                        val query = "?aggregate=%7B%22function%22%3A%22MAX%22%2C%22field%22%3A%22someLong%22%2C%22groupBy%22%3A%5B%7B%22groupBy%22%3A%22someStringOne%22%7D%5D%7D"
                        val index = getIndex(port, null, null, 200, query)
                        val etag = index.header(HttpHeaders.ETAG)
                        assertThat(etag).isEqualTo(getIndex(port, null, null, 200, query).header(HttpHeaders.ETAG))
                        getIndex(port, etag, null, 304, query)

                        it.complete()
                    }
                } catch (e: Exception) {
                    it.fail(e)
                }
            }, false, {
                if (it.failed()) {
                    context.failNow(it.cause())
                } else {
                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun create(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        val port: Int = contextObjects["${testInfo.testMethod.get().name}-http-port"] as Int

        vertx.executeBlocking<Void>({
            context.verify {
                var response = createByRest(port, nonNullTestModel())
                val testModel = Json.decodeValue<TestModel>(response.asString(), TestModel::class.java)
                response = getResponse(port, testModel, null, 200)
                getResponse(port, testModel, response.header(HttpHeaders.ETAG), 304)

                it.complete()
            }
        }, false, {
            if (it.failed()) context.failNow(it.cause())

            context.completeNow()
        })
    }

    private fun createByRest(port: Int, testModel: TestModel): Response {
        return given()
                .port(port)
                .body(JsonObject()
                        .put("someDateTwo", testModel.someDateTwo!!.time).encode())
            .`when`()
                .post("/parent/testString/testModels")
            .then()
                .statusCode(201)
                    .extract()
                        .response()
    }

    @Test
    fun update(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        val port: Int = contextObjects["${testInfo.testMethod.get().name}-http-port"] as Int

        vertx.executeBlocking<Void>({
            context.verify {
                var response = createByRest(port, nonNullTestModel())
                var testModel = Json.decodeValue<TestModel>(response.asString(), TestModel::class.java)
                val oldEtag = testModel.etag
                response = getResponse(port, testModel, null, 200)
                assertEquals(oldEtag, response.header(HttpHeaders.ETAG))
                getResponse(port, testModel, response.header(HttpHeaders.ETAG), 304)

                testModel.someLong = 1L

                response = given()
                        .port(port)
                        .body(testModel.toJsonFormat().encode())
                        .`when`()
                        .put("/parent/" + testModel.hash + "/testModels/" + testModel.range)
                        .then()
                        .statusCode(200)
                        .extract()
                        .response()

                val updatedTestModel = Json.decodeValue<TestModel>(response.asString(), TestModel::class.java)
                response = getResponse(port, testModel, null, 200)
                testModel = Json.decodeValue<TestModel>(response.asString(), TestModel::class.java)
                assertNotEquals(oldEtag, response.header(HttpHeaders.ETAG))

                assertEquals(1L, testModel.someLong!!)
                assertEquals(1L, updatedTestModel.someLong!!)

                it.complete()
            }
        }, false, {
            if (it.failed()) context.failNow(it.cause())

            context.completeNow()
        })
    }

    @Test
    fun delete(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        val port: Int = contextObjects["${testInfo.testMethod.get().name}-http-port"] as Int

        vertx.executeBlocking<Void>({
            context.verify {
                var response = createByRest(port, nonNullTestModel())
                val testModel = Json.decodeValue<TestModel>(response.asString(), TestModel::class.java)
                response = getResponse(port, testModel, null, 200)
                getResponse(port, testModel, response.header(HttpHeaders.ETAG), 304)
                response = getIndex(port, null, null, 200)
                val etagRoot = response.header(HttpHeaders.ETAG)
                getIndex(port, response.header(HttpHeaders.ETAG), null, 304)
                val etagQuery = getIndex(port, response.header(HttpHeaders.ETAG), null, 200, "?limit=50").header(HttpHeaders.ETAG)
                destroyByRest(port, testModel)
                getResponse(port, testModel, testModel.etag, 404)
                getResponse(port, testModel, null, 404)
                getIndex(port, etagRoot, null, 304)
                getIndex(port, etagQuery, null, 304, "?limit=50")

                it.complete()
            }
        }, false, {
            if (it.failed()) context.failNow(it.cause())

            context.completeNow()
        })
    }

    private fun destroyByRest(port: Int, testModel: TestModel): Response {
        return given()
                .port(port)
            .`when`()
                .delete("/parent/" + testModel.hash + "/testModels/" + testModel.range)
            .then()
                .statusCode(204)
                    .extract()
                        .response()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RestControllerImplTestIT::class.java.simpleName)
    }
}
