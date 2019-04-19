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
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression
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
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.serviceproxy.ServiceException
import java.util.Arrays

import java.util.function.Function

import java.util.stream.Collectors.toList

/**
 * This class defines the creation operations for the DynamoDBRepository.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class DynamoDBCreator<E>(
    private val TYPE: Class<E>,
    private val vertx: Vertx,
    private val db: DynamoDBRepository<E>,
    private val HASH_IDENTIFIER: String,
    private val IDENTIFIER: String?,
    private val cacheManager: CacheManager<E>,
    private val eTagManager: ETagManager<E>?
)
        where E : DynamoDBModel, E : Model, E : ETagable, E : Cacheable {
    private val DYNAMO_DB_MAPPER: DynamoDBMapper = db.dynamoDbMapper

    private val shortCacheIdSupplier: Function<E, String>
    private val cacheIdSupplier: Function<E, String>

    init {
        cacheIdSupplier = Function { e ->
            val hash = e.hash
            val range = e.range

            @Suppress("SENSELESS_COMPARISON")
            TYPE.simpleName + "_" + hash + if (range == null || range == "") "" else "/$range"
        }

        shortCacheIdSupplier = Function { e ->
            val hash = e.hash

            TYPE.simpleName + "_" + hash
        }
    }

    fun doWrite(create: Boolean, writeMap: Map<E, Function<E, E>>, resultHandler: Handler<AsyncResult<List<E>>>) {
        vertx.executeBlocking<List<E>>({ future ->
            try {
                val writeFutures = ArrayList<Future<*>>()

                writeMap.forEach { record: E, updateLogic: Function<E, E> ->
                    val writeFuture = Future.future<E>()

                    when {
                        !create -> {
                            if (logger.isDebugEnabled) {
                                logger.debug("Running remoteUpdate...")
                            }

                            try {
                                this.optimisticLockingSave(null, updateLogic, null, writeFuture, record)
                            } catch (e: Exception) {
                                logger.error(e)

                                writeFuture.fail(e)
                            }

                            writeFutures.add(writeFuture)
                        }
                        else -> {
                            if (logger.isDebugEnabled) {
                                logger.debug("Running remoteCreate...")
                            }

                            eTagManager?.setSingleRecordEtag(record.generateAndSetEtag(HashMap()), Handler {
                                if (it.failed()) logger.error("Failed etag operation!", it.cause())
                            })

                            try {
                                val finalRecord = db.setCreatedAt(db.setUpdatedAt(record))
                                val es = listOf(finalRecord)

                                DYNAMO_DB_MAPPER.save(finalRecord, buildExistingExpression(finalRecord, false))

                                val purgeFuture = Future.future<Boolean>()
                                destroyEtagsAfterCachePurge(writeFuture, finalRecord, purgeFuture)

                                cacheManager.replaceCache(purgeFuture, es, shortCacheIdSupplier, cacheIdSupplier)
                            } catch (e: Exception) {
                                writeFuture.fail(e)
                            }

                            writeFutures.add(writeFuture)
                        }
                    }
                }

                CompositeFuture.all(writeFutures).setHandler {
                    when {
                        it.failed() -> future.fail(it.cause())
                        else -> future.complete(writeFutures.stream()
                                .map { finalFuture ->
                                    @Suppress("UNCHECKED_CAST")
                                    finalFuture.result() as E
                                }
                                .collect(toList()))
                    }
                }
            } catch (ase: AmazonServiceException) {
                logger.error("Could not complete DynamoDB Operation, " +
                        "Error Message:  " + ase.message + ", " +
                        "HTTP Status:    " + ase.statusCode + ", " +
                        "AWS Error Code: " + ase.errorCode + ", " +
                        "Error Type:     " + ase.errorType + ", " +
                        "Request ID:     " + ase.requestId, ase)

                future.fail(ase)
            } catch (ace: AmazonClientException) {
                logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message, ace)

                future.fail(ace)
            } catch (e: Exception) {
                logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace), e)

                future.fail(e)
            }
        }, false) {
            if (it.failed()) {
                logger.error("Error in doWrite!", it.cause())

                resultHandler.handle(ServiceException.fail(500,
                        "An error occured when running doWrite: " + it.cause().message,
                        JsonObject(Json.encode(it.cause()))))
            } else {
                resultHandler.handle(Future.succeededFuture(it.result()))
            }
        }
    }

    private fun optimisticLockingSave(
        newerVersion: E?,
        updateLogic: Function<E, E>?,
        prevCounter: Int?,
        writeFuture: Future<E>,
        record: E
    ) {
        @Suppress("NAME_SHADOWING")
        var newerVersion = newerVersion
        var counter = 0
        if (prevCounter != null) counter = prevCounter

        try {
            when {
                newerVersion != null -> {
                    newerVersion = updateLogic!!.apply(newerVersion)
                    newerVersion = db.setUpdatedAt(newerVersion)

                    eTagManager?.setSingleRecordEtag(newerVersion.generateAndSetEtag(HashMap()), Handler {
                        if (it.failed()) logger.error("Failed etag operation!", it.cause())
                    })

                    if (logger.isDebugEnabled) {
                        logger.debug("Performing $counter remoteUpdate!")
                    }

                    DYNAMO_DB_MAPPER.save(newerVersion, buildExistingExpression(newerVersion, true))
                    val purgeFuture = Future.future<Boolean>()
                    destroyEtagsAfterCachePurge(writeFuture, record, purgeFuture)

                    cacheManager.replaceCache(purgeFuture, listOf(newerVersion),
                            shortCacheIdSupplier, cacheIdSupplier)
                    if (logger.isDebugEnabled) {
                        logger.debug("Update $counter performed successfully!")
                    }
                }
                else -> {
                    val updatedRecord = updateLogic!!.apply(record)
                    newerVersion = db.setUpdatedAt(updatedRecord)

                    eTagManager?.setSingleRecordEtag(updatedRecord.generateAndSetEtag(HashMap()), Handler {
                        if (it.failed()) logger.error("Failed etag operation!", it.cause())
                    })

                    if (logger.isDebugEnabled) {
                        logger.debug("Performing immediate remoteUpdate!")
                    }

                    DYNAMO_DB_MAPPER.save(updatedRecord, buildExistingExpression(record, true))
                    val purgeFuture = Future.future<Boolean>()
                    purgeFuture.setHandler { destroyEtagsAfterCachePurge(writeFuture, record, purgeFuture) }

                    cacheManager.replaceCache(purgeFuture, listOf(updatedRecord),
                            shortCacheIdSupplier, cacheIdSupplier)
                    if (logger.isDebugEnabled) {
                        logger.debug("Immediate remoteUpdate performed!")
                    }
                }
            }
        } catch (e: ConditionalCheckFailedException) {
            logger.error("SaveCollision on: " +
                    record.javaClass.simpleName + " : " + record.hash + " : " + record.range + ", " +
                    "Error Message:  " + e.message + ", " +
                    "HTTP Status:    " + e.statusCode + ", " +
                    "AWS Error Code: " + e.errorCode + ", " +
                    "Error Type:     " + e.errorType + ", " +
                    "Request ID:     " + e.requestId + ", " +
                    ", retrying...")

            if (counter > 100) {
                logger.error(Json.encodePrettily(record) + "\n:\n" + Json.encodePrettily(newerVersion))

                throw InternalError()
            }

            val newestRecord = db.fetchNewestRecord(TYPE, record.hash!!, record.range)

            optimisticLockingSave(newestRecord, updateLogic, ++counter, writeFuture, record)
        } catch (ase: AmazonServiceException) {
            logger.error("Could not complete DynamoDB Operation, " +
                    "Error Message:  " + ase.message + ", " +
                    "HTTP Status:    " + ase.statusCode + ", " +
                    "AWS Error Code: " + ase.errorCode + ", " +
                    "Error Type:     " + ase.errorType + ", " +
                    "Request ID:     " + ase.requestId)

            if (counter > 100) {
                logger.error(Json.encodePrettily(record) + "\n:\n" + Json.encodePrettily(newerVersion))

                throw InternalError()
            }

            val newestRecord = db.fetchNewestRecord(TYPE, record.hash!!, record.range)

            optimisticLockingSave(newestRecord, updateLogic, ++counter, writeFuture, record)
        } catch (ace: AmazonClientException) {
            logger.error("Internal Dynamodb Error, " + "Error Message:  " + ace.message)

            if (counter > 100) {
                logger.error(Json.encodePrettily(record) + "\n:\n" + Json.encodePrettily(newerVersion))

                throw InternalError()
            }

            val newestRecord = db.fetchNewestRecord(TYPE, record.hash!!, record.range)

            optimisticLockingSave(newestRecord, updateLogic, ++counter, writeFuture, record)
        }
    }

    private fun destroyEtagsAfterCachePurge(writeFuture: Future<E>, record: E, purgeFuture: Future<Boolean>) {
        val hashId = JsonObject().put("hash", record.hash).encode().hashCode()

        purgeFuture.setHandler { purgeRes ->
            when {
                purgeRes.failed() ->
                    when {
                        eTagManager != null -> eTagManager.destroyEtags(hashId, Handler { writeFuture.complete(record) })
                        else -> writeFuture.complete(record)
                    }
                else ->
                    when {
                        eTagManager != null -> {
                            val removeProjections = Future.future<Boolean>()
                            val removeETags = Future.future<Boolean>()

                            eTagManager.removeProjectionsEtags(hashId, removeProjections.completer())
                            eTagManager.destroyEtags(hashId, removeETags.completer())

                            CompositeFuture.all(removeProjections, removeETags).setHandler {
                                when {
                                    it.failed() -> writeFuture.fail(it.cause())
                                    else -> writeFuture.complete(record)
                                }
                            }
                        }
                        else -> writeFuture.complete(record)
                    }
            }
        }
    }

    private fun buildExistingExpression(element: E, exists: Boolean): DynamoDBSaveExpression {
        val expectationbuilder = ImmutableMap.Builder<String, ExpectedAttributeValue>()
                .put(HASH_IDENTIFIER, db.buildExpectedAttributeValue(element.hash!!, exists))
        val rangeValue = element.range

        if (IDENTIFIER != null && IDENTIFIER != "" && rangeValue != null) {
            expectationbuilder.put(IDENTIFIER, db.buildExpectedAttributeValue(rangeValue, exists))
        }

        val saveExpr = DynamoDBSaveExpression()
        saveExpr.expected = expectationbuilder.build()

        return saveExpr
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DynamoDBCreator::class.java.simpleName)
    }
}
