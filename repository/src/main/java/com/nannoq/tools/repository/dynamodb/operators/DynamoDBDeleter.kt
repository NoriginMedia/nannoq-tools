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
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDeleteExpression
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.KeyPair
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue
import com.google.common.collect.ImmutableMap
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.DynamoDBModel
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.repository.cache.CacheManager
import com.nannoq.tools.repository.repository.etag.ETagManager
import io.vertx.core.*
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.serviceproxy.ServiceException
import java.util.*
import java.util.Collections.singletonMap
import java.util.stream.Collectors.toList
import java.util.stream.IntStream

/**
 * This class defines the deletion operations for the DynamoDBRepository.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class DynamoDBDeleter<E>(private val TYPE: Class<E>, private val vertx: Vertx, private val db: DynamoDBRepository<E>,
                         private val HASH_IDENTIFIER: String, private val IDENTIFIER: String?,
                         private val cacheManager: CacheManager<E>,
                         private val eTagManager: ETagManager<E>?)
        where E : Cacheable, E : ETagable, E : DynamoDBModel, E : Model {
    private val DYNAMO_DB_MAPPER: DynamoDBMapper

    init {
        this.DYNAMO_DB_MAPPER = db.dynamoDbMapper
    }

    fun doDelete(identifiers: List<JsonObject>, resultHandler: Handler<AsyncResult<List<E>>>) {
        vertx.executeBlocking<List<E>>({ future ->
            try {
                @Suppress("UNCHECKED_CAST")
                val items = DYNAMO_DB_MAPPER.batchLoad(singletonMap<Class<*>, List<KeyPair>>(TYPE, identifiers.stream()
                        .map { id ->
                            KeyPair()
                                    .withHashKey(id.getString("hash"))
                                    .withRangeKey(id.getString("range"))
                        }
                        .collect(toList()))).entries.iterator().next().value.stream()
                        .map { item -> item as E }
                        .collect(toList())

                if (logger.isDebugEnabled) {
                    logger.debug("To Delete: " + Json.encodePrettily(items))
                }

                val deleteFutures = ArrayList<Future<*>>()
                val etagFutures = ArrayList<Future<*>>()

                IntStream.range(0, items.size).forEach { i ->
                    val record = items[i]
                    val deleteFuture = Future.future<E>()
                    val deleteEtagsFuture = Future.future<Boolean>()

                    try {
                        eTagManager?.removeProjectionsEtags(identifiers[i].hashCode(), deleteEtagsFuture.completer())

                        this.optimisticLockingDelete(record, null, deleteFuture)
                    } catch (e: Exception) {
                        logger.error(e)

                        deleteFuture.fail(e)
                    }

                    deleteFutures.add(deleteFuture)
                    etagFutures.add(deleteEtagsFuture)
                }

                CompositeFuture.all(deleteFutures).setHandler { res ->
                    if (res.failed()) {
                        future.fail(res.cause())
                    } else {
                        val purgeFuture = Future.future<Boolean>()
                        purgeFuture.setHandler { purgeRes ->
                            if (purgeRes.failed()) {
                                future.fail(purgeRes.cause())
                            } else {
                                if (eTagManager != null) {
                                    val removeETags = Future.future<Boolean>()

                                    val hash = JsonObject().put("hash", items[0].hash)
                                            .encode().hashCode()
                                    eTagManager.destroyEtags(hash, removeETags.completer())

                                    etagFutures.add(removeETags)

                                    CompositeFuture.all(etagFutures).setHandler { etagRes ->
                                        if (etagRes.failed()) {
                                            future.fail(purgeRes.cause())
                                        } else {
                                            future.complete(deleteFutures.stream()
                                                    .map { finalFuture ->
                                                        @Suppress("UNCHECKED_CAST")
                                                        finalFuture.result() as E
                                                    }
                                                    .collect(toList()))
                                        }
                                    }
                                } else {
                                    future.complete(deleteFutures.stream()
                                            .map { finalFuture ->
                                                @Suppress("UNCHECKED_CAST")
                                                finalFuture.result() as E
                                            }
                                            .collect(toList()))
                                }
                            }
                        }

                        cacheManager.purgeCache(purgeFuture, items, {
                            val hash = it.hash
                            val range = it.range

                            TYPE.simpleName + "_" + hash + if (range == "") "" else "/$range"
                        })
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
        }, false) { result ->
            if (result.failed()) {
                resultHandler.handle(ServiceException.fail(500, "Unable to perform remoteDelete!",
                        JsonObject(Json.encode(result.cause()))))
            } else {
                resultHandler.handle(Future.succeededFuture(result.result()))
            }
        }
    }

    private fun optimisticLockingDelete(record: E?, prevCounter: Int?, deleteFuture: Future<E>) {
        var counter = 0
        if (prevCounter != null) counter = prevCounter

        try {
            DYNAMO_DB_MAPPER.delete(record, buildExistingDeleteExpression(record!!))

            deleteFuture.complete(record)
        } catch (e: ConditionalCheckFailedException) {
            logger.error("DeleteCollision on: " +
                    record!!.javaClass.simpleName + " : " + record.hash + " : " + record.range + ", " +
                    "Error Message:  " + e.message + ", " +
                    "HTTP Status:    " + e.statusCode + ", " +
                    "AWS Error Code: " + e.errorCode + ", " +
                    "Error Type:     " + e.errorType + ", " +
                    "Request ID:     " + e.requestId + ", " +
                    ", retrying...")

            if (counter > 100) {
                logger.error(Json.encodePrettily(record))

                throw InternalError()
            }

            val newestRecord = db.fetchNewestRecord(TYPE, record.hash!!, record.range)

            optimisticLockingDelete(newestRecord, ++counter, deleteFuture)
        } catch (ase: AmazonServiceException) {
            logger.error("Could not complete DynamoDB Operation, " +
                    "Error Message:  " + ase.message + ", " +
                    "HTTP Status:    " + ase.statusCode + ", " +
                    "AWS Error Code: " + ase.errorCode + ", " +
                    "Error Type:     " + ase.errorType + ", " +
                    "Request ID:     " + ase.requestId)

            if (counter > 100) {
                logger.error(Json.encodePrettily(record))

                throw InternalError()
            }

            val newestRecord = db.fetchNewestRecord(TYPE, record!!.hash!!, record.range)

            optimisticLockingDelete(newestRecord, ++counter, deleteFuture)
        } catch (ace: AmazonClientException) {
            logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

            if (counter > 100) {
                logger.error(Json.encodePrettily(record))

                throw InternalError()
            }

            val newestRecord = db.fetchNewestRecord(TYPE, record!!.hash!!, record.range)

            optimisticLockingDelete(newestRecord, ++counter, deleteFuture)
        }

    }

    private fun buildExistingDeleteExpression(element: E): DynamoDBDeleteExpression {
        val expectationbuilder = ImmutableMap.Builder<String, ExpectedAttributeValue>()
                .put(HASH_IDENTIFIER, db.buildExpectedAttributeValue(element.hash!!, true))

        if (IDENTIFIER != "") {
            expectationbuilder.put(IDENTIFIER, db.buildExpectedAttributeValue(element.range!!, true))
        }

        val saveExpr = DynamoDBDeleteExpression()
        saveExpr.expected = expectationbuilder.build()

        return saveExpr
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DynamoDBDeleter::class.java.simpleName)
    }
}