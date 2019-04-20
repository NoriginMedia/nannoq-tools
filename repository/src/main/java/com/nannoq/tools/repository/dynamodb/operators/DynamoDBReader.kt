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

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.datamodeling.KeyPair
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedParallelScanList
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository.Companion.PAGINATION_INDEX
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.DynamoDBModel
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.repository.Repository.Companion.MULTIPLE_KEY
import com.nannoq.tools.repository.repository.cache.CacheManager
import com.nannoq.tools.repository.repository.etag.ETagManager
import com.nannoq.tools.repository.repository.results.ItemListResult
import com.nannoq.tools.repository.repository.results.ItemResult
import com.nannoq.tools.repository.utils.FilterParameter
import com.nannoq.tools.repository.utils.ItemList
import com.nannoq.tools.repository.utils.PageTokens
import com.nannoq.tools.repository.utils.QueryPack
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.serviceproxy.ServiceException
import java.util.Arrays
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors.toList

/**
 * This class defines the read operations for the DynamoDBRepository.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class DynamoDBReader<E>(
    private val TYPE: Class<E>,
    private val vertx: Vertx,
    private val db: DynamoDBRepository<E>,
    private val COLLECTION: String,
    private val HASH_IDENTIFIER: String,
    private val IDENTIFIER: String?,
    private val PAGINATION_IDENTIFIER: String?,
    private val GSI_KEY_MAP: Map<String, JsonObject>,
    private val dbParams: DynamoDBParameters<E>,
    private val cacheManager: CacheManager<E>,
    private val etagManager: ETagManager<E>?
)
        where E : ETagable, E : Cacheable, E : DynamoDBModel, E : Model {
    private val mapper: DynamoDBMapper = db.dynamoDbMapper
    private val coreNum = Runtime.getRuntime().availableProcessors() * 2

    private val paginationIndex: String?
        get() = if (PAGINATION_IDENTIFIER != null && PAGINATION_IDENTIFIER != "") PAGINATION_INDEX else null

    fun read(identifiers: JsonObject, resultHandler: Handler<AsyncResult<ItemResult<E>>>) {
        val startTime = AtomicLong()
        startTime.set(System.nanoTime())
        val preOperationTime = AtomicLong()
        val operationTime = AtomicLong()
        val postOperationTime = AtomicLong()

        val hash = identifiers.getString("hash")
        val range = identifiers.getString("range")
        val cacheBase = TYPE.simpleName + "_" + hash + if (range == null)
            if (db.hasRangeKey()) "/null" else ""
        else
            "/$range"
        val cacheId = "FULL_CACHE_$cacheBase"

        vertx.executeBlocking<E>({
            cacheManager.checkObjectCache(cacheId, Handler { result ->
                when {
                    result.failed() -> it.fail(result.cause())
                    else -> it.complete(result.result())
                }
            })
        }, false, { checkResult ->
            when {
                checkResult.succeeded() -> {
                    resultHandler.handle(Future.succeededFuture(ItemResult(checkResult.result(), true)))

                    if (logger.isDebugEnabled) {
                        logger.debug("Served cached version of: $cacheId")
                    }
                }
                else -> vertx.executeBlocking<E>({
                    val item = fetchItem(startTime, preOperationTime, operationTime, hash, range, true)

                    when {
                        item != null && cacheManager.isObjectCacheAvailable ->
                            cacheManager.replaceObjectCache(cacheBase, item, it, arrayOf())
                        else -> when (item) {
                            null -> it.fail(NoSuchElementException())
                            else -> it.complete(item)
                        }
                    }
                }, false, {
                    when {
                        it.failed() -> doReadResult(postOperationTime, startTime, it, resultHandler)
                        else -> {
                            postOperationTime.set(System.nanoTime() - startTime.get())

                            returnTimedResult(it, preOperationTime, operationTime, postOperationTime, resultHandler)
                        }
                    }
                })
            }
        })
    }

    fun read(
        identifiers: JsonObject,
        consistent: Boolean,
        projections: Array<String>?,
        resultHandler: Handler<AsyncResult<ItemResult<E>>>
    ) {
        val startTime = AtomicLong()
        startTime.set(System.nanoTime())
        val preOperationTime = AtomicLong()
        val operationTime = AtomicLong()
        val postOperationTime = AtomicLong()

        val hash = identifiers.getString("hash")
        val range = identifiers.getString("range")
        val cacheId = TYPE.simpleName + "_" + hash + (if (range == null) "" else "/$range") +
                if (projections != null && projections.isNotEmpty()) "/projection/" + Arrays.hashCode(projections) else ""

        vertx.executeBlocking<E>({ future ->
            cacheManager.checkObjectCache(cacheId, Handler { result ->
                when {
                    result.failed() -> future.fail(result.cause())
                    else -> future.complete(result.result())
                }
            })
        }, false, { checkResult ->
            when {
                checkResult.succeeded() -> {
                    resultHandler.handle(Future.succeededFuture(ItemResult(checkResult.result(), true)))

                    if (logger.isDebugEnabled) {
                        logger.debug("Served cached version of: $cacheId")
                    }
                }
                else -> vertx.executeBlocking<E>({ future ->
                    val item = fetchItem(startTime, preOperationTime, operationTime, hash, range, consistent)

                    item?.generateAndSetEtag(ConcurrentHashMap())

                    etagManager?.setProjectionEtags(projections ?: arrayOf(), identifiers.encode().hashCode(), item!!)

                    when {
                        item != null && cacheManager.isObjectCacheAvailable ->
                            cacheManager.replaceObjectCache(cacheId, item, future, projections ?: arrayOf())
                        else -> when (item) {
                            null -> future.fail(NoSuchElementException())
                            else -> future.complete(item)
                        }
                    }
                }, false, { readResult ->
                    when {
                        readResult.failed() -> doReadResult(postOperationTime, startTime, readResult, resultHandler)
                        else -> {
                            postOperationTime.set(System.nanoTime() - operationTime.get())

                            returnTimedResult(readResult, preOperationTime, operationTime, postOperationTime, resultHandler)
                        }
                    }
                })
            }
        })
    }

    private fun doReadResult(
        postOperationTime: AtomicLong,
        startTime: AtomicLong,
        readResult: AsyncResult<E>,
        resultHandler: Handler<AsyncResult<ItemResult<E>>>
    ) {
        when {
            readResult.cause().javaClass == NoSuchElementException::class.java -> {
                postOperationTime.set(System.nanoTime() - startTime.get())
                resultHandler.handle(ServiceException.fail(404, "Not found!",
                        JsonObject(Json.encode(readResult.cause()))))
            }
            else -> {
                logger.error("Error in read!", readResult.cause())

                postOperationTime.set(System.nanoTime() - startTime.get())
                resultHandler.handle(ServiceException.fail(500, "Error in read!",
                        JsonObject(Json.encode(readResult.cause()))))
            }
        }
    }

    private fun fetchItem(
        startTime: AtomicLong,
        preOperationTime: AtomicLong,
        operationTime: AtomicLong,
        hash: String,
        range: String?,
        consistent: Boolean
    ): E? {
        return try {
            when {
                db.hasRangeKey() -> when (range) {
                    null -> {
                        if (logger.isDebugEnabled) {
                            logger.debug("Loading ranged item without range key!")
                        }

                        fetchHashItem(hash, startTime, preOperationTime, operationTime, consistent)
                    }
                    else -> fetchHashAndRangeItem(hash, range, startTime, preOperationTime, operationTime)
                }
                else -> fetchHashItem(hash, startTime, preOperationTime, operationTime, consistent)
            }
        } catch (e: Exception) {
            logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

            null
        }
    }

    private fun fetchHashAndRangeItem(
        hash: String,
        range: String,
        startTime: AtomicLong,
        preOperationTime: AtomicLong,
        operationTime: AtomicLong
    ): E {
        val timeBefore = System.currentTimeMillis()

        preOperationTime.set(System.nanoTime() - startTime.get())
        val item = mapper.load(TYPE, hash, range)
        operationTime.set(System.nanoTime() - startTime.get())

        if (logger.isDebugEnabled) {
            logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
        }

        return item
    }

    @Throws(IllegalAccessException::class, InstantiationException::class)
    private fun fetchHashItem(
        hash: String,
        startTime: AtomicLong,
        preOperationTime: AtomicLong,
        operationTime: AtomicLong,
        consistent: Boolean
    ): E? {
        val query = DynamoDBQueryExpression<E>()
        val keyObject = TYPE.getDeclaredConstructor().newInstance()
        keyObject.hash = hash
        query.isConsistentRead = consistent
        query.hashKeyValues = keyObject
        query.limit = 1

        val timeBefore = System.currentTimeMillis()

        preOperationTime.set(System.nanoTime() - startTime.get())
        val items = mapper.query(TYPE, query)
        operationTime.set(System.nanoTime() - startTime.get())

        if (logger.isDebugEnabled) {
            logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
        }

        return if (!items.isEmpty()) items[0] else null
    }

    private fun returnTimedResult(
        readResult: AsyncResult<E>,
        preOperationTime: AtomicLong,
        operationTime: AtomicLong,
        postOperationTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemResult<E>>>
    ) {
        val eItemResult = ItemResult(readResult.result(), false)
        eItemResult.preOperationProcessingTime = preOperationTime.get()
        eItemResult.operationProcessingTime = operationTime.get()
        eItemResult.postOperationProcessingTime = postOperationTime.get()

        resultHandler.handle(Future.succeededFuture(eItemResult))
    }

    fun readAll(resultHandler: Handler<AsyncResult<List<E>>>) {
        vertx.executeBlocking<List<E>>({ future ->
            try {
                val timeBefore = System.currentTimeMillis()

                val items = mapper.parallelScan(TYPE, DynamoDBScanExpression(), coreNum)
                items.loadAllResults()

                if (logger.isDebugEnabled) {
                    logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
                }

                future.complete(items)
            } catch (ase: AmazonServiceException) {
                logger.error("Could not complete DynamoDB Operation, " +
                        "Error Message:  " + ase.message + ", " +
                        "HTTP Status:    " + ase.statusCode + ", " +
                        "AWS Error Code: " + ase.errorCode + ", " +
                        "Error Type:     " + ase.errorType + ", " +
                        "Request ID:     " + ase.requestId)

                future.fail(ase)
            } catch (ace: AmazonClientException) {
                logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

                future.fail(ace)
            } catch (e: Exception) {
                logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                future.fail(e)
            }
        }, false, {
            when {
                it.failed() -> {
                    logger.error("Error in readAll!", it.cause())

                    resultHandler.handle(ServiceException.fail(500, "Error in readAll!",
                            JsonObject(Json.encode(it.cause()))))
                }
                else -> resultHandler.handle(Future.succeededFuture(it.result()))
            }
        })
    }

    fun readAllPaginated(resultHandler: Handler<AsyncResult<PaginatedParallelScanList<E>>>) {
        vertx.executeBlocking<PaginatedParallelScanList<E>>({
            try {
                val timeBefore = System.currentTimeMillis()

                val items = mapper.parallelScan(TYPE, DynamoDBScanExpression(), coreNum)

                if (logger.isDebugEnabled) {
                    logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
                }

                it.complete(items)
            } catch (ase: AmazonServiceException) {
                logger.error("Could not complete DynamoDB Operation, " +
                        "Error Message:  " + ase.message + ", " +
                        "HTTP Status:    " + ase.statusCode + ", " +
                        "AWS Error Code: " + ase.errorCode + ", " +
                        "Error Type:     " + ase.errorType + ", " +
                        "Request ID:     " + ase.requestId)

                it.fail(ase)
            } catch (ace: AmazonClientException) {
                logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

                it.fail(ace)
            } catch (e: Exception) {
                logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                it.fail(e)
            }
        }, false, {
            when {
                it.failed() -> {
                    logger.error("Error in readAllPaginated!", it.cause())

                    resultHandler.handle(ServiceException.fail(500, "Error in readAll!",
                            JsonObject(Json.encode(it.cause()))))
                }
                else -> resultHandler.handle(Future.succeededFuture(it.result()))
            }
        })
    }

    fun readAll(
        identifiers: JsonObject,
        filterParameterMap: Map<String, List<FilterParameter>>,
        resultHandler: Handler<AsyncResult<List<E>>>
    ) {
        vertx.executeBlocking<List<E>>({
            try {
                val identifier = identifiers.getString("hash")
                val filterExpression = dbParams.applyParameters(null, filterParameterMap)

                if (filterExpression.keyConditionExpression == null) {
                    val keyItem = TYPE.getDeclaredConstructor().newInstance()
                    keyItem.hash = identifier
                    filterExpression.hashKeyValues = keyItem
                }

                filterExpression.isConsistentRead = false

                val timeBefore = System.currentTimeMillis()

                val items = mapper.query(TYPE, filterExpression)
                items.loadAllResults()

                if (logger.isDebugEnabled) {
                    logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
                }

                it.complete(items)
            } catch (ase: AmazonServiceException) {
                logger.error("Could not complete DynamoDB Operation, " +
                        "Error Message:  " + ase.message + ", " +
                        "HTTP Status:    " + ase.statusCode + ", " +
                        "AWS Error Code: " + ase.errorCode + ", " +
                        "Error Type:     " + ase.errorType + ", " +
                        "Request ID:     " + ase.requestId)

                it.fail(ase)
            } catch (ace: AmazonClientException) {
                logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

                it.fail(ace)
            } catch (e: Exception) {
                logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                it.fail(e)
            }
        }, false, {
            when {
                it.failed() -> {
                    logger.error("Error in Read All!", it.cause())

                    resultHandler.handle(ServiceException.fail(500, "Error in readAllWithoutPagination!",
                            JsonObject(Json.encode(it.cause()))))
                }
                else -> resultHandler.handle(Future.succeededFuture(it.result()))
            }
        })
    }

    fun readAll(
        identifiers: JsonObject,
        pageToken: String?,
        queryPack: QueryPack,
        projections: Array<String>,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        readAll(identifiers, pageToken, queryPack, projections, null, resultHandler)
    }

    fun readAll(
        pageToken: String?,
        queryPack: QueryPack,
        projections: Array<String>,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        readAll(JsonObject(), pageToken, queryPack, projections, null, resultHandler)
    }

    fun readAll(
        identifiers: JsonObject,
        pageToken: String?,
        queryPack: QueryPack,
        projections: Array<String>,
        GSI: String?,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        val startTime = AtomicLong()
        startTime.set(System.nanoTime())

        val hash = identifiers.getString("hash")
        val cacheId = TYPE.simpleName + "_" + hash +
                if (queryPack.baseEtagKey != null) "/" + queryPack.baseEtagKey!! else "/START"

        if (logger.isDebugEnabled) {
            logger.debug("Running readAll with: $hash : $cacheId")
        }

        vertx.executeBlocking<ItemList<E>>({ future ->
            cacheManager.checkItemListCache(cacheId, projections, Handler {
                when {
                    it.failed() -> future.fail(it.cause())
                    else -> future.complete(it.result())
                }
            })
        }, false, {
            when {
                it.succeeded() -> {
                    resultHandler.handle(Future.succeededFuture(ItemListResult(it.result(), true)))

                    if (logger.isDebugEnabled) {
                        logger.debug("Served cached version of: $cacheId")
                    }
                }
                else -> {
                    val orderByQueue = queryPack.orderByQueue
                    val params = queryPack.params
                    val indexName = queryPack.indexName
                    val limit = queryPack.limit

                    if (logger.isDebugEnabled) {
                        logger.debug("Building expression with: " + Json.encodePrettily(queryPack))
                    }

                    var filterExpression: DynamoDBQueryExpression<E>? = null
                    val multiple = identifiers.getBoolean(MULTIPLE_KEY)

                    if ((multiple == null || !multiple) && (orderByQueue != null || params != null)) {
                        filterExpression = dbParams.applyParameters(orderByQueue?.peek(), params)
                        filterExpression = dbParams.applyOrderBy(orderByQueue, GSI, indexName, filterExpression)
                        filterExpression.limit = if (limit == null || limit == 0) 20 else limit

                        if (logger.isDebugEnabled) {
                            logger.debug("Custom filter is: " +
                                    "\nIndex: " + filterExpression.indexName +
                                    "\nLimit: " + filterExpression.limit +
                                    " (" + (limit ?: 20) + ") " +
                                    "\nExpression: " + filterExpression.filterExpression +
                                    "\nRange Key Condition: " + filterExpression.rangeKeyConditions +
                                    "\nAsc: " + filterExpression.isScanIndexForward)
                        }
                    }

                    val etagKey = queryPack.baseEtagKey

                    returnDatabaseContent(queryPack, identifiers, pageToken, hash, etagKey!!, cacheId,
                            filterExpression, projections, GSI, startTime, resultHandler)
                }
            }
        })
    }

    private fun returnDatabaseContent(
        queryPack: QueryPack,
        identifiers: JsonObject,
        pageToken: String?,
        hash: String?,
        etagKey: String,
        cacheId: String,
        filteringExpression: DynamoDBQueryExpression<E>?,
        projections: Array<String>,
        GSI: String?,
        startTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        vertx.executeBlocking<ItemListResult<E>>({ future ->
            val multiple = identifiers.getBoolean(MULTIPLE_KEY)
            val unFilteredIndex = filteringExpression == null
            var alternateIndex: String? = null
            val params = queryPack.params
            val nameParams = if (params == null) null else params[PAGINATION_IDENTIFIER]
            val itemListFuture = Future.future<ItemListResult<E>>()

            if (logger.isDebugEnabled) {
                logger.debug("Do remoteRead")
            }

            try {
                if (filteringExpression != null) {
                    alternateIndex = db.getAlternativeIndexIdentifier(filteringExpression.indexName)
                }

                when {
                    identifiers.isEmpty || identifiers.getString("hash") == null ->
                        runRootQuery(queryPack.baseEtagKey, multiple, identifiers, hash, queryPack, filteringExpression,
                            GSI, projections, pageToken, unFilteredIndex, alternateIndex, startTime,
                            itemListFuture)
                    params != null && nameParams != null && dbParams.isIllegalRangedKeyQueryParams(nameParams) ->
                        runIllegalRangedKeyQueryAsScan(queryPack.baseEtagKey, hash, queryPack,
                            GSI, projections, pageToken, unFilteredIndex, alternateIndex, startTime,
                            itemListFuture)
                    else -> runStandardQuery(queryPack.baseEtagKey, multiple, identifiers, hash, filteringExpression,
                            GSI, projections, pageToken, unFilteredIndex, alternateIndex, startTime,
                            itemListFuture)
                }

                itemListFuture.setHandler { itemListResult ->
                    when {
                        itemListResult.failed() -> future.fail(itemListResult.cause())
                        else -> {
                            val returnList = itemListResult.result()
                            val itemList = returnList.itemList

                            if (logger.isDebugEnabled) {
                                logger.debug("Constructed items!")
                            }
                            val itemListCacheFuture = Future.future<Boolean>()

                            when {
                                cacheManager.isItemListCacheAvailable -> {
                                    if (logger.isDebugEnabled) {
                                        logger.debug("Constructing cache!")
                                    }

                                    val cacheObject = itemList?.toJson(projections)

                                    if (logger.isDebugEnabled) {
                                        logger.debug("Cache complete!")
                                    }

                                    val content = cacheObject?.encode()

                                    if (logger.isDebugEnabled) {
                                        logger.debug("Cache encoded!")
                                    }

                                    cacheManager.replaceItemListCache(content!!, Supplier { cacheId }, Handler {
                                        if (logger.isDebugEnabled) {
                                            logger.debug("Setting: " + etagKey + " with: " + itemList.meta?.etag)
                                        }

                                        val etagItemListHashKey = TYPE.simpleName + "_" +
                                                identifiers.encode().hashCode() + "_" + "itemListEtags"

                                        when {
                                            etagManager != null ->
                                                etagManager.setItemListEtags(etagItemListHashKey, etagKey, itemList, itemListCacheFuture)
                                            else -> itemListCacheFuture.complete()
                                        }
                                    })
                                }
                                else -> itemListCacheFuture.complete()
                            }

                            itemListCacheFuture.setHandler { future.complete(returnList) }
                        }
                    }
                }
            } catch (ase: AmazonServiceException) {
                logger.error("Could not complete DynamoDB Operation, " +
                        "Error Message:  " + ase.message + ", " +
                        "HTTP Status:    " + ase.statusCode + ", " +
                        "AWS Error Code: " + ase.errorCode + ", " +
                        "Error Type:     " + ase.errorType + ", " +
                        "Request ID:     " + ase.requestId)

                future.fail(ase)
            } catch (ace: AmazonClientException) {
                logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

                future.fail(ace)
            } catch (e: Exception) {
                logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                future.fail(e)
            }
        }, false, {
            when {
                it.failed() -> resultHandler.handle(ServiceException.fail(500, "Error in readAll!",
                        JsonObject(Json.encode(it.cause()))))
                else -> resultHandler.handle(Future.succeededFuture(it.result()))
            }
        })
    }

    @Throws(InstantiationException::class, IllegalAccessException::class)
    private fun runStandardQuery(
        baseEtagKey: String?,
        multiple: Boolean?,
        identifiers: JsonObject,
        hash: String?,
        filteringExpression: DynamoDBQueryExpression<E>?,
        GSI: String?,
        projections: Array<String>,
        pageToken: String?,
        unFilteredIndex: Boolean,
        alternateIndex: String?,
        startTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        when {
            multiple != null && multiple ->
                standardMultipleQuery(baseEtagKey, identifiers, hash, filteringExpression, pageToken, GSI, unFilteredIndex,
                    alternateIndex, projections, startTime, resultHandler)
            else -> standardQuery(baseEtagKey, identifiers, hash, filteringExpression, pageToken, GSI, unFilteredIndex,
                    alternateIndex, projections, startTime, resultHandler)
        }
    }

    private fun standardMultipleQuery(
        baseEtagKey: String?,
        identifiers: JsonObject,
        hash: String?,
        filteringExpression: DynamoDBQueryExpression<E>?,
        pageToken: String?,
        GSI: String?,
        unFilteredIndex: Boolean,
        alternateIndex: String?,
        projections: Array<String>,
        startTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        val preOperationTime = AtomicLong()
        val operationTime = AtomicLong()
        val postOperationTime = AtomicLong()

        if (logger.isDebugEnabled) {
            logger.debug("Running multiple id query...")
        }

        val multipleIds = identifiers.getJsonArray("range").stream()
                .map<String> { it.toString() }
                .collect(toList())

        val keyPairsList = multipleIds.stream()
                .distinct()
                .map<KeyPair> { id ->
                    if (hashOnlyModel()) {
                        KeyPair().withHashKey(hash)
                    } else {
                        KeyPair().withHashKey(hash).withRangeKey(id)
                    }
                }.collect(toList())

        val keyPairs = HashMap<Class<*>, List<KeyPair>>()
        keyPairs[TYPE] = keyPairsList

        if (logger.isDebugEnabled) {
            logger.debug("Keypairs: " + Json.encodePrettily(keyPairs))
        }

        val timeBefore = System.currentTimeMillis()

        preOperationTime.set(System.nanoTime() - startTime.get())
        val items = mapper.batchLoad(keyPairs)
        operationTime.set(System.nanoTime() - preOperationTime.get())

        var pageCount = items[COLLECTION]?.size
        val desiredCount = if (filteringExpression != null && filteringExpression.limit != null)
            filteringExpression.limit
        else
            20

        if (logger.isDebugEnabled) {
            logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
        }

        if (logger.isDebugEnabled) {
            logger.debug("Items Returned for collection: $pageCount")
        }

        @Suppress("UNCHECKED_CAST")
        var allItems = items[COLLECTION]?.stream()
                ?.map { item -> item as E }
                ?.sorted(Comparator.comparing(Function<E, String> { it.range!! },
                        Comparator.comparingInt<String> { multipleIds.indexOf(it) }))
                ?.collect(toList())
        var oldPageToken: AttributeValue? = null

        if (pageToken != null) {
            val pageTokenMap = getTokenMap(pageToken, GSI, PAGINATION_IDENTIFIER)
            oldPageToken = pageTokenMap?.remove("oldPageToken")
            val id = pageTokenMap!![IDENTIFIER]?.s

            val first = allItems?.stream()
                    ?.filter { item ->
                        if (logger.isDebugEnabled) {
                            logger.debug("Id is: " + id + ", Range is: " + item.range)
                        }

                        item.range == id
                    }
                    ?.findFirst()

            if (first?.isPresent!!) {
                allItems = allItems?.subList(allItems.indexOf(first.get()) + 1, allItems.size)
            }
        }

        val itemList = allItems!!.stream()
                .limit(desiredCount.toLong())
                .collect(toList())

        pageCount = allItems.size
        val count = if (pageCount < desiredCount) pageCount else desiredCount
        val pagingToken = setScanPageToken(pageToken, pageCount, desiredCount, itemList,
                GSI, alternateIndex, unFilteredIndex)

        returnTimedItemListResult(baseEtagKey, resultHandler, count, items.size,
                oldPageToken?.s, pageToken, pagingToken,
                itemList, projections,
                preOperationTime, operationTime, postOperationTime)
    }

    private fun returnTimedItemListResult(
        baseEtagKey: String?,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>,
        count: Int,
        totalCount: Int,
        previousPageToken: String?,
        pageToken: String?,
        nextPageToken: String,
        itemList: List<E>,
        projections: Array<String>,
        preOperationTime: AtomicLong,
        operationTime: AtomicLong,
        postOperationTime: AtomicLong
    ) {
        postOperationTime.set(System.nanoTime() - operationTime.get())
        val pageTokens = PageTokens(self = pageToken, next = nextPageToken, previous = previousPageToken)
        val eItemListResult = ItemListResult(baseEtagKey!!, count, totalCount, itemList, pageTokens, projections, false)
        eItemListResult.preOperationProcessingTime = preOperationTime.get()
        eItemListResult.operationProcessingTime = operationTime.get()
        eItemListResult.postOperationProcessingTime = postOperationTime.get()

        resultHandler.handle(Future.succeededFuture(eItemListResult))
    }

    @Throws(IllegalAccessException::class, InstantiationException::class)
    private fun standardQuery(
        baseEtagKey: String?,
        identifiers: JsonObject,
        hash: String?,
        filteringExpression: DynamoDBQueryExpression<E>?,
        pageToken: String?,
        GSI: String?,
        unFilteredIndex: Boolean,
        alternateIndex: String?,
        projections: Array<String>,
        startTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        val preOperationTime = AtomicLong()
        val operationTime = AtomicLong()
        val postOperationTime = AtomicLong()
        val queryExpression: DynamoDBQueryExpression<E>

        val effectivelyFinalProjections = dbParams.buildProjections(projections,
                if (identifiers.isEmpty || filteringExpression == null)
                    paginationIndex!!
                else
                    alternateIndex!!)

        if (logger.isDebugEnabled) {
            logger.debug("Running standard query...")
        }

        val oldPageToken: AttributeValue?

        when (filteringExpression) {
            null -> {
                val tokenMap = getTokenMap(pageToken, GSI, PAGINATION_IDENTIFIER)
                oldPageToken = tokenMap?.remove("oldPageToken")
                queryExpression = DynamoDBQueryExpression()
                queryExpression.indexName = GSI ?: paginationIndex
                queryExpression.limit = 20
                queryExpression.isScanIndexForward = false
                queryExpression.setExclusiveStartKey(tokenMap)
            }
            else -> {
                val tokenMap = getTokenMap(pageToken, GSI, alternateIndex ?: PAGINATION_IDENTIFIER)
                oldPageToken = tokenMap?.remove("oldPageToken")
                queryExpression = filteringExpression
                queryExpression.setExclusiveStartKey(tokenMap)
            }
        }

        when (GSI) {
            null -> when {
                queryExpression.keyConditionExpression == null -> {
                    val keyItem = TYPE.getDeclaredConstructor().newInstance()
                    keyItem.hash = hash
                    keyItem.range = null
                    queryExpression.hashKeyValues = keyItem
                }
            }
            else -> if (queryExpression.keyConditionExpression == null && hash != null) {
                setFilterExpressionKeyCondition(queryExpression, GSI, hash)
            }
        }

        queryExpression.isConsistentRead = false

        var desiredCount = 0

        if (!unFilteredIndex) {
            desiredCount = queryExpression.limit!!
            queryExpression.limit = null
        }

        setProjectionsOnQueryExpression(queryExpression, effectivelyFinalProjections)

        if (logger.isDebugEnabled) {
            logger.debug(Json.encodePrettily(queryExpression))
        }

        val timeBefore = System.currentTimeMillis()

        preOperationTime.set(System.nanoTime() - startTime.get())
        val queryPageResults = mapper.queryPage(TYPE, queryExpression)
        operationTime.set(System.nanoTime() - preOperationTime.get())

        if (logger.isDebugEnabled) {
            logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
        }

        if (logger.isDebugEnabled) {
            logger.debug("Scanned items: " + queryPageResults.scannedCount!!)
        }

        val pageCount: Int
        val count: Int
        val itemList: List<E>
        val pagingToken: String
        val lastEvaluatedKey: Map<String, AttributeValue>?

        when {
            !unFilteredIndex -> {
                pageCount = queryPageResults.count!!
                itemList = queryPageResults.results
                        .subList(0, if (pageCount < desiredCount) pageCount else desiredCount)
                count = if (pageCount < desiredCount) pageCount else desiredCount
                pagingToken = setScanPageToken(pageToken, pageCount, desiredCount, itemList, GSI, alternateIndex, false)
            }
            else -> {
                count = queryPageResults.count!!
                itemList = queryPageResults.results
                lastEvaluatedKey = queryPageResults.lastEvaluatedKey
                pagingToken = if (lastEvaluatedKey == null)
                    "END_OF_LIST"
                else
                    Base64.getUrlEncoder().encodeToString(
                            (("${extractSelfToken(pageToken)}:" +
                                    setPageToken(lastEvaluatedKey, GSI, true, alternateIndex))
                                    .toByteArray()))
            }
        }

        returnTimedItemListResult(baseEtagKey, resultHandler, count, queryPageResults.scannedCount,
                oldPageToken?.s, pageToken, pagingToken, itemList, projections,
                preOperationTime, operationTime, postOperationTime)
    }

    private fun runIllegalRangedKeyQueryAsScan(
        baseEtagKey: String?,
        hash: String?,
        queryPack: QueryPack,
        GSI: String?,
        projections: Array<String>,
        pageToken: String?,
        unFilteredIndex: Boolean,
        alternateIndex: String?,
        startTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        val preOperationTime = AtomicLong()
        val operationTime = AtomicLong()
        val postOperationTime = AtomicLong()

        if (logger.isDebugEnabled) {
            logger.debug("Running illegal rangedKey query...")
        }

        val hashScanKey = "#HASH_KEY_VALUE"
        val hashScanValue = ":HASH_VALUE"

        val scanExpression = dbParams.applyParameters(queryPack.params)
        var conditionString = scanExpression.filterExpression
        if (hash != null) conditionString += " AND $hashScanKey = $hashScanValue"
        scanExpression.filterExpression = conditionString

        when (GSI) {
            null -> scanExpression.expressionAttributeNames[hashScanKey] = HASH_IDENTIFIER
            else -> scanExpression.expressionAttributeNames[hashScanKey] = GSI_KEY_MAP[GSI]?.getString("hash")
        }

        scanExpression.expressionAttributeValues[hashScanValue] = AttributeValue().withS(hash)

        val pageTokenMap = getTokenMap(pageToken, GSI, PAGINATION_IDENTIFIER)

        scanExpression.limit = null
        scanExpression.indexName = GSI ?: paginationIndex
        scanExpression.isConsistentRead = false
        setProjectionsOnScanExpression(scanExpression, projections)

        val timeBefore = System.currentTimeMillis()

        if (logger.isDebugEnabled) {
            logger.debug("Scan expression is: " + Json.encodePrettily(scanExpression))
        }

        preOperationTime.set(System.nanoTime() - startTime.get())
        val items = mapper.scanPage(TYPE, scanExpression)
        operationTime.set(System.nanoTime() - preOperationTime.get())

        var pageCount = items.count!!
        val desiredCount = if (scanExpression.limit != null) scanExpression.limit else 20

        if (logger.isDebugEnabled) {
            logger.debug(pageCount.toString() + " results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
        }

        val queue = queryPack.orderByQueue

        val orderIsAscending = queue != null && queue.size > 0 && queue.peek().direction == "asc"
        val finalAlternateIndex = if (queue != null && queue.size > 0) queue.peek().field else PAGINATION_IDENTIFIER
        val comparator = Comparator.comparing<E, String> { e -> db.getFieldAsString(finalAlternateIndex!!, e) }
        val comparing = if (orderIsAscending) comparator else comparator.reversed()

        var allItems = items.results.stream()
                .sorted(comparing)
                .collect(toList())

        var oldPageToken: AttributeValue? = null

        if (pageToken != null) {
            val id = pageTokenMap!![IDENTIFIER]?.s
            oldPageToken = pageTokenMap.remove("oldPageToken")

            val first = allItems.stream()
                    .filter { item ->
                        if (logger.isDebugEnabled) {
                            logger.debug("Id is: " + id + ", Range is: " + item.range)
                        }

                        item.range == id
                    }
                    .findFirst()

            if (first.isPresent) {
                allItems = allItems.subList(allItems.indexOf(first.get()) + 1, allItems.size)
            }
        }

        val itemList = allItems.stream()
                .limit(desiredCount.toLong())
                .collect(toList())

        pageCount = allItems.size
        val count = if (pageCount < desiredCount) pageCount else desiredCount
        val pagingToken = setScanPageToken(pageToken, pageCount, desiredCount, itemList,
                GSI, alternateIndex, unFilteredIndex)

        returnTimedItemListResult(baseEtagKey, resultHandler, count, items.scannedCount,
                oldPageToken?.s, pageToken, pagingToken, itemList, projections,
                preOperationTime, operationTime, postOperationTime)
    }

    private fun runRootQuery(
        baseEtagKey: String?,
        multiple: Boolean?,
        identifiers: JsonObject,
        hash: String?,
        queryPack: QueryPack,
        filteringExpression: DynamoDBQueryExpression<E>?,
        GSI: String?,
        projections: Array<String>,
        pageToken: String?,
        unFilteredIndex: Boolean,
        alternateIndex: String?,
        startTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        when {
            multiple != null && multiple ->
                rootMultipleQuery(baseEtagKey, identifiers, hash, filteringExpression, GSI, pageToken, projections,
                    unFilteredIndex, alternateIndex, startTime, resultHandler)
            else ->
                rootRootQuery(baseEtagKey, queryPack, GSI, pageToken, projections, unFilteredIndex, alternateIndex,
                        startTime, resultHandler)
        }
    }

    private fun rootMultipleQuery(
        baseEtagKey: String?,
        identifiers: JsonObject,
        hash: String?,
        filteringExpression: DynamoDBQueryExpression<E>?,
        GSI: String?,
        pageToken: String?,
        projections: Array<String>,
        unFilteredIndex: Boolean,
        alternateIndex: String?,
        startTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        val preOperationTime = AtomicLong()
        val operationTime = AtomicLong()
        val postOperationTime = AtomicLong()

        if (logger.isDebugEnabled) {
            logger.debug("Running root multiple id query...")
        }

        val multipleIds = identifiers.getJsonArray("range").stream()
                .map<String> { it.toString() }
                .collect(toList())

        val keyPairsList = multipleIds.stream()
                .distinct()
                .map<KeyPair> { id ->
                    if (hashOnlyModel()) {
                        KeyPair().withHashKey(id)
                    } else {
                        KeyPair().withHashKey(hash).withRangeKey(id)
                    }
                }.collect(toList())

        val keyPairs = HashMap<Class<*>, List<KeyPair>>()
        keyPairs[TYPE] = keyPairsList

        if (logger.isDebugEnabled) {
            logger.debug("Keypairs: " + Json.encodePrettily(keyPairs))
        }

        val timeBefore = System.currentTimeMillis()

        preOperationTime.set(System.nanoTime() - startTime.get())
        val items = mapper.batchLoad(keyPairs)
        operationTime.set(System.nanoTime() - preOperationTime.get())

        var pageCount = items[COLLECTION]?.size
        val desiredCount = if (filteringExpression != null && filteringExpression.limit != null)
            filteringExpression.limit
        else
            20

        if (logger.isDebugEnabled) {
            logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
        }

        if (logger.isDebugEnabled) {
            logger.debug("Items Returned for collection: $pageCount")
        }

        @Suppress("UNCHECKED_CAST")
        var allItems = items[COLLECTION]?.stream()
                ?.map { item -> item as E }
                ?.sorted(Comparator.comparing(if (hashOnlyModel()) Function<E, String> { it.hash!! } else Function { it.range!! },
                        Comparator.comparingInt<String> { multipleIds.indexOf(it) }))
                ?.collect(toList())

        var oldPageToken: AttributeValue? = null

        if (pageToken != null) {
            val pageTokenMap = getTokenMap(pageToken, GSI, PAGINATION_IDENTIFIER)
            oldPageToken = pageTokenMap?.remove("oldPageToken")
            val id = pageTokenMap!![if (hashOnlyModel()) HASH_IDENTIFIER else IDENTIFIER]?.s

            allItems = reduceByPageToken(allItems!!, id!!)
        }

        val itemList = allItems!!.stream()
                .limit(desiredCount.toLong())
                .collect(toList())

        pageCount = allItems.size
        val count = if (pageCount < desiredCount) pageCount else desiredCount
        val pagingToken = setScanPageToken(pageToken, pageCount, desiredCount, itemList,
                GSI, alternateIndex, unFilteredIndex)

        returnTimedItemListResult(baseEtagKey, resultHandler, count, items.size,
                oldPageToken?.s, pageToken, pagingToken, itemList, projections,
                preOperationTime, operationTime, postOperationTime)
    }

    private fun rootRootQuery(
        baseEtagKey: String?,
        queryPack: QueryPack,
        GSI: String?,
        pageToken: String?,
        projections: Array<String>,
        unFilteredIndex: Boolean,
        alternateIndex: String?,
        startTime: AtomicLong,
        resultHandler: Handler<AsyncResult<ItemListResult<E>>>
    ) {
        val preOperationTime = AtomicLong()
        val operationTime = AtomicLong()
        val postOperationTime = AtomicLong()

        if (logger.isDebugEnabled) {
            logger.debug("Running root query...")
        }

        val scanExpression = dbParams.applyParameters(queryPack.params)
        val pageTokenMap = getTokenMap(pageToken, GSI, PAGINATION_IDENTIFIER)
        val oldPageToken = pageTokenMap?.remove("oldPageToken")

        scanExpression.limit = null
        scanExpression.indexName = GSI ?: paginationIndex
        scanExpression.isConsistentRead = false
        setProjectionsOnScanExpression(scanExpression, projections)

        val timeBefore = System.currentTimeMillis()

        if (logger.isDebugEnabled) {
            logger.debug("Scan expression is: " + Json.encodePrettily(scanExpression))
        }

        preOperationTime.set(System.nanoTime() - startTime.get())
        val items = mapper.scanPage(TYPE, scanExpression)
        operationTime.set(System.nanoTime() - preOperationTime.get())

        var pageCount = items.count!!
        val desiredCount = if (queryPack.limit == null || queryPack.limit == 0) 20 else queryPack.limit ?: 20

        if (logger.isDebugEnabled) {
            logger.debug("DesiredCount is: $desiredCount")
            logger.debug(pageCount.toString() + " results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
        }

        val queue = queryPack.orderByQueue

        val orderIsAscending = queue != null && queue.size > 0 && queue.peek().direction == "asc"

        val finalAlternateIndex = if (queue != null && queue.size > 0)
            queue.peek().field
        else
            PAGINATION_IDENTIFIER

        val comparator = Comparator.comparing<E, String> { e -> db.getFieldAsString(finalAlternateIndex!!, e) }
        val comparing = if (orderIsAscending) comparator else comparator.reversed()

        var allItems = items.results.stream()
                .sorted(comparing)
                .collect(toList())

        if (pageToken != null) {
            val id = pageTokenMap!![if (hashOnlyModel()) HASH_IDENTIFIER else IDENTIFIER]?.s

            allItems = reduceByPageToken(allItems, id!!)
        }

        val itemList = allItems.stream()
                .limit(desiredCount.toLong())
                .collect(toList())

        pageCount = allItems.size
        val count = if (pageCount < desiredCount) pageCount else desiredCount
        val pagingToken = setScanPageToken(pageToken, pageCount, desiredCount, itemList,
                GSI, alternateIndex, unFilteredIndex)

        returnTimedItemListResult(baseEtagKey, resultHandler, count, items.scannedCount,
                oldPageToken?.s, pageToken, pagingToken,
                itemList, projections, preOperationTime, operationTime, postOperationTime)
    }

    private fun setProjectionsOnScanExpression(scanExpression: DynamoDBScanExpression, projections: Array<String>?) {
        if (projections != null && projections.isNotEmpty()) {
            scanExpression.withSelect("SPECIFIC_ATTRIBUTES")

            when {
                projections.size == 1 -> scanExpression.withProjectionExpression(projections[0])
                else -> scanExpression.withProjectionExpression(projections.joinToString(", "))
            }
        }
    }

    private fun setProjectionsOnQueryExpression(queryExpression: DynamoDBQueryExpression<E>, projections: Array<String>?) {
        if (projections != null && projections.isNotEmpty()) {
            queryExpression.withSelect("SPECIFIC_ATTRIBUTES")

            when {
                projections.size == 1 -> queryExpression.withProjectionExpression(projections[0])
                else -> queryExpression.withProjectionExpression(projections.joinToString(", "))
            }
        }
    }

    private fun setScanPageToken(
        pageToken: String?,
        pageCount: Int,
        desiredCount: Int,
        itemList: List<E>,
        GSI: String?,
        alternateIndex: String?,
        unFilteredIndex: Boolean
    ): String {
        when {
            pageCount > desiredCount -> {
                val lastItem = itemList[if (itemList.isEmpty()) 0 else itemList.size - 1]
                val lastEvaluatedKey = setLastKey(lastItem, GSI, alternateIndex)

                return when {
                    lastEvaluatedKey.isEmpty() -> "END_OF_LIST"
                    else -> {
                        Base64.getUrlEncoder().encodeToString(
                                ("${extractSelfToken(pageToken)}:" +
                                        setPageToken(lastEvaluatedKey, GSI, unFilteredIndex, alternateIndex))
                                        .toByteArray())
                    }
                }
            }
            else -> return "END_OF_LIST"
        }
    }

    private fun extractSelfToken(pageToken: String?): String {
        if (pageToken == null) return "null"

        return String(Base64.getUrlDecoder().decode(pageToken)).split(":".toRegex(), 2)[1]
    }

    private fun reduceByPageToken(allItems: List<E>, id: String): List<E> {
        val first = allItems.stream()
                .filter { item ->
                    when {
                        hashOnlyModel() -> {
                            if (logger.isDebugEnabled) {
                                logger.debug("Id is: " + id + ", Hash is: " + item.hash)
                            }

                            item.hash!! == id
                        }
                        else -> {
                            if (logger.isDebugEnabled) {
                                logger.debug("Id is: " + id + ", Range is: " + item.range)
                            }

                            item.range!! == id
                        }
                    }
                }
                .findFirst()

        return when {
            first.isPresent -> allItems.subList(allItems.indexOf(first.get()) + 1, allItems.size)
            else -> allItems
        }
    }

    private fun hashOnlyModel(): Boolean {
        return IDENTIFIER == null || IDENTIFIER == ""
    }

    private fun setPageToken(
        lastEvaluatedKey: Map<String, AttributeValue>,
        GSI: String?,
        unFilteredIndex: Boolean,
        alternateIndex: String?
    ): String {
        return dbParams.createNewPageToken(HASH_IDENTIFIER, IDENTIFIER!!, PAGINATION_IDENTIFIER,
                lastEvaluatedKey, GSI, GSI_KEY_MAP, if (unFilteredIndex) null else alternateIndex)
    }

    private fun getTokenMap(pageToken: String?, GSI: String?, index: String?): MutableMap<String, AttributeValue>? {
        return dbParams.createPageTokenMap(pageToken, HASH_IDENTIFIER, IDENTIFIER, index, GSI, GSI_KEY_MAP)
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    private fun setLastKey(lastKey: E, GSI: String?, index: String?): Map<String, AttributeValue> {
        val keyMap = HashMap<String, AttributeValue>()
        val hashOnlyModel = hashOnlyModel()

        keyMap[HASH_IDENTIFIER] = AttributeValue().withS(lastKey.hash)
        if (!hashOnlyModel) keyMap[IDENTIFIER!!] = AttributeValue().withS(lastKey.range)

        when {
            index == null && !hashOnlyModel ->
                keyMap[PAGINATION_IDENTIFIER!!] = db.getIndexValue(PAGINATION_IDENTIFIER, lastKey)
            !hashOnlyModel -> {
                if (logger.isDebugEnabled) {
                    logger.debug("Fetching remoteIndex value!")
                }

                keyMap[index!!] = db.getIndexValue(index, lastKey)
            }
        }

        if (GSI != null) {
            val keyObject = GSI_KEY_MAP[GSI]
            val hash = keyObject!!.getString("hash")
            val range = keyObject.getString("range")

            (keyMap as java.util.Map<String, AttributeValue>)
                    .putIfAbsent(hash, AttributeValue().withS(db.getFieldAsString(hash, lastKey)))

            if (!hashOnlyModel) {
                (keyMap as java.util.Map<String, AttributeValue>).putIfAbsent(range, db.getIndexValue(range, lastKey))
            }
        }

        return keyMap
    }

    fun readAllWithoutPagination(
        identifier: String,
        resultHandler: Handler<AsyncResult<List<E>>>
    ) {
        vertx.executeBlocking<List<E>>({
            try {
                val queryExpression = DynamoDBQueryExpression<E>()
                val keyItem = TYPE.getDeclaredConstructor().newInstance()
                keyItem.hash = identifier
                queryExpression.hashKeyValues = keyItem
                queryExpression.isConsistentRead = true

                if (logger.isDebugEnabled) {
                    logger.debug("readAllWithoutPagination single id, hashObject: " + Json.encodePrettily(keyItem))
                }

                val timeBefore = System.currentTimeMillis()

                val items = mapper.query(TYPE, queryExpression)
                items.loadAllResults()

                if (logger.isDebugEnabled) {
                    logger.debug(items.size.toString() + " results received in: " +
                            (System.currentTimeMillis() - timeBefore) + " ms")
                }

                it.complete(items)
            } catch (ase: AmazonServiceException) {
                logger.error("Could not complete DynamoDB Operation, " +
                        "Error Message:  " + ase.message + ", " +
                        "HTTP Status:    " + ase.statusCode + ", " +
                        "AWS Error Code: " + ase.errorCode + ", " +
                        "Error Type:     " + ase.errorType + ", " +
                        "Request ID:     " + ase.requestId)

                it.fail(ase)
            } catch (ace: AmazonClientException) {
                logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

                it.fail(ace)
            } catch (e: Exception) {
                logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                it.fail(e)
            }
        }, false, {
            when {
                it.failed() -> {
                    logger.error("Error in readAllWithoutPagination!", it.cause())

                    resultHandler.handle(ServiceException.fail(500, "Error in readAllWithoutPagination!",
                            JsonObject(Json.encode(it.cause()))))
                }
                else -> resultHandler.handle(Future.succeededFuture(it.result()))
            }
        })
    }

    fun readAllWithoutPagination(
        identifier: String,
        queryPack: QueryPack,
        resultHandler: Handler<AsyncResult<List<E>>>
    ) {
        readAllWithoutPagination(identifier, queryPack, null, resultHandler)
    }

    fun readAllWithoutPagination(
        queryPack: QueryPack?,
        projections: Array<String>?,
        GSI: String?,
        resultHandler: Handler<AsyncResult<List<E>>>
    ) {
        var params: Map<String, List<FilterParameter>>? = null
        if (queryPack != null) params = queryPack.params
        val finalParams = params

        if (logger.isDebugEnabled) {
            logger.debug("Running aggregation non pagination scan!")
        }

        vertx.executeBlocking<List<E>>({
            try {
                var scanExpression = DynamoDBScanExpression()
                if (finalParams != null) scanExpression = dbParams.applyParameters(finalParams)

                if (projections != null) {
                    setProjectionsOnScanExpression(scanExpression, projections)
                }

                val timeBefore = System.currentTimeMillis()

                if (GSI != null) {
                    scanExpression.indexName = GSI
                    scanExpression.isConsistentRead = false
                }

                val items = mapper.parallelScan(TYPE, scanExpression, coreNum)
                items.loadAllResults()

                if (logger.isDebugEnabled) {
                    logger.debug(items.size.toString() + " results received in: " +
                            (System.currentTimeMillis() - timeBefore) + " ms")
                }

                it.complete(items)
            } catch (ase: AmazonServiceException) {
                logger.error("Could not complete DynamoDB Operation, " +
                        "Error Message:  " + ase.message + ", " +
                        "HTTP Status:    " + ase.statusCode + ", " +
                        "AWS Error Code: " + ase.errorCode + ", " +
                        "Error Type:     " + ase.errorType + ", " +
                        "Request ID:     " + ase.requestId)

                it.fail(ase)
            } catch (ace: AmazonClientException) {
                logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

                it.fail(ace)
            } catch (e: Exception) {
                logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                it.fail(e)
            }
        }, false, {
            when {
                it.failed() -> {
                    logger.error("Error in readAllWithoutPagination!", it.cause())

                    resultHandler.handle(ServiceException.fail(500, "Error in readAll!",
                            JsonObject(Json.encode(it.cause()))))
                }
                else -> resultHandler.handle(Future.succeededFuture(it.result()))
            }
        })
    }

    @Suppress("SameParameterValue")
    private fun readAllWithoutPagination(
        identifier: String,
        queryPack: QueryPack,
        projections: Array<String>? = null,
        resultHandler: Handler<AsyncResult<List<E>>>
    ) {
        readAllWithoutPagination(identifier, queryPack, projections, null, resultHandler)
    }

    fun readAllWithoutPagination(
        identifier: String,
        queryPack: QueryPack,
        projections: Array<String>?,
        GSI: String?,
        resultHandler: Handler<AsyncResult<List<E>>>
    ) {
        vertx.executeBlocking<List<E>>({
            try {
                if (logger.isDebugEnabled) {
                    logger.debug("Running aggregation non pagination query with id: $identifier")
                }

                val orderByQueue = queryPack.orderByQueue
                val params = queryPack.params
                val indexName = queryPack.indexName

                if (logger.isDebugEnabled) {
                    logger.debug("Building expression...")
                }

                var filterExpression: DynamoDBQueryExpression<E>

                when {
                    params != null -> {
                        filterExpression = dbParams.applyParameters(
                                if (projections == null && orderByQueue != null) orderByQueue.peek() else null,
                                params)
                        if (projections == null) {
                            filterExpression = dbParams.applyOrderBy(orderByQueue, GSI, indexName, filterExpression)
                        }
                    }
                    else -> filterExpression = DynamoDBQueryExpression()
                }

                when (GSI) {
                    null -> {
                        if (filterExpression.keyConditionExpression == null) {
                            val keyItem = TYPE.getDeclaredConstructor().newInstance()
                            keyItem.hash = identifier
                            filterExpression.hashKeyValues = keyItem
                        }

                        filterExpression.setConsistentRead(true)
                    }
                    else -> {
                        filterExpression.indexName = GSI

                        if (filterExpression.keyConditionExpression == null) {
                            setFilterExpressionKeyCondition(filterExpression, GSI, identifier)
                        }
                    }
                }

                setProjectionsOnQueryExpression(filterExpression, projections)

                val timeBefore = System.currentTimeMillis()

                if (logger.isDebugEnabled) {
                    logger.debug("Filter Expression: " + Json.encodePrettily(filterExpression))
                }

                val items = mapper.query(TYPE, filterExpression)
                items.loadAllResults()

                if (logger.isDebugEnabled) {
                    logger.debug(items.size.toString() +
                            " results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
                }

                it.complete(items)
            } catch (ase: AmazonServiceException) {
                logger.error("Could not complete DynamoDB Operation, " +
                        "Error Message:  " + ase.message + ", " +
                        "HTTP Status:    " + ase.statusCode + ", " +
                        "AWS Error Code: " + ase.errorCode + ", " +
                        "Error Type:     " + ase.errorType + ", " +
                        "Request ID:     " + ase.requestId)

                it.fail(ase)
            } catch (ace: AmazonClientException) {
                logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

                it.fail(ace)
            } catch (e: Exception) {
                logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

                it.fail(e)
            }
        }, false, {
            when {
                it.failed() -> {
                    logger.error("Error in readAllWithoutPagination!", it.cause())

                    resultHandler.handle(ServiceException.fail(500, "Error in readAllWithoutPagination!",
                            JsonObject(Json.encode(it.cause()))))
                }
                else -> resultHandler.handle(Future.succeededFuture(it.result()))
            }
        })
    }

    @Throws(IllegalAccessException::class, InstantiationException::class)
    private fun setFilterExpressionKeyCondition(
        filterExpression: DynamoDBQueryExpression<E>,
        GSI: String?,
        identifier: String
    ) {
        val keyItem = TYPE.getDeclaredConstructor().newInstance()
        val gsiField = GSI_KEY_MAP[GSI]?.getString("hash")

        if (logger.isDebugEnabled) {
            logger.debug("Fetching field $gsiField")
        }

        val field = db.getField(gsiField!!)

        if (logger.isDebugEnabled) {
            logger.debug("Field collected $field")
        }

        field.set(keyItem, identifier)

        filterExpression.hashKeyValues = keyItem
        filterExpression.isConsistentRead = false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DynamoDBReader::class.java.simpleName)
    }
}
