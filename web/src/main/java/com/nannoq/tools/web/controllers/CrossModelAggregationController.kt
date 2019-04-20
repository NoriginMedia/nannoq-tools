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

import com.google.common.util.concurrent.AtomicDouble
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository.Companion.PAGINATION_INDEX
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.ModelUtils
import com.nannoq.tools.repository.models.ValidationError
import com.nannoq.tools.repository.repository.Repository
import com.nannoq.tools.repository.utils.AggregateFunction
import com.nannoq.tools.repository.utils.AggregateFunctions
import com.nannoq.tools.repository.utils.AggregateFunctions.AVG
import com.nannoq.tools.repository.utils.AggregateFunctions.COUNT
import com.nannoq.tools.repository.utils.AggregateFunctions.SUM
import com.nannoq.tools.repository.utils.CrossModelAggregateFunction
import com.nannoq.tools.repository.utils.CrossModelGroupingConfiguration
import com.nannoq.tools.repository.utils.CrossTableProjection
import com.nannoq.tools.repository.utils.FilterPack
import com.nannoq.tools.repository.utils.FilterPackField
import com.nannoq.tools.repository.utils.FilterPackModel
import com.nannoq.tools.repository.utils.FilterParameter
import com.nannoq.tools.repository.utils.GroupingConfiguration
import com.nannoq.tools.repository.utils.QueryPack
import com.nannoq.tools.web.RoutingHelper.setStatusCodeAndAbort
import com.nannoq.tools.web.RoutingHelper.setStatusCodeAndContinue
import com.nannoq.tools.web.RoutingHelper.splitQuery
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.addLogMessageToRequestLog
import com.nannoq.tools.web.responsehandlers.ResponseLogHandler.Companion.BODY_CONTENT_TAG
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.core.impl.ConcurrentHashSet
import io.vertx.core.json.DecodeException
import io.vertx.core.json.EncodeException
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import java.util.AbstractMap.SimpleEntry
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors.groupingBy
import java.util.stream.Collectors.toList
import java.util.stream.Collectors.toMap
import java.util.stream.Collectors.toSet
import kotlin.collections.Map.Entry

