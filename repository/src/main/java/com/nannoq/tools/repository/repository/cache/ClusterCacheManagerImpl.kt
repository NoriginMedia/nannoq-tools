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

package com.nannoq.tools.repository.repository.cache

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.hazelcast.cache.CacheNotExistsException
import com.hazelcast.cache.HazelcastCachingProvider
import com.hazelcast.cache.ICache
import com.hazelcast.core.ExecutionCallback
import com.hazelcast.core.Hazelcast
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.utils.ItemList
import com.nannoq.tools.repository.utils.ItemListMeta
import com.nannoq.tools.repository.utils.PageTokens
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.DecodeException
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.shareddata.AsyncMap
import io.vertx.serviceproxy.ServiceException
import java.util.Arrays
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors.toList
import javax.cache.CacheException
import javax.cache.Caching
import javax.cache.configuration.CompleteConfiguration
import javax.cache.configuration.MutableConfiguration
import javax.cache.expiry.AccessedExpiryPolicy
import javax.cache.expiry.Duration.FIVE_MINUTES

/**
 * The ClusterCacheManagerImpl contains the logic for setting, removing, and replace caches.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class ClusterCacheManagerImpl<E>(private val TYPE: Class<E>, private val vertx: Vertx) : CacheManager<E> where E : Cacheable, E : Model {
    private val ITEM_LIST_KEY_MAP: String = TYPE.simpleName + "/ITEMLIST"
    private val AGGREGATION_KEY_MAP: String = TYPE.simpleName + "/AGGREGATION"

    private val CACHE_READ_TIMEOUT_VALUE = 500L
    private val CACHE_WRITE_TIMEOUT_VALUE = 10000L
    private val expiryPolicy = AccessedExpiryPolicy.factoryOf(FIVE_MINUTES).create()

    private val hasTypeJsonField: Boolean

    init {

        hasTypeJsonField = Arrays.stream(TYPE.declaredAnnotations).anyMatch { a -> a is JsonTypeInfo }
    }

    override fun initializeCache(resultHandler: Handler<AsyncResult<Boolean>>) {
        if (cachesCreated) return

        vertx.executeBlocking<Boolean>({
            try {
                objectCache = createCache("object")
                itemListCache = createCache("itemList")
                aggregationCache = createCache("aggregation")

                it.complete(true)
            } catch (e: CacheException) {
                logger.error("Cache creation interrupted: " + e.message)

                it.fail(e)
            }
        }, false) {
            if (it.failed()) {
                resultHandler.handle(Future.failedFuture(it.cause()))
            } else {
                cachesCreated = true

                resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
            }
        }
    }

    private fun createCache(cacheName: String): ICache<String, String>? {
        val instances = Hazelcast.getAllHazelcastInstances()
        val hzOpt = instances.stream().findFirst()

        when {
            hzOpt.isPresent -> {
                val hz = hzOpt.get()

                try {
                    val cache = hz.cacheManager.getCache<String, String>(cacheName)

                    logger.info("Initialized cache: " + cache.name + " ok!")

                    return cache
                } catch (cnee: CacheNotExistsException) {
                    val cachingProvider = Caching.getCachingProvider(HazelcastCachingProvider::class.java.name)
                    val config = MutableConfiguration<String, String>()
                            .setTypes(String::class.java, String::class.java)
                            .setManagementEnabled(false)
                            .setStatisticsEnabled(false)
                            .setReadThrough(false)
                            .setWriteThrough(false)

                    @Suppress("UNCHECKED_CAST")
                    return cachingProvider.cacheManager.createCache<String, String, CompleteConfiguration<String, String>>(
                            cacheName, config).unwrap<ICache<*, *>>(ICache::class.java) as ICache<String, String>?
                } catch (ilse: IllegalStateException) {
                    logger.error("JCache not available!")

                    return null
                }
            }
            else -> {
                logger.error("Cannot find hazelcast instance!")

                return null
            }
        }
    }

    override fun checkObjectCache(cacheId: String, resultHandler: Handler<AsyncResult<E>>) {
        if (isObjectCacheAvailable) {
            val completeOrTimeout = AtomicBoolean()
            completeOrTimeout.set(false)

            vertx.setTimer(CACHE_READ_TIMEOUT_VALUE) {
                if (!completeOrTimeout.getAndSet(true)) {
                    resultHandler.handle(ServiceException.fail(502, "Cache timeout!"))
                }
            }

            objectCache!!.getAsync(cacheId).andThen(object : ExecutionCallback<String> {
                override fun onResponse(s: String?) {
                    when {
                        !completeOrTimeout.getAndSet(true) -> try {
                            if (logger.isDebugEnabled) {
                                logger.debug("Cached Content is: " + s!!)
                            }

                            when (s) {
                                null -> resultHandler.handle(ServiceException.fail(404, "Cache result is null!"))
                                else -> resultHandler.handle(Future.succeededFuture(Json.decodeValue(s, TYPE)))
                            }
                        } catch (e: DecodeException) {
                            logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                            resultHandler.handle(ServiceException.fail(404, "Cache result is null...",
                                    JsonObject(Json.encode(e))))
                        }
                        else -> resultHandler.handle(ServiceException.fail(502, "Cache timeout!"))
                    }
                }

                override fun onFailure(throwable: Throwable) {
                    logger.error(throwable.toString() + " : " + throwable.message + " : " +
                            Arrays.toString(throwable.stackTrace))

                    if (!completeOrTimeout.getAndSet(true)) {
                        resultHandler.handle(ServiceException.fail(500, "Unable to retrieve from cache...",
                                JsonObject(Json.encode(throwable))))
                    }
                }
            })
        } else {
            logger.error("ObjectCache is null, recreating...")

            resultHandler.handle(ServiceException.fail(404, "Unable to retrieve from cache, cache was null..."))
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
                val completeOrTimeout = AtomicBoolean()
                completeOrTimeout.set(false)

                vertx.setTimer(CACHE_READ_TIMEOUT_VALUE) {
                    if (!completeOrTimeout.getAndSet(true)) {
                        resultHandler.handle(ServiceException.fail(502, "Cache timeout!"))
                    }
                }

                itemListCache!!.getAsync(cacheId).andThen(object : ExecutionCallback<String> {
                    override fun onResponse(s: String?) {
                        if (!completeOrTimeout.getAndSet(true)) {
                            when (s) {
                                null -> resultHandler.handle(ServiceException.fail(404, "Cache result is null!"))
                                else -> try {
                                    val jsonObject = JsonObject(s)
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
                    }

                    override fun onFailure(throwable: Throwable) {
                        logger.error(throwable.toString() + " : " + throwable.message + " : " +
                                Arrays.toString(throwable.stackTrace))

                        if (!completeOrTimeout.getAndSet(true)) {
                            resultHandler.handle(ServiceException.fail(500, "Cache fetch failed...",
                                    JsonObject(Json.encode(throwable))))
                        }
                    }
                })
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
                val completeOrTimeout = AtomicBoolean()
                completeOrTimeout.set(false)

                vertx.setTimer(CACHE_READ_TIMEOUT_VALUE) {
                    if (!completeOrTimeout.getAndSet(true)) {
                        resultHandler.handle(
                                ServiceException.fail(502, "Cache timeout!"))
                    }
                }

                aggregationCache!!.getAsync(cacheKey, expiryPolicy).andThen(object : ExecutionCallback<String> {

                    override fun onResponse(s: String?) {
                        if (!completeOrTimeout.getAndSet(true)) {
                            when (s) {
                                null -> resultHandler.handle(ServiceException.fail(404, "Cache result is null..."))
                                else -> {
                                    if (logger.isDebugEnabled) {
                                        logger.debug("Returning cached content...")
                                    }

                                    resultHandler.handle(Future.succeededFuture(s))
                                }
                            }
                        }
                    }

                    override fun onFailure(throwable: Throwable) {
                        logger.error(throwable.toString() + " : " + throwable.message + " : " +
                                Arrays.toString(throwable.stackTrace))

                        if (!completeOrTimeout.getAndSet(true)) {
                            resultHandler.handle(ServiceException.fail(500,
                                    "Unable to retrieve from cache...", JsonObject(Json.encode(throwable))))
                        }
                    }
                })
            }
            else -> resultHandler.handle(ServiceException.fail(404, "Cache is null..."))
        }
    }

    override fun replaceObjectCache(cacheId: String, item: E, future: Future<E>, projections: Array<String>) {
        when {
            isObjectCacheAvailable -> {
                val fullCacheContent = Json.encode(item)
                val jsonRepresentationCache = item.toJsonFormat(projections).encode()
                val fullCacheFuture = Future.future<Boolean>()
                val jsonFuture = Future.future<Boolean>()

                vertx.setTimer(CACHE_WRITE_TIMEOUT_VALUE) {
                    vertx.executeBlocking<Any>({ fut ->
                        if (!fullCacheFuture.isComplete) {
                            objectCache!!.removeAsync("FULL_CACHE_$cacheId")
                            fullCacheFuture.tryComplete()

                            logger.error("Cache timeout!")
                        }

                        if (!jsonFuture.isComplete) {
                            objectCache!!.removeAsync(cacheId)
                            jsonFuture.tryComplete()

                            logger.error("Cache timeout!")
                        }

                        fut.complete()
                    }, false) { res -> logger.trace("Result of timeout cache clear is: " + res.succeeded()) }
                }

                objectCache!!.putAsync("FULL_CACHE_$cacheId", fullCacheContent, expiryPolicy).andThen(object : ExecutionCallback<Void> {
                    override fun onResponse(b: Void?) {
                        if (logger.isDebugEnabled) {
                            logger.debug("Set new cache on: $cacheId is $b")
                        }

                        fullCacheFuture.tryComplete(java.lang.Boolean.TRUE)
                    }

                    override fun onFailure(throwable: Throwable) {
                        logger.error(throwable.toString() + " : " + throwable.message + " : " +
                                Arrays.toString(throwable.stackTrace))

                        fullCacheFuture.tryFail(throwable)
                    }
                })

                objectCache!!.putAsync(cacheId, jsonRepresentationCache, expiryPolicy).andThen(object : ExecutionCallback<Void> {
                    override fun onResponse(b: Void?) {
                        if (logger.isDebugEnabled) {
                            logger.debug("Set new cache on: $cacheId is $b")
                        }

                        jsonFuture.tryComplete(java.lang.Boolean.TRUE)
                    }

                    override fun onFailure(throwable: Throwable) {
                        logger.error(throwable.toString() + " : " + throwable.message + " : " +
                                Arrays.toString(throwable.stackTrace))

                        jsonFuture.tryFail(throwable)
                    }
                })

                CompositeFuture.all(fullCacheFuture, jsonFuture).setHandler {
                    if (it.failed()) {
                        future.fail(it.cause())
                    } else {
                        future.complete(item)
                    }
                }
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
                val replaceFutures = ArrayList<Future<*>>()

                records.forEach { record ->
                    val replaceFuture = Future.future<Boolean>()
                    val shortCacheId = shortCacheIdSupplier.apply(record)
                    val cacheId = cacheIdSupplier.apply(record)

                    val rFirst = Future.future<Boolean>()
                    replaceTimeoutHandler(cacheId, rFirst)
                    replace(rFirst, cacheId, record.toJsonString())

                    val rFirstRoot = Future.future<Boolean>()
                    replaceTimeoutHandler(shortCacheId, rFirstRoot)
                    replace(rFirstRoot, shortCacheId, record.toJsonString())

                    val secondaryCache = "FULL_CACHE_$cacheId"
                    val rSecond = Future.future<Boolean>()
                    replaceTimeoutHandler(secondaryCache, rSecond)
                    replace(rSecond, secondaryCache, Json.encode(record))

                    val rSecondRoot = Future.future<Boolean>()
                    replaceTimeoutHandler(cacheId, rSecondRoot)
                    replace(rSecondRoot, "FULL_CACHE_$shortCacheId", Json.encode(record))

                    CompositeFuture.all(rFirst, rSecond, rFirstRoot, rSecondRoot).setHandler {
                        when {
                            it.succeeded() -> replaceFuture.complete(java.lang.Boolean.TRUE)
                            else -> replaceFuture.fail(it.cause())
                        }
                    }

                    replaceFutures.add(replaceFuture)
                }

                CompositeFuture.all(replaceFutures).setHandler { purgeSecondaryCaches(writeFuture.completer()) }
            }
            else -> {
                logger.error("ObjectCache is null, recreating...")

                purgeSecondaryCaches(writeFuture.completer())
            }
        }
    }

    private fun replace(replaceFuture: Future<Boolean>, cacheId: String, recordAsJson: String) {
        when {
            isObjectCacheAvailable -> objectCache!!.putAsync(cacheId, recordAsJson, expiryPolicy).andThen(object : ExecutionCallback<Void> {
                override fun onResponse(b: Void?) {
                    if (logger.isDebugEnabled) {
                        logger.debug("Cache Replaced for: $cacheId is $b")
                    }

                    replaceFuture.tryComplete(java.lang.Boolean.TRUE)
                }

                override fun onFailure(throwable: Throwable) {
                    logger.error(throwable.toString() + " : " + throwable.message + " : " +
                            Arrays.toString(throwable.stackTrace))

                    replaceFuture.tryComplete(java.lang.Boolean.FALSE)
                }
            })
            else -> replaceFuture.tryComplete(java.lang.Boolean.FALSE)
        }
    }

    private fun replaceTimeoutHandler(cacheId: String, replaceFirst: Future<Boolean>) {
        vertx.setTimer(CACHE_WRITE_TIMEOUT_VALUE) {
            try {
                vertx.executeBlocking<Any>({ future ->
                    if (!replaceFirst.isComplete && !objectCache!!.isDestroyed) {
                        objectCache!!.removeAsync(cacheId)

                        replaceFirst.tryComplete(java.lang.Boolean.TRUE)

                        logger.error("Cache timeout when replacing$cacheId!")
                    }

                    future.complete()
                }, false) { res -> logger.trace("Result of timeout cache clear is: " + res.succeeded()) }
            } catch (ignored: RejectedExecutionException) {}
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
                val cacheFuture = Future.future<Boolean>()

                vertx.setTimer(CACHE_WRITE_TIMEOUT_VALUE) {
                    vertx.executeBlocking<Any>({ fut ->
                        if (!cacheFuture.isComplete) {
                            itemListCache!!.removeAsync(cacheId)

                            cacheFuture.tryFail(TimeoutException(
                                    "Cache request timed out, above: $CACHE_WRITE_TIMEOUT_VALUE!"))

                            logger.error("Cache timeout when replacing itemlistcache for: $cacheId!")
                        }

                        fut.complete()
                    }, false) { res -> logger.trace("Result of timeout cache clear is: " + res.succeeded()) }
                }

                itemListCache!!.putAsync(cacheId, content, expiryPolicy).andThen(object : ExecutionCallback<Void> {
                    override fun onResponse(b: Void?) {
                        if (logger.isDebugEnabled) {
                            logger.debug("Set new cache on: $cacheId is $b")
                        }

                        replaceMapValues(cacheFuture, ITEM_LIST_KEY_MAP, cacheId)
                    }

                    override fun onFailure(throwable: Throwable) {
                        logger.error(throwable.toString() + " : " + throwable.message + " : " +
                                Arrays.toString(throwable.stackTrace))

                        cacheFuture.tryComplete()
                    }
                })

                cacheFuture.setHandler {
                    when {
                        it.failed() -> resultHandler.handle(ServiceException.fail(504, it.cause().message))
                        else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                    }
                }
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

                val cacheIdFuture = Future.future<Boolean>()
                vertx.setTimer(CACHE_WRITE_TIMEOUT_VALUE) {
                    vertx.executeBlocking<Any>({ future ->
                        if (!cacheIdFuture.isComplete) {
                            aggregationCache!!.removeAsync(cacheKey)

                            cacheIdFuture.tryComplete()

                            logger.error("Cache timeout when replacing aggregationcache for: $cacheKey!")
                        }

                        future.complete()
                    }, false) { logger.trace("Result of timeout cache clear is: " + it.succeeded()) }
                }

                aggregationCache!!.putAsync(cacheKey, content, expiryPolicy).andThen(object : ExecutionCallback<Void> {
                    override fun onResponse(b: Void?) {
                        if (logger.isDebugEnabled) {
                            logger.debug("Set cache for $cacheKey is $b")
                        }

                        replaceMapValues(cacheIdFuture, AGGREGATION_KEY_MAP, cacheKey)
                    }

                    override fun onFailure(throwable: Throwable) {
                        logger.error(throwable)

                        cacheIdFuture.tryComplete()
                    }
                })

                cacheIdFuture.setHandler {
                    when {
                        it.failed() -> resultHandler.handle(ServiceException.fail(500, it.cause().message))
                        else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                    }
                }
            }
            else -> {
                logger.error("AggregationCache is null, recreating...")

                resultHandler.handle(ServiceException.fail(500, "Aggregation cache does not exist!"))
            }
        }
    }

    private fun replaceMapValues(cacheIdFuture: Future<Boolean>, AGGREGATION_KEY_MAP: String, cacheKey: String) {
        vertx.sharedData().getClusterWideMap<String, Set<String>>(AGGREGATION_KEY_MAP) { map ->
            when {
                map.failed() -> {
                    logger.error("Cannot set cachemap...", map.cause())

                    cacheIdFuture.tryComplete()
                }
                else -> map.result().get(TYPE.simpleName) { set ->
                    when {
                        set.failed() -> {
                            logger.error("Unable to get TYPE id set!", set.cause())

                            cacheIdFuture.tryComplete()
                        }
                        else -> {
                            var idSet: MutableSet<String>? = set.result().toMutableSet()

                            when (idSet) {
                                null -> {
                                    idSet = HashSet()

                                    idSet.add(cacheKey)

                                    map.result().put(TYPE.simpleName, idSet) { setRes ->
                                        if (setRes.failed()) {
                                            logger.error("Unable to set cacheIdSet!", setRes.cause())
                                        }

                                        cacheIdFuture.tryComplete()
                                    }
                                }
                                else -> {
                                    idSet.add(cacheKey)

                                    map.result().replace(TYPE.simpleName, idSet) { setRes ->
                                        if (setRes.failed()) {
                                            logger.error("Unable to set cacheIdSet!", setRes.cause())
                                        }

                                        cacheIdFuture.tryComplete()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun purgeCache(future: Future<Boolean>, records: List<E>, cacheIdSupplier: (E) -> String) {
        when {
            isObjectCacheAvailable -> {
                val purgeFutures = ArrayList<Future<*>>()

                records.forEach { record ->
                    val purgeFuture = Future.future<Boolean>()
                    val cacheId = cacheIdSupplier(record)
                    val purgeFirst = Future.future<Boolean>()

                    objectCache!!.removeAsync(cacheId).andThen(object : ExecutionCallback<Boolean> {
                        override fun onResponse(b: Boolean?) {
                            if (logger.isDebugEnabled) {
                                logger.debug("Cache Removal on $cacheId success: $b")
                            }

                            purgeFirst.tryComplete(java.lang.Boolean.TRUE)
                        }

                        override fun onFailure(throwable: Throwable) {
                            logger.error(throwable.toString() + " : " + throwable.message + " : " +
                                    Arrays.toString(throwable.stackTrace))

                            purgeFirst.tryComplete(java.lang.Boolean.TRUE)
                        }
                    })

                    val secondaryCache = "FULL_CACHE_$cacheId"
                    val purgeSecond = Future.future<Boolean>()
                    vertx.setTimer(CACHE_WRITE_TIMEOUT_VALUE) {
                        vertx.executeBlocking<Any>({ fut ->
                            if (!purgeFirst.isComplete) {
                                objectCache!!.removeAsync(cacheId)

                                purgeFirst.tryComplete()

                                logger.error("Cache timeout purging cache for: $cacheId!")
                            }

                            if (!purgeSecond.isComplete) {
                                objectCache!!.removeAsync(secondaryCache)

                                purgeSecond.tryComplete()

                                logger.error("Cache timeout purging full cache for: $secondaryCache!")
                            }

                            fut.complete()
                        }, false) { logger.trace("Result of timeout cache clear is: " + it.succeeded()) }
                    }

                    objectCache!!.removeAsync(secondaryCache).andThen(object : ExecutionCallback<Boolean> {
                        override fun onResponse(b: Boolean?) {
                            if (logger.isDebugEnabled) {
                                logger.debug("Full Cache Removal on $cacheId success: $b")
                            }

                            purgeSecond.tryComplete(java.lang.Boolean.TRUE)
                        }

                        override fun onFailure(throwable: Throwable) {
                            logger.error(throwable.toString() + " : " + throwable.message + " : " +
                                    Arrays.toString(throwable.stackTrace))

                            purgeSecond.tryComplete(java.lang.Boolean.TRUE)
                        }
                    })

                    CompositeFuture.all(purgeFirst, purgeSecond).setHandler {
                        if (it.succeeded()) {
                            purgeFuture.complete(java.lang.Boolean.TRUE)
                        } else {
                            purgeFuture.fail(it.cause())
                        }
                    }

                    purgeFutures.add(purgeFuture)
                }

                CompositeFuture.all(purgeFutures).setHandler { purgeSecondaryCaches(future.completer()) }
            }
            else -> {
                logger.error("ObjectCache is null, recreating...")

                purgeSecondaryCaches(future.completer())
            }
        }
    }

    private fun purgeSecondaryCaches(resultHandler: Handler<AsyncResult<Boolean>>) {
        val itemListFuture = Future.future<Boolean>()
        val aggregationFuture = Future.future<Boolean>()

        when {
            isItemListCacheAvailable -> {
                vertx.setTimer(CACHE_WRITE_TIMEOUT_VALUE) {
                    try {
                        vertx.executeBlocking<Any>({ future ->
                            if (!itemListFuture.isComplete && !itemListCache!!.isDestroyed) {
                                itemListCache!!.clear()

                                itemListFuture.tryComplete()

                                logger.error("Cache Timeout purging secondary caches for itemlist!")
                            }

                            future.complete()
                        }, false) { res -> logger.trace("Result of timeout cache clear is: " + res.succeeded()) }
                    } catch (ignored: RejectedExecutionException) {}
                }

                purgeMap(ITEM_LIST_KEY_MAP, itemListCache, Handler {
                    if (it.failed()) itemListCache = null

                    itemListFuture.tryComplete()
                })
            }
            else -> {
                logger.error("ItemListCache is null, recreating...")

                itemListFuture.tryComplete()
            }
        }

        when {
            isAggregationCacheAvailable -> {
                vertx.setTimer(CACHE_WRITE_TIMEOUT_VALUE) {
                    try {
                        vertx.executeBlocking<Any>({
                            if (!aggregationFuture.isComplete && !aggregationCache!!.isDestroyed) {
                                aggregationCache!!.clear()

                                aggregationFuture.tryComplete()

                                logger.error("Cache timeout purging aggregationcache!")
                            }

                            it.complete()
                        }, false) { logger.trace("Result of timeout cache clear is: " + it.succeeded()) }
                    } catch (ignored: RejectedExecutionException) {}
                }

                purgeMap(AGGREGATION_KEY_MAP, aggregationCache, Handler {
                    if (it.failed()) aggregationCache = null

                    aggregationFuture.tryComplete()
                })
            }
            else -> {
                logger.error("AggregateCache is null, recreating...")

                aggregationFuture.tryComplete()
            }
        }

        CompositeFuture.any(itemListFuture, aggregationFuture).setHandler { resultHandler.handle(Future.succeededFuture()) }
    }

    private fun purgeMap(
        MAP_KEY: String,
        cache: ICache<String, String>?,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        vertx.executeBlocking<Boolean>({ purgeAllListCaches ->
            if (logger.isDebugEnabled) logger.debug("Now purging cache: $MAP_KEY")

            try {
                vertx.sharedData().getClusterWideMap<String, Set<String>>(MAP_KEY) { map ->
                    when {
                        map.failed() -> {
                            logger.error("Cannot get cachemap...", map.cause())

                            vertx.executeBlocking<Any>({ future ->
                                cache!!.clear()
                                purgeAllListCaches.tryComplete()

                                future.complete()
                            }, false) { res -> logger.trace("Result of timeout cache clear is: " + res.succeeded()) }
                        }
                        else ->
                            try {
                                val cachePartitionKey = TYPE.newInstance().cachePartitionKey

                                map.result().get(cachePartitionKey) {
                                    purgeMapContents(it, cache, purgeAllListCaches, cachePartitionKey, map.result())
                                }
                            } catch (e: InstantiationException) {
                                logger.error("Unable to build partitionKey", e)

                                purgeAllListCaches.tryFail(e)
                            } catch (e: IllegalAccessException) {
                                logger.error("Unable to build partitionKey", e)
                                purgeAllListCaches.tryFail(e)
                            }
                    }

                    if (logger.isDebugEnabled) {
                        logger.debug("Cache cleared: " + cache!!.size())
                    }
                }
            } catch (e: Exception) {
                logger.error(e)
                logger.error("Unable to purge cache, nulling...")

                purgeAllListCaches.tryFail(e)
            }
        }) { res -> resultHandler.handle(res.map(res.result())) }
    }

    private fun purgeMapContents(
        getSet: AsyncResult<Set<String>>,
        cache: ICache<String, String>?,
        purgeAllListCaches: Future<Boolean>,
        cachePartitionKey: String,
        result: AsyncMap<String, Set<String>>
    ) {
        when {
            getSet.failed() -> {
                logger.error("Unable to get idSet!", getSet.cause())

                vertx.executeBlocking<Any>({
                    cache!!.clear()
                    purgeAllListCaches.tryComplete()

                    it.complete()
                }, false) { logger.trace("Result of timeout cache clear is: " + it.succeeded()) }
            }
            else -> {
                val idSet = getSet.result()

                when {
                    idSet != null -> vertx.executeBlocking<Any>({
                        cache!!.removeAll(getSet.result())

                        purgeAllListCaches.tryComplete()

                        it.complete()
                    }, false) { logger.trace("Result of timeout cache clear is: " + it.succeeded()) }
                    else -> vertx.executeBlocking<Any>({ future ->
                        cache!!.clear()

                        result.put(cachePartitionKey, HashSet()) {
                            if (it.failed()) {
                                logger.error("Unable to clear set...", it.cause())
                            }

                            purgeAllListCaches.tryComplete()
                        }

                        future.complete()
                    }, false) { logger.trace("Result of timeout cache clear is: " + it.succeeded()) }
                }
            }
        }
    }

    private fun recreateObjectCache() {
        vertx.executeBlocking<Any>({
            objectCache = createCache("object")

            it.complete(true)
        }, false) {
            if (logger.isDebugEnabled) {
                logger.debug("Caches ok: " + it.result())
            }
        }
    }

    private fun recreateItemListCache() {
        vertx.executeBlocking<Any>({
            itemListCache = createCache("itemList")

            it.complete(true)
        }, false) {
            if (logger.isDebugEnabled) {
                logger.debug("Caches ok: " + it.result())
            }
        }
    }

    private fun recreateAggregateCache() {
        vertx.executeBlocking<Any>({
            aggregationCache = createCache("aggregation")

            it.complete(true)
        }, false) {
            if (logger.isDebugEnabled) {
                logger.debug("Caches ok: " + it.result())
            }
        }
    }

    override val isObjectCacheAvailable: Boolean
        get() {
            val available = objectCache != null

            if (!available) {
                recreateObjectCache()
            }

            return available
        }

    override val isItemListCacheAvailable: Boolean
        get() {
            val available = itemListCache != null

            if (!available) {
                recreateItemListCache()
            }

            return available
        }

    override val isAggregationCacheAvailable: Boolean
        get() {
            val available = aggregationCache != null

            if (!available) {
                recreateAggregateCache()
            }

            return available
        }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterCacheManagerImpl::class.java.simpleName)

        private var cachesCreated = false
        private var objectCache: ICache<String, String>? = null
        private var itemListCache: ICache<String, String>? = null
        private var aggregationCache: ICache<String, String>? = null
    }
}
