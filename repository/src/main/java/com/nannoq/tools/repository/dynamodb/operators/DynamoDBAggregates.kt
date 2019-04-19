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

package com.nannoq.tools.repository.dynamodb.operators

import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.DynamoDBModel
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.models.ModelUtils
import com.nannoq.tools.repository.repository.cache.CacheManager
import com.nannoq.tools.repository.repository.etag.ETagManager
import com.nannoq.tools.repository.utils.AggregateFunction
import com.nannoq.tools.repository.utils.AggregateFunctions
import com.nannoq.tools.repository.utils.GroupingConfiguration
import com.nannoq.tools.repository.utils.QueryPack
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.time.Duration
import java.util.AbstractMap.SimpleEntry
import java.util.Arrays
import java.util.Comparator.comparing
import java.util.Comparator.comparingLong
import java.util.Date
import java.util.Objects
import java.util.function.BiFunction
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors.averagingDouble
import java.util.stream.Collectors.counting
import java.util.stream.Collectors.groupingBy
import java.util.stream.Collectors.summingDouble
import java.util.stream.Collectors.toList
import java.util.stream.Collectors.toMap
import java.util.stream.IntStream

/**
 * This class defines the aggregate operations for the DynamoDBRepository.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class DynamoDBAggregates<E>(
    private val TYPE: Class<E>,
    private val db: DynamoDBRepository<E>,
    private val HASH_IDENTIFIER: String,
    private val IDENTIFIER: String?,
    private val cacheManager: CacheManager<E>,
    private val eTagManager: ETagManager<E>?
)
        where E : ETagable, E : Cacheable, E : DynamoDBModel, E : Model {
    fun aggregation(
        identifiers: JsonObject,
        queryPack: QueryPack,
        projections: Array<String>,
        GSI: String?,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        if (logger.isDebugEnabled) {
            logger.debug("QueryPack is: " + Json.encodePrettily(queryPack) + ", projections: " +
                    Arrays.toString(projections) + ", ids: " + identifiers.encodePrettily())
        }

        when (queryPack.aggregateFunction!!.function) {
            AggregateFunctions.MIN -> findItemsWithMinOfField(identifiers, queryPack, projections, GSI, resultHandler)
            AggregateFunctions.MAX -> findItemsWithMaxOfField(identifiers, queryPack, projections, GSI, resultHandler)
            AggregateFunctions.AVG -> avgField(identifiers, queryPack, GSI, resultHandler)
            AggregateFunctions.SUM -> sumField(identifiers, queryPack, GSI, resultHandler)
            AggregateFunctions.COUNT -> countItems(identifiers, queryPack, GSI, resultHandler)
        }
    }

    private fun findItemsWithMinOfField(
        identifiers: JsonObject,
        queryPack: QueryPack,
        projections: Array<String>,
        GSI: String?,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        performMinOrMaxAggregation(identifiers, queryPack, "MIN",
                BiFunction { r, f -> getAllItemsWithLowestValue(r, f) }, projections, GSI, resultHandler)
    }

    private fun findItemsWithMaxOfField(
        identifiers: JsonObject,
        queryPack: QueryPack,
        projections: Array<String>,
        GSI: String?,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        performMinOrMaxAggregation(identifiers, queryPack, "MAX",
                BiFunction { r, f -> getAllItemsWithHighestValue(r, f) }, projections, GSI, resultHandler)
    }

    private fun doIdentifierBasedQuery(
        identifiers: JsonObject,
        queryPack: QueryPack,
        GSI: String??,
        res: Handler<AsyncResult<List<E>>>,
        projs: Array<Array<String>>
    ) {
        when {
            identifiers.isEmpty ->
                when {
                    GSI != null -> db.readAllWithoutPagination(queryPack, addIdentifiers(projs[0]), GSI, res)
                    else -> db.readAllWithoutPagination(queryPack, addIdentifiers(projs[0]), res)
                }
            else ->
                when {
                    GSI != null -> db.readAllWithoutPagination(identifiers.getString("hash"), queryPack, addIdentifiers(projs[0]), GSI, res)
                    else -> db.readAllWithoutPagination(identifiers.getString("hash"), queryPack, addIdentifiers(projs[0]), res)
                }
        }
    }

    private fun calculateGroupingPageToken(groupingParam: List<GroupingConfiguration>?, projs: Array<Array<String>>, finalProjections: Array<String>) {
        groupingParam?.stream()?.filter { param -> Arrays.stream(finalProjections).noneMatch { p -> p == param.groupBy } }?.forEach { groupByParam ->
            val newProjectionArray = arrayOfNulls<String>(finalProjections.size + 1)
            IntStream.range(0, finalProjections.size).forEach { i -> newProjectionArray[i] = finalProjections[i] }
            newProjectionArray[finalProjections.size] = groupByParam.groupBy
            projs[0] = newProjectionArray.requireNoNulls()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun calculateGroupings(aggregateFunction: AggregateFunction?, minItems: List<E>): JsonObject {
        return performGroupingAndSorting(minItems, aggregateFunction!!, BiFunction { items, groupingConfigurations ->
            if (groupingConfigurations.size > 3) throw IllegalArgumentException("GroupBy size of three is max!")
            val levelOne = groupingConfigurations[0]
            val levelTwo = if (groupingConfigurations.size > 1) groupingConfigurations[1] else null
            val levelThree = if (groupingConfigurations.size > 2) groupingConfigurations[2] else null

            when {
                levelTwo == null ->
                    items.parallelStream()
                        .collect(groupingBy<E, String> {
                            calculateGroupingKey(it, levelOne)
                        })
                levelThree == null ->
                    items.parallelStream()
                        .collect(groupingBy({
                            calculateGroupingKey<E>(it, levelOne)
                        }, groupingBy<E, String> {
                            calculateGroupingKey(it, levelTwo)
                        }))
                else ->
                    items.parallelStream()
                        .collect(groupingBy({
                            calculateGroupingKey<E>(it, levelOne)
                        }, groupingBy({
                            calculateGroupingKey<E>(it, levelTwo)
                        }, groupingBy<E, String> {
                            calculateGroupingKey(it, levelThree)
                        })))
            }
        })
    }

    private fun getAllItemsWithLowestValue(records: List<E>, field: String): List<E> {
        val result = ArrayList<E>()
        val min = ArrayList<E>()

        records.forEach {
            if (min.isEmpty() || db.extractValueAsDouble(db.checkAndGetField(field), it) <
                    db.extractValueAsDouble(db.checkAndGetField(field), min[0])) {
                min.add(it)
                result.clear()
                result.add(it)
            } else if (min.isNotEmpty() && db.extractValueAsDouble(db.checkAndGetField(field), it)
                            .compareTo(db.extractValueAsDouble(db.checkAndGetField(field), min[0])) == 0) {
                result.add(it)
            }
        }

        return result
    }

    private fun performMinOrMaxAggregation(
        identifiers: JsonObject,
        queryPack: QueryPack,
        command: String,
        valueExtractor: BiFunction<List<E>, String, List<E>>,
        projections: Array<String>?,
        GSI: String?,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        val hashCode = if (queryPack.aggregateFunction!!.groupBy == null)
            0
        else
            queryPack.aggregateFunction!!.groupBy!!.hashCode()
        val aggregateFunction = queryPack.aggregateFunction
        val field = aggregateFunction!!.field
        val newEtagKeyPostfix = "_" + field + "_" + command
        val etagKey = queryPack.baseEtagKey + newEtagKeyPostfix + hashCode
        val cacheKey = queryPack.baseEtagKey + newEtagKeyPostfix + hashCode
        val groupingParam = queryPack.aggregateFunction!!.groupBy

        cacheManager.checkAggregationCache(cacheKey, Handler { cacheRes ->
            when {
                cacheRes.failed() -> {
                    val res = Handler<AsyncResult<List<E>>> { allResult ->
                        when {
                            allResult.failed() -> resultHandler.handle(Future.failedFuture("Could not remoteRead all records..."))
                            else -> {
                                val records = allResult.result()

                                when {
                                    records.isEmpty() ->
                                        setEtagAndCacheAndReturnContent(etagKey, identifiers.encode().hashCode(), cacheKey,
                                                JsonObject().put("error", "Empty table!").encode(), resultHandler)
                                    else ->
                                        when {
                                            queryPack.aggregateFunction!!.hasGrouping() -> {
                                                val maxItems = getAllItemsWithHighestValue(allResult.result(), field!!)
                                                val aggregatedItems = calculateGroupings(aggregateFunction, maxItems)

                                                setEtagAndCacheAndReturnContent(etagKey, identifiers.encode().hashCode(), cacheKey, aggregatedItems.encode(), resultHandler)
                                            }
                                            else -> {
                                                val items = JsonArray()
                                                valueExtractor.apply(records, field!!).stream()
                                                        .map { o -> o.toJsonFormat() }
                                                        .forEach { items.add(it) }

                                                setEtagAndCacheAndReturnContent(etagKey, identifiers.encode().hashCode(), cacheKey, items.encode(), resultHandler)
                                            }
                                        }
                                }
                            }
                        }
                    }

                    val projs = if (projections == null) arrayOf(emptyArray()) else arrayOf(projections)
                    val finalProjections = projections ?: arrayOf()

                    calculateGroupingPageToken(groupingParam, projs, finalProjections)

                    val finalProjections2 = if (projs.isEmpty()) arrayOf() else projs[0]

                    if (field != null) {
                        if (Arrays.stream(finalProjections2).noneMatch { p -> p.equals(field, ignoreCase = true) }) {
                            val newProjectionArray = arrayOfNulls<String>(finalProjections2.size + 1)
                            IntStream.range(0, finalProjections2.size).forEach { i -> newProjectionArray[i] = finalProjections2[i] }
                            newProjectionArray[finalProjections2.size] = field
                            projs[0] = newProjectionArray.requireNoNulls()
                        }
                    }

                    if (logger.isDebugEnabled) {
                        logger.debug("Projections: " + Arrays.toString(projs[0]))
                    }

                    doIdentifierBasedQuery(identifiers, queryPack, GSI, res, projs)
                }
                else -> resultHandler.handle(Future.succeededFuture(cacheRes.result()))
            }
        })
    }

    private fun addIdentifiers(projections: Array<String>): Array<String> {
        val projs = addHashIdentifierToProjections(projections)

        return addRangeIdentifierToProjections(projs)
    }

    private fun addHashIdentifierToProjections(projections: Array<String>): Array<String> {
        return when {
            Arrays.stream(projections).noneMatch { p -> p.equals(HASH_IDENTIFIER, ignoreCase = true) } -> {
                val newProjectionArray = arrayOfNulls<String>(projections.size + 1)
                IntStream.range(0, projections.size).forEach { i -> newProjectionArray[i] = projections[i] }
                newProjectionArray[projections.size] = HASH_IDENTIFIER

                newProjectionArray.requireNoNulls()
            }
            else -> projections
        }
    }

    private fun addRangeIdentifierToProjections(projections: Array<String>): Array<String> {
        return when {
            IDENTIFIER != null && Arrays.stream(projections).noneMatch { p -> p != null && p.equals(IDENTIFIER, ignoreCase = true) } -> {
                val newProjectionArray = arrayOfNulls<String>(projections.size + 1)
                IntStream.range(0, projections.size).forEach { i -> newProjectionArray[i] = projections[i] }
                newProjectionArray[projections.size] = IDENTIFIER

                newProjectionArray.requireNoNulls()
            }
            else -> projections
        }
    }

    private fun getAllItemsWithHighestValue(records: List<E>, field: String): List<E> {
        val result = ArrayList<E>()
        val max = ArrayList<E>()

        records.forEach {
            when {
                max.isEmpty() || db.extractValueAsDouble(db.checkAndGetField(field), it) >
                        db.extractValueAsDouble(db.checkAndGetField(field), max[0]) -> {
                    max.add(it)
                    result.clear()
                    result.add(it)
                }
                max.isNotEmpty() && db.extractValueAsDouble(db.checkAndGetField(field), it)
                        .compareTo(db.extractValueAsDouble(db.checkAndGetField(field), max[0])) == 0 -> result.add(it)
            }
        }

        return result
    }

    private fun avgField(
        identifiers: JsonObject,
        queryPack: QueryPack,
        GSI: String?,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        val hashCode = if (queryPack.aggregateFunction!!.groupBy == null)
            0
        else
            queryPack.aggregateFunction!!.groupBy!!.hashCode()
        val aggregateFunction = queryPack.aggregateFunction
        val field = aggregateFunction!!.field
        val newEtagKeyPostfix = "_" + field + "_AVG"
        val etagKey = queryPack.baseEtagKey + newEtagKeyPostfix + hashCode
        val cacheKey = queryPack.baseEtagKey + newEtagKeyPostfix + hashCode
        val groupingParam = queryPack.aggregateFunction!!.groupBy

        cacheManager.checkAggregationCache(cacheKey, Handler { cacheRes ->
            when {
                cacheRes.failed() -> {
                    val res = Handler<AsyncResult<List<E>>> { allResult ->
                        when {
                            allResult.failed() -> resultHandler.handle(Future.failedFuture("Could not remoteRead all records..."))
                            else -> {
                                val records = allResult.result()

                                when {
                                    records.isEmpty() ->
                                        setEtagAndCacheAndReturnContent(etagKey, identifiers.encode().hashCode(), cacheKey,
                                                JsonObject().put("error", "Empty table!").encode(), resultHandler)
                                    else -> {
                                        val avg: JsonObject

                                        when {
                                            queryPack.aggregateFunction!!.hasGrouping() ->
                                                avg = avgGrouping(allResult.result(), aggregateFunction, field)
                                            else -> {
                                                avg = JsonObject()

                                                records.stream()
                                                        .mapToDouble { r -> db.extractValueAsDouble(db.checkAndGetField(field!!), r) }
                                                        .filter { Objects.nonNull(it) }
                                                        .average()
                                                        .ifPresent { value -> avg.put("avg", value) }

                                                if (avg.size() == 0) {
                                                    avg.put("avg", 0.0)
                                                }
                                            }
                                        }

                                        setEtagAndCacheAndReturnContent(etagKey, identifiers.encode().hashCode(), cacheKey, avg.encode(), resultHandler)
                                    }
                                }
                            }
                        }
                    }

                    val projections = arrayOf(arrayOf(field).requireNoNulls())
                    val finalProjections = projections[0]

                    calculateGroupingPageToken(groupingParam, projections, finalProjections)

                    doIdentifierBasedQueryNoIdentifierAddition(identifiers, queryPack, GSI, res, projections)
                }
                else -> resultHandler.handle(Future.succeededFuture(cacheRes.result()))
            }
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun avgGrouping(result: List<E>, aggregateFunction: AggregateFunction?, field: String?): JsonObject {
        return performGroupingAndSorting(result, aggregateFunction!!, BiFunction { items, groupingConfigurations ->
            if (groupingConfigurations.size > 3) throw IllegalArgumentException("GroupBy size of three is max!")
            val levelOne = groupingConfigurations[0]
            val levelTwo = if (groupingConfigurations.size > 1) groupingConfigurations[1] else null
            val levelThree = if (groupingConfigurations.size > 2) groupingConfigurations[2] else null

            when {
                levelTwo == null ->
                    items.parallelStream()
                            .collect(groupingBy({
                                calculateGroupingKey<E>(it, levelOne)
                            }, averagingDouble<E> {
                                db.extractValueAsDouble(db.checkAndGetField(field!!), it)
                            }))
                levelThree == null ->
                    items.parallelStream()
                            .collect(groupingBy({
                                calculateGroupingKey<E>(it, levelOne)
                            }, groupingBy({
                                calculateGroupingKey<E>(it, levelTwo)
                            }, averagingDouble<E> {
                                db.extractValueAsDouble(db.checkAndGetField(field!!), it)
                            })))
                else ->
                    items.parallelStream()
                            .collect(groupingBy({
                                calculateGroupingKey<E>(it, levelOne)
                            }, groupingBy({
                                calculateGroupingKey<E>(it, levelTwo)
                            }, groupingBy({
                                calculateGroupingKey<E>(it, levelThree)
                            }, summingDouble<E> {
                                db.extractValueAsDouble(db.checkAndGetField(field!!), it)
                            }))))
            }
        })
    }

    private fun doIdentifierBasedQueryNoIdentifierAddition(
        identifiers: JsonObject,
        queryPack: QueryPack,
        GSI: String?,
        res: Handler<AsyncResult<List<E>>>,
        projections: Array<String>
    ) {
        doIdentifierBasedQueryNoIdentifierAddition(identifiers, queryPack, GSI, res, arrayOf(projections))
    }

    private fun doIdentifierBasedQueryNoIdentifierAddition(
        identifiers: JsonObject,
        queryPack: QueryPack,
        GSI: String?,
        res: Handler<AsyncResult<List<E>>>,
        projections: Array<Array<String>>
    ) {
        when {
            identifiers.isEmpty ->
                when {
                    GSI != null -> db.readAllWithoutPagination(queryPack, projections[0], GSI, res)
                    else -> db.readAllWithoutPagination(queryPack, projections[0], res)
                }
            else ->
                when {
                    GSI != null -> db.readAllWithoutPagination(identifiers.getString("hash"), queryPack, projections[0], GSI, res)
                    else -> db.readAllWithoutPagination(identifiers.getString("hash"), queryPack, projections[0], res)
                }
        }
    }

    private fun sumField(
        identifiers: JsonObject,
        queryPack: QueryPack,
        GSI: String?,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        val hashCode = if (queryPack.aggregateFunction!!.groupBy == null)
            0
        else
            queryPack.aggregateFunction!!.groupBy!!.hashCode()
        val aggregateFunction = queryPack.aggregateFunction
        val field = aggregateFunction!!.field
        val newEtagKeyPostfix = "_" + field + "_SUM"
        val etagKey = queryPack.baseEtagKey + newEtagKeyPostfix + hashCode
        val cacheKey = queryPack.baseEtagKey + newEtagKeyPostfix + hashCode
        val groupingParam = queryPack.aggregateFunction!!.groupBy

        cacheManager.checkAggregationCache(cacheKey, Handler { cacheRes ->
            when {
                cacheRes.failed() -> {
                    val res = Handler<AsyncResult<List<E>>> { allResult ->
                        when {
                            allResult.failed() -> {
                                logger.error("Read all failed!", allResult.cause())

                                resultHandler.handle(Future.failedFuture("Could not remoteRead all records..."))
                            }
                            else -> {
                                val records = allResult.result()

                                when {
                                    records.isEmpty() ->
                                        setEtagAndCacheAndReturnContent(etagKey, identifiers.encode().hashCode(), cacheKey,
                                                JsonObject().put("error", "Empty table!").encode(), resultHandler)
                                    else -> {
                                        val sum = if (aggregateFunction.hasGrouping())
                                            sumGrouping(allResult.result(), aggregateFunction, field)
                                        else
                                            JsonObject().put("sum", records.stream()
                                                    .mapToDouble { r -> db.extractValueAsDouble(db.checkAndGetField(field!!), r) }
                                                    .filter { Objects.nonNull(it) }
                                                    .sum())

                                        setEtagAndCacheAndReturnContent(etagKey, identifiers.encode().hashCode(), cacheKey, sum.encode(), resultHandler)
                                    }
                                }
                            }
                        }
                    }

                    val projections = arrayOf(arrayOf(field).requireNoNulls())
                    val finalProjections = projections[0]

                    calculateGroupingPageToken(groupingParam, projections, finalProjections)

                    doIdentifierBasedQueryNoIdentifierAddition(identifiers, queryPack, GSI, res, projections)
                }
                else -> resultHandler.handle(Future.succeededFuture(cacheRes.result()))
            }
        })
    }

    private fun sumGrouping(result: List<E>, aggregateFunction: AggregateFunction?, field: String?): JsonObject {
        return performGroupingAndSorting(result, aggregateFunction!!, BiFunction { items, groupingConfigurations ->
            if (groupingConfigurations.size > 3) throw IllegalArgumentException("GroupBy size of three is max!")
            val levelOne = groupingConfigurations[0]
            val levelTwo = if (groupingConfigurations.size > 1) groupingConfigurations[1] else null
            val levelThree = if (groupingConfigurations.size > 2) groupingConfigurations[2] else null

            when {
                levelTwo == null ->
                    items.parallelStream()
                            .collect(groupingBy({
                                calculateGroupingKey<E>(it, levelOne)
                            }, summingDouble<E> {
                                db.extractValueAsDouble(db.checkAndGetField(field!!), it)
                            }))
                levelThree == null ->
                    items.parallelStream()
                            .collect(groupingBy({
                                calculateGroupingKey<E>(it, levelOne)
                            }, groupingBy({
                                calculateGroupingKey<E>(it, levelTwo)
                            }, summingDouble<E> {
                                db.extractValueAsDouble(db.checkAndGetField(field!!), it)
                            })))
                else ->
                    items.parallelStream()
                            .collect(groupingBy({
                                calculateGroupingKey<E>(it, levelOne)
                            }, groupingBy({
                                calculateGroupingKey<E>(it, levelTwo)
                            }, groupingBy({
                                calculateGroupingKey<E>(it, levelThree)
                            }, summingDouble<E> {
                                db.extractValueAsDouble(db.checkAndGetField(field!!), it)
                            }))))
            }
        })
    }

    private fun countItems(
        identifiers: JsonObject,
        queryPack: QueryPack,
        GSI: String?,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        val newEtagKeyPostfix = "_COUNT"
        val etagKey = queryPack.baseEtagKey +
                newEtagKeyPostfix + queryPack.aggregateFunction!!.groupBy!!.hashCode()
        val cacheKey = queryPack.baseEtagKey +
                newEtagKeyPostfix + queryPack.aggregateFunction!!.groupBy!!.hashCode()

        cacheManager.checkAggregationCache(cacheKey, Handler { cacheRes ->
            when {
                cacheRes.failed() -> {
                    val aggregateFunction = queryPack.aggregateFunction

                    val res = Handler<AsyncResult<List<E>>> {
                        when {
                            it.failed() -> resultHandler.handle(Future.failedFuture("Could not remoteRead all records..."))
                            else -> {
                                val count = if (aggregateFunction!!.hasGrouping())
                                    countGrouping(it.result(), aggregateFunction)
                                else
                                    JsonObject().put("count", it.result().size)

                                setEtagAndCacheAndReturnContent(etagKey, identifiers.encode().hashCode(), cacheKey, count.encode(), resultHandler)
                            }
                        }
                    }

                    val projections = if (!aggregateFunction!!.hasGrouping())
                        arrayOf("etag")
                    else
                        aggregateFunction.groupBy!!
                                .map { it.groupBy }
                                .distinct()
                                .toTypedArray()
                                .requireNoNulls()

                    doIdentifierBasedQueryNoIdentifierAddition(identifiers, queryPack, GSI, res, projections)
                }
                else -> resultHandler.handle(Future.succeededFuture(cacheRes.result()))
            }
        })
    }

    private fun countGrouping(result: List<E>, aggregateFunction: AggregateFunction?): JsonObject {
        return performGroupingAndSorting(result, aggregateFunction!!, BiFunction { items, groupingConfigurations ->
            if (groupingConfigurations.size > 3) throw IllegalArgumentException("GroupBy size of three is max!")
            val levelOne = groupingConfigurations[0]
            val levelTwo = if (groupingConfigurations.size > 1) groupingConfigurations[1] else null
            val levelThree = if (groupingConfigurations.size > 2) groupingConfigurations[2] else null

            when {
                levelTwo == null ->
                    items.parallelStream()
                        .collect(groupingBy({
                            calculateGroupingKey<E>(it, levelOne)
                        }, counting<E>()))
                levelThree == null ->
                    items.parallelStream()
                        .collect(groupingBy({
                            calculateGroupingKey<E>(it, levelOne)
                        }, groupingBy({
                            calculateGroupingKey<E>(it, levelTwo)
                        }, counting<E>())))
                else ->
                    items.parallelStream()
                        .collect(groupingBy({
                            calculateGroupingKey<E>(it, levelOne)
                        }, groupingBy({
                            calculateGroupingKey<E>(it, levelTwo)
                        }, groupingBy({
                            calculateGroupingKey<E>(it, levelThree)
                        }, counting<E>()))))
            }
        })
    }

    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    private fun performGroupingAndSorting(
        items: List<E>,
        aggregateFunction: AggregateFunction,
        mappingFunction: BiFunction<List<E>, List<GroupingConfiguration>, Map<String, *>>
    ): JsonObject {
        val groupingConfigurations = aggregateFunction.groupBy
        if (groupingConfigurations!!.size > 3) throw IllegalArgumentException("GroupBy size of three is max!")
        val levelOne = groupingConfigurations[0]
        val levelTwo = if (groupingConfigurations.size > 1) groupingConfigurations[1] else null
        val levelThree = if (groupingConfigurations.size > 2) groupingConfigurations[2] else null
        val collect = mappingFunction.apply(items, groupingConfigurations)

        val funcName = aggregateFunction.function!!.name.toLowerCase()

        if (logger.isDebugEnabled) {
            logger.debug("Map is: " + Json.encodePrettily(collect) + " with size: " + collect.size)
        }

        @Suppress("SENSELESS_COMPARISON")
        when {
            collect != null -> {
                val totalGroupCount = collect.size
                val levelOneStream: Map<String, *> = when {
                    levelOne.hasGroupRanging() -> doRangedSorting(collect, levelOne)
                    else -> doNormalSorting(collect, levelOne)
                }

                when (levelTwo) {
                    null -> return when {
                        levelOne.hasGroupRanging() -> doRangedGrouping(funcName, levelOneStream, levelOne, totalGroupCount)
                        else -> doNormalGrouping(funcName, levelOneStream, totalGroupCount)
                    }
                    else -> {
                        val levelTwoStream = levelOneStream.entries.stream().map { e ->
                            val entry = e as Map.Entry<String, *>
                            val superGroupedItems = entry.value as Map<String, *>
                            val totalSubGroupCount = superGroupedItems.size

                            when (levelThree) {
                                null -> when {
                                    levelTwo.hasGroupRanging() ->
                                        SimpleEntry(entry.key, doRangedGrouping(funcName,
                                            doRangedSorting(superGroupedItems, levelTwo),
                                            levelTwo, totalSubGroupCount))
                                    else ->
                                        SimpleEntry(entry.key,
                                            doNormalGrouping(funcName,
                                                    doNormalSorting(superGroupedItems, levelTwo),
                                                    totalSubGroupCount))
                                }
                                else -> {
                                    val levelTwoMap: Map<*, *> = when {
                                        levelTwo.hasGroupRanging() -> doRangedSorting(superGroupedItems, levelTwo)
                                        else -> doNormalSorting(superGroupedItems, levelTwo)
                                    }

                                    val levelThreeStream = levelTwoMap.entries.stream().map { subE ->
                                        val subEntry = subE as Map.Entry<String, *>
                                        val subSuperGroupedItems = subEntry.value as Map<String, *>

                                        val totalSubSuperGroupCount = subSuperGroupedItems.size

                                        when {
                                            levelThree.hasGroupRanging() ->
                                                SimpleEntry(subEntry.key,
                                                    doRangedGrouping(funcName, doRangedSorting(
                                                            subSuperGroupedItems, levelThree), levelThree, totalSubSuperGroupCount))
                                            else ->
                                                SimpleEntry(subEntry.key,
                                                    doNormalGrouping(funcName, doNormalSorting(
                                                            subSuperGroupedItems, levelThree), totalSubSuperGroupCount))
                                        }
                                    }

                                    @Suppress("RedundantSamConstructor")
                                    val levelThreeMap = levelThreeStream.collect(toMap(
                                            Function { it.key as String },
                                            Function { it.value },
                                            BinaryOperator { e1, _ -> e1 },
                                            Supplier<Map<String, Any>> { mapOf() }
                                    ))

                                    when {
                                        levelTwo.hasGroupRanging() -> SimpleEntry<Any, JsonObject>(entry.key,
                                                doRangedGrouping(funcName, levelThreeMap, levelTwo, totalSubGroupCount))
                                        else -> SimpleEntry<Any, JsonObject>(entry.key,
                                                doNormalGrouping(funcName, levelThreeMap, totalSubGroupCount))
                                    }
                                }
                            }
                        }

                        @Suppress("RedundantSamConstructor")
                        val levelTwoMap = levelTwoStream.collect(toMap(
                                Function { it.key as String },
                                Function { it.value },
                                BinaryOperator { e1, _ -> e1 },
                                Supplier<Map<String, Any>> { mapOf() }
                        ))

                        return when {
                            levelOne.hasGroupRanging() ->
                                doRangedGrouping(funcName, levelTwoMap, levelOne, totalGroupCount)
                            else -> doNormalGrouping(funcName, levelTwoMap, totalGroupCount)
                        }
                    }
                }
            }
            else -> throw InternalError()
        }
    }

    private fun doNormalGrouping(aggregationFunctionKey: String, mapStream: Map<String, *>, totalGroupCount: Int): JsonObject {
        val results = JsonArray()
        mapStream.forEach { key, value ->
            results.add(JsonObject()
                    .put("groupByKey", key)
                    .put(aggregationFunctionKey, value))
        }

        return JsonObject()
                .put("totalGroupCount", totalGroupCount)
                .put("count", results.size())
                .put("results", results)
    }

    private fun doRangedGrouping(
        aggregationFunctionKey: String,
        mapStream: Map<String, *>,
        groupingConfiguration: GroupingConfiguration,
        totalGroupCount: Int
    ): JsonObject {
        val results = JsonArray()
        mapStream.forEach { key, value ->
            val rangeObject = JsonObject(key)
            val resultObject = JsonObject()
                    .put("floor", rangeObject.getLong("floor"))
                    .put("ceil", rangeObject.getLong("ceil"))
                    .put(aggregationFunctionKey, value)

            results.add(resultObject)
        }

        return JsonObject()
                .put("totalGroupCount", totalGroupCount)
                .put("count", results.size())
                .put("rangeGrouping", JsonObject()
                        .put("unit", groupingConfiguration.groupByUnit)
                        .put("range", groupingConfiguration.groupByRange))
                .put("results", results)
    }

    private fun <T> doNormalSorting(
        collect: Map<String, T>,
        groupingConfiguration: GroupingConfiguration
    ): Map<String, T> {
        val asc = groupingConfiguration.groupingSortOrder.equals("asc", ignoreCase = true)

        when {
            collect.isEmpty() -> return collect.entries
                    .map { e -> SimpleEntry(e.key, e.value) }
                    .fold(LinkedHashMap()) { accumulator, item -> accumulator[item.key] = item.value; accumulator }
            else -> {
                val next = collect.entries.iterator().next()
                val isCollection = next.value is Collection<*> || next.value is Map<*, *>
                val keyIsRanged = groupingConfiguration.hasGroupRanging()

                val comp = when {
                    keyIsRanged -> comparing(Function <SimpleEntry<String, T>, JsonObject> { e -> JsonObject(e.key) },
                            comparing<JsonObject, Long> { keyOne -> keyOne.getLong("floor") })
                    isCollection -> comparing<SimpleEntry<String, T>, String> { it.key }
                    else -> comparing<SimpleEntry<String, T>, String> { Json.encode(it.value) }
                }

                val sorted = collect.entries.stream()
                        .map { e -> SimpleEntry(e.key, e.value) }
                        .sorted(if (asc) comp else comp.reversed())

                return when {
                    groupingConfiguration.isFullList -> sorted
                            .collect(toList())
                            .fold(LinkedHashMap()) { accumulator, item -> accumulator[item.key] = item.value; accumulator }
                    else -> sorted
                            .limit(groupingConfiguration.groupingListLimit.toLong())
                            .collect(toList())
                            .fold(LinkedHashMap()) { accumulator, item -> accumulator[item.key] = item.value; accumulator }
                }
            }
        }
    }

    private fun <T> doRangedSorting(
        collect: Map<String, T>,
        groupingConfiguration: GroupingConfiguration
    ): Map<String, T> {
        val asc = groupingConfiguration.groupingSortOrder.equals("asc", ignoreCase = true)
        val comp = comparingLong<SimpleEntry<String, T>> { e -> JsonObject(e.key).getLong("floor") }

        val sorted = collect.entries
                .stream()
                .map { e -> SimpleEntry(e.key, e.value) }
                .sorted(if (asc) comp else comp.reversed())

        return when {
            groupingConfiguration.isFullList -> sorted
                    .collect(toList())
                    .fold(LinkedHashMap()) { accumulator, item -> accumulator[item.key] = item.value; accumulator }
            else -> sorted
                    .limit(groupingConfiguration.groupingListLimit.toLong())
                    .collect(toList())
                    .fold(LinkedHashMap()) { accumulator, item -> accumulator[item.key] = item.value; accumulator }
        }
    }

    private fun <T> calculateGroupingKey(item: T, groupingConfiguration: GroupingConfiguration?): String {
        val groupingKey: String?

        try {
            groupingKey = db.getFieldAsString(groupingConfiguration!!.groupBy!!, item as Any)
            @Suppress("SENSELESS_COMPARISON")
            if (groupingKey == null) throw UnknownError("Cannot find field!")
        } catch (e: NullPointerException) {
            throw UnknownError("Field is null!")
        }

        when {
            groupingConfiguration.hasGroupRanging() -> {
                val groupByRangeUnit = groupingConfiguration.groupByUnit
                val groupByRangeRange = groupingConfiguration.groupByRange
                var groupingValue: Long? = null
                var rangingValue: Double? = null

                when {
                    groupByRangeUnit!!.equals("INTEGER", ignoreCase = true) -> {
                        groupingValue = java.lang.Long.parseLong(groupByRangeRange!!.toString())
                        val value: Long = try {
                            java.lang.Long.parseLong(groupingKey)
                        } catch (nfe: NumberFormatException) {
                            java.lang.Double.parseDouble(groupingKey).toLong()
                        }

                        rangingValue = Math.ceil((value / groupingValue).toDouble())
                    }
                    groupByRangeUnit.equals("DATE", ignoreCase = true) -> {
                        val date: Date = db.getFieldAsObject(groupingConfiguration.groupBy!!, item as Any)
                        groupingValue = getTimeRangeFromDateUnit(groupByRangeRange!!.toString())

                        rangingValue = Math.ceil((date.time / groupingValue).toDouble())
                    }
                }

                return when {
                    rangingValue != null -> JsonObject()
                            .put("floor", Math.floor(rangingValue).toLong() * groupingValue!!)
                            .put("base", groupingValue)
                            .put("ratio", rangingValue)
                            .put("ceil", (Math.ceil(rangingValue).toLong() + 1L) * groupingValue)
                            .encode()
                    else -> throw UnknownError("Cannot find field!")
                }
            }
            else -> return groupingKey
        }
    }

    private fun getTimeRangeFromDateUnit(groupByRangeRange: String): Long {
        return when (AggregateFunction.TIMEUNIT_DATE.valueOf(groupByRangeRange.toUpperCase())) {
            AggregateFunction.TIMEUNIT_DATE.HOUR -> Duration.ofHours(1).toMillis()
            AggregateFunction.TIMEUNIT_DATE.TWELVE_HOUR -> Duration.ofHours(12).toMillis()
            AggregateFunction.TIMEUNIT_DATE.DAY -> Duration.ofDays(1).toMillis()
            AggregateFunction.TIMEUNIT_DATE.WEEK -> Duration.ofDays(7).toMillis()
            AggregateFunction.TIMEUNIT_DATE.MONTH -> Duration.ofDays(30).toMillis()
            AggregateFunction.TIMEUNIT_DATE.YEAR -> Duration.ofDays(365).toMillis()
        }
    }

    private fun setEtagAndCacheAndReturnContent(
        etagKey: String,
        hash: Int,
        cacheKey: String,
        content: String,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        val etagItemListHashKey = TYPE.simpleName + "_" + hash + "_" + "itemListEtags"
        val newEtag = ModelUtils.returnNewEtag(content.hashCode().toLong())

        cacheManager.replaceAggregationCache(content, Supplier { cacheKey }, Handler {
            if (it.failed()) {
                logger.error("Cache failed on agg!")
            }

            when {
                eTagManager != null ->
                    eTagManager.replaceAggregationEtag(etagItemListHashKey, etagKey, newEtag, Handler { result ->
                        when {
                            result.failed() -> resultHandler.handle(Future.failedFuture(result.cause()))
                            else -> resultHandler.handle(Future.succeededFuture(content))
                        }
                    })
                else -> resultHandler.handle(Future.succeededFuture(content))
            }
        })
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DynamoDBAggregates::class.java.simpleName)
    }
}
