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
import com.nannoq.tools.repository.utils.AggregateFunction
import com.nannoq.tools.repository.utils.AggregateFunctions
import com.nannoq.tools.repository.utils.AggregateFunctions.MAX
import com.nannoq.tools.repository.utils.FilterParameter
import com.nannoq.tools.repository.utils.GroupingConfiguration
import com.nannoq.tools.repository.utils.QueryPack
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.stream.Collectors.toList
import java.util.stream.IntStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * This class defines DynamoDBRepository class. It handles almost all cases of use with the DynamoDB of AWS.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(VertxExtension::class)
class DynamoDBRepositoryTestIT : DynamoDBTestClass() {
    @Suppress("UNCHECKED_CAST")
    private fun createXItems(
        testInfo: TestInfo,
        count: Int,
        resultHandler: Handler<AsyncResult<List<CreateResult<TestModel>>>>
    ) {
        val items = ArrayList<TestModel>()
        val futures = CopyOnWriteArrayList<Future<*>>()
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

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

        items.forEach {
            val future = Future.future<CreateResult<TestModel>>()

            repo.create(it, future)

            futures.add(future)

            try {
                Thread.sleep(10)
            } catch (ignored: InterruptedException) {}
        }

        CompositeFuture.all(futures).setHandler { res ->
            if (res.failed()) {
                resultHandler.handle(Future.failedFuture(res.cause()))
            } else {
                val collect = futures.stream()
                        .map { it.result() }
                        .map { o -> o as CreateResult<TestModel> }
                        .collect(toList())

                Thread.sleep(5000) // compensate for eventual consistency in testing

                resultHandler.handle(Future.succeededFuture(collect))
            }
        }
    }

    @Test
    fun getBucketName(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val config: JsonObject = contextObjects["${testInfo.testMethod.get().name}-config"] as JsonObject

        context.verify {
            assertThat(config.getString("content_bucket") ?: "default").isEqualTo(repo.bucketName)

            context.completeNow()
        }
    }

    @Test
    fun getField(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.verify {
            assertThat(repo.getField("someStringOne")).isNotNull

            context.completeNow()
        }
    }

    @Test
    fun getFieldFail(testInfo: TestInfo, context: VertxTestContext) {
        context.verify {
            assertThrows<UnknownError> {
                val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

                repo.getField("someBogusField")
            }

            context.completeNow()
        }
    }

    @Test
    fun getFieldAsObject(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.verify {
            assertThat(repo.getFieldAsObject<Any, TestModel>("someStringOne", nonNullTestModel())).isNotNull

            context.completeNow()
        }
    }

    @Test
    fun getFieldAsString(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.verify {
            assertThat(repo.getField("someStringOne")).isNotNull
            assertThat(repo.getFieldAsString("someStringOne", nonNullTestModel()).javaClass).isEqualTo(String::class.java)

            context.completeNow()
        }
    }

    @Test
    fun checkAndGetField(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val field = repo.checkAndGetField("someLong")

        context.verify {
            assertThat(field).isNotNull.describedAs("CheckAndGetField is null!")
            assertThat(field.type == java.lang.Long::class.java).describedAs("CheckAndGetField is not a long!").isTrue()

            context.completeNow()
        }
    }

    @Test
    fun checkAndGetFieldNonIncrementable(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.verify {
            assertThrows<IllegalArgumentException> {

                assertThat(repo.checkAndGetField("someStringOne")).isNotNull.describedAs("CheckAndGetField is null!")
            }

            context.completeNow()
        }
    }

    @Test
    fun hasField(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val declaredFields = TestModel::class.java.declaredFields

        context.verify {
            assertThat(repo.hasField(declaredFields, "someStringOne")).isTrue()
            assertThat(repo.hasField(declaredFields, "someBogusField")).isFalse()

            context.completeNow()
        }
    }

    @Test
    fun getAlternativeIndexIdentifier(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.verify {
            assertThat(repo.getAlternativeIndexIdentifier(PAGINATION_INDEX))
                    .describedAs("alternateIndex not correct!").isEqualTo("someDate")

            context.completeNow()
        }
    }

    @Test
    @Throws(ParseException::class)
    fun getIndexValue(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val attributeValue = repo.getIndexValue("someDate", nonNullTestModel())
        val df1 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        val date = df1.parse(attributeValue.s)

        context.verify {
            assertThat(date).describedAs("Not the same date!").isEqualTo(testDate)

            context.completeNow()
        }
    }