/**
 * This class defines a Handler implementation that handles aggregation queries on multiple models.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class CrossModelAggregationController(
    private val repositoryProvider: (Class<*>) -> (Repository<*>?),
    models: Array<Class<*>>
) : Handler<RoutingContext> {
    private val modelMap: Map<String, Class<*>>

    init {
        this.modelMap = buildModelMap(models)
    }

    private fun buildModelMap(models: Array<Class<*>>): Map<String, Class<*>> {
        return Arrays.stream(models)
                .map { SimpleEntry<String, Class<*>>(buildCollectionName(it.simpleName), it) }
                .collect(toMap<SimpleEntry<String, Class<*>>, String, Class<*>>({ it.key }) { it.value })
    }

    private fun buildCollectionName(typeName: String): String {
        val c = typeName.toCharArray()
        c[0] = c[0] + 32

        return String(c) + "s"
    }

    override fun handle(routingContext: RoutingContext) {
        try {
            val initialProcessNanoTime = System.nanoTime()
            val request = routingContext.request()
            val query = request.query()

            val aggregationPack = verifyRequest(
                    routingContext, query, initialProcessNanoTime)

            when {
                aggregationPack?.aggregate == null -> {
                    routingContext.put(BODY_CONTENT_TAG, JsonObject()
                            .put("request_error", "aggregate function cannot be null!"))

                    setStatusCodeAndContinue(400, routingContext, initialProcessNanoTime)
                }
                else -> performAggregation(routingContext, aggregationPack.aggregate, aggregationPack.projection,
                        initialProcessNanoTime)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun performAggregation(
        routingContext: RoutingContext,
        aggregateFunction: CrossModelAggregateFunction,
        projection: Map<Class<*>, Set<String>>,
        initialNanoTime: Long
    ) {
        val request = routingContext.request()
        val route = request.path()
        val query = request.query()
        val requestEtag = request.getHeader(HttpHeaders.IF_NONE_MATCH)
        val identifier = JsonObject()
        val function = aggregateFunction.function
        val projections = getProjections(routingContext)

        when (function) {
            AVG, SUM, COUNT ->
                doValueAggregation(routingContext, route, query, requestEtag, identifier, aggregateFunction, projection,
                    function, projections, initialNanoTime)
            else -> {
                val supportErrorObject = JsonObject().put("function_support_error",
                        "Function " + function?.name + " is not yet supported...")
                sendQueryErrorResponse(supportErrorObject, routingContext, initialNanoTime)
            }
        }
    }

    private fun doValueAggregation(
        routingContext: RoutingContext,
        route: String,
        query: String,
        requestEtag: String,
        identifier: JsonObject,
        aggregate: CrossModelAggregateFunction,
        projection: Map<Class<*>, Set<String>>,
        aggregateFunction: AggregateFunctions,
        projections: Array<String>?,
        initialNanoTime: Long
    ) {
        val groupingList = CopyOnWriteArrayList<Map<String, Double>>()
        val totalValue = AtomicDouble()
        val aggFutures = CopyOnWriteArrayList<Future<*>>()

        if (logger.isDebugEnabled) {
            addLogMessageToRequestLog(routingContext, "ProjectionMap: " + Json.encodePrettily(projection))
        }

        when (val valueResultHandler = getResultHandler(aggregate)) {
            null -> {
                addLogMessageToRequestLog(routingContext, "ResultHandler is null!")

                setStatusCodeAndAbort(500, routingContext, initialNanoTime)
            }
            else -> {
                val queryMap = splitQuery(query)
                val filterPack = convertToFilterPack(routingContext, queryMap[FILTER_KEY])

                projection.keys.forEach {
                    val groupingConfigurations = getGroupingConfigurations(aggregate, it, true)
                    val longSorter = buildLongSorter(aggregate, groupingConfigurations)
                    val doubleSorter = buildDoubleSorter(aggregate, groupingConfigurations)

                    val aggregationResultHandler = getAggregationResultHandler(longSorter, doubleSorter, aggregateFunction)

                    val aggregator = Consumer<String?> { field ->
                        val fut = Future.future<Boolean>()
                        val temp = AggregateFunction.builder()
                                .withAggregateFunction(aggregateFunction)
                                .withField(if (aggregateFunction != COUNT) field else null)
                                .withGroupBy(groupingConfigurations)
                                .build()

                        when (val repo = repositoryProvider(it)) {
                            null -> {
                                addLogMessageToRequestLog(routingContext, it.simpleName + " is not valid!")

                                val future = Future.future<Boolean>()
                                aggFutures.add(future)

                                future.fail(IllegalArgumentException(it.simpleName + " is not valid!"))
                            }
                            else -> {
                                valueAggregation(routingContext, it, repo, fut,
                                        aggregationResultHandler, identifier, route, requestEtag,
                                        if (aggregateFunction == COUNT) null else projections,
                                        groupingList, totalValue, aggregate, temp, filterPack)

                                aggFutures.add(fut)
                            }
                        }
                    }

                    when (aggregationResultHandler) {
                        null -> {
                            addLogMessageToRequestLog(routingContext, "AggResultHandler is null!")

                            val fut = Future.future<Boolean>()
                            aggFutures.add(fut)
                            fut.tryFail(IllegalArgumentException("AggResultHandler cannot be null!"))
                        }
                        else ->
                            when {
                                projection[it]?.isEmpty()!! -> aggregator.accept(null)
                                else -> projection[it]?.forEach(aggregator)
                            }
                    }
                }

                CompositeFuture.all(aggFutures).setHandler { res ->
                    when {
                        res.failed() -> {
                            addLogMessageToRequestLog(routingContext, "Unknown aggregation error!", res.cause())

                            routingContext.put(BODY_CONTENT_TAG, JsonObject()
                                    .put("unknown_error", "Something went horribly wrong..."))
                            routingContext.response().statusCode = 500
                            routingContext.fail(res.cause())
                        }
                        else -> valueResultHandler.accept(
                                ValueAggregationResultPack(aggregate, routingContext, initialNanoTime, totalValue),
                                groupingList)
                    }
                }
            }
        }
    }

    private fun getModelName(klazz: Class<*>): String {
        return klazz.simpleName.toLowerCase() + "s"
    }

    private fun buildLongSorter(
        function: CrossModelAggregateFunction,
        groupingConfigurations: List<GroupingConfiguration>
    ): Comparator<JsonObject> {
        val lowerCaseFunctionName = function.function?.name?.toLowerCase()
        if (groupingConfigurations.size > 3) throw IllegalArgumentException("GroupBy size of three is max!")
        val levelOne = if (groupingConfigurations.isNotEmpty()) groupingConfigurations[0] else null
        val levelTwo = if (groupingConfigurations.size > 1) groupingConfigurations[1] else null
        val levelThree = if (groupingConfigurations.size > 2) groupingConfigurations[2] else null
        var finalConfig: GroupingConfiguration? = null

        when {
            levelThree != null -> finalConfig = levelThree
            levelTwo != null -> finalConfig = levelTwo
            levelOne != null -> finalConfig = levelOne
        }

        return if (finalConfig == null || finalConfig.groupingSortOrder == "asc")
            Comparator.comparingLong { item -> item.getLong(lowerCaseFunctionName) }
        else
            Comparator.comparingLong<JsonObject> { item -> item.getLong(lowerCaseFunctionName) }.reversed()
    }

    private fun buildDoubleSorter(
        function: CrossModelAggregateFunction,
        groupingConfigurations: List<GroupingConfiguration>
    ): Comparator<JsonObject> {
        val lowerCaseFunctionName = function.function?.name?.toLowerCase()
        if (groupingConfigurations.size > 3) throw IllegalArgumentException("GroupBy size of three is max!")
        val levelOne = if (groupingConfigurations.isNotEmpty()) groupingConfigurations[0] else null
        val levelTwo = if (groupingConfigurations.size > 1) groupingConfigurations[1] else null
        val levelThree = if (groupingConfigurations.size > 2) groupingConfigurations[2] else null
        var finalConfig: GroupingConfiguration? = null

        when {
            levelThree != null -> finalConfig = levelThree
            levelTwo != null -> finalConfig = levelTwo
            levelOne != null -> finalConfig = levelOne
        }

        return if (finalConfig == null || finalConfig.groupingSortOrder == "asc")
            Comparator.comparingDouble { item -> item.getDouble(lowerCaseFunctionName) }
        else
            Comparator.comparingDouble<JsonObject> { item -> item.getDouble(lowerCaseFunctionName) }.reversed()
    }

    private inner class AggregationResultPack internal constructor(internal val aggregate: CrossModelAggregateFunction, internal val result: JsonObject, internal val value: AtomicDouble)

    private fun getAggregationResultHandler(
        longSorter: Comparator<JsonObject>,
        doubleSorter: Comparator<JsonObject>,
        aggregateFunction: AggregateFunctions
    ): BiConsumer<AggregationResultPack, MutableList<Map<String, Double>>>? {
        val keyMapper = BiFunction<AggregationResultPack, JsonObject, String> { resultPack, item ->
            val aggregate = resultPack.aggregate

            when {
                aggregate.hasGrouping() && aggregate.groupBy!![0].hasGroupRanging() -> item.encode()
                else -> item.getString("groupByKey")
            }
        }

        when (aggregateFunction) {
            AVG -> return BiConsumer { resultPack, groupingList ->
                when {
                    resultPack.aggregate.hasGrouping() -> {
                        val array = resultPack.result.getJsonArray("results")

                        logger.debug("Results before sort is: " + array.encodePrettily())
                        logger.debug("Aggregate: " + Json.encodePrettily(aggregateFunction))

                        val collect = array.stream()
                                .map { itemAsString -> JsonObject(itemAsString.toString()) }
                                .sorted(doubleSorter)
                                .collect(toMap<JsonObject, String, Double, LinkedHashMap<String, Double>>(
                                        { keyMapper.apply(resultPack, it) },
                                        { it.getDouble(aggregateFunction.name.toLowerCase()) },
                                        { u, _ -> throw IllegalStateException(String.format("Duplicate key %s", u)) },
                                        { LinkedHashMap() }))

                        groupingList.add(collect)
                    }
                    else -> resultPack.value.addAndGet(
                            resultPack.result.getDouble(aggregateFunction.name.toLowerCase())!!)
                }
            }
            SUM, COUNT -> return BiConsumer { resultPack, groupingList ->
                when {
                    resultPack.aggregate.hasGrouping() -> {
                        val array = resultPack.result.getJsonArray("results")

                        logger.debug("Results before sort is: " + array.encodePrettily())
                        logger.debug("Aggregate: " + Json.encodePrettily(aggregateFunction))

                        val collect = array.stream()
                                .map { itemAsString -> JsonObject(itemAsString.toString()) }
                                .sorted(longSorter)
                                .collect(toMap<JsonObject, String, Double, LinkedHashMap<String, Double>>(
                                        { keyMapper.apply(resultPack, it) },
                                        { it.getLong(aggregateFunction.name.toLowerCase())!!.toDouble() },
                                        { u, _ -> throw IllegalStateException(String.format("Duplicate key %s", u)) },
                                        { LinkedHashMap() }))

                        groupingList.add(collect)
                    }
                    else -> resultPack.value.addAndGet(
                            resultPack.result.getLong(aggregateFunction.name.toLowerCase())!!.toDouble())
                }
            }
            else -> return null
        }
    }

    private inner class ValueAggregationResultPack internal constructor(
        internal val aggregate: CrossModelAggregateFunction,
        internal val routingContext: RoutingContext,
        internal val initTime: Long,
        internal val value: AtomicDouble
    )

    private fun getResultHandler(
        aggregateFunction: CrossModelAggregateFunction
    ): BiConsumer<ValueAggregationResultPack, List<Map<String, Double>>>? {
        val asc = aggregateFunction.hasGrouping() && aggregateFunction.groupBy!![0].groupingSortOrder!!.equals("asc", ignoreCase = true)
        val comparator = buildComparator<Long>(aggregateFunction, if (aggregateFunction.hasGrouping())
            aggregateFunction.groupBy!![0]
        else
            null)

        var valueMapper = Function<List<Map<String, Double>>, JsonArray> { groupingList ->
            val results = JsonArray()
            val countMap = LinkedHashMap<String, Long>()
            groupingList.forEach { map ->
                map.forEach { (k, v) ->
                    when {
                        aggregateFunction.hasGrouping() && aggregateFunction.groupBy!![0].hasGroupRanging() -> {
                            val groupingObject = JsonObject(k)
                            val key = JsonObject()
                                    .put("floor", groupingObject.getLong("floor"))
                                    .put("ceil", groupingObject.getLong("ceil")).encode()

                            when {
                                countMap.containsKey(key) -> countMap[key] = countMap[key]!! + v.toLong()
                                else -> countMap[key] = v.toLong()
                            }
                        }
                        else ->
                            when {
                                countMap.containsKey(k) -> countMap[k] = countMap[k]!! + v.toLong()
                                else -> countMap[k] = v.toLong()
                            }
                    }
                }
            }

            countMap.entries.stream()
                    .sorted(createValueComparator(asc, if (aggregateFunction.hasGrouping())
                        aggregateFunction.groupBy!![0]
                    else
                        null))
                    .limit((if (aggregateFunction.hasGrouping())
                        aggregateFunction.groupBy!![0].groupingListLimit
                    else
                        10).toLong())
                    .forEachOrdered { x -> comparator.accept(results, x) }

            results
        }

        when (aggregateFunction.function) {
            AVG -> {
                val doubleComparator = buildComparator<Double>(aggregateFunction, if (aggregateFunction.hasGrouping())
                    aggregateFunction.groupBy!![0]
                else
                    null)

                valueMapper = Function { groupingList ->
                    val results = JsonArray()
                    val countMap = LinkedHashMap<String, Double>()
                    groupingList.forEach { map ->
                        map.forEach { (k, v) ->
                            when {
                                aggregateFunction.hasGrouping() && aggregateFunction.groupBy!![0].hasGroupRanging() -> {
                                    val groupingObject = JsonObject(k)
                                    val key = JsonObject()
                                            .put("floor", groupingObject.getLong("floor"))
                                            .put("ceil", groupingObject.getLong("ceil")).encode()

                                    when {
                                        countMap.containsKey(key) -> countMap[key] = countMap[key]!! + v
                                        else -> countMap[key] = v
                                    }
                                }
                                else ->
                                    when {
                                        countMap.containsKey(k) -> countMap[k] = countMap[k]!! + v
                                        else -> countMap[k] = v
                                    }
                            }
                        }
                    }

                    countMap.entries.stream()
                            .sorted(createValueComparator(asc,
                                    if (aggregateFunction.hasGrouping())
                                        aggregateFunction.groupBy!![0]
                                    else
                                        null))
                            .limit((if (aggregateFunction.hasGrouping())
                                aggregateFunction.groupBy!![0].groupingListLimit
                            else
                                10).toLong())
                            .forEachOrdered { x -> doubleComparator.accept(results, x) }

                    results
                }

                val finalValueMapper = valueMapper

                return BiConsumer { resultPack, groupingList ->
                    val routingContext = resultPack.routingContext
                    val initialNanoTime = resultPack.initTime

                    when {
                        resultPack.aggregate.hasGrouping() -> {
                            val result = finalValueMapper.apply(groupingList)
                            val newEtag = ModelUtils.returnNewEtag(result.encode().hashCode().toLong())
                            val results = JsonObject().put("count", result.size())

                            if (resultPack.aggregate.hasGrouping() && resultPack.aggregate.groupBy!![0].hasGroupRanging()) {
                                results.put("rangeGrouping", JsonObject()
                                        .put("unit", aggregateFunction.groupBy!![0].groupByUnit)
                                        .put("range", aggregateFunction.groupBy!![0].groupByRange))
                            }

                            results.put("results", result)
                            val content = results.encode()

                            checkEtagAndReturn(content, newEtag, routingContext, initialNanoTime)
                        }
                        else -> {
                            val functionName = aggregateFunction.function!!.name.toLowerCase()
                            val result = JsonObject().put(functionName, if (aggregateFunction.function == AVG)
                                resultPack.value
                            else
                                resultPack.value.toLong())
                            val content = result.encode()
                            val newEtag = ModelUtils.returnNewEtag(content.hashCode().toLong())

                            checkEtagAndReturn(content, newEtag, routingContext, initialNanoTime)
                        }
                    }
                }
            }
            SUM, COUNT -> {
                val finalValueMapper = valueMapper

                return BiConsumer { resultPack, groupingList ->
                    val routingContext = resultPack.routingContext
                    val initialNanoTime = resultPack.initTime

                    when {
                        resultPack.aggregate.hasGrouping() -> {
                            val result = finalValueMapper.apply(groupingList)
                            val newEtag = ModelUtils.returnNewEtag(result.encode().hashCode().toLong())
                            val results = JsonObject().put("count", result.size())
                            if (resultPack.aggregate.hasGrouping() && resultPack.aggregate.groupBy!![0].hasGroupRanging()) {
                                results.put("rangeGrouping", JsonObject()
                                        .put("unit", aggregateFunction.groupBy!![0].groupByUnit)
                                        .put("range", aggregateFunction.groupBy!![0].groupByRange))
                            }
                            results.put("results", result)
                            val content = results.encode()
                            checkEtagAndReturn(content, newEtag, routingContext, initialNanoTime)
                        }
                        else -> {
                            val functionName = aggregateFunction.function!!.name.toLowerCase()
                            val result = JsonObject().put(functionName, if (aggregateFunction.function == AVG)
                                resultPack.value
                            else
                                resultPack.value.toLong())
                            val content = result.encode()
                            val newEtag = ModelUtils.returnNewEtag(content.hashCode().toLong())
                            checkEtagAndReturn(content, newEtag, routingContext, initialNanoTime)
                        }
                    }
                }
            }
            else -> return null
        }
    }

    private fun <T : Comparable<T>> createValueComparator(
        asc: Boolean,
        groupingConfiguration: CrossModelGroupingConfiguration?
    ): Comparator<Entry<String, T>> {
        return when {
            groupingConfiguration != null && groupingConfiguration.hasGroupRanging() ->
                if (asc) KeyComparator() else KeyComparator<String, T>().reversed()
            else ->
                if (asc) ValueThenKeyComparator() else ValueThenKeyComparator<String, T>().reversed()
        }
    }

    private fun <T> buildComparator(
        aggregateFunction: CrossModelAggregateFunction,
        groupingConfiguration: CrossModelGroupingConfiguration?
    ): BiConsumer<JsonArray, Entry<String, T>> {
        return when {
            groupingConfiguration != null && groupingConfiguration.hasGroupRanging() -> BiConsumer { results, x ->
                results.add(
                        JsonObject(x.key)
                                .put(aggregateFunction.function?.name?.toLowerCase(), x.value))
            }
            else -> BiConsumer { results, x -> results.add(JsonObject().put(x.key, x.value)) }
        }
    }

    private inner class KeyComparator<K : Comparable<K>, V : Comparable<V>> : Comparator<Entry<K, V>> {
        override fun compare(a: Entry<K, V>, b: Entry<K, V>): Int {
            val keyObjectA = JsonObject(a.key.toString())
            val keyObjectB = JsonObject(b.key.toString())

            return keyObjectA.getLong("ceil")!!.compareTo(keyObjectB.getLong("ceil")!!)
        }
    }

    private inner class ValueThenKeyComparator<K : Comparable<K>, V : Comparable<V>> : Comparator<Entry<K, V>> {
        override fun compare(a: Entry<K, V>, b: Entry<K, V>): Int {
            val cmp1 = a.value.compareTo(b.value)

            return when {
                cmp1 != 0 -> cmp1
                else -> a.key.compareTo(b.key)
            }
        }
    }

    private fun checkEtagAndReturn(content: String, newEtag: String?, routingContext: RoutingContext, initialNanoTime: Long) {
        val requestEtag = routingContext.request().getHeader(HttpHeaders.IF_NONE_MATCH)

        when {
            newEtag != null && requestEtag != null && requestEtag.equals(newEtag, ignoreCase = true) ->
                setStatusCodeAndContinue(304, routingContext, initialNanoTime)
            else -> {
                if (newEtag != null) routingContext.response().putHeader(HttpHeaders.ETAG, newEtag)
                routingContext.put(BODY_CONTENT_TAG, content)
                setStatusCodeAndContinue(200, routingContext, initialNanoTime)
            }
        }
    }

    private fun valueAggregation(
        routingContext: RoutingContext,
        TYPE: Class<*>,
        repo: Repository<*>,
        fut: Future<Boolean>,
        aggregationResultHandler: BiConsumer<AggregationResultPack, MutableList<Map<String, Double>>>?,
        identifier: JsonObject,
        route: String,
        requestEtag: String,
        projections: Array<String>?,
        groupingList: List<Map<String, Double>>,
        totalCount: AtomicDouble,
        aggregate: CrossModelAggregateFunction,
        temp: AggregateFunction,
        filterPack: FilterPack?
    ) {
        val finalRoute = TYPE.simpleName + "_AGG_" + route
        val params = convertToFilterList(TYPE, filterPack)
        val valueQueryPack = QueryPack.builder(TYPE)
                .withRoutingContext(routingContext)
                .withCustomRoute(finalRoute)
                .withPageToken(routingContext.request().getParam(PAGING_TOKEN_KEY))
                .withRequestEtag(requestEtag)
                .withFilterParameters(params)
                .withAggregateFunction(temp)
                .withProjections(projections ?: arrayOf())
                .withIndexName(PAGINATION_INDEX)
                .build()

        if (logger.isDebugEnabled) {
            addLogMessageToRequestLog(routingContext, "PROJECTIONS: " + Arrays.toString(projections))
        }

        repo.aggregation(identifier, valueQueryPack, projections, Handler {
            when {
                it.failed() -> {
                    addLogMessageToRequestLog(routingContext, "Unable to fetch res for " + TYPE.simpleName + "!",
                            it.cause())

                    fut.fail(it.cause())
                }
                else -> {
                    if (logger.isDebugEnabled) {
                        addLogMessageToRequestLog(routingContext, "Countres: " + it.result())
                    }

                    val valueResultObject = JsonObject(it.result())

                    if (logger.isDebugEnabled) {
                        addLogMessageToRequestLog(routingContext, TYPE.simpleName + "_agg: " +
                                Json.encodePrettily(valueResultObject))
                    }

                    val pack = AggregationResultPack(aggregate, valueResultObject, totalCount)
                    aggregationResultHandler!!.accept(pack, groupingList.toMutableList())

                    fut.complete(java.lang.Boolean.TRUE)
                }
            }
        })
    }

    private fun convertToFilterPack(routingContext: RoutingContext, filter: List<String>?): FilterPack? {
        if (filter == null) return null

        try {
            return Json.decodeValue<FilterPack>(filter[0], FilterPack::class.java)
        } catch (e: DecodeException) {
            addLogMessageToRequestLog(routingContext, "Cannot decode FilterPack...", e)
        }

        return null
    }

    private fun convertToFilterList(
        TYPE: Class<*>,
        filterPack: FilterPack?
    ): Map<String, MutableList<FilterParameter>> {
        if (filterPack == null) return ConcurrentHashMap()

        val params = ConcurrentHashMap<String, MutableList<FilterParameter>>()

        val first = filterPack.models!!.stream()
                .filter { model -> model.model!!.equals(TYPE.simpleName + "s", ignoreCase = true) }
                .findFirst()

        if (first.isPresent) {
            val parameters = ArrayList<FilterParameter>()

            val groupedFields = getGroupedParametersForFields(first.get())
            groupedFields.keys.forEach {
                groupedFields[it]?.forEach { fpf ->
                    fpf.parameters!!.forEach { fieldFilter ->
                        fieldFilter.field = it
                        parameters.add(fieldFilter)
                    }
                }

                params[it] = parameters
            }
        }

        return params
    }

    private fun getGroupedParametersForFields(pm: FilterPackModel): Map<String, List<FilterPackField>> {
        return pm.fields!!.stream().collect(groupingBy { it.field })
    }

    private fun verifyRequest(
        routingContext: RoutingContext,
        query: String?,
        initialProcessTime: Long
    ): AggregationPack? {
        when (query) {
            null -> noQueryError(routingContext, initialProcessTime)
            else -> {
                val queryMap = splitQuery(query)

                if (verifyQuery(queryMap, routingContext, initialProcessTime)) {
                    try {
                        val aggregateFunction = Json.decodeValue<CrossModelAggregateFunction>(
                                queryMap[AGGREGATE_KEY]?.get(0), CrossModelAggregateFunction::class.java)
                        var crossTableProjection = Json.decodeValue<CrossTableProjection>(
                                queryMap[PROJECTION_KEY]?.get(0), CrossTableProjection::class.java)
                        crossTableProjection = CrossTableProjection(
                                crossTableProjection.models,
                                ArrayList(modelMap.keys),
                                crossTableProjection.fields)
                        val pageToken = routingContext.request().getParam(PAGING_TOKEN_KEY)

                        when {
                            aggregateFunction.field != null -> {
                                val aggregationQueryErrorObject = JsonObject().put("aggregate_field_error",
                                        "Field must be null in aggregate, use fields in projection instead")

                                sendQueryErrorResponse(aggregationQueryErrorObject, routingContext, initialProcessTime)
                            }
                            else -> {
                                val errors = crossTableProjection.validate(aggregateFunction.function!!)

                                when {
                                    errors.isEmpty() -> {
                                        val projectionMap = buildProjectionMap(crossTableProjection)
                                        val aggregationErrors = verifyAggregation(aggregateFunction, projectionMap)

                                        when {
                                            aggregationErrors.isEmpty() ->
                                                return AggregationPack(aggregateFunction, projectionMap, pageToken)
                                            else -> sendQueryErrorResponse(buildJsonErrorObject("Aggregate",
                                                    aggregationErrors), routingContext, initialProcessTime)
                                        }
                                    }
                                    else -> sendQueryErrorResponse(buildValidationErrorObject("Projection", errors),
                                            routingContext, initialProcessTime)
                                }
                            }
                        }
                    } catch (e: DecodeException) {
                        val aggregationQueryErrorObject = JsonObject()
                                .put("json_parse_error", "Unable to parse json in query, are you sure it is URL encoded?")

                        sendQueryErrorResponse(aggregationQueryErrorObject, routingContext, initialProcessTime)
                    }
                }
            }
        }

        return null
    }

    private fun buildProjectionMap(crossTableProjection: CrossTableProjection): Map<Class<*>, Set<String>> {
        return crossTableProjection.models!!.stream()
                .map { SimpleEntry<Class<*>, Set<String>>(modelMap[it], getFieldsForCollection(crossTableProjection, it)) }
                .collect(toMap<SimpleEntry<Class<*>, Set<String>>, Class<*>, Set<String>>({ it.key }) { it.value })
    }

    private fun getFieldsForCollection(crossTableProjection: CrossTableProjection, collection: String): Set<String> {
        return when {
            crossTableProjection.fields == null -> ConcurrentHashSet()
            else -> crossTableProjection.fields!!.stream()
                    .map<String> { field ->
                        val fieldSplit = field.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                        if (fieldSplit[0].equals(collection, ignoreCase = true)) fieldSplit[1] else null
                    }
                    .collect(toSet())
        }
    }

    private fun verifyAggregation(
        aggregateFunction: CrossModelAggregateFunction,
        crossTableProjection: Map<Class<*>, Set<String>>
    ): List<JsonObject> {
        val function = aggregateFunction.function
        val errors = ArrayList<JsonObject>()

        when {
            aggregateFunction.groupBy!!.size > 1 -> errors.add(
                    JsonObject().put("grouping_error", "Max grouping for cross model is 1!"))
            else -> crossTableProjection.forEach { (klazz, fieldSet) ->
                fieldSet.forEach {
                    val groupingConfigurations = getGroupingConfigurations(aggregateFunction, klazz)
                    val temp = AggregateFunction.builder()
                            .withAggregateFunction(function!!)
                            .withField(it)
                            .withGroupBy(groupingConfigurations)
                            .build()

                    @Suppress("UNCHECKED_CAST")
                    temp.validateFieldForFunction(klazz as Class<ETagable>)
                    if (!temp.validationError.isEmpty) errors.add(temp.validationError)
                }
            }
        }

        return errors
    }

    private fun getGroupingConfigurations(
        aggregateFunction: CrossModelAggregateFunction,
        klazz: Class<*>,
        fullList: Boolean = false
    ): List<GroupingConfiguration> {
        val groupBy = aggregateFunction.groupBy ?: return ArrayList()
        val modelName = getModelName(klazz)

        return groupBy
                .stream()
                .map { cmgf ->
                    val innerGroupBy = cmgf.groupBy
                    if (innerGroupBy!!.size == 1) innerGroupBy[0]

                    innerGroupBy.stream()
                            .filter { gb -> gb.startsWith(modelName) }
                            .findFirst()
                            .map<String> { s ->
                                s.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                            }
                            .orElse(null)
                }
                .findFirst()
                .map { s ->
                    aggregateFunction.groupBy!!.stream()
                            .map {
                                GroupingConfiguration.builder()
                                        .withGroupBy(s)
                                        .withGroupByUnit(it.groupByUnit)
                                        .withGroupByRange(it.groupByRange)
                                        .withGroupingSortOrder(it.groupingSortOrder)
                                        .withGroupingListLimit(it.groupingListLimit)
                                        .withFullList(fullList).build()
                            }
                            .collect(toList())
                }
                .orElseGet { ArrayList() }
    }

    @Suppress("SameParameterValue")
    private fun buildValidationErrorObject(projection: String, errors: List<ValidationError>): JsonObject {
        val validationArray = JsonArray()
        errors.stream().map { it.toJson() }.forEach { validationArray.add(it) }

        return JsonObject()
                .put("validation_error", "$projection is invalid...")
                .put("errors", validationArray)
    }

    @Suppress("SameParameterValue")
    private fun buildJsonErrorObject(projection: String, errors: List<JsonObject>): JsonObject {
        val validationArray = JsonArray()
        errors.forEach { validationArray.add(it) }

        return JsonObject()
                .put("validation_error", "$projection is invalid...")
                .put("errors", validationArray)
    }

    private fun verifyQuery(
        queryMap: Map<String, List<String>>?,
        routingContext: RoutingContext,
        initialProcessTime: Long
    ): Boolean {
        when {
            queryMap == null -> {
                noAggregateError(routingContext, initialProcessTime)

                return false
            }
            queryMap[AGGREGATE_KEY] == null -> {
                noAggregateError(routingContext, initialProcessTime)

                return false
            }
            queryMap[PROJECTION_KEY] == null -> {
                noProjectionError(routingContext, initialProcessTime)

                return false
            }
            queryMap[PAGING_TOKEN_KEY] != null &&
                    queryMap[PAGING_TOKEN_KEY]?.get(0).equals(END_OF_PAGING_KEY, ignoreCase = true) -> {
                noPageError(routingContext, initialProcessTime)

                return false
            }
            else -> return true
        }
    }

    private fun noAggregateError(routingContext: RoutingContext, initialProcessTime: Long) {
        val aggregationQueryErrorObject = JsonObject()
                .put("aggregate_query_error", "$AGGREGATE_KEY query param is required!")

        sendQueryErrorResponse(aggregationQueryErrorObject, routingContext, initialProcessTime)
    }

    private fun noProjectionError(routingContext: RoutingContext, initialProcessTime: Long) {
        val aggregationQueryErrorObject = JsonObject()
                .put("projection_query_error", "$PROJECTION_KEY query param is required!")

        sendQueryErrorResponse(aggregationQueryErrorObject, routingContext, initialProcessTime)
    }

    private fun noQueryError(routingContext: RoutingContext, initialProcessTime: Long) {
        val aggregationQueryErrorObject = JsonObject()
                .put("aggregation_error", "Query cannot be null for this endpoint!")

        sendQueryErrorResponse(aggregationQueryErrorObject, routingContext, initialProcessTime)
    }

    private fun noPageError(routingContext: RoutingContext, initialProcessTime: Long) {
        val aggregationQueryErrorObject = JsonObject()
                .put("paging_error", "You cannot page for the " + END_OF_PAGING_KEY + ", " +
                        "this message means you have reached the end of the results requested.")

        sendQueryErrorResponse(aggregationQueryErrorObject, routingContext, initialProcessTime)
    }

    private fun sendQueryErrorResponse(
        aggregationQueryErrorObject: JsonObject,
        routingContext: RoutingContext,
        initialProcessTimeNano: Long
    ) {
        routingContext.put(BODY_CONTENT_TAG, aggregationQueryErrorObject)
        setStatusCodeAndContinue(400, routingContext, initialProcessTimeNano)
    }

    private fun getProjections(routingContext: RoutingContext): Array<String>? {
        val projectionJson = routingContext.request().getParam(PROJECTION_KEY)
        var projections: Array<String>? = null

        if (projectionJson != null) {
            try {
                val projection = JsonObject(projectionJson)
                val array = projection.getJsonArray(PROJECTION_FIELDS_KEY, null)

                if (array != null) {
                    projections = array.stream()
                            .map { o -> o.toString()
                                    .split("\\.".toRegex())
                                    .dropLastWhile { it.isEmpty() }
                                    .toTypedArray()[1]
                            }
                            .collect(toList())
                            .toTypedArray()
                            .requireNoNulls()

                    if (logger.isDebugEnabled) {
                        addLogMessageToRequestLog(routingContext, "Projection ready!")
                    }
                }
            } catch (e: DecodeException) {
                addLogMessageToRequestLog(routingContext, "Unable to parse projections: $e")

                projections = null
            } catch (e: EncodeException) {
                addLogMessageToRequestLog(routingContext, "Unable to parse projections: $e")
                projections = null
            }
        }

        return projections
    }

    @Suppress("unused")
    private inner class AggregationPack internal constructor(
        internal val aggregate: CrossModelAggregateFunction,
        internal val projection: Map<Class<*>, Set<String>>,
        internal val pageToken: String?
    )

    companion object {
        private val logger = LoggerFactory.getLogger(CrossModelAggregationController::class.java.simpleName)

        private const val PROJECTION_KEY = "projection"
        private const val PROJECTION_FIELDS_KEY = "fields"
        private const val FILTER_KEY = "filter"
        private const val AGGREGATE_KEY = "aggregate"

        private const val PAGING_TOKEN_KEY = "paging"
        private const val END_OF_PAGING_KEY = "END_OF_LIST"
    }
}