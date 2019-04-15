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

package com.nannoq.tools.repository.repository.etag

import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.repository.redis.RedisUtils
import com.nannoq.tools.repository.repository.redis.RedisUtils.performJedisWithRetry
import com.nannoq.tools.repository.utils.ItemList
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.RedisClient
import java.util.*

/**
 * The RedisETagManagerImpl contains the logic for setting, removing, and replace etags.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class RedisETagManagerImpl<E>(private val TYPE: Class<E>, private val REDIS_CLIENT: RedisClient) : ETagManager<E> where E : ETagable, E : Model {

    override fun removeProjectionsEtags(hash: Int, resultHandler: Handler<AsyncResult<Boolean>>) {
        val etagKeyBase = TYPE.simpleName + "_" + hash + "/projections"

        doEtagRemovalWithRetry(etagKeyBase, resultHandler)
    }

    override fun destroyEtags(hash: Int, resultHandler: Handler<AsyncResult<Boolean>>) {
        val etagItemListHashKey = TYPE.simpleName + "_" + hash + "_" + "itemListEtags"

        doEtagRemovalWithRetry(etagItemListHashKey, resultHandler)
    }

    private fun doEtagRemovalWithRetry(etagKeyBase: String, resultHandler: Handler<AsyncResult<Boolean>>) {
        performJedisWithRetry(REDIS_CLIENT) {
            it.hgetall(etagKeyBase) { result ->
                when {
                    result.failed() -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                    else -> {
                        val getAllResult = result.result()
                        val itemsToRemove = mutableListOf<String>()
                        getAllResult.iterator().forEachRemaining { entry -> itemsToRemove.add(entry.key) }

                        if (itemsToRemove.isNotEmpty()) {
                            performJedisWithRetry(REDIS_CLIENT) { client ->
                                client.hdelMany(etagKeyBase, itemsToRemove) {
                                    resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                                }
                            }
                        } else {
                            resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                        }
                    }
                }
            }
        }
    }

    override fun replaceAggregationEtag(etagItemListHashKey: String, etagKey: String, newEtag: String,
                                        resultHandler: Handler<AsyncResult<Boolean>>) {
        performJedisWithRetry(REDIS_CLIENT) {
            it.hset(etagItemListHashKey, etagKey, newEtag) { result ->
                when {
                    result.failed() -> resultHandler.handle(Future.failedFuture(result.cause()))
                    else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                }
            }
        }
    }

    override fun setSingleRecordEtag(etagMap: Map<String, String>,
                                     resultHandler: Handler<AsyncResult<Boolean>>) {
        RedisUtils.performJedisWithRetry(REDIS_CLIENT) {
            etagMap.keys.forEach { key ->
                it.set(key, etagMap[key]) {
                    if (it.failed()) {
                        logger.error("Could not set " + etagMap[key] + " for " + key + ", cause: " + it.cause())
                    }
                }
            }
        }

        resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
    }

    override fun setProjectionEtags(projections: Array<String>, hash: Int, item: E) {
        if (projections.isNotEmpty()) {
            val etagKeyBase = TYPE.simpleName + "_" + hash + "/projections"
            val key = TYPE.simpleName + "_" + hash + "/projections" + Arrays.hashCode(projections)
            val etag = item.etag

            RedisUtils.performJedisWithRetry(REDIS_CLIENT) {
                it.hset(etagKeyBase, key, etag) { result ->
                    if (result.failed()) {
                        logger.error("Unable to store projection etag!", result.cause())
                    }
                }
            }
        }
    }

    override fun setItemListEtags(etagItemListHashKey: String, etagKey: String, itemList: ItemList<E>,
                                  itemListEtagFuture: Future<Boolean>) {
        RedisUtils.performJedisWithRetry(REDIS_CLIENT) {
            it.hset(etagItemListHashKey, etagKey, itemList.meta?.etag) {
                itemListEtagFuture.complete(java.lang.Boolean.TRUE)
            }
        }
    }

    override fun checkItemEtag(etagKeyBase: String, key: String, requestEtag: String,
                               resultHandler: Handler<AsyncResult<Boolean>>) {
        RedisUtils.performJedisWithRetry(REDIS_CLIENT) {
            it.hget(etagKeyBase, key) { result ->
                when {
                    result.succeeded() && result.result() != null && result.result() == requestEtag ->
                        resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                    else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.FALSE))
                }
            }
        }
    }

    override fun checkItemListEtag(etagItemListHashKey: String, etagKey: String, etag: String,
                                   resultHandler: Handler<AsyncResult<Boolean>>) {
        RedisUtils.performJedisWithRetry(REDIS_CLIENT) {
            it.hget(etagItemListHashKey, etagKey) { result ->
                if (logger.isDebugEnabled) {
                    logger.debug("Stored etag: " + result.result() + ", request: " + etag)
                }

                when {
                    result.succeeded() && result.result() != null && result.result() == etag ->
                        resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                    else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.FALSE))
                }
            }
        }
    }

    override fun checkAggregationEtag(etagItemListHashKey: String, etagKey: String, etag: String,
                                      resultHandler: Handler<AsyncResult<Boolean>>) {
        RedisUtils.performJedisWithRetry(REDIS_CLIENT) {
            it.hget(etagItemListHashKey, etagKey) { result ->
                when {
                    result.succeeded() && result.result() != null && result.result() == etag ->
                        resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                    else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.FALSE))
                }
            }
        }

    }

    companion object {
        private val logger = LoggerFactory.getLogger(RedisETagManagerImpl::class.java.simpleName)
    }
}
