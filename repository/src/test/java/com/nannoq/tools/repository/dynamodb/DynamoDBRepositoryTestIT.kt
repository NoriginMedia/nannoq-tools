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

package com.nannoq.tools.repository.dynamodb

import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository.Companion.PAGINATION_INDEX
import com.nannoq.tools.repository.dynamodb.gen.TestModelReceiverImpl
import com.nannoq.tools.repository.dynamodb.gen.models.TestModel
import com.nannoq.tools.repository.dynamodb.utils.DynamoDBTestClass
import com.nannoq.tools.repository.repository.results.CreateResult
import com.nannoq.tools.repository.repository.results.ItemListResult
import com.nannoq.tools.repository.utils.*
import com.nannoq.tools.repository.utils.AggregateFunctions.MAX
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.unit.Async
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.stream.Collectors.toList
import java.util.stream.IntStream

/**
 * This class defines DynamoDBRepository class. It handles almost all cases of use with the DynamoDB of AWS.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@RunWith(VertxUnitRunner::class)
class DynamoDBRepositoryTestIT : DynamoDBTestClass() {
    @Suppress("UNCHECKED_CAST")
    private fun createXItems(context: TestContext, count: Int,
                             resultHandler: Handler<AsyncResult<List<CreateResult<TestModel>>>>) {
        val items = ArrayList<TestModel>()
        val futures = CopyOnWriteArrayList<Future<*>>()
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        IntStream.range(0, count).forEach {
            val testModel = nonNullTestModel()
            testModel.range = (UUID.randomUUID().toString())

            val startDate = LocalDate.of(1990, 1, 1)
            val endDate = LocalDate.now()
            val start = startDate.toEpochDay()
            val end = endDate.toEpochDay()

            val randomEpochDay = ThreadLocalRandom.current().longs(start, end).findAny().asLong

            testModel.setSomeDate(Date(randomEpochDay + 1000L))
            testModel.setSomeDateTwo(Date(randomEpochDay))
            testModel.setSomeLong(Random().nextLong())

            items.add(testModel)
        }

        items.forEach {
            val future = Future.future<CreateResult<TestModel>>()

            repo.create(it, future.completer())

            futures.add(future)

            try {
                Thread.sleep(10)
            } catch (ignored: InterruptedException) {
            }
        }

        CompositeFuture.all(futures).setHandler { res ->
            if (res.failed()) {
                resultHandler.handle(Future.failedFuture(res.cause()))
            } else {
                val collect = futures.stream()
                        .map({ it.result() })
                        .map { o -> o as CreateResult<TestModel> }
                        .collect(toList())

                resultHandler.handle(Future.succeededFuture(collect))
            }
        }
    }

    @Test
    fun getBucketName(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val config: JsonObject = context.get("${name.methodName}-config")
        
        assertEquals("BucketName does not match Config!", config.getString("content_bucket") ?: "default", repo.bucketName)
    }

    @Test
    fun stripGet() {
        assertEquals("stripGet does not strip correctly!", "someStringOne", DynamoDBRepository.stripGet("getSomeStringOne"))
    }

    @Test
    fun getField(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        
        assertNotNull("Field is null!", repo.getField("someStringOne"))
    }

    @Test(expected = UnknownError::class)
    fun getFieldFail(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        repo.getField("someBogusField")
    }

    @Test
    fun getFieldAsObject(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        assertNotNull("FieldAsObject is null!", repo.getFieldAsObject<Any, TestModel>("someStringOne", nonNullTestModel()))
    }

    @Test
    fun getFieldAsString(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        assertNotNull("FieldAsString is null!", repo.getField("someStringOne"))
        assertEquals(repo.getFieldAsString("someStringOne", nonNullTestModel()).javaClass, String::class.java)
    }

    @Test
    fun checkAndGetField(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val field = repo.checkAndGetField("someLong")

        assertNotNull("CheckAndGetField is null!", field)
        assertTrue("CheckAndGetField is not a long!", field.type == java.lang.Long::class.java)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkAndGetFieldNonIncrementable(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        assertNotNull("CheckAndGetField is null!", repo.checkAndGetField("someStringOne"))
    }

    @Test
    fun hasField(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        val declaredFields = TestModel::class.java.declaredFields

        assertTrue(repo.hasField(declaredFields, "someStringOne"))
        assertFalse(repo.hasField(declaredFields, "someBogusField"))
    }

    @Test
    fun getAlternativeIndexIdentifier(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        assertEquals("alternateIndex not correct!", "someDate", repo.getAlternativeIndexIdentifier(PAGINATION_INDEX))
    }

    @Test
    @Throws(ParseException::class)
    fun getIndexValue(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        val attributeValue = repo.getIndexValue("someDate", nonNullTestModel())
        val df1 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        val date = df1.parse(attributeValue.s)

        assertEquals("Not the same date!", testDate, date)
    }

    @Test
    fun createAttributeValue(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val attributeValueString = repo.createAttributeValue("someStringOne", "someTestString")

        assertEquals("Value not correct!", "someTestString", attributeValueString.s)

        val attributeValueLong = repo.createAttributeValue("someLong", "1000")

        assertEquals("Value not correct!", "1000", attributeValueLong.n)
    }

    @Test
    fun createAttributeValueWithComparison(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        val attributeValueGE = repo.createAttributeValue("someLong", "1000", ComparisonOperator.GE)
        val attributeValueLE = repo.createAttributeValue("someLong", "1000", ComparisonOperator.LE)

        assertEquals("Value not correct: ${Json.encodePrettily(attributeValueGE)}", "999", attributeValueGE.n)
        assertEquals("Value not correct: ${Json.encodePrettily(attributeValueLE)}", "1001", attributeValueLE.n)
    }

    @Test
    fun fetchNewestRecord(context: TestContext) {
        val async = context.async()
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        
        repo.create(nonNullTestModel()).setHandler { res ->
            val testModel = res.result().item
            val newest = repo.fetchNewestRecord(TestModel::class.java, testModel.hash!!, testModel.range)

            context.assertNotNull(newest)
            context.assertTrue(testModel == newest,
                    "Original: " + testModel.toJsonFormat().encodePrettily() +
                            ", Newest: " + newest!!.toJsonFormat().encodePrettily())

            async.complete()
        }
    }

    @Test
    fun buildExpectedAttributeValue(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val exists = repo.buildExpectedAttributeValue("someStringOne", true)
        val notExists = repo.buildExpectedAttributeValue("someStringOne", false)

        assertTrue(exists.isExists!!)
        assertFalse(notExists.isExists!!)

        assertEquals("someStringOne", exists.value.s)
        assertNull(notExists.value)
    }

    @Test
    fun incrementField(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        repo.create(nonNullTestModel()).setHandler { res ->
            val item = res.result().item
            val count = item.getSomeLong()!!

            repo.update(item, repo.incrementField(item, "someLong"), Handler { updateRes ->
                if (updateRes.failed()) {
                    context.fail(updateRes.cause())
                } else {
                    val updatedItem = updateRes.result().item

                    context.assertNotEquals(count, updatedItem.getSomeLong())
                    context.assertTrue(updatedItem.getSomeLong() == count + 1)
                }

                async.complete()
            })
        }
    }

    @Test
    fun decrementField(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        repo.create(nonNullTestModel()).setHandler { res ->
            val item = res.result().item
            val count = item.getSomeLong()!!

            repo.update(item, repo.decrementField(item, "someLong"), Handler {
                if (it.failed()) {
                    context.fail(it.cause())
                } else {
                    val updatedItem = it.result().item

                    context.assertNotEquals(count, updatedItem.getSomeLong())
                    context.assertTrue(updatedItem.getSomeLong() == count - 1)
                }

                async.complete()
            })
        }
    }

    @Test
    fun create(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        repo.create(nonNullTestModel(), Handler {
            context.assertTrue(it.succeeded())
            context.assertEquals(it.result().item.hash, nonNullTestModel().hash)
            context.assertEquals(it.result().item.range, nonNullTestModel().range)

            async.complete()
        })
    }

    @Test
    fun update(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        repo.create(nonNullTestModel(), Handler { createRes ->
            context.assertTrue(createRes.succeeded())
            context.assertEquals(createRes.result().item.hash, nonNullTestModel().hash)
            context.assertEquals(createRes.result().item.range, nonNullTestModel().range)

            val testDate = Date()
            repo.update(createRes.result().item, Function { it.setSomeDateTwo(testDate) }, Handler {
                context.assertTrue(it.succeeded())
                context.assertNotEquals(it.result().item, nonNullTestModel())
                context.assertEquals(testDate, it.result().item.getSomeDateTwo())

                async.complete()
            })
        })
    }

    @Test
    fun delete(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()
        val futureList = CopyOnWriteArrayList<Future<*>>()

        createXItems(context, 20, Handler {
            context.assertTrue(it.succeeded())

            it.result().stream().parallel().forEach({ cr ->
                val future = Future.future<Void>()
                val testModel = cr.item
                val id = JsonObject()
                        .put("hash", testModel.hash)
                        .put("range", testModel.range)

                repo.delete(id, Handler {
                    context.assertTrue(it.succeeded())

                    repo.read(id, Handler { readRes ->
                        context.assertFalse(readRes.succeeded())

                        future.tryComplete()
                    })
                })

                futureList.add(future)
            })
        })

        CompositeFuture.all(futureList).setHandler {
            if (it.failed()) {
                context.fail(it.cause())
            } else {
                async.complete()
            }
        }
    }

    @Test
    fun read(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()
        val futureList = CopyOnWriteArrayList<Future<*>>()

        createXItems(context, 20, Handler { res ->
            context.assertTrue(res.succeeded())

            res.result().stream().parallel().forEach({ cr ->
                val future = Future.future<Void>()
                val testModel = cr.item
                val id = JsonObject()
                        .put("hash", testModel.hash)
                        .put("range", testModel.range)

                repo.read(id, Handler {
                    context.assertTrue(it.succeeded())

                    if (it.succeeded()) {
                        repo.read(id, Handler {
                            context.assertTrue(it.succeeded())
                            context.assertTrue(it.result().isCacheHit)

                            future.tryComplete()
                        })
                    } else {
                        future.tryComplete()
                    }
                })

                futureList.add(future)
            })
        })

        CompositeFuture.all(futureList).setHandler {
            if (it.failed()) {
                context.fail(it.cause())
            } else {
                async.complete()
            }
        }
    }

    @Test
    fun batchRead(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 20, Handler { res ->
            context.assertTrue(res.succeeded())

            val items = res.result().stream()
                    .map({ it.item })
                    .collect(toList<TestModel>())

            val id = items.stream()
                    .map({
                        JsonObject()
                                .put("hash", it.hash)
                                .put("range", it.range)
                    })
                    .collect(toList<JsonObject>())

            repo.batchRead(id, Handler { batchRead ->
                context.assertTrue(batchRead.succeeded())
                context.assertTrue(batchRead.result().size == res.result().size)

                batchRead.result().stream()
                        .map({ it.item })
                        .forEach({ context.assertTrue(items.contains(it)) })

                async.complete()
            })
        })
    }

    @Test
    fun readWithConsistencyAndProjections(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()
        val futureList = CopyOnWriteArrayList<Future<*>>()

        createXItems(context, 20, Handler { res ->
            context.assertTrue(res.succeeded())

            res.result().stream().parallel().forEach({ cr ->
                val future = Future.future<Void>()
                val testModel = cr.item
                val id = JsonObject()
                        .put("hash", testModel.hash)
                        .put("range", testModel.range)

                repo.read(id, false, arrayOf("someLong"), Handler { firstRead ->
                    context.assertTrue(firstRead.succeeded())

                    if (firstRead.succeeded()) {
                        repo.read(id, false, arrayOf("someLong"), Handler { secondRead ->
                            context.assertTrue(secondRead.succeeded())
                            context.assertTrue(secondRead.result().isCacheHit)

                            future.tryComplete()
                        })
                    } else {
                        future.tryComplete()
                    }
                })

                futureList.add(future)
            })
        })

        CompositeFuture.all(futureList).setHandler { res ->
            if (res.failed()) {
                context.fail(res.cause())
            } else {
                async.complete()
            }
        }
    }

    @Test
    fun readAll(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler { res ->
            context.assertTrue(res.succeeded())

            repo.readAll(Handler { allItemsRes ->
                context.assertTrue(allItemsRes.succeeded())
                context.assertTrue(allItemsRes.result().size == 100,
                        "Actual count: " + allItemsRes.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun readAllWithIdentifiersAndFilterParameters(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler { res ->
            context.assertTrue(res.succeeded())
            val idObject = JsonObject()
                    .put("hash", "testString")
            val fp = FilterParameter.builder("someStringThree")
                    .withEq("1")
                    .build()
            val fpList = ConcurrentHashMap<String, List<FilterParameter>>()
            fpList["someLong"] = listOf(fp)

            repo.readAll(idObject, fpList, Handler { allItemsRes ->
                context.assertTrue(allItemsRes.succeeded())
                context.assertTrue(allItemsRes.result().isEmpty(), "Actual: " + allItemsRes.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun readAllWithIdentifiersAndPageTokenAndQueryPackAndProjections(context: TestContext) {
        val async = context.async()

        createXItems(context, 100, Handler { res ->
            context.assertTrue(res.succeeded())
            val idObject = JsonObject()
                    .put("hash", "testString")

            pageAllResults(100, 0, idObject, null, null, context, async)
        })
    }

    @Test
    fun readAllWithPageTokenAndQueryPackAndProjections(context: TestContext) {
        val async = context.async()

        createXItems(context, 100, Handler { res ->
            context.assertTrue(res.succeeded())
            pageAllResults(100, 0, null, null, null, context, async)
        })
    }

    @Test
    fun readAllWithIdentifiersAndPageTokenAndQueryPackAndProjectionsAndGSI(context: TestContext) {
        val async = context.async()

        createXItems(context, 100, Handler { res ->
            context.assertTrue(res.succeeded())
            val idObject = JsonObject()
                    .put("hash", "testStringThree")

            pageAllResults(100, 0, idObject, null, "TEST_GSI", context, async)
        })
    }

    private fun pageAllResults(totalCount: Int, currentCount: Int,
                               idObject: JsonObject?, pageToken: String?, GSI: String?,
                               context: TestContext, async: Async) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val queryPack = QueryPack.builder(TestModel::class.java)
                .withPageToken(pageToken ?: "NoToken")
                .build()
        val handler = Handler<AsyncResult<ItemListResult<TestModel>>> { allItemsRes ->
            if (allItemsRes.succeeded()) {
                val finalPageToken = allItemsRes.result().itemList?.pageTokens
                val newCount = currentCount + allItemsRes.result().count

                if (finalPageToken!!.equals("END_OF_LIST", ignoreCase = true)) {
                    context.assertEquals(totalCount, newCount)

                    async.complete()
                } else {
                    pageAllResults(totalCount, newCount, idObject, finalPageToken, GSI, context, async)
                }
            } else {
                context.fail("All Items Result is false!")

                async.complete()
            }
        }

        if (idObject == null) {
            if (GSI == null) {
                repo.readAll(pageToken, queryPack, arrayOf(), handler)
            } else {
                context.fail("Must use an idobject with GSI")

                async.complete()
            }
        } else {
            if (GSI != null) {
                repo.readAll(idObject, pageToken, queryPack, arrayOf(), GSI, handler)
            } else {
                repo.readAll(idObject, pageToken, queryPack, arrayOf(), handler)
            }
        }
    }

    @Test
    fun aggregation(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            val idObject = JsonObject()
                    .put("hash", "testString")
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.aggregation(idObject, queryPack, arrayOf(), Handler { res ->
                val count = JsonObject(res.result()).getInteger("count")

                context.assertEquals(100, count, "Count is: " + count!!)

                async.complete()
            })
        })
    }

    @Test
    fun aggregationGroupedRanged(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            val idObject = JsonObject()
                    .put("hash", "testString")
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(MAX)
                            .withField("someLong")
                            .withGroupBy(listOf(GroupingConfiguration.builder()
                                    .withGroupBy("someLong")
                                    .withGroupByUnit("INTEGER")
                                    .withGroupByRange(10000)
                                    .build()))
                            .build())
                    .build()

            val etagOne = AtomicInteger()

            repo.aggregation(idObject, queryPack, arrayOf(), Handler { res ->
                etagOne.set(res.result().hashCode())

                repo.aggregation(idObject, queryPack, arrayOf(), Handler { secondRes ->
                    context.assertEquals(etagOne.get(), secondRes.result().hashCode())
                    async.complete()
                })
            })
        })
    }

    @Test
    fun aggregationWithGSI(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            val idObject = JsonObject()
                    .put("hash", "testStringThree")
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withCustomQuery("TEST_GSI")
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringThree")
                            .build())
                    .build()

            repo.aggregation(idObject, queryPack, arrayOf(), "TEST_GSI", Handler { res ->
                val results = JsonObject(res.result())
                val count = results.getInteger("count")

                context.assertEquals(100, count, "Count is: " + count!!)

                async.complete()
            })
        })
    }

    @Test
    fun buildParameters(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        async.complete()
    }

    @Test
    fun readAllWithoutPagination(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            repo.readAllWithoutPagination("testString", Handler { allItems ->
                context.assertTrue(allItems.succeeded())
                context.assertEquals(100, allItems.result().size, "Size incorrect: " + allItems.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithIdentifierAndQueryPack(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.readAllWithoutPagination("testString", queryPack, Handler { allItems ->
                context.assertTrue(allItems.succeeded())
                context.assertEquals(100, allItems.result().size, "Size incorrect: " + allItems.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithIdentifierAndQueryPackAndProjections(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.readAllWithoutPagination("testString", queryPack, arrayOf(), Handler { allItems ->
                context.assertTrue(allItems.succeeded())
                context.assertEquals(100, allItems.result().size, "Size incorrect: " + allItems.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithIdentifierAndQueryPackAndProjectionsAndGSI(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withCustomQuery("TEST_GSI")
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.readAllWithoutPagination("testStringThree", queryPack, arrayOf(), "TEST_GSI", Handler { allItems ->
                context.assertTrue(allItems.succeeded())
                context.assertEquals(100, allItems.result().size, "Size incorrect: " + allItems.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithQueryPackAndProjections(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.readAllWithoutPagination(queryPack, arrayOf(), Handler { allItems ->
                context.assertTrue(allItems.succeeded())
                context.assertEquals(100, allItems.result().size, "Size incorrect: " + allItems.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithQueryPackAndProjectionsAndGSI(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withCustomQuery("TEST_GSI")
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringThree")
                            .build())
                    .build()

            repo.readAllWithoutPagination(queryPack, arrayOf(), "TEST_GSI", Handler { allItems ->
                context.assertTrue(allItems.succeeded())
                context.assertEquals(100, allItems.result().size, "Size incorrect: " + allItems.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun readAllPaginated(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler {
            repo.readAllPaginated(Handler { allItems ->
                context.assertTrue(allItems.succeeded())
                context.assertEquals(100, allItems.result().size, "Size incorrect: " + allItems.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun remoteCreate(context: TestContext) {
        val service: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        service.remoteCreate(nonNullTestModel(), Handler { createRes ->
            context.assertTrue(createRes.succeeded())
            context.assertEquals(createRes.result().hash, nonNullTestModel().hash)
            context.assertEquals(createRes.result().range, nonNullTestModel().range)

            async.complete()
        })
    }

    @Test
    fun remoteRead(context: TestContext) {
        val service: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()
        val futureList = CopyOnWriteArrayList<Future<*>>()

        createXItems(context, 20, Handler { res ->
            context.assertTrue(res.succeeded())

            res.result().stream().parallel().forEach({ cr ->
                val future = Future.future<Void>()
                val testModel = cr.item
                val id = JsonObject()
                        .put("hash", testModel.hash)
                        .put("range", testModel.range)

                service.remoteRead(id, Handler { firstRead ->
                    context.assertTrue(firstRead.succeeded())

                    future.tryComplete()
                })

                futureList.add(future)
            })
        })

        CompositeFuture.all(futureList).setHandler { res ->
            if (res.failed()) {
                context.fail(res.cause())
            } else {
                async.complete()
            }
        }
    }

    @Test
    fun remoteIndex(context: TestContext) {
        val service: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        createXItems(context, 100, Handler { res ->
            context.assertTrue(res.succeeded())
            val idObject = JsonObject()
                    .put("hash", "testString")

            service.remoteIndex(idObject, Handler { allItemsRes ->
                context.assertTrue(allItemsRes.succeeded())
                context.assertTrue(allItemsRes.result().size == 100,
                        "Actual count: " + allItemsRes.result().size)

                async.complete()
            })
        })
    }

    @Test
    fun remoteUpdate(context: TestContext) {
        val service: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        service.remoteCreate(nonNullTestModel(), Handler { createRes ->
            context.assertTrue(createRes.succeeded())
            context.assertEquals(createRes.result().hash, nonNullTestModel().hash)
            context.assertEquals(createRes.result().range, nonNullTestModel().range)

            val testDate = Date()
            val result = createRes.result()

            service.remoteUpdate(result, Handler { updateRes ->
                context.assertTrue(updateRes.succeeded())
                context.assertEquals(updateRes.result().getSomeDateTwo()!!.toString(), testDate.toString())

                async.complete()
            })
        })
    }

    @Test
    fun remoteDelete(context: TestContext) {
        val service: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val async = context.async()

        service.remoteCreate(nonNullTestModel(), Handler { createRes ->
            context.assertTrue(createRes.succeeded())
            context.assertEquals(createRes.result().hash, nonNullTestModel().hash)
            context.assertEquals(createRes.result().range, nonNullTestModel().range)
            val id = JsonObject()
                    .put("hash", createRes.result().hash)
                    .put("range", createRes.result().range)

            service.remoteDelete(id, Handler { deleteRes ->
                context.assertTrue(deleteRes.succeeded())

                service.remoteRead(id, Handler { res ->
                    context.assertFalse(res.succeeded())

                    async.complete()
                })
            })
        })
    }

    @Test
    fun getModelName(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        assertEquals("TestModel", repo.modelName)
    }

    @Test
    fun createS3Link(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val test = DynamoDBRepository.createS3Link(repo.dynamoDbMapper, "someName", "/someBogusPath")

        assertNotNull(test)
        assertEquals("Path is not equal!", "/someBogusPath", test.key)
    }

    @Test
    fun createSignedUrl(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val test = DynamoDBRepository.createS3Link(repo.dynamoDbMapper, "someName", "/someBogusPath")
        val signedUrl = DynamoDBRepository.createSignedUrl(repo.dynamoDbMapper, test)

        assertNotNull("Url is null!", signedUrl)
        assertTrue("Url is not secure: $signedUrl", signedUrl.startsWith("https://s3"))
        assertTrue("Url is not secure: $signedUrl", signedUrl.contains("X-Amz-Algorithm"))
    }

    @Test
    fun createSignedUrlWithDays(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        val test = DynamoDBRepository.createS3Link(repo.dynamoDbMapper, "someName", "/someBogusPath")
        val signedUrl = DynamoDBRepository.createSignedUrl(repo.dynamoDbMapper, 7, test)

        assertNotNull("Url is null!", signedUrl)
        assertTrue("Url is not secure: $signedUrl", signedUrl.startsWith("https://s3"))
        assertTrue("Url is not secure: $signedUrl", signedUrl.contains("X-Amz-Algorithm"))
    }

    @Test
    fun buildEventbusProjections(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val array = JsonArray()
                .add("someStringOne")
                .add("someLong")

        repo.buildEventbusProjections(array)
    }

    @Test
    fun hasRangeKey(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        assertTrue(repo.hasRangeKey())
    }

    @Test
    fun getDynamoDbMapper(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")

        assertNotNull(repo.dynamoDbMapper)
    }

    @Test
    fun getRedisClient(context: TestContext) {
        val repo: TestModelReceiverImpl = context.get("${name.methodName}-repo")
        val config: JsonObject = context.get("${name.methodName}-config")
        val async = context.async()

        if (config.getString("redis_host") != null) {
            context.assertNotNull(repo.redisClient)
            repo.redisClient!!.info { async.complete() }
        } else {
            context.assertNull(repo.redisClient)

            async.complete()
        }
    }

    companion object {
        @Suppress("unused")
        private val logger = LoggerFactory.getLogger(DynamoDBRepositoryTestIT::class.java.simpleName)
    }
}