    @Test
    fun createAttributeValue(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val attributeValueString = repo.createAttributeValue("someStringOne", "someTestString")
        val attributeValueLong = repo.createAttributeValue("someLong", "1000")

        context.verify {
            assertThat(attributeValueString.s).describedAs("Value not correct!").isEqualTo("someTestString")
            assertThat(attributeValueLong.n).describedAs("Value not correct!").isEqualTo("1000")

            context.completeNow()
        }
    }

    @Test
    fun createAttributeValueWithComparison(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val attributeValueGE = repo.createAttributeValue("someLong", "1000", ComparisonOperator.GE)
        val attributeValueLE = repo.createAttributeValue("someLong", "1000", ComparisonOperator.LE)

        context.verify {
            assertThat(attributeValueGE.n)
                    .describedAs("Value not correct: ${Json.encodePrettily(attributeValueGE)}").isEqualTo("999")
            assertThat(attributeValueLE.n)
                    .describedAs("Value not correct: ${Json.encodePrettily(attributeValueLE)}").isEqualTo("1001")

            context.completeNow()
        }
    }

    @Test
    fun fetchNewestRecord(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        repo.create(nonNullTestModel()).setHandler { res ->
            val testModel = res.result().item
            val newest = repo.fetchNewestRecord(TestModel::class.java, testModel.hash!!, testModel.range)

            context.verify {
                assertThat(newest).isNotNull
                assertThat(testModel)
                        .describedAs("Original: " + testModel.toJsonFormat().encodePrettily() +
                                ", Newest: " + newest!!.toJsonFormat().encodePrettily())
                        .isEqualTo(newest)

                context.completeNow()
            }
        }
    }

    @Test
    fun buildExpectedAttributeValue(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val exists = repo.buildExpectedAttributeValue("someStringOne", true)
        val notExists = repo.buildExpectedAttributeValue("someStringOne", false)

        context.verify {
            assertThat(exists.isExists!!).isTrue()
            assertThat(notExists.isExists!!).isFalse()

            assertThat(exists.value.s).isEqualTo("someStringOne")
            assertThat(notExists.value).isNull()

            context.completeNow()
        }
    }

    @Test
    fun incrementField(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        repo.create(nonNullTestModel()).setHandler { res ->
            val item = res.result().item
            val count = item.someLong!!

            repo.update(item, repo.incrementField(item, "someLong"), Handler { updateRes ->
                context.verify {
                    if (updateRes.failed()) {
                        context.failNow(updateRes.cause())
                    } else {
                        val updatedItem = updateRes.result().item

                        assertThat(count).isNotEqualTo(updatedItem.someLong)
                        assertThat(updatedItem.someLong == count + 1).isTrue()
                    }

                    context.completeNow()
                }
            })
        }
    }

    @Test
    fun decrementField(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        repo.create(nonNullTestModel()).setHandler { res ->
            val item = res.result().item
            val count = item.someLong!!

            repo.update(item, repo.decrementField(item, "someLong"), Handler {
                context.verify {
                    if (it.failed()) {
                        context.failNow(it.cause())
                    } else {
                        val updatedItem = it.result().item

                        assertThat(count).isNotEqualTo(updatedItem.someLong)
                        assertThat(updatedItem.someLong == count - 1).isTrue()
                    }

                    context.completeNow()
                }
            })
        }
    }

    @Test
    fun create(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        repo.create(nonNullTestModel(), Handler {
            context.verify {
                assertThat(it.succeeded()).isTrue()
                assertThat(it.result().item.hash).isEqualTo(nonNullTestModel().hash)
                assertThat(nonNullTestModel().range).isEqualTo(it.result().item.range)

                context.completeNow()
            }
        })
    }

