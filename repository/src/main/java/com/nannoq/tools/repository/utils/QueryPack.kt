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

package com.nannoq.tools.repository.utils

import com.nannoq.tools.repository.models.ModelUtils
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import java.util.*

/**
 * This class defines the querypack. A querypack includes the orderByQueue, the map of filterparameters to be performed,
 * and any aggregate function.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class QueryPack internal constructor() {
    var query: String? = null
        private set
    var baseEtagKey: String? = null
        private set
    var route: String? = null
        private set
    var pageToken: String? = null
        private set
    var requestEtag: String? = null
        private set
    var orderByQueue: Queue<OrderByParameter>? = null
        private set
    var params: MutableMap<String, MutableList<FilterParameter>>? = null
        private set
    var aggregateFunction: AggregateFunction? = null
        private set
    var projections: Array<String>? = null
        private set
    var indexName: String? = null
        private set
    var limit: Int? = null
        private set

    class QueryPackBuilder internal constructor(model: Class<*>?) {

        private var query: String? = null
        private var route: String? = null
        private var pageToken: String? = null
        private var requestEtag: String? = null
        private var orderByQueue: Queue<OrderByParameter>? = null
        private var params: MutableMap<String, MutableList<FilterParameter>>? = null
        private var aggregateFunction: AggregateFunction? = null
        private var projections: Array<String>? = null
        private var indexName: String? = null
        private var limit: Int? = null

        init {
            if (model != null) {
                route = model.simpleName
            }
        }

        fun build(): QueryPack {
            if (route == null) {
                throw IllegalArgumentException("Route cannot be null, " + "set class in constructor, or use withRoutingContext or withCustomRoute!")
            }

            val queryPack = QueryPack()
            queryPack.query = query
            queryPack.route = route
            queryPack.pageToken = pageToken
            queryPack.requestEtag = requestEtag
            queryPack.orderByQueue = orderByQueue
            queryPack.projections = projections
            queryPack.params = params
            queryPack.aggregateFunction = aggregateFunction
            queryPack.indexName = indexName
            queryPack.limit = limit
            queryPack.calculateKey()

            return queryPack
        }

        @Fluent
        fun withRoutingContext(routingContext: RoutingContext): QueryPackBuilder {
            this.requestEtag = routingContext.request().getHeader("If-None-Match")
            this.pageToken = routingContext.request().getParam("paging")
            this.query = routingContext.request().query()
            this.route = routingContext.request().path()

            return this
        }

        @Fluent
        fun withCustomRoute(route: String): QueryPackBuilder {
            this.route = route

            return this
        }

        @Fluent
        fun withCustomQuery(query: String): QueryPackBuilder {
            this.query = query

            return this
        }

        @Fluent
        fun withPageToken(pageToken: String?): QueryPackBuilder {
            this.pageToken = pageToken

            return this
        }

        @Fluent
        fun withProjections(projections: Array<String>): QueryPackBuilder {
            this.projections = projections

            return this
        }

        @Fluent
        fun withRequestEtag(requestEtag: String): QueryPackBuilder {
            this.requestEtag = requestEtag

            return this
        }

        @Fluent
        fun withFilterParameters(params: Map<String, MutableList<FilterParameter>>): QueryPackBuilder {
            this.params = LinkedHashMap()
            this.params!!.putAll(params)

            return this
        }

        @Fluent
        fun addFilterParameter(field: String, param: FilterParameter): QueryPackBuilder {
            if (this.params == null) this.params = LinkedHashMap()
            if (params!![field] != null) {
                params!![field]?.add(param)
            } else {
                params!![field] = mutableListOf(param)
            }

            return this
        }

        @Fluent
        fun addFilterParameters(field: String, parameters: List<FilterParameter>): QueryPackBuilder {
            if (this.params == null) this.params = LinkedHashMap()
            if (params!![field] != null) {
                val filterParameters = params!![field]
                filterParameters?.addAll(parameters)
                params!![field] = filterParameters!!
            } else {
                params!![field] = parameters.toMutableList()
            }

            return this
        }

        @Fluent
        fun withOrderByQueue(orderByQueue: Queue<OrderByParameter>): QueryPackBuilder {
            this.orderByQueue = orderByQueue

            return this
        }

        @Fluent
        fun withAggregateFunction(aggregateFunction: AggregateFunction?): QueryPackBuilder {
            this.aggregateFunction = aggregateFunction

            return this
        }

        @Fluent
        fun withIndexName(indexName: String): QueryPackBuilder {
            this.indexName = indexName

            return this
        }

        @Fluent
        fun withLimit(limit: Int?): QueryPackBuilder {
            this.limit = limit

            return this
        }

        companion object {
            private val logger = LoggerFactory.getLogger(QueryPackBuilder::class.java.simpleName)
        }
    }

    private fun calculateKey() {
        baseEtagKey = ModelUtils.returnNewEtag(Objects.hashCode(this).toLong())
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val queryPack = o as QueryPack?

        return baseEtagKey == queryPack!!.baseEtagKey
    }

    override fun hashCode(): Int {
        val hash = intArrayOf(Objects.hash(query, route, pageToken, params, aggregateFunction, indexName, limit))

        if (orderByQueue != null) {
            if (orderByQueue!!.size > 0) {
                orderByQueue!!.forEach { o -> hash[0] = hash[0] xor o.hashCode() }
            }
        }

        if (projections != null) {
            hash[0] = hash[0] xor Arrays.hashCode(projections)
        }

        return hash[0]
    }

    companion object {

        @JvmOverloads
        fun builder(model: Class<*>? = null): QueryPackBuilder {
            return QueryPackBuilder(model)
        }
    }
}
