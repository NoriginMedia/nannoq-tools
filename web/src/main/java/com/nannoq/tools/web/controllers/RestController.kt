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

import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.models.Model.Companion.buildValidationErrorObject
import com.nannoq.tools.repository.models.ModelUtils
import com.nannoq.tools.repository.utils.AggregateFunction
import com.nannoq.tools.repository.utils.FilterParameter
import com.nannoq.tools.repository.utils.ItemList
import com.nannoq.tools.repository.utils.OrderByParameter
import com.nannoq.tools.repository.utils.QueryPack
import com.nannoq.tools.web.RoutingHelper.denyQuery
import com.nannoq.tools.web.RoutingHelper.setStatusCodeAndAbort
import com.nannoq.tools.web.RoutingHelper.setStatusCodeAndContinue
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.REQUEST_PROCESS_TIME_TAG
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.addLogMessageToRequestLog
import com.nannoq.tools.web.responsehandlers.ResponseLogHandler.Companion.BODY_CONTENT_TAG
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Date
import java.util.Queue
import java.util.function.Function
import org.apache.commons.lang3.ArrayUtils

@Suppress("unused")
/**
 * This interface defines the RestController. It defines a chain of operations for CRUD and Index operations. Overriding
 * functions must remember to call the next element in the chain.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
interface RestController<E> where E : ETagable, E : Model {
    fun show(routingContext: RoutingContext) {
        try {
            preShow(routingContext)
        } catch (e: Exception) {
            addLogMessageToRequestLog(routingContext, "Error in Show!", e)

            routingContext.fail(e)
        }
    }

    fun preShow(routingContext: RoutingContext) {
        performShow(routingContext)
    }

    fun performShow(routingContext: RoutingContext)

    fun postShow(routingContext: RoutingContext, item: E, projections: Array<String>?) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val requestEtag = routingContext.request().getHeader("If-None-Match")

        when {
            requestEtag != null && requestEtag == item.etag ->
                unChangedShow(routingContext)
            else -> {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                routingContext.put(BODY_CONTENT_TAG,
                        if (projections != null) item.toJsonString(projections) else item.toJsonString())

                setStatusCodeAndContinue(200, routingContext, initialNanoTime)
            }
        }
    }

    fun unChangedShow(routingContext: RoutingContext) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        setStatusCodeAndContinue(304, routingContext, initialNanoTime)
    }

    fun notFoundShow(routingContext: RoutingContext) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        setStatusCodeAndAbort(404, routingContext, initialNanoTime)
    }

    fun failedShow(routingContext: RoutingContext, debugInformation: JsonObject?) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        routingContext.put(BODY_CONTENT_TAG, debugInformation?.encode())
        setStatusCodeAndAbort(500, routingContext, initialNanoTime)
    }

    fun index(routingContext: RoutingContext) {
        try {
            preIndex(routingContext, null)
        } catch (e: Exception) {
            addLogMessageToRequestLog(routingContext, "Error in Index!", e)

            routingContext.fail(e)
        }
    }

    fun index(routingContext: RoutingContext, customQuery: String) {
        try {
            preIndex(routingContext, customQuery)
        } catch (e: Exception) {
            addLogMessageToRequestLog(routingContext, "Error in Index!", e)

            routingContext.fail(e)
        }
    }

    fun preIndex(routingContext: RoutingContext, customQuery: String?) {
        prepareQuery(routingContext, customQuery)
    }

    fun prepareQuery(routingContext: RoutingContext, customQuery: String?)

    fun preProcessQuery(routingContext: RoutingContext, queryMap: MutableMap<String, List<String>>) {
        processQuery(routingContext, queryMap)
    }

    fun processQuery(routingContext: RoutingContext, queryMap: MutableMap<String, List<String>>)

    fun postProcessQuery(
        routingContext: RoutingContext,
        aggregateFunction: AggregateFunction?,
        orderByQueue: Queue<OrderByParameter>,
        params: Map<String, MutableList<FilterParameter>>,
        projections: Array<String>,
        indexName: String,
        limit: Int?
    ) {
        postPrepareQuery(routingContext, aggregateFunction, orderByQueue, params, projections, indexName, limit)
    }

    fun postPrepareQuery(
        routingContext: RoutingContext,
        aggregateFunction: AggregateFunction?,
        orderByQueue: Queue<OrderByParameter>,
        params: Map<String, MutableList<FilterParameter>>,
        projections: Array<String>,
        indexName: String,
        limit: Int?
    ) {
        createIdObjectForIndex(routingContext, aggregateFunction, orderByQueue, params, projections, indexName, limit)
    }

    fun createIdObjectForIndex(
        routingContext: RoutingContext,
        aggregateFunction: AggregateFunction?,
        orderByQueue: Queue<OrderByParameter>,
        params: Map<String, MutableList<FilterParameter>>,
        projections: Array<String>,
        indexName: String,
        limit: Int?
    )

    fun performIndex(
        routingContext: RoutingContext,
        identifiers: JsonObject,
        aggregateFunction: AggregateFunction?,
        orderByQueue: Queue<OrderByParameter>,
        params: Map<String, MutableList<FilterParameter>>,
        projections: Array<String>,
        indexName: String,
        limit: Int?
    )

    fun proceedWithPagedIndex(
        id: JsonObject,
        pageToken: String?,
        queryPack: QueryPack,
        projections: Array<String>,
        routingContext: RoutingContext
    )

    fun proceedWithAggregationIndex(
        routingContext: RoutingContext,
        etag: String?,
        id: JsonObject,
        queryPack: QueryPack,
        projections: Array<String>
    )

    fun postIndex(routingContext: RoutingContext, items: ItemList<E>, projections: Array<String>?) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val requestEtag = routingContext.request().getHeader("If-None-Match")

        when {
            requestEtag != null && requestEtag == items.meta?.etag ->
                unChangedIndex(routingContext)
            else -> {
                val content = items.toJsonString(projections ?: arrayOf())

                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                routingContext.put(BODY_CONTENT_TAG, content)

                setStatusCodeAndContinue(200, routingContext, initialNanoTime)
            }
        }
    }

    fun postAggregation(routingContext: RoutingContext, content: String) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val requestEtag = routingContext.request().getHeader("If-None-Match")

        when {
            requestEtag != null && requestEtag == ModelUtils.returnNewEtag(content.hashCode().toLong()) ->
                unChangedIndex(routingContext)
            else -> {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                routingContext.put(BODY_CONTENT_TAG, content)

                setStatusCodeAndContinue(200, routingContext, initialNanoTime)
            }
        }
    }

    fun unChangedIndex(routingContext: RoutingContext) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        setStatusCodeAndContinue(304, routingContext, initialNanoTime)
    }

    fun failedIndex(routingContext: RoutingContext, debugInformation: JsonObject?) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        routingContext.put(BODY_CONTENT_TAG, debugInformation?.encode())
        setStatusCodeAndAbort(500, routingContext, initialNanoTime)
    }

    fun create(routingContext: RoutingContext) {
        try {
            preCreate(routingContext)
        } catch (e: Exception) {
            addLogMessageToRequestLog(routingContext, "Error in Create!", e)

            routingContext.fail(e)
        }
    }

    fun preCreate(routingContext: RoutingContext) {
        if (denyQuery(routingContext)) return

        parseBodyForCreate(routingContext)
    }

    fun parseBodyForCreate(routingContext: RoutingContext)

    fun preVerifyNotExists(newRecord: E, routingContext: RoutingContext) {
        verifyNotExists(newRecord, routingContext)
    }

    fun verifyNotExists(newRecord: E, routingContext: RoutingContext)

    fun postVerifyNotExists(newRecord: E, routingContext: RoutingContext) {
        preSetIdentifiers(newRecord, routingContext)
    }

    fun preSetIdentifiers(newRecord: E, routingContext: RoutingContext) {
        setIdentifiers(newRecord, routingContext)
    }

    fun setIdentifiers(newRecord: E, routingContext: RoutingContext)

    fun preSanitizeForCreate(record: E, routingContext: RoutingContext) {
        performSanitizeForCreate(record, routingContext)
    }

    fun performSanitizeForCreate(record: E, routingContext: RoutingContext) {
        record.sanitize()

        postSanitizeForCreate(record, routingContext)
    }

    fun postSanitizeForCreate(record: E, routingContext: RoutingContext) {
        preValidateForCreate(record, routingContext)
    }

    fun preValidateForCreate(record: E, routingContext: RoutingContext) {
        performValidateForCreate(record, routingContext)
    }

    fun performValidateForCreate(record: E, routingContext: RoutingContext) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val errors = record.validateCreate()

        when {
            errors.isNotEmpty() -> {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                routingContext.put(BODY_CONTENT_TAG, Json.encodePrettily(buildValidationErrorObject(errors)))
                setStatusCodeAndAbort(422, routingContext, initialNanoTime)
            }
            else -> postValidateForCreate(record, routingContext)
        }
    }

    fun postValidateForCreate(record: E, routingContext: RoutingContext) {
        performCreate(record, routingContext)
    }

    fun performCreate(newRecord: E, routingContext: RoutingContext)

    fun postCreate(createdRecord: E, routingContext: RoutingContext) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
        routingContext.put(BODY_CONTENT_TAG, createdRecord.toJsonString())
        setStatusCodeAndContinue(201, routingContext, initialNanoTime)
    }

    fun failedCreate(routingContext: RoutingContext, userFeedBack: JsonObject?) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        if (userFeedBack != null) {
            routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
            routingContext.put(BODY_CONTENT_TAG, userFeedBack.encode())
        }

        setStatusCodeAndContinue(500, routingContext, initialNanoTime)
    }

    fun update(routingContext: RoutingContext) {
        try {
            preUpdate(routingContext)
        } catch (e: Exception) {
            addLogMessageToRequestLog(routingContext, "Error in Update!", e)

            routingContext.fail(e)
        }
    }

    fun preUpdate(routingContext: RoutingContext) {
        if (denyQuery(routingContext)) return

        parseBodyForUpdate(routingContext)
    }

    fun parseBodyForUpdate(routingContext: RoutingContext)

    fun preVerifyExistsForUpdate(newRecord: E, routingContext: RoutingContext) {
        verifyExistsForUpdate(newRecord, routingContext)
    }

    fun verifyExistsForUpdate(newRecord: E, routingContext: RoutingContext)

    fun postVerifyExistsForUpdate(oldRecord: E, newRecord: E, routingContext: RoutingContext) {
        preSanitizeForUpdate(oldRecord, newRecord, routingContext)
    }

    fun preSanitizeForUpdate(record: E, newRecord: E, routingContext: RoutingContext) {
        performSanitizeForUpdate(record, newRecord, routingContext)
    }

    fun performSanitizeForUpdate(record: E, newRecord: E, routingContext: RoutingContext) {
        val setNewValues = Function<E, E> {
            it.setModifiables(newRecord)
            it.sanitize()

            it
        }

        postSanitizeForUpdate(setNewValues.apply(record), setNewValues, routingContext)
    }

    fun postSanitizeForUpdate(record: E, setNewValues: Function<E, E>, routingContext: RoutingContext) {
        preValidateForUpdate(record, setNewValues, routingContext)
    }

    fun preValidateForUpdate(record: E, setNewValues: Function<E, E>, routingContext: RoutingContext) {
        performValidateForUpdate(record, setNewValues, routingContext)
    }

    fun performValidateForUpdate(record: E, setNewValues: Function<E, E>, routingContext: RoutingContext) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        record.updatedAt = Date()
        val errors = record.validateUpdate()

        when {
            errors.isNotEmpty() -> {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                routingContext.put(BODY_CONTENT_TAG, Json.encodePrettily(buildValidationErrorObject(errors)))
                setStatusCodeAndAbort(422, routingContext, initialNanoTime)
            }
            else -> postValidateForUpdate(record, setNewValues, routingContext)
        }
    }

    fun postValidateForUpdate(record: E, setNewValues: Function<E, E>, routingContext: RoutingContext) {
        performUpdate(record, setNewValues, routingContext)
    }

    fun performUpdate(updatedRecord: E, setNewValues: Function<E, E>, routingContext: RoutingContext)

    fun postUpdate(updatedRecord: E, routingContext: RoutingContext) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
        routingContext.put(BODY_CONTENT_TAG, updatedRecord.toJsonString())
        setStatusCodeAndContinue(200, routingContext, initialNanoTime)
    }

    fun failedUpdate(routingContext: RoutingContext, userFeedBack: JsonObject?) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        if (userFeedBack != null) {
            routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
            routingContext.put(BODY_CONTENT_TAG, userFeedBack.encode())
        }

        setStatusCodeAndContinue(500, routingContext, initialNanoTime)
    }

    fun destroy(routingContext: RoutingContext) {
        try {
            preDestroy(routingContext)
        } catch (e: Exception) {
            addLogMessageToRequestLog(routingContext, "Error in Destroy!", e)

            routingContext.fail(e)
        }
    }

    fun preDestroy(routingContext: RoutingContext) {
        if (denyQuery(routingContext)) return

        verifyExistsForDestroy(routingContext)
    }

    fun verifyExistsForDestroy(routingContext: RoutingContext)

    fun postVerifyExistsForDestroy(recordForDestroy: E, routingContext: RoutingContext) {
        performDestroy(recordForDestroy, routingContext)
    }

    fun performDestroy(recordForDestroy: E, routingContext: RoutingContext)

    fun postDestroy(destroyedRecord: E, routingContext: RoutingContext) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        setStatusCodeAndContinue(204, routingContext, initialNanoTime)
    }

    fun failedDestroy(routingContext: RoutingContext, userFeedBack: JsonObject?) {
        val initialNanoTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)

        if (userFeedBack != null) {
            routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
            routingContext.put(BODY_CONTENT_TAG, userFeedBack.encode())
        }

        setStatusCodeAndContinue(500, routingContext, initialNanoTime)
    }

    fun getAllFieldsOnType(klazz: Class<*>): Array<Field> {
        val fields = klazz.declaredFields

        return if (klazz.superclass != null && klazz.superclass != Any::class.java) {
            ArrayUtils.addAll(fields, *getAllFieldsOnType(klazz.superclass))
        } else fields
    }

    fun getAllMethodsOnType(klazz: Class<*>): Array<Method> {
        val methods = klazz.declaredMethods

        return if (klazz.superclass != null && klazz.superclass != Any::class.java) {
            ArrayUtils.addAll(methods, *getAllMethodsOnType(klazz.superclass))
        } else methods
    }
}
