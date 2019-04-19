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

package com.nannoq.tools.repository.repository

import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.models.ModelUtils
import com.nannoq.tools.repository.repository.etag.ETagManager
import com.nannoq.tools.repository.repository.results.CreateResult
import com.nannoq.tools.repository.repository.results.DeleteResult
import com.nannoq.tools.repository.repository.results.ItemListResult
import com.nannoq.tools.repository.repository.results.ItemResult
import com.nannoq.tools.repository.repository.results.UpdateResult
import com.nannoq.tools.repository.utils.FilterParameter
import com.nannoq.tools.repository.utils.OrderByParameter
import com.nannoq.tools.repository.utils.QueryPack
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.serviceproxy.ServiceException
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.security.NoSuchAlgorithmException
import java.util.AbstractMap.SimpleEntry
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.Collections
import java.util.Date
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Function
import java.util.stream.Collectors.toList
import java.util.stream.Collectors.toMap

/**
 * The repository interface is the base of the Repository tools. It defines a contract for all repositories and contains
 * standardized logic.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
interface Repository<E : Model> {
    val etagManager: ETagManager<*>?

    val isCached: Boolean
    val isEtagEnabled: Boolean

    enum class INCREMENTATION {
        ADDITION, SUBTRACTION
    }

    fun create(record: E, resultHandler: Handler<AsyncResult<CreateResult<E>>>) {
        batchCreate(listOf(record), Handler {
            when {
                it.failed() -> resultHandler.handle(Future.failedFuture<CreateResult<E>>(it.cause()))
                else -> {
                    val iterator = it.result().iterator()

                    when {
                        iterator.hasNext() -> resultHandler.handle(Future.succeededFuture(iterator.next()))
                        else -> resultHandler.handle(Future.failedFuture(NullPointerException()))
                    }
                }
            }
        })
    }

    fun create(record: E): Future<CreateResult<E>> {
        val createFuture = Future.future<CreateResult<E>>()

        create(record, Handler {
            when {
                it.failed() -> createFuture.fail(it.cause())
                else -> createFuture.complete(it.result())
            }
        })

        return createFuture
    }

    fun batchCreate(records: List<E>, resultHandler: Handler<AsyncResult<List<CreateResult<E>>>>) {
        doWrite(true, records.stream()
                .map { r -> SimpleEntry<E, Function<E, E>>(r, Function { record -> record }) }
                .collect(toMap({ it.key }, { it.value })), Handler { res ->
            when {
                res.failed() -> resultHandler.handle(Future.failedFuture<List<CreateResult<E>>>(res.cause()))
                else -> resultHandler.handle(Future.succeededFuture(res.result().stream()
                        .map { CreateResult(it) }
                        .collect(toList<CreateResult<E>>())))
            }
        })
    }

    fun batchCreate(records: List<E>): Future<List<CreateResult<E>>> {
        val createFuture = Future.future<List<CreateResult<E>>>()

        batchCreate(records, Handler {
            when {
                it.failed() -> createFuture.fail(it.cause())
                else -> createFuture.complete(it.result())
            }
        })

        return createFuture
    }

    fun update(record: E, resultHandler: Handler<AsyncResult<UpdateResult<E>>>) {
        batchUpdate(Collections.singletonMap<E, Function<E, E>>(record, Function { r -> r }), Handler {
            doUpdate(resultHandler, it)
        })
    }

    fun update(record: E): Future<UpdateResult<E>> {
        val updateFuture = Future.future<UpdateResult<E>>()

        update(record, Function { r -> r }, Handler {
            when {
                it.failed() -> updateFuture.fail(it.cause())
                else -> updateFuture.complete(it.result())
            }
        })

        return updateFuture
    }

    fun update(record: E, updateLogic: Function<E, E>, resultHandler: Handler<AsyncResult<UpdateResult<E>>>) {
        batchUpdate(Collections.singletonMap(record, updateLogic), Handler { doUpdate(resultHandler, it) })
    }

    fun doUpdate(resultHandler: Handler<AsyncResult<UpdateResult<E>>>, res: AsyncResult<List<UpdateResult<E>>>) {
        when {
            res.failed() -> resultHandler.handle(Future.failedFuture(res.cause()))
            else -> {
                val iterator = res.result().iterator()

                when {
                    iterator.hasNext() -> resultHandler.handle(Future.succeededFuture(iterator.next()))
                    else -> resultHandler.handle(Future.failedFuture(NullPointerException()))
                }
            }
        }
    }

    fun update(record: E, updateLogic: Function<E, E>): Future<UpdateResult<E>> {
        val updateFuture = Future.future<UpdateResult<E>>()

        update(record, updateLogic, Handler {
            when {
                it.failed() -> updateFuture.fail(it.cause())
                else -> updateFuture.complete(it.result())
            }
        })

        return updateFuture
    }

    fun batchUpdate(records: List<E>, resultHandler: Handler<AsyncResult<List<UpdateResult<E>>>>) {
        val collect: Map<E, Function<E, E>> = records
                .map { r -> SimpleImmutableEntry<E, Function<E, E>>(r, Function { rec -> rec }) }
                .fold(HashMap()) { accumulator, item -> accumulator[item.key] = item.value; accumulator }

        batchUpdate(collect, resultHandler)
    }

    fun batchUpdate(records: Map<E, Function<E, E>>, resultHandler: Handler<AsyncResult<List<UpdateResult<E>>>>) {
        doWrite(false, records, Handler { res ->
            when {
                res.failed() -> resultHandler.handle(Future.failedFuture<List<UpdateResult<E>>>(res.cause()))
                else -> resultHandler.handle(Future.succeededFuture(res.result().stream()
                        .map { UpdateResult(it) }
                        .collect(toList<UpdateResult<E>>())))
            }
        })
    }

    fun batchUpdate(records: List<E>): Future<List<UpdateResult<E>>> {
        val future = Future.future<List<UpdateResult<E>>>()

        batchUpdate(records, future)

        return future
    }

    fun batchUpdate(records: Map<E, Function<E, E>>): Future<List<UpdateResult<E>>> {
        val updateFuture = Future.future<List<UpdateResult<E>>>()

        batchUpdate(records, Handler {
            when {
                it.failed() -> updateFuture.fail(it.cause())
                else -> updateFuture.complete(it.result())
            }
        })

        return updateFuture
    }

    fun delete(identifiers: JsonObject, resultHandler: Handler<AsyncResult<DeleteResult<E>>>) {
        when {
            identifiers.isEmpty -> resultHandler.handle(ServiceException.fail(400,
                    "Identifier for remoteDelete cannot be empty!"))
            else -> batchDelete(listOf(identifiers), Handler {
                when {
                    it.failed() -> resultHandler.handle(Future.failedFuture<DeleteResult<E>>(it.cause()))
                    else -> {
                        val iterator = it.result().iterator()

                        when {
                            iterator.hasNext() -> resultHandler.handle(Future.succeededFuture(iterator.next()))
                            else -> resultHandler.handle(Future.failedFuture(NullPointerException()))
                        }
                    }
                }
            })
        }
    }

    fun delete(identifiers: JsonObject): Future<DeleteResult<E>> {
        val deleteFuture = Future.future<DeleteResult<E>>()

        delete(identifiers, Handler {
            when {
                it.failed() -> deleteFuture.fail(it.cause())
                else -> deleteFuture.complete(it.result())
            }
        })

        return deleteFuture
    }

    fun batchDelete(
        identifiers: List<JsonObject>,
        resultHandler: Handler<AsyncResult<List<DeleteResult<E>>>>
    ) {
        when {
            identifiers.isEmpty() -> resultHandler.handle(ServiceException.fail(400,
                    "Identifiers for batchDelete cannot be empty!"))
            else -> doDelete(identifiers, Handler { res ->
                when {
                    res.failed() -> resultHandler.handle(Future.failedFuture<List<DeleteResult<E>>>(res.cause()))
                    else -> resultHandler.handle(Future.succeededFuture(res.result().stream()
                            .map { DeleteResult(it) }
                            .collect(toList<DeleteResult<E>>())))
                }
            })
        }
    }

    fun batchDelete(identifiers: List<JsonObject>): Future<List<DeleteResult<E>>> {
        val deleteFuture = Future.future<List<DeleteResult<E>>>()

        batchDelete(identifiers, Handler {
            when {
                it.failed() -> deleteFuture.fail(it.cause())
                else -> deleteFuture.complete(it.result())
            }
        })

        return deleteFuture
    }

    @Throws(IllegalArgumentException::class)
    fun incrementField(record: E, fieldName: String): Function<E, E>

    @Throws(IllegalArgumentException::class)
    fun decrementField(record: E, fieldName: String): Function<E, E>

    fun read(identifiers: JsonObject, resultHandler: Handler<AsyncResult<ItemResult<E>>>)

    fun read(identifiers: JsonObject, projections: Array<String>, resultHandler: Handler<AsyncResult<ItemResult<E>>>)

    fun read(identifiers: JsonObject): Future<ItemResult<E>> {
        val readFuture = Future.future<ItemResult<E>>()

        read(identifiers, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun batchRead(identifiers: Set<JsonObject>, resultHandler: Handler<AsyncResult<List<ItemResult<E>>>>) {
        batchRead(ArrayList(identifiers), resultHandler)
    }

    fun batchRead(
        identifiers: Set<JsonObject>,
        projections: Array<String>,
        resultHandler: Handler<AsyncResult<List<ItemResult<E>>>>
    ) {
        batchRead(ArrayList(identifiers), resultHandler)
    }

    fun batchRead(
        identifiers: List<JsonObject>,
        resultHandler: Handler<AsyncResult<List<ItemResult<E>>>>
    ) {
        batchRead(ArrayList(identifiers), null, resultHandler)
    }

    fun batchRead(
        identifiers: List<JsonObject>,
        projections: Array<String>?,
        resultHandler: Handler<AsyncResult<List<ItemResult<E>>>>
    ) {
        val futureList = ArrayList<Future<*>>()
        val queuedFutures = ConcurrentLinkedQueue<Future<ItemResult<E>>>()

        identifiers.stream().forEachOrdered {
            val future = Future.future<ItemResult<E>>()
            futureList.add(future)
            queuedFutures.add(future)

            when {
                projections != null -> read(it, projections, future)
                else -> read(it, future)
            }
        }

        CompositeFuture.all(futureList).setHandler { res ->
            when {
                res.failed() -> resultHandler.handle(ServiceException.fail(500, "Unable to performed batchread!",
                        JsonObject().put("ids", identifiers)))
                else -> {
                    val results = queuedFutures.stream()
                            .map { it.result() }
                            .collect(toList())

                    resultHandler.handle(Future.succeededFuture(results))
                }
            }
        }
    }

    fun batchRead(identifiers: List<JsonObject>, projections: Array<String> = arrayOf()): Future<List<ItemResult<E>>> {
        val future = Future.future<List<ItemResult<E>>>()

        batchRead(identifiers, projections, future)

        return future
    }

    fun read(identifiers: JsonObject, consistent: Boolean, resultHandler: Handler<AsyncResult<ItemResult<E>>>) {
        read(identifiers, consistent, null, resultHandler)
    }

    fun read(identifiers: JsonObject, consistent: Boolean): Future<ItemResult<E>> {
        val readFuture = Future.future<ItemResult<E>>()

        read(identifiers, consistent, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun read(identifiers: JsonObject, consistent: Boolean, projections: Array<String>?, resultHandler: Handler<AsyncResult<ItemResult<E>>>)

    fun read(identifiers: JsonObject, consistent: Boolean, projections: Array<String>): Future<ItemResult<E>> {
        val readFuture = Future.future<ItemResult<E>>()

        read(identifiers, consistent, projections, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun readAll(resultHandler: Handler<AsyncResult<List<E>>>)

    fun readAll(): Future<List<E>> {
        val readFuture = Future.future<List<E>>()

        readAll(Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun readAll(pageToken: String?, resultHandler: Handler<AsyncResult<ItemListResult<E>>>) {
        readAll(null, pageToken, null, null, resultHandler)
    }

    fun readAll(pageToken: String?): Future<ItemListResult<E>> {
        val readFuture = Future.future<ItemListResult<E>>()

        readAll(null, pageToken, null, null, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun readAll(
        identifiers: JsonObject,
        filterParameterMap: Map<String, List<FilterParameter>>,
        resultHandler: Handler<AsyncResult<List<E>>>
    )

    fun readAll(identifiers: JsonObject, filterParamterMap: Map<String, List<FilterParameter>>): Future<List<E>> {
        val readFuture = Future.future<List<E>>()

        readAll(identifiers, filterParamterMap, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun readAll(
        identifiers: JsonObject,
        queryPack: QueryPack,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        readAll(identifiers, queryPack.pageToken, queryPack, queryPack.projections, resultHandler)
    }

    fun readAll(identifiers: JsonObject, queryPack: QueryPack): Future<ItemListResult<E>> {
        val future = Future.future<ItemListResult<E>>()

        readAll(identifiers, queryPack.pageToken, queryPack, queryPack.projections, future)

        return future
    }

    fun readAll(
        identifiers: JsonObject?,
        pageToken: String?,
        queryPack: QueryPack?,
        projections: Array<String>?,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    )

    fun readAll(
        identifiers: JsonObject,
        pageToken: String?,
        queryPack: QueryPack,
        projections: Array<String>
    ): Future<ItemListResult<E>> {
        val readFuture = Future.future<ItemListResult<E>>()

        readAll(identifiers, pageToken, queryPack, projections, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun readAll(
        pageToken: String?,
        queryPack: QueryPack,
        projections: Array<String>,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    )

    fun readAll(
        pageToken: String?,
        queryPack: QueryPack,
        projections: Array<String>
    ): Future<ItemListResult<E>> {
        val readFuture = Future.future<ItemListResult<E>>()

        readAll(pageToken, queryPack, projections, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun aggregation(identifiers: JsonObject, queryPack: QueryPack, resultHandler: Handler<AsyncResult<String>>) {
        aggregation(identifiers, queryPack, queryPack.projections, resultHandler)
    }

    fun aggregation(identifiers: JsonObject, queryPack: QueryPack): Future<String> {
        val readFuture = Future.future<String>()

        aggregation(identifiers, queryPack, readFuture)

        return readFuture
    }

    fun aggregation(
        identifiers: JsonObject,
        queryPack: QueryPack,
        projections: Array<String>?,
        resultHandler: Handler<AsyncResult<String>>
    )

    fun aggregation(identifiers: JsonObject, queryPack: QueryPack, projections: Array<String>): Future<String> {
        val readFuture = Future.future<String>()

        aggregation(identifiers, queryPack, projections, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun buildParameters(
        queryMap: Map<String, List<String>>,
        fields: Array<Field>,
        methods: Array<Method>,
        errors: JsonObject,
        params: Map<String, List<FilterParameter>>,
        limit: IntArray,
        orderByQueue: Queue<OrderByParameter>,
        indexName: Array<String>
    ): JsonObject

    fun parseParam(
        type: Class<E>,
        paramJsonString: String,
        key: String,
        params: MutableMap<String, List<FilterParameter>>,
        errors: JsonObject
    ) {
        val filterParameters = Json.decodeValue<FilterParameter>(paramJsonString, FilterParameter::class.java)

        when {
            filterParameters != null -> {
                filterParameters.setField(key)

                when {
                    filterParameters.isValid -> {
                        var filterParameterList: MutableList<FilterParameter>? = params[key]?.toMutableList()
                        if (filterParameterList == null) filterParameterList = ArrayList()
                        filterParameterList.add(filterParameters)
                        params[key] = filterParameterList
                    }
                    else -> filterParameters.collectErrors(errors)
                }
            }
            else -> errors.put(key + "_error", "Unable to parse JSON in '$key' value!")
        }
    }

    fun readAllWithoutPagination(identifier: String, resultHandler: Handler<AsyncResult<List<E>>>)

    fun readAllWithoutPagination(identifier: String): Future<List<E>> {
        val readFuture = Future.future<List<E>>()

        readAllWithoutPagination(identifier, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun readAllWithoutPagination(identifier: String, queryPack: QueryPack, resultHandler: Handler<AsyncResult<List<E>>>)

    fun readAllWithoutPagination(identifier: String, queryPack: QueryPack): Future<List<E>> {
        val readFuture = Future.future<List<E>>()

        readAllWithoutPagination(identifier, queryPack, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun readAllWithoutPagination(identifier: String, queryPack: QueryPack, projections: Array<String>, resultHandler: Handler<AsyncResult<List<E>>>)

    fun readAllWithoutPagination(identifier: String, queryPack: QueryPack, projections: Array<String>): Future<List<E>> {
        val readFuture = Future.future<List<E>>()

        readAllWithoutPagination(identifier, queryPack, projections, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun readAllWithoutPagination(queryPack: QueryPack, resultHandler: Handler<AsyncResult<List<E>>>) {
        readAllWithoutPagination(queryPack, null, resultHandler)
    }

    fun readAllWithoutPagination(queryPack: QueryPack, projections: Array<String>?, resultHandler: Handler<AsyncResult<List<E>>>)

    fun readAllWithoutPagination(queryPack: QueryPack, projections: Array<String> = arrayOf()): Future<List<E>> {
        val readFuture = Future.future<List<E>>()

        readAllWithoutPagination(queryPack, projections, Handler {
            when {
                it.failed() -> readFuture.fail(it.cause())
                else -> readFuture.complete(it.result())
            }
        })

        return readFuture
    }

    fun doWrite(create: Boolean, records: Map<E, Function<E, E>>, resultHandler: Handler<AsyncResult<List<E>>>)

    fun setCreatedAt(record: E): E {
        @Suppress("UNCHECKED_CAST")
        return record.setCreatedAt(Date()) as E
    }

    fun setUpdatedAt(record: E): E {
        @Suppress("UNCHECKED_CAST")
        return record.setUpdatedAt(Date()) as E
    }

    fun doDelete(identifiers: List<JsonObject>, resultHandler: Handler<AsyncResult<List<E>>>)

    fun buildCollectionEtag(etags: List<String>): String {
        val newEtag = LongArray(1)

        return try {
            etags.forEach { etag -> newEtag[0] += etag.hashCode().toLong() }

            ModelUtils.hashString(newEtag[0].toString())
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()

            "noTagForCollection"
        } catch (e: NullPointerException) {
            e.printStackTrace()
            "noTagForCollection"
        }
    }

    fun extractValueAsDouble(field: Field, r: E): Double {
        try {
            return field.getLong(r).toDouble()
        } catch (e: IllegalArgumentException) {
            try {
                return (field.get(r) as Long).toDouble()
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: IllegalAccessException) {
            }
        } catch (e: IllegalAccessException) {
            try {
                return (field.get(r) as Long).toDouble()
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: IllegalAccessException) {
            }
        }

        try {
            return field.getInt(r).toDouble()
        } catch (e: IllegalArgumentException) {
            try {
                return (field.get(r) as Int).toDouble()
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: IllegalAccessException) {
            }
        } catch (e: IllegalAccessException) {
            try {
                return (field.get(r) as Int).toDouble()
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: IllegalAccessException) {
            }
        }

        try {
            return field.getShort(r).toDouble()
        } catch (e: IllegalArgumentException) {
            try {
                return (field.get(r) as Short).toDouble()
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: IllegalAccessException) {
            }
        } catch (e: IllegalAccessException) {
            try {
                return (field.get(r) as Short).toDouble()
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: IllegalAccessException) {
            }
        }

        try {
            return field.getDouble(r)
        } catch (ignored: IllegalArgumentException) {
        } catch (ignored: IllegalAccessException) {
        }

        try {
            return field.getFloat(r).toDouble()
        } catch (e: IllegalArgumentException) {
            try {
                return (field.get(r) as Float).toDouble()
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: IllegalAccessException) {
            }
        } catch (e: IllegalAccessException) {
            try {
                return (field.get(r) as Float).toDouble()
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: IllegalAccessException) {
            }
        }

        logger.error("Conversion is null!")

        throw IllegalArgumentException()
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Repository::class.java.simpleName)

        const val ORDER_BY_KEY = "orderBy"
        const val LIMIT_KEY = "limit"
        const val PROJECTION_KEY = "projection"
        const val MULTIPLE_KEY = "multiple"
        const val MULTIPLE_IDS_KEY = "ids"
    }
}
