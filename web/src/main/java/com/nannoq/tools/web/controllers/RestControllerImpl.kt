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

package com.nannoq.tools.web.controllers

import com.nannoq.tools.repository.dynamodb.DynamoDBRepository.Companion.PAGINATION_INDEX
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.models.ModelUtils
import com.nannoq.tools.repository.repository.Repository
import com.nannoq.tools.repository.repository.etag.ETagManager
import com.nannoq.tools.repository.repository.etag.InMemoryETagManagerImpl
import com.nannoq.tools.repository.repository.etag.RedisETagManagerImpl
import com.nannoq.tools.repository.repository.redis.RedisUtils.getRedisClient
import com.nannoq.tools.repository.utils.*
import com.nannoq.tools.repository.utils.AggregateFunctions.MAX
import com.nannoq.tools.repository.utils.AggregateFunctions.MIN
import com.nannoq.tools.web.RoutingHelper.denyQuery
import com.nannoq.tools.web.RoutingHelper.setStatusCodeAndAbort
import com.nannoq.tools.web.RoutingHelper.splitQuery
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.REQUEST_PROCESS_TIME_TAG
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.addLogMessageToRequestLog
import com.nannoq.tools.web.responsehandlers.ResponseLogHandler.Companion.BODY_CONTENT_TAG
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.*
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Function
import java.util.stream.Collectors.toList

