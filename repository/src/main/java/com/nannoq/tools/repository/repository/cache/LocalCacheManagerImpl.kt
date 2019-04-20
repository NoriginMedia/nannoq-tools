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

package com.nannoq.tools.repository.repository.cache

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.utils.ItemList
import com.nannoq.tools.repository.utils.ItemListMeta
import com.nannoq.tools.repository.utils.PageTokens
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.DecodeException
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.shareddata.LocalMap
import io.vertx.serviceproxy.ServiceException
import java.util.Arrays
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors.toList

/**
 * The LocalCacheManagerImpl contains the logic for setting, removing, and replace caches.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@Suppress("PrivatePropertyName")
class LocalCacheManagerImpl<E>(private val TYPE: Class<E>, private val vertx: Vertx) : CacheManager<E> where E : Model, E : Cacheable {
    private val ITEM_LIST_KEY_MAP: String = TYPE.simpleName + "/ITEMLIST"
    private val AGGREGATION_KEY_MAP: String = TYPE.simpleName + "/AGGREGATION"

    private val hasTypeJsonField: Boolean

    private val objectCache: LocalMap<String, String>?
        get() = vertx.sharedData().getLocalMap("objectCache")

    private val itemListCache: LocalMap<String, String>?
        get() = vertx.sharedData().getLocalMap("itemListCache")

    private val aggregationCache: LocalMap<String, String>?
        get() = vertx.sharedData().getLocalMap("aggregationCache")

    override val isObjectCacheAvailable: Boolean
        get() {
            val available = objectCache != null

            if (!available) {
                objectCache
            }

            return available
        }

    override val isItemListCacheAvailable: Boolean
        get() {
            val available = itemListCache != null

            if (!available) {
                itemListCache
            }

            return available
        }

    override val isAggregationCacheAvailable: Boolean
        get() {
            val available = aggregationCache != null

            if (!available) {
                aggregationCache
            }

            return available
        }

    init {
        hasTypeJsonField = Arrays.stream(TYPE.declaredAnnotations).anyMatch { a -> a is JsonTypeInfo }
    }

    override fun initializeCache(resultHandler: Handler<AsyncResult<Boolean>>) {
        if (cachesCreated) return

        cachesCreated = true

        resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
    }

    override fun checkObjectCache(cacheId: String, resultHandler: Handler<AsyncResult<E>>) {
        when {
            isObjectCacheAvailable -> {
                when (val content = objectCache!![cacheId]) {
                    null -> resultHandler.handle(ServiceException.fail(404, "Cache result is null!"))
                    else -> resultHandler.handle(Future.succeededFuture(Json.decodeValue(content, TYPE)))
                }
            }
            else -> {
                logger.error("ObjectCache is null, recreating...")

                resultHandler.handle(ServiceException.fail(404, "Unable to retrieve from cache, cache was null..."))
            }
        }
    }

    override fun checkItemListCache(
        cacheId: String,
        projections: Array<String>,
        resultHandler: Handler<AsyncResult<ItemList<E>>>
    ) {
        if (logger.isDebugEnabled) {
            logger.debug("Checking Item List Cache")
        }

        when {
            isItemListCacheAvailable -> {
                when (val content = itemListCache!![cacheId]) {
                    null -> resultHandler.handle(ServiceException.fail(404, "Cache result is null!"))
                    else -> try {
                        val jsonObject = JsonObject(content)
                        val jsonArray = jsonObject.getJsonArray("items")
                        val meta = ItemListMeta(jsonObject.getJsonObject("meta"))
                        val pageToken = PageTokens(jsonObject.getJsonObject("paging"))
                        val items = jsonArray.stream()
                                .map { json ->
                                    val obj = JsonObject(json.toString())

                                    if (hasTypeJsonField) {
                                        obj.put("@type", TYPE.simpleName)
                                    }

                                    Json.decodeValue(obj.encode(), TYPE)
                                }
                                .collect(toList())

                        val eItemList = ItemList<E>()
                        eItemList.items = items
                        eItemList.meta = meta
                        eItemList.paging = pageToken

                        resultHandler.handle(Future.succeededFuture(eItemList))
                    } catch (e: DecodeException) {
                        logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                        resultHandler.handle(ServiceException.fail(404, "Cache result is null...",
                                JsonObject(Json.encode(e))))
                    }
                }
            }
            else -> {
                logger.error("ItemList Cache is null, recreating...")

                resultHandler.handle(ServiceException.fail(404, "Unable to perform cache fetch, cache was null..."))
            }
        }
    }

    override fun checkAggregationCache(cacheKey: String, resultHandler: Handler<AsyncResult<String>>) {
        when {
            isAggregationCacheAvailable -> {
                when (val content = aggregationCache!![cacheKey]) {
                    null -> resultHandler.handle(ServiceException.fail(404, "Cache result is null..."))
                    else -> {
                        if (logger.isDebugEnabled) {
                            logger.debug("Returning cached content...")
                        }

                        resultHandler.handle(Future.succeededFuture(content))
                    }
                }
            }
            else -> resultHandler.handle(ServiceException.fail(404, "Cache is null..."))
        }
    }

    override fun replaceObjectCache(cacheId: String, item: E, future: Future<E>, projections: Array<String>) {
        when {
            isObjectCacheAvailable -> {
                val fullCacheContent = Json.encode(item)
                val jsonRepresentationCache = item.toJsonFormat(projections).encode()

                objectCache!!["FULL_CACHE_$cacheId"] = fullCacheContent
                objectCache!![cacheId] = jsonRepresentationCache

                future.complete(item)
            }
            else -> {
                logger.error("ObjectCache is null, recreating...")

                future.complete(item)
            }
        }
    }

    override fun replaceCache(
        writeFuture: Future<Boolean>,
        records: List<E>,
        shortCacheIdSupplier: Function<E, String>,
        cacheIdSupplier: Function<E, String>
    ) {
        when {
            isObjectCacheAvailable -> {
                records.forEach { record ->
                    val shortCacheId = shortCacheIdSupplier.apply(record)
                    val cacheId = cacheIdSupplier.apply(record)

                    objectCache!![cacheId] = record.toJsonString()
                    objectCache!![shortCacheId] = record.toJsonString()

                    val secondaryCache = "FULL_CACHE_$cacheId"
                    objectCache!![secondaryCache] = Json.encode(record)
                    objectCache!!["FULL_CACHE_$shortCacheId"] = Json.encode(record)
                }

                purgeSecondaryCaches(writeFuture)
            }
            else -> {
                logger.error("ObjectCache is null, recreating...")

                purgeSecondaryCaches(writeFuture)
            }
        }
    }

    override fun replaceItemListCache(
        content: String,
        cacheIdSupplier: Supplier<String>,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        when {
            isItemListCacheAvailable -> {
                val cacheId = cacheIdSupplier.get()

                itemListCache!![cacheId] = content
                replaceMapValues(ITEM_LIST_KEY_MAP, cacheId)

                resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
            }
            else -> {
                logger.error("ItemListCache is null, recreating...")

                resultHandler.handle(ServiceException.fail(500, "Itemlist cache does not exist!"))
            }
        }
    }

    override fun replaceAggregationCache(
        content: String,
        cacheIdSupplier: Supplier<String>,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        when {
            isAggregationCacheAvailable -> {
                val cacheKey = cacheIdSupplier.get()

                aggregationCache!![cacheKey] = content
                replaceMapValues(AGGREGATION_KEY_MAP, cacheKey)

                resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
            }
            else -> {
                logger.error("AggregationCache is null, recreating...")

                resultHandler.handle(ServiceException.fail(500, "Aggregation cache does not exist!"))
            }
        }
    }

    private fun replaceMapValues(AGGREGATION_KEY_MAP: String, cacheKey: String) {
        val map = vertx.sharedData().getLocalMap<String, String>(AGGREGATION_KEY_MAP)
        var idSet: String? = map[TYPE.simpleName]

        when (idSet) {
            null -> {
                idSet = JsonArray()
                        .add(cacheKey)
                        .encode()

                map[TYPE.simpleName] = idSet
            }
            else -> map.replace(TYPE.simpleName, JsonArray(idSet).add(cacheKey).encode())
        }
    }

    override fun purgeCache(future: Future<Boolean>, records: List<E>, cacheIdSupplier: (E) -> String) {
        when {
            isObjectCacheAvailable -> {
                records.forEach { record ->
                    val cacheId = cacheIdSupplier(record)
                    val secondaryCache = "FULL_CACHE_$cacheId"

                    objectCache!!.remove(cacheId)
                    objectCache!!.remove(secondaryCache)
                }

                purgeSecondaryCaches(future)
            }
            else -> {
                logger.error("ObjectCache is null, recreating...")

                purgeSecondaryCaches(future)
            }
        }
    }

    private fun purgeSecondaryCaches(resultHandler: Handler<AsyncResult<Boolean>>) {
        when {
            isItemListCacheAvailable -> purgeMap(ITEM_LIST_KEY_MAP, itemListCache)
            else -> logger.error("ItemListCache is null, recreating...")
        }

        when {
            isAggregationCacheAvailable -> purgeMap(AGGREGATION_KEY_MAP, aggregationCache)
            else -> logger.error("AggregateCache is null, recreating...")
        }

        resultHandler.handle(Future.succeededFuture())
    }

    private fun purgeMap(MAP_KEY: String, cache: MutableMap<String, String>?) {
        try {
            val localMap = vertx.sharedData().getLocalMap<String, String>(MAP_KEY)

            try {
                val cachePartitionKey = TYPE.getDeclaredConstructor().newInstance().cachePartitionKey

                val strings = localMap[cachePartitionKey]

                when {
                    strings != null -> JsonArray(strings).stream()
                            .map { it.toString() }
                            .forEach { cache!!.remove(it) }
                    else -> localMap[cachePartitionKey] = JsonArray().encode()
                }
            } catch (e: InstantiationException) {
                logger.error("Unable to build partitionKey", e)
            } catch (e: IllegalAccessException) {
                logger.error("Unable to build partitionKey", e)
            }

            if (logger.isDebugEnabled) {
                logger.debug("Cache cleared: " + cache!!.size)
            }
        } catch (e: Exception) {
            logger.error(e)
            logger.error("Unable to purge cache, nulling...")

            cache!!.clear()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LocalCacheManagerImpl::class.java.simpleName)

        private var cachesCreated = false
    }
}
