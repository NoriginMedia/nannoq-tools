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

package com.nannoq.tools.repository.repository.etag

import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.utils.ItemList
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.shareddata.AsyncMap
import io.vertx.core.shareddata.LocalMap
import java.util.Arrays

/**
 * The InMemoryETagManagerImpl contains the logic for setting, removing, and replace etags.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class InMemoryETagManagerImpl<E>(private val vertx: Vertx, private val TYPE: Class<E>) : ETagManager<E> where E : ETagable, E : Model {
    private val logger = LoggerFactory.getLogger(InMemoryETagManagerImpl::class.java.simpleName)

    private val localObjectMap: LocalMap<String, String>
        get() = vertx.sharedData().getLocalMap(OBJECT_ETAG_MAP)

    constructor(TYPE: Class<E>) : this(Vertx.currentContext().owner(), TYPE)

    private fun getLocalObjectMap(etagKeyBase: String): LocalMap<String, String> {
        return vertx.sharedData().getLocalMap(etagKeyBase)
    }

    private fun getLocalItemListMap(itemListHashKey: String): LocalMap<String, String> {
        return vertx.sharedData().getLocalMap(itemListHashKey)
    }

    private fun getClusteredObjectMap(resultHandler: Handler<AsyncResult<AsyncMap<String, String>>>) {
        if (!vertx.isClustered) throw IllegalStateException("Vertx is not clustered!")
        vertx.sharedData().getClusterWideMap(OBJECT_ETAG_MAP, resultHandler)
    }

    private fun getClusteredObjectMap(etagKeyBase: String, resultHandler: Handler<AsyncResult<AsyncMap<String, String>>>) {
        if (!vertx.isClustered) throw IllegalStateException("Vertx is not clustered!")
        vertx.sharedData().getClusterWideMap(etagKeyBase, resultHandler)
    }

    private fun getClusteredItemListMap(
        itemListHashKey: String,
        resultHandler: Handler<AsyncResult<AsyncMap<String, String>>>
    ) {
        if (!vertx.isClustered) throw IllegalStateException("Vertx is not clustered!")
        vertx.sharedData().getClusterWideMap(itemListHashKey, resultHandler)
    }

    override fun removeProjectionsEtags(hash: Int, resultHandler: Handler<AsyncResult<Boolean>>) {
        val etagKeyBase = TYPE.simpleName + "_" + hash + "/projections"

        when {
            vertx.isClustered -> getClusteredObjectMap(etagKeyBase, Handler { clearClusteredMap(resultHandler, it) })
            else -> {
                getLocalItemListMap(etagKeyBase).clear()

                resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
            }
        }
    }

    override fun destroyEtags(hash: Int, resultHandler: Handler<AsyncResult<Boolean>>) {
        val etagItemListHashKey = TYPE.simpleName + "_" + hash + "_" + "itemListEtags"

        when {
            vertx.isClustered -> getClusteredItemListMap(etagItemListHashKey, Handler {
                clearClusteredMap(resultHandler, it)
            })
            else -> {
                getLocalItemListMap(etagItemListHashKey).clear()

                resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
            }
        }
    }

    private fun clearClusteredMap(
        resultHandler: Handler<AsyncResult<Boolean>>,
        mapRes: AsyncResult<AsyncMap<String, String>>
    ) {
        when {
            mapRes.failed() -> resultHandler.handle(Future.failedFuture(mapRes.cause()))
            else -> mapRes.result().clear { clearRes ->
                if (clearRes.failed()) {
                    resultHandler.handle(Future.failedFuture(clearRes.cause()))
                } else {
                    resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                }
            }
        }
    }

    override fun replaceAggregationEtag(
        etagItemListHashKey: String,
        etagKey: String,
        newEtag: String,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        when {
            vertx.isClustered -> getClusteredItemListMap(etagItemListHashKey, Handler {
                when {
                    it.failed() -> resultHandler.handle(Future.failedFuture<Boolean>(it.cause()))
                    else -> it.result().put(etagKey, newEtag) { setRes ->
                        when {
                            setRes.failed() -> {
                                logger.error("Unable to set etag!", setRes.cause())

                                resultHandler.handle(Future.failedFuture<Boolean>(setRes.cause()))
                            }
                            else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                        }
                    }
                }
            })
            else -> {
                getLocalItemListMap(etagItemListHashKey)[etagKey] = newEtag

                resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
            }
        }
    }

    override fun setSingleRecordEtag(
        etagMap: Map<String, String>,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        when {
            vertx.isClustered -> getClusteredObjectMap(Handler {
                when {
                    it.failed() -> {
                        logger.error("Failed etag setting for objects!", it.cause())

                        resultHandler.handle(Future.failedFuture<Boolean>(it.cause()))
                    }
                    else -> {
                        val map = it.result()
                        val setFutures = ArrayList<Future<*>>()

                        etagMap.forEach { k, v ->
                            val setFuture = Future.future<Void>()

                            map.put(k, v, setFuture.completer())

                            setFutures.add(setFuture)
                        }

                        CompositeFuture.all(setFutures).setHandler { result ->
                            when {
                                result.failed() -> resultHandler.handle(Future.failedFuture(result.cause()))
                                else -> resultHandler.handle(Future.succeededFuture())
                            }
                        }
                    }
                }
            })
            else -> {
                val localObjectMap = localObjectMap
                etagMap.forEach { k, v -> localObjectMap[k] = v }

                resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
            }
        }
    }

    override fun setProjectionEtags(projections: Array<String>, hash: Int, item: E) {
        if (projections.isNotEmpty()) {
            val etagKeyBase = TYPE.simpleName + "_" + hash + "/projections"
            val key = TYPE.simpleName + "_" + hash + "/projections" + Arrays.hashCode(projections)
            val etag = item.etag

            when {
                vertx.isClustered -> getClusteredObjectMap(etagKeyBase, Handler { mapRes ->
                    if (mapRes.succeeded()) {
                        mapRes.result().put(key, etag) { setRes -> }
                    }
                })
                else -> getLocalObjectMap(etagKeyBase)[key] = etag
            }
        }
    }

    override fun setItemListEtags(
        etagItemListHashKey: String,
        etagKey: String,
        itemList: ItemList<E>,
        itemListEtagFuture: Future<Boolean>
    ) {
        setItemListEtags(etagItemListHashKey, etagKey, itemList.meta?.etag, itemListEtagFuture)
    }

    private fun setItemListEtags(
        etagItemListHashKey: String,
        etagKey: String,
        etag: String?,
        itemListEtagFuture: Future<Boolean>
    ) {
        when {
            vertx.isClustered -> getClusteredItemListMap(etagItemListHashKey, Handler {
                when {
                    it.failed() -> itemListEtagFuture.fail(it.cause())
                    else -> it.result().put(etagKey, etag) { result ->
                        when {
                            result.failed() -> {
                                logger.error("Unable to set etag!", result.cause())

                                itemListEtagFuture.fail(result.cause())
                            }
                            else -> itemListEtagFuture.complete(java.lang.Boolean.TRUE)
                        }
                    }
                }
            })
            else -> {
                getLocalItemListMap(etagItemListHashKey)[etagKey] = etag

                itemListEtagFuture.complete(java.lang.Boolean.TRUE)
            }
        }
    }

    override fun checkItemEtag(
        etagKeyBase: String,
        key: String,
        etag: String,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        when {
            vertx.isClustered -> getClusteredObjectMap(etagKeyBase, Handler {
                checkEtagFromMap(key, etag, resultHandler, it)
            })
            else -> {
                val s = localObjectMap[key]

                when {
                    s != null -> resultHandler.handle(Future.succeededFuture(s == etag))
                    else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.FALSE))
                }
            }
        }
    }

    override fun checkItemListEtag(
        etagItemListHashKey: String,
        etagKey: String,
        etag: String,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        when {
            vertx.isClustered -> getClusteredItemListMap(etagItemListHashKey, Handler {
                checkEtagFromMap(etagKey, etag, resultHandler, it)
            })
            else -> {
                val s = getLocalItemListMap(etagItemListHashKey)[etagKey]

                when {
                    s != null -> resultHandler.handle(Future.succeededFuture(s == etag))
                    else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.FALSE))
                }
            }
        }
    }

    override fun checkAggregationEtag(
        etagItemListHashKey: String,
        etagKey: String,
        etag: String,
        resultHandler: Handler<AsyncResult<Boolean>>
    ) {
        checkItemListEtag(etagItemListHashKey, etagKey, etag, resultHandler)
    }

    private fun checkEtagFromMap(
        key: String,
        etag: String,
        resultHandler: Handler<AsyncResult<Boolean>>,
        res: AsyncResult<AsyncMap<String, String>>
    ) {
        when {
            res.failed() -> resultHandler.handle(Future.failedFuture(res.cause()))
            else -> res.result().get(key) { getRes ->
                when {
                    getRes.failed() -> {
                        logger.error("Unable to set etag!", getRes.cause())

                        resultHandler.handle(Future.failedFuture(getRes.cause()))
                    }
                    else -> when {
                        getRes.result() != null && getRes.result() == etag ->
                            resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                        else -> resultHandler.handle(Future.succeededFuture(java.lang.Boolean.FALSE))
                    }
                }
            }
        }
    }

    companion object {
        private const val OBJECT_ETAG_MAP = "OBJECT_ETAG_MAP"
    }
}