/**
 * This interface defines the default RestControllerImpl. It prepares queries and builds responses. Standard model
 * operations need not override anything to use this controller. Overriding functions must remember to call the next
 * element in the chain.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
open class RestControllerImpl<E>(vertx: Vertx, protected val TYPE: Class<E>, appConfig: JsonObject, protected val REPOSITORY: Repository<E>,
                                 protected val idSupplier: Function<RoutingContext, JsonObject>,
                                 eTagManager: ETagManager<E>?) : RestController<E> where E : ETagable, E : Model {
    protected val COLLECTION: String
    protected val eTagManager: ETagManager<E>?
    private val fields: Array<Field>
    private val methods: Array<Method>

    val type: Class<E>
        get() = TYPE

    constructor(type: Class<E>, appConfig: JsonObject, repository: Repository<E>) : this(Vertx.currentContext().owner(), type, appConfig, repository, defaultSupplier, null)

    constructor(vertx: Vertx, type: Class<E>, appConfig: JsonObject, repository: Repository<E>) : this(vertx, type, appConfig, repository, defaultSupplier, null)

    constructor(type: Class<E>, appConfig: JsonObject, repository: Repository<E>,
                eTagManager: ETagManager<E>?) : this(Vertx.currentContext().owner(), type, appConfig, repository, defaultSupplier, eTagManager)

    constructor(vertx: Vertx, type: Class<E>, appConfig: JsonObject, repository: Repository<E>,
                eTagManager: ETagManager<E>?) : this(vertx, type, appConfig, repository, defaultSupplier, eTagManager)

    constructor(type: Class<E>, appConfig: JsonObject, repository: Repository<E>,
                idSupplier: Function<RoutingContext, JsonObject>) : this(Vertx.currentContext().owner(), type, appConfig, repository, idSupplier, null)

    constructor(type: Class<E>, appConfig: JsonObject, repository: Repository<E>,
                idSupplier: Function<RoutingContext, JsonObject>,
                eTagManager: ETagManager<E>?) : this(Vertx.currentContext().owner(), type, appConfig, repository, idSupplier, eTagManager)

    init {
        this.COLLECTION = buildCollectionName(TYPE.name)
        fields = this.getAllFieldsOnType(TYPE)
        methods = this.getAllMethodsOnType(TYPE)

        val eTagManagerRepo = REPOSITORY.etagManager

        @Suppress("UNCHECKED_CAST")
        when {
            eTagManager != null -> this.eTagManager = eTagManager
            eTagManagerRepo != null -> this.eTagManager = eTagManagerRepo as ETagManager<E>
            appConfig.getString("redis_host") != null -> this.eTagManager = RedisETagManagerImpl(TYPE, getRedisClient(vertx, appConfig))
            else -> this.eTagManager = InMemoryETagManagerImpl(vertx, TYPE)
        }
    }

    private fun buildCollectionName(typeName: String): String {
        val c = typeName.toCharArray()
        c[0] = c[0] + 32

        return String(c) + "s"
    }

    override fun performShow(routingContext: RoutingContext) {
        if (denyQuery(routingContext)) return

        val initialProcessNanoTime = System.nanoTime()
        val id = getAndVerifyId(routingContext)

        if (id.isEmpty) {
            setStatusCodeAndAbort(400, routingContext, initialProcessNanoTime)
        } else {
            val projectionJson = routingContext.request().getParam(PROJECTION_KEY)
            var projections: Array<String>? = null

            if (projectionJson != null) {
                try {
                    val projection = JsonObject(projectionJson)
                    val array = projection.getJsonArray(PROJECTION_FIELDS_KEY, null)

                    if (array != null) {
                        projections = array.stream()
                                .map { it.toString() }
                                .collect(toList())
                                .toTypedArray()
                                .requireNoNulls()

                        if (logger.isDebugEnabled) {
                            addLogMessageToRequestLog(routingContext, "Projection ready!")
                        }
                    }
                } catch (e: DecodeException) {
                    addLogMessageToRequestLog(routingContext, "Unable to parse projections: ", e)

                    projections = null
                } catch (e: EncodeException) {
                    addLogMessageToRequestLog(routingContext, "Unable to parse projections: ", e)
                    projections = null
                }

            }

            if (logger.isDebugEnabled) {
                addLogMessageToRequestLog(routingContext, "Show projection: " + Arrays.toString(projections))
            }

            val finalProjections = projections

            if (finalProjections != null && finalProjections.isNotEmpty()) {
                val etag = routingContext.request().getHeader(HttpHeaders.IF_NONE_MATCH)

                if (logger.isDebugEnabled) {
                    addLogMessageToRequestLog(routingContext, "Etag is: " + etag!!)
                }

                if (etag != null && eTagManager != null) {
                    val hash = id.encode().hashCode()
                    val etagKeyBase = TYPE.simpleName + "_" + hash + "/projections"
                    val key = TYPE.simpleName + "_" + hash + "/projections" + Arrays.hashCode(finalProjections)

                    if (logger.isDebugEnabled) {
                        addLogMessageToRequestLog(routingContext, "Checking etag for show...")
                    }

                    eTagManager.checkItemEtag(etagKeyBase, key, etag, Handler {
                        if (it.succeeded() && it.result()) {
                            unChangedIndex(routingContext)
                        } else {
                            proceedWithRead(routingContext, id, finalProjections)
                        }
                    })
                } else {
                    proceedWithRead(routingContext, id, finalProjections)
                }
            } else {
                proceedWithRead(routingContext, id, finalProjections)
            }
        }
    }

    private fun proceedWithRead(routingContext: RoutingContext, id: JsonObject, finalProjections: Array<String>?) {
        REPOSITORY.read(id, false, finalProjections, Handler {
            if (it.failed()) {
                if (it.result() == null) {
                    notFoundShow(routingContext)
                } else {
                    failedShow(routingContext, JsonObject().put("error", "Service Unavailable..."))
                }
            } else {
                val etag = routingContext.request().getHeader(HttpHeaders.IF_NONE_MATCH)
                val itemResult = it.result()
                val item = itemResult.item

                routingContext.response().putHeader(HttpHeaders.ETAG, item.etag)
                routingContext.response().putHeader("X-Cache", if (itemResult.isCacheHit) "HIT" else "MISS")
                routingContext.response().putHeader("X-Repository-Pre-Operation-Nanos", "" + itemResult.preOperationProcessingTime)
                routingContext.response().putHeader("X-Repository-Operation-Nanos", "" + itemResult.operationProcessingTime)
                routingContext.response().putHeader("X-Repository-Post-Operation-Nanos", "" + itemResult.postOperationProcessingTime)

                if (etag != null && item.etag!!.equals(etag, ignoreCase = true)) {
                    unChangedShow(routingContext)
                } else {
                    postShow(routingContext, item, finalProjections ?: arrayOf())
                }
            }
        })
    }

    override fun prepareQuery(routingContext: RoutingContext, customQuery: String?) {
        val initialProcessNanoTime = System.nanoTime()
        routingContext.put(CONTROLLER_START_TIME, initialProcessNanoTime)
        val query = customQuery ?: routingContext.request().query()

        if (query == null || query.isEmpty()) {
            preProcessQuery(routingContext, ConcurrentHashMap())
        } else {
            val queryMap = splitQuery(query)

            preProcessQuery(routingContext, queryMap)
        }
    }

    override fun processQuery(routingContext: RoutingContext, queryMap: MutableMap<String, List<String>>) {
        var errors = JsonObject()
        var aggregateFunction: AggregateFunction? = null
        val params = ConcurrentHashMap<String, MutableList<FilterParameter>>()
        val orderByQueue = ConcurrentLinkedQueue<OrderByParameter>()
        val aggregateQuery = queryMap[AGGREGATE_KEY]
        val indexName = arrayOf(PAGINATION_INDEX)
        val limit = IntArray(1)

        if (aggregateQuery != null && queryMap[ORDER_BY_KEY] != null) {
            val aggregateJson = aggregateQuery[0]

            try {
                aggregateFunction = Json.decodeValue<AggregateFunction>(aggregateJson, AggregateFunction::class.java)

                if (!(aggregateFunction!!.function == MIN || aggregateFunction.function == MAX)) {
                    routingContext.put(BODY_CONTENT_TAG, JsonObject().put("aggregate_error",
                            "AVG, SUM and COUNT cannot be performed in conjunction with ordering..."))
                    routingContext.fail(400)

                    return
                }
            } catch (e: DecodeException) {
                addLogMessageToRequestLog(routingContext, "Unable to parse projections", e)

                routingContext.put(BODY_CONTENT_TAG, JsonObject().put("aggregate_query_error",
                        "Unable to parse json..."))
                routingContext.fail(400)

                return
            } catch (e: EncodeException) {
                addLogMessageToRequestLog(routingContext, "Unable to parse projections", e)
                routingContext.put(BODY_CONTENT_TAG, JsonObject().put("aggregate_query_error", "Unable to parse json..."))
                routingContext.fail(400)
                return
            }

        }

        queryMap.remove(PAGING_TOKEN_KEY)
        queryMap.remove(AGGREGATE_KEY)

        if (aggregateQuery != null && aggregateFunction == null) {
            val aggregateJson = aggregateQuery[0]

            try {
                aggregateFunction = Json.decodeValue<AggregateFunction>(aggregateJson, AggregateFunction::class.java)
            } catch (e: DecodeException) {
                addLogMessageToRequestLog(routingContext, "Unable to parse projections", e)

                routingContext.put(BODY_CONTENT_TAG, JsonObject().put("aggregate_query_error",
                        "Unable to parse json..."))
                routingContext.fail(400)

                return
            } catch (e: EncodeException) {
                addLogMessageToRequestLog(routingContext, "Unable to parse projections", e)
                routingContext.put(BODY_CONTENT_TAG, JsonObject().put("aggregate_query_error", "Unable to parse json..."))
                routingContext.fail(400)
                return
            }

        }

        errors = REPOSITORY.buildParameters(
                queryMap, fields, methods, errors, params, limit, orderByQueue, indexName)

        if (errors.isEmpty) {
            val projectionJson = routingContext.request().getParam(PROJECTION_KEY)
            var projections: Array<String>? = null

            if (projectionJson != null) {
                try {
                    val projection = JsonObject(projectionJson)
                    val array = projection.getJsonArray(PROJECTION_FIELDS_KEY, null)

                    if (array != null) {
                        projections = array.stream()
                                .map { it.toString() }
                                .collect(toList())
                                .toTypedArray()
                                .requireNoNulls()

                        if (logger.isDebugEnabled) {
                            addLogMessageToRequestLog(routingContext, "Projection ready!")
                        }
                    }
                } catch (e: DecodeException) {
                    logger.error("Unable to parse projections: $e", e)

                    projections = null
                } catch (e: EncodeException) {
                    logger.error("Unable to parse projections: $e", e)
                    projections = null
                }

            }

            if (logger.isDebugEnabled) {
                addLogMessageToRequestLog(routingContext, "Index projections: " + Arrays.toString(projections))
            }

            postProcessQuery(routingContext, aggregateFunction, orderByQueue, params,
                    if (projections == null) arrayOf() else projections, indexName[0], limit[0])
        } else {
            val errorObject = JsonObject()
            errorObject.put("request_errors", errors)

            routingContext.response().statusCode = 400
            routingContext.put(BODY_CONTENT_TAG, errorObject)
            routingContext.next()
        }
    }

    override fun createIdObjectForIndex(routingContext: RoutingContext, aggregateFunction: AggregateFunction?,
                                        orderByQueue: Queue<OrderByParameter>, params: Map<String, MutableList<FilterParameter>>,
                                        projections: Array<String>, indexName: String, limit: Int?) {
        val id = getAndVerifyId(routingContext)

        performIndex(routingContext, id, aggregateFunction, orderByQueue, params, projections, indexName, limit)
    }

    override fun performIndex(routingContext: RoutingContext, identifiers: JsonObject, aggregateFunction: AggregateFunction?,
                              orderByQueue: Queue<OrderByParameter>, params: Map<String, MutableList<FilterParameter>>,
                              projections: Array<String>, indexName: String, limit: Int?) {
        val initialProcessNanoTime = routingContext.get<Long>(CONTROLLER_START_TIME)
        val request = routingContext.request()
        val pageToken : String? = request.getParam(PAGING_TOKEN_KEY)
        val etag = request.getHeader(HttpHeaders.IF_NONE_MATCH)

        if (request.rawMethod().equals("GET", ignoreCase = true)) {
            val idArray = request.getParam(MULTIPLE_IDS_KEY)

            if (idArray != null) {
                try {
                    val ids = JsonArray(idArray)
                    identifiers
                            .put("range", ids)
                            .put("multiple", true)

                } catch (e: DecodeException) {
                    addLogMessageToRequestLog(routingContext, "Unable to parse projections!", e)

                    routingContext.put(BODY_CONTENT_TAG, JsonObject().put("ids_query_error",
                            "Unable to parse json..."))
                    routingContext.fail(400)

                    return
                } catch (e: EncodeException) {
                    addLogMessageToRequestLog(routingContext, "Unable to parse projections!", e)
                    routingContext.put(BODY_CONTENT_TAG, JsonObject().put("ids_query_error", "Unable to parse json..."))
                    routingContext.fail(400)
                    return
                }

            }
        }

        val multiple = identifiers.getBoolean("multiple")
        var ids: JsonArray? = null
        if (multiple != null && multiple) ids = identifiers.getJsonArray("range")

        if (multiple != null && multiple && ids != null && ids.isEmpty) {
            routingContext.put(BODY_CONTENT_TAG, Json.encodePrettily(JsonObject().put("error",
                    "You cannot request multiple ids with an empty array!")))

            setStatusCodeAndAbort(400, routingContext, initialProcessNanoTime)
        } else {
            if (logger.isDebugEnabled) {
                addLogMessageToRequestLog(routingContext, "Started index!")
            }

            if (pageToken != null && pageToken.equals(END_OF_PAGING_KEY, ignoreCase = true)) {
                routingContext.put(BODY_CONTENT_TAG, Json.encodePrettily(JsonObject().put("error",
                        "You cannot page for the " + END_OF_PAGING_KEY + ", " +
                                "this message means you have reached the end of the results requested.")))

                setStatusCodeAndAbort(400, routingContext, initialProcessNanoTime)
            } else {
                val queryPack = QueryPack.builder(TYPE)
                        .withRoutingContext(routingContext)
                        .withPageToken(pageToken)
                        .withRequestEtag(etag)
                        .withOrderByQueue(orderByQueue)
                        .withFilterParameters(params)
                        .withAggregateFunction(aggregateFunction)
                        .withProjections(projections)
                        .withIndexName(indexName)
                        .withLimit(limit)
                        .build()

                if (queryPack.aggregateFunction != null) {
                    proceedWithAggregationIndex(routingContext, etag, identifiers, queryPack, projections)
                } else {
                    val hash = identifiers.encode().hashCode()
                    val etagItemListHashKey = TYPE.simpleName + "_" + hash + "_" + "itemListEtags"
                    val etagKey = queryPack.baseEtagKey

                    if (logger.isDebugEnabled) {
                        logger.debug("EtagKey is: " + etagKey!!)

                        addLogMessageToRequestLog(routingContext, "Querypack ok, fetching etag for $etagKey")
                    }

                    if (etag != null && eTagManager != null) {
                        eTagManager.checkItemListEtag(etagItemListHashKey, etagKey!!, etag, Handler {
                            if (it.succeeded() && it.result()) {
                                unChangedIndex(routingContext)
                            } else {
                                proceedWithPagedIndex(identifiers, pageToken,
                                        queryPack, projections, routingContext)
                            }
                        })
                    } else {
                        proceedWithPagedIndex(identifiers, pageToken, queryPack, projections, routingContext)
                    }
                }
            }
        }
    }

    override fun proceedWithPagedIndex(id: JsonObject, pageToken: String?, queryPack: QueryPack,
                                       projections: Array<String>, routingContext: RoutingContext) {
        REPOSITORY.readAll(id, pageToken, queryPack, projections, Handler {
            if (it.failed()) {
                addLogMessageToRequestLog(routingContext, "FAILED: " + if (it.result() == null)
                    null
                else
                    it.result().items, it.cause())

                failedIndex(routingContext, JsonObject().put("error", "Service unavailable..."))
            } else {
                val itemsResult = it.result()
                val items = itemsResult.itemList

                routingContext.response().putHeader("X-Cache", if (itemsResult.isCacheHit) "HIT" else "MISS")
                routingContext.response().putHeader("X-Repository-Pre-Operation-Nanos", "" + itemsResult.preOperationProcessingTime)
                routingContext.response().putHeader("X-Repository-Operation-Nanos", "" + itemsResult.operationProcessingTime)
                routingContext.response().putHeader("X-Repository-Post-Operation-Nanos", "" + itemsResult.postOperationProcessingTime)

                if (items != null) {
                    routingContext.response().putHeader(HttpHeaders.ETAG, items.etag)

                    if (logger.isDebugEnabled) {
                        addLogMessageToRequestLog(routingContext,
                                "Projections for output is: " + Arrays.toString(projections))
                    }

                    postIndex(routingContext, items, projections)
                } else {
                    addLogMessageToRequestLog(routingContext, "FAILED ITEMS!")

                    failedIndex(routingContext, JsonObject().put("error", "Returned items is null!"))
                }
            }
        })
    }

    override fun proceedWithAggregationIndex(routingContext: RoutingContext, etag: String?, id: JsonObject,
                                             queryPack: QueryPack, projections: Array<String>) {
        if (logger.isDebugEnabled) {
            addLogMessageToRequestLog(routingContext, "Started aggregation request")
        }

        val function = queryPack.aggregateFunction
        val hashCode = if (function!!.groupBy == null) 0 else function.groupBy!!.hashCode()

        val etagKey = when (function.function) {
            MIN -> queryPack.baseEtagKey + "_" + function.field + "_MIN" + hashCode
            MAX -> queryPack.baseEtagKey + "_" + function.field + "_MAX" + hashCode
            AggregateFunctions.AVG -> queryPack.baseEtagKey + "_" + function.field + "_AVG" + hashCode
            AggregateFunctions.SUM -> queryPack.baseEtagKey + "_" + function.field + "_SUM" + hashCode
            AggregateFunctions.COUNT -> queryPack.baseEtagKey + "_COUNT" + hashCode
            else -> throw IllegalArgumentException("Illegal Function!")
        }

        if (etag != null && eTagManager != null) {
            val hash = id.encode().hashCode()
            val etagItemListHashKey = TYPE.simpleName + "_" + hash + "_" + "itemListEtags"

            eTagManager.checkAggregationEtag(etagItemListHashKey, etagKey, etag, Handler {
                if (it.succeeded() && it.result()) {
                    unChangedIndex(routingContext)
                } else {
                    doAggregation(routingContext, id, queryPack, projections)
                }
            })
        } else {
            doAggregation(routingContext, id, queryPack, projections)
        }
    }

    protected fun doAggregation(routingContext: RoutingContext, id: JsonObject,
                                queryPack: QueryPack, projections: Array<String>) {
        REPOSITORY.aggregation(id, queryPack, projections, Handler {
            if (it.failed()) {
                addLogMessageToRequestLog(routingContext,
                        "FAILED AGGREGATION: " + Json.encodePrettily(queryPack), it.cause())

                failedIndex(routingContext, JsonObject().put("error", "Aggregation Index failed..."))
            } else {
                val output = it.result()

                if (output != null) {
                    val newEtag = ModelUtils.returnNewEtag(output.hashCode().toLong())

                    routingContext.response().putHeader(HttpHeaders.ETAG, newEtag)

                    postAggregation(routingContext, output)
                } else {
                    addLogMessageToRequestLog(routingContext, "FAILED AGGREGATION, NULL")

                    failedIndex(routingContext, JsonObject().put("error", "Aggregation Index failed..."))
                }
            }
        })
    }

    override fun setIdentifiers(newRecord: E, routingContext: RoutingContext) {
        newRecord.setIdentifiers(getAndVerifyId(routingContext))

        preSanitizeForCreate(newRecord, routingContext)
    }

    override fun parseBodyForCreate(routingContext: RoutingContext) {
        val initialProcessNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        if (routingContext.body.bytes.isEmpty()) {
            try {
                preVerifyNotExists(TYPE.newInstance(), routingContext)
            } catch (e: InstantiationException) {
                addLogMessageToRequestLog(routingContext, "Unable to create empty body!", e)

                setStatusCodeAndAbort(500, routingContext, initialProcessNanoTime)
            } catch (e: IllegalAccessException) {
                addLogMessageToRequestLog(routingContext, "Unable to create empty body!", e)
                setStatusCodeAndAbort(500, routingContext, initialProcessNanoTime)
            }

        } else {
            try {
                val json = routingContext.bodyAsString
                val newRecord = Json.decodeValue(json, TYPE)

                preVerifyNotExists(newRecord, routingContext)
            } catch (e: DecodeException) {
                addLogMessageToRequestLog(routingContext, "Unable to parse body!", e)

                setStatusCodeAndAbort(500, routingContext, initialProcessNanoTime)
            }

        }
    }

    override fun verifyNotExists(newRecord: E, routingContext: RoutingContext) {
        val initialProcessNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val id = getAndVerifyId(routingContext)

        try {
            val e = TYPE.newInstance()

            if (e == null) {
                logger.error("Could not instantiate object of type: " + TYPE.simpleName)

                setStatusCodeAndAbort(500, routingContext, initialProcessNanoTime)
            } else {
                REPOSITORY.read(id, Handler {
                    if (it.succeeded()) {
                        setStatusCodeAndAbort(409, routingContext, initialProcessNanoTime)
                    } else {
                        e.setInitialValues(newRecord)
                        postVerifyNotExists(e, routingContext)
                    }
                })
            }
        } catch (ie: InstantiationException) {
            addLogMessageToRequestLog(routingContext, "Could not create item!", ie)

            setStatusCodeAndAbort(500, routingContext, initialProcessNanoTime)
        } catch (ie: IllegalAccessException) {
            addLogMessageToRequestLog(routingContext, "Could not create item!", ie)
            setStatusCodeAndAbort(500, routingContext, initialProcessNanoTime)
        }

    }

    override fun performCreate(newRecord: E, routingContext: RoutingContext) {
        REPOSITORY.create(newRecord, Handler {
            if (it.failed()) {
                addLogMessageToRequestLog(routingContext, "Could not create item!", it.cause())

                val errorObject = JsonObject()
                        .put("create_error", "Unable to create record...")

                failedCreate(routingContext, errorObject)
            } else {
                val finalRecordResult = it.result()
                val finalRecord = finalRecordResult.item

                routingContext.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .putHeader(HttpHeaders.ETAG, finalRecord.etag)

                postCreate(finalRecord, routingContext)
            }
        })
    }

    override fun parseBodyForUpdate(routingContext: RoutingContext) {
        val initialProcessNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val json = routingContext.bodyAsString

        if (json == null) {
            setStatusCodeAndAbort(422, routingContext, initialProcessNanoTime)
        } else {
            try {
                val newRecord = Json.decodeValue(json, TYPE)

                preVerifyExistsForUpdate(newRecord, routingContext)
            } catch (e: DecodeException) {
                addLogMessageToRequestLog(routingContext, "Unable to parse body!", e)

                setStatusCodeAndAbort(500, routingContext, initialProcessNanoTime)
            }

        }
    }

    override fun verifyExistsForUpdate(newRecord: E, routingContext: RoutingContext) {
        val initialProcessNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val id = getAndVerifyId(routingContext)

        if (id.isEmpty) {
            setStatusCodeAndAbort(400, routingContext, initialProcessNanoTime)
        } else {
            REPOSITORY.read(id, Handler {
                if (it.failed()) {
                    setStatusCodeAndAbort(404, routingContext, initialProcessNanoTime)
                } else {
                    val record = it.result().item

                    preSanitizeForUpdate(record, newRecord, routingContext)
                }
            })
        }
    }

    override fun performUpdate(updatedRecord: E, setNewValues: Function<E, E>, routingContext: RoutingContext) {
        REPOSITORY.update(updatedRecord, setNewValues, Handler {
            if (it.failed()) {
                failedUpdate(routingContext, JsonObject().put("error", "Unable to update record..."))
            } else {
                val finalRecordResult = it.result()
                val finalRecord = finalRecordResult.item

                routingContext.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .putHeader(HttpHeaders.ETAG, finalRecord.etag)

                postUpdate(finalRecord, routingContext)
            }
        })
    }

    override fun verifyExistsForDestroy(routingContext: RoutingContext) {
        val initialProcessNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val id = getAndVerifyId(routingContext)

        if (id.isEmpty) {
            setStatusCodeAndAbort(400, routingContext, initialProcessNanoTime)
        } else {
            REPOSITORY.read(id, Handler {
                if (it.failed()) {
                    logger.error("Could not find record!", it.cause())

                    setStatusCodeAndAbort(404, routingContext, initialProcessNanoTime)
                } else {
                    postVerifyExistsForDestroy(it.result().item, routingContext)
                }
            })
        }
    }

    override fun performDestroy(recordForDestroy: E, routingContext: RoutingContext) {
        val id = getAndVerifyId(routingContext)

        REPOSITORY.delete(id, Handler {
            if (it.failed()) {
                failedDestroy(routingContext, JsonObject().put("error", "Unable to destroy record!"))
            } else {
                val finalRecordResult = it.result()
                val finalRecord = finalRecordResult.item

                postDestroy(finalRecord, routingContext)
            }
        })
    }

    protected fun getAndVerifyId(routingContext: RoutingContext): JsonObject {
        return idSupplier.apply(routingContext)
    }

    private fun buildCollectionEtagKey(): String {
        return "data_api_" + COLLECTION + "_s_etag"
    }

    companion object {
        internal val logger = LoggerFactory.getLogger(RestControllerImpl::class.java.simpleName)

        const val PROJECTION_KEY = "projection"
        const val PROJECTION_FIELDS_KEY = "fields"
        const val ORDER_BY_KEY = "orderBy"
        const val AGGREGATE_KEY = "aggregate"

        const val MULTIPLE_IDS_KEY = "ids"
        const val PAGING_TOKEN_KEY = "pageToken"
        const val END_OF_PAGING_KEY = "END_OF_LIST"

        const val CONTROLLER_START_TIME = "controllerStartTimeTag"

        private val defaultSupplier = Function<RoutingContext, JsonObject> {
            val ids = JsonObject()

            it.pathParams().forEach({ key, value -> ids.put(key, value) })

            if (logger.isDebugEnabled) {
                logger.debug("Identifiers are: " + ids.encodePrettily())
            }

            ids
        }
    }
}