    @Test
    fun update(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        repo.create(nonNullTestModel(), Handler { createRes ->
            context.verify {
                assertThat(createRes.succeeded()).isTrue()
                assertThat(createRes.result().item.hash).isEqualTo(nonNullTestModel().hash)
                assertThat(nonNullTestModel().range).isEqualTo(createRes.result().item.range)
            }

            val testDate = Date()

            repo.update(createRes.result().item, Function {
                it.someDateTwo = testDate
                it
            }, Handler {
                context.verify {
                    assertThat(it.succeeded()).isTrue()
                    assertThat(it.result().item).isNotEqualTo(nonNullTestModel())
                    assertThat(it.result().item.someDateTwo).isEqualTo(testDate)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun delete(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val futureList = CopyOnWriteArrayList<Future<*>>()

        createXItems(testInfo, 20, Handler { result ->
            context.verify {
                assertThat(result.succeeded()).describedAs("Create failed").isTrue()
            }

            result.result().stream().parallel().forEach { cr ->
                val future = Future.future<Void>()
                val testModel = cr.item
                val id = JsonObject()
                        .put("hash", testModel.hash)
                        .put("range", testModel.range)

                repo.delete(id, Handler {
                    context.verify {
                        assertThat(it.succeeded()).describedAs("Delete failed").isTrue()
                    }

                    repo.read(id, Handler { readRes ->
                        context.verify {
                            assertThat(readRes.succeeded()).describedAs("Read succeeded").isFalse()
                        }

                        future.tryComplete()
                    })
                })

                futureList.add(future)
            }
        })

        CompositeFuture.all(futureList).setHandler {
            if (it.failed()) {
                context.failNow(it.cause())
            } else {
                context.completeNow()
            }
        }
    }

    @Test
    fun read(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val futureList = CopyOnWriteArrayList<Future<*>>()

        createXItems(testInfo, 20, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).describedAs("Create failed").isTrue()
            }

            res.result().stream().parallel().forEach { cr ->
                val future = Future.future<Void>()
                val testModel = cr.item
                val id = JsonObject()
                        .put("hash", testModel.hash)
                        .put("range", testModel.range)

                repo.read(id, Handler { result ->
                    context.verify {
                        assertThat(result.succeeded()).isTrue()
                    }

                    if (result.succeeded()) {
                        repo.read(id, Handler {
                            context.verify {
                                assertThat(it.succeeded()).isTrue()
                                assertThat(it.result().isCacheHit).isTrue()
                            }

                            future.tryComplete()
                        })
                    } else {
                        future.tryComplete()
                    }
                })

                futureList.add(future)
            }
        })

        CompositeFuture.all(futureList).setHandler {
            if (it.failed()) {
                context.failNow(it.cause())
            } else {
                context.completeNow()
            }
        }
    }

    @Test
    fun batchRead(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 20, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            val items = res.result().stream()
                    .map { it.item }
                    .collect(toList<TestModel>())

            val id = items.stream()
                    .map {
                        JsonObject()
                                .put("hash", it.hash)
                                .put("range", it.range)
                    }
                    .collect(toList<JsonObject>())

            repo.batchRead(id, Handler { batchRead ->
                context.verify {
                    assertThat(batchRead.succeeded()).isTrue()
                    assertThat(batchRead.result().size == res.result().size).isTrue()

                    batchRead.result().stream()
                            .map { it.item }
                            .forEach { assertThat(items).contains(it) }

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readWithConsistencyAndProjections(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val futureList = CopyOnWriteArrayList<Future<*>>()

        createXItems(testInfo, 20, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            res.result().stream().parallel().forEach { cr ->
                val future = Future.future<Void>()
                val testModel = cr.item
                val id = JsonObject()
                        .put("hash", testModel.hash)
                        .put("range", testModel.range)

                repo.read(id, false, arrayOf("someLong"), Handler { firstRead ->
                    context.verify {
                        assertThat(firstRead.succeeded()).isTrue()
                    }

                    if (firstRead.succeeded()) {
                        repo.read(id, false, arrayOf("someLong"), Handler { secondRead ->
                            context.verify {
                                assertThat(secondRead.succeeded()).isTrue()
                                assertThat(secondRead.result().isCacheHit).isTrue()
                            }

                            future.tryComplete()
                        })
                    } else {
                        future.tryComplete()
                    }
                })

                futureList.add(future)
            }
        })

        CompositeFuture.all(futureList).setHandler { res ->
            if (res.failed()) {
                context.failNow(res.cause())
            } else {
                context.completeNow()
            }
        }
    }

    @Test
    fun readAll(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            repo.readAll(Handler { allItemsRes ->
                context.verify {
                    assertThat(allItemsRes.succeeded()).isTrue()
                    assertThat(allItemsRes.result().isNotEmpty())
                            .describedAs("Actual: " + allItemsRes.result().size)
                            .isTrue()

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readAllWithIdentifiersAndFilterParameters(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            val idObject = JsonObject()
                    .put("hash", "testString")
            val fp = FilterParameter.builder("someStringThree")
                    .withEq("1")
                    .build()
            val fpList = ConcurrentHashMap<String, List<FilterParameter>>()
            fpList["someLong"] = listOf(fp)

            repo.readAll(idObject, fpList, Handler { allItemsRes ->
                context.verify {
                    assertThat(allItemsRes.succeeded()).isTrue()
                    assertThat(allItemsRes.result().isEmpty())
                            .describedAs("Actual: " + allItemsRes.result().size)
                            .isTrue()

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readAllWithIdentifiersAndPageTokenAndQueryPackAndProjections(testInfo: TestInfo, context: VertxTestContext) {
        createXItems(testInfo, 100, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            val idObject = JsonObject()
                    .put("hash", "testString")

            pageAllResults(100, 0, idObject, null, null, testInfo, context)
        })
    }

    @Test
    fun readAllWithPageTokenAndQueryPackAndProjections(testInfo: TestInfo, context: VertxTestContext) {
        createXItems(testInfo, 100, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            pageAllResults(100, 0, null, null, null, testInfo, context)
        })
    }

    @Test
    fun readAllWithIdentifiersAndPageTokenAndQueryPackAndProjectionsAndGSI(testInfo: TestInfo, context: VertxTestContext) {
        createXItems(testInfo, 100, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            val idObject = JsonObject()
                    .put("hash", "testStringThree")

            pageAllResults(100, 0, idObject, null, "TEST_GSI", testInfo, context)
        })
    }

    private fun pageAllResults(
        totalCount: Int,
        currentCount: Int,
        idObject: JsonObject?,
        pageToken: String?,
        GSI: String?,
        testInfo: TestInfo,
        context: VertxTestContext
    ) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val queryPack = QueryPack.builder(TestModel::class.java)
                .withPageToken(pageToken ?: "NoToken")
                .build()
        val handler = Handler<AsyncResult<ItemListResult<TestModel>>> { allItemsRes ->
            if (allItemsRes.succeeded()) {
                val finalPageToken = allItemsRes.result().itemList?.paging
                val newCount = currentCount + allItemsRes.result().count

                if (finalPageToken?.next!!.equals("END_OF_LIST", ignoreCase = true)) {
                    context.verify {
                        assertThat(newCount).isEqualTo(totalCount)

                        context.completeNow()
                    }
                } else {
                    pageAllResults(totalCount, newCount, idObject, finalPageToken.next, GSI, testInfo, context)
                }
            } else {
                context.failNow(allItemsRes.cause())

                context.completeNow()
            }
        }

        if (idObject == null) {
            if (GSI == null) {
                repo.readAll(pageToken, queryPack, arrayOf(), handler)
            } else {
                context.failNow(Throwable("Must use an idobject with GSI"))

                context.completeNow()
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
    fun aggregation(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
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

                context.verify {
                    assertThat(count).describedAs("Count is: " + count!!).isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun aggregationGroupedRanged(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
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
                    context.verify {
                        assertThat(secondRes.result().hashCode()).isEqualTo(etagOne.get())

                        context.completeNow()
                    }
                })
            })
        })
    }

    @Test
    fun aggregationWithGSI(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
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

                context.verify {
                    assertThat(count).describedAs("Count is: " + count!!).isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun buildParameters(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.completeNow()
    }

    @Test
    fun readAllWithoutPagination(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
            repo.readAllWithoutPagination("testString", Handler { allItems ->
                context.verify {
                    assertThat(allItems.succeeded()).isTrue()
                    assertThat(allItems.result().size)
                            .describedAs("Size incorrect: " + allItems.result().size)
                            .isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithIdentifierAndQueryPack(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.readAllWithoutPagination("testString", queryPack, Handler { allItems ->
                context.verify {
                    assertThat(allItems.succeeded()).isTrue()
                    assertThat(allItems.result().size)
                            .describedAs("Size incorrect: " + allItems.result().size)
                            .isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithIdentifierAndQueryPackAndProjections(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.readAllWithoutPagination("testString", queryPack, arrayOf(), Handler { allItems ->
                context.verify {
                    assertThat(allItems.succeeded()).isTrue()
                    assertThat(allItems.result().size)
                            .describedAs("Size incorrect: " + allItems.result().size)
                            .isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithIdentifierAndQueryPackAndProjectionsAndGSI(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withCustomQuery("TEST_GSI")
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.readAllWithoutPagination("testStringThree", queryPack, arrayOf(), "TEST_GSI", Handler { allItems ->
                context.verify {
                    assertThat(allItems.succeeded()).isTrue()
                    assertThat(allItems.result().size)
                            .describedAs("Size incorrect: " + allItems.result().size)
                            .isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithQueryPackAndProjections(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringOne")
                            .build())
                    .build()

            repo.readAllWithoutPagination(queryPack, arrayOf(), Handler { allItems ->
                context.verify {
                    assertThat(allItems.succeeded()).isTrue()
                    assertThat(allItems.result().size)
                            .describedAs("Size incorrect: " + allItems.result().size)
                            .isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readAllWithoutPaginationWithQueryPackAndProjectionsAndGSI(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
            val queryPack = QueryPack.builder(TestModel::class.java)
                    .withCustomQuery("TEST_GSI")
                    .withAggregateFunction(AggregateFunction.builder()
                            .withAggregateFunction(AggregateFunctions.COUNT)
                            .withField("someStringThree")
                            .build())
                    .build()

            repo.readAllWithoutPagination(queryPack, arrayOf(), "TEST_GSI", Handler { allItems ->
                context.verify {
                    assertThat(allItems.succeeded()).isTrue()
                    assertThat(allItems.result().size)
                            .describedAs("Size incorrect: " + allItems.result().size)
                            .isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun readAllPaginated(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler {
            repo.readAllPaginated(Handler { allItems ->
                context.verify {
                    assertThat(allItems.succeeded()).isTrue()
                    assertThat(allItems.result().size)
                            .describedAs("Size incorrect: " + allItems.result().size)
                            .isEqualTo(100)

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun remoteCreate(testInfo: TestInfo, context: VertxTestContext) {
        val service: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        service.remoteCreate(nonNullTestModel(), Handler { createRes ->
            context.verify {
                assertThat(createRes.succeeded()).isTrue()
                assertThat(nonNullTestModel().hash).isEqualTo(createRes.result().hash)
                assertThat(nonNullTestModel().range).isEqualTo(createRes.result().range)

                context.completeNow()
            }
        })
    }

    @Test
    fun remoteRead(testInfo: TestInfo, context: VertxTestContext) {
        val service: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val futureList = CopyOnWriteArrayList<Future<*>>()

        createXItems(testInfo, 20, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            res.result().stream().parallel().forEach { cr ->
                val future = Future.future<Void>()
                val testModel = cr.item
                val id = JsonObject()
                        .put("hash", testModel.hash)
                        .put("range", testModel.range)

                service.remoteRead(id, Handler { firstRead ->
                    context.verify {
                        assertThat(firstRead.succeeded()).isTrue()
                    }

                    future.tryComplete()
                })

                futureList.add(future)
            }
        })

        CompositeFuture.all(futureList).setHandler { res ->
            if (res.failed()) {
                context.failNow(res.cause())
            } else {
                context.completeNow()
            }
        }
    }

    @Test
    fun remoteIndex(testInfo: TestInfo, context: VertxTestContext) {
        val service: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        createXItems(testInfo, 100, Handler { res ->
            context.verify {
                assertThat(res.succeeded()).isTrue()
            }

            val idObject = JsonObject()
                    .put("hash", "testString")

            service.remoteIndex(idObject, Handler { allItemsRes ->
                context.verify {
                    assertThat(allItemsRes.succeeded()).isTrue()
                    assertThat(allItemsRes.result().size == 100)
                            .describedAs("Actual count: " + allItemsRes.result().size)
                            .isTrue()

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun remoteUpdate(testInfo: TestInfo, context: VertxTestContext) {
        val service: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        service.remoteCreate(nonNullTestModel(), Handler { createRes ->
            context.verify {
                assertThat(createRes.succeeded()).isTrue()
                assertThat(nonNullTestModel().hash).isEqualTo(createRes.result().hash)
                assertThat(nonNullTestModel().range).isEqualTo(createRes.result().range)
            }

            val testDate = Date()
            val result = createRes.result()
            result.someDateTwo = testDate

            service.remoteUpdate(result, Handler { updateRes ->
                if (updateRes.failed()) context.failNow(updateRes.cause())

                context.verify {
                    assertThat(updateRes.succeeded()).isTrue()
                    assertThat(testDate.toString()).isEqualTo(updateRes.result().someDateTwo!!.toString())

                    context.completeNow()
                }
            })
        })
    }

    @Test
    fun remoteDelete(testInfo: TestInfo, context: VertxTestContext) {
        val service: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        service.remoteCreate(nonNullTestModel(), Handler { createRes ->
            context.verify {
                assertThat(createRes.succeeded()).isTrue()
                assertThat(nonNullTestModel().hash).isEqualTo(createRes.result().hash)
                assertThat(nonNullTestModel().range).isEqualTo(createRes.result().range)
            }

            val id = JsonObject()
                    .put("hash", createRes.result().hash)
                    .put("range", createRes.result().range)

            service.remoteDelete(id, Handler { deleteRes ->
                context.verify {
                    assertThat(deleteRes.succeeded()).isTrue()
                }

                service.remoteRead(id, Handler { res ->
                    context.verify {
                        assertThat(res.succeeded()).isFalse()

                        context.completeNow()
                    }
                })
            })
        })
    }

    @Test
    fun getModelName(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.verify {
            assertThat(repo.modelName).isEqualTo("TestModel")

            context.completeNow()
        }
    }

    @Test
    fun createS3Link(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val test = DynamoDBRepository.createS3Link(repo.dynamoDbMapper, "someName", "/someBogusPath")

        context.verify {
            assertThat(test).isNotNull
            assertThat(test.key).describedAs("Path is not equal!").isEqualTo("/someBogusPath")

            context.completeNow()
        }
    }

    @Test
    fun createSignedUrl(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val test = DynamoDBRepository.createS3Link(repo.dynamoDbMapper, "someName", "/someBogusPath")
        val signedUrl = DynamoDBRepository.createSignedUrl(repo.dynamoDbMapper, test)

        context.verify {
            assertThat(signedUrl).isNotNull()
            assertThat(signedUrl.startsWith("https://s3")).describedAs("Url is not secure: $signedUrl").isTrue()
            assertThat(signedUrl.contains("X-Amz-Algorithm")).describedAs("Url is not secure: $signedUrl").isTrue()

            context.completeNow()
        }
    }

    @Test
    fun createSignedUrlWithDays(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        val test = DynamoDBRepository.createS3Link(repo.dynamoDbMapper, "someName", "/someBogusPath")
        val signedUrl = DynamoDBRepository.createSignedUrl(repo.dynamoDbMapper, 7, test)

        context.verify {
            assertThat(signedUrl).isNotNull()
            assertThat(signedUrl.startsWith("https://s3")).describedAs("Url is not secure: $signedUrl").isTrue()
            assertThat(signedUrl.contains("X-Amz-Algorithm")).describedAs("Url is not secure: $signedUrl").isTrue()

            context.completeNow()
        }
    }

    @Test
    fun buildEventbusProjections(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val array = JsonArray()
                .add("someStringOne")
                .add("someLong")

        repo.buildEventbusProjections(array)

        context.completeNow()
    }

    @Test
    fun hasRangeKey(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.verify {
            assertThat(repo.hasRangeKey()).isTrue()

            context.completeNow()
        }
    }

    @Test
    fun getDynamoDbMapper(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl

        context.verify {
            assertThat(repo.dynamoDbMapper).isNotNull

            context.completeNow()
        }
    }

    @Test
    fun getRedisClient(testInfo: TestInfo, context: VertxTestContext) {
        val repo: TestModelReceiverImpl = contextObjects["${testInfo.testMethod.get().name}-repo"] as TestModelReceiverImpl
        val config: JsonObject = contextObjects["${testInfo.testMethod.get().name}-config"] as JsonObject

        if (config.getString("redis_host") != null) {
            context.verify {
                assertThat(repo.redisClient).isNotNull
            }

            repo.redisClient!!.info { context.completeNow() }
        } else {
            context.verify {
                assertThat(repo.redisClient).isNull()

                context.completeNow()
            }
        }
    }

    companion object {
        @Suppress("unused")
        private val logger = LoggerFactory.getLogger(DynamoDBRepositoryTestIT::class.java.simpleName)
    }
}
