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

package com.nannoq.tools.repository.dynamodb

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedParallelScanList
import com.amazonaws.services.dynamodbv2.datamodeling.S3Link
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import com.amazonaws.services.dynamodbv2.model.CreateTableResult
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement
import com.amazonaws.services.dynamodbv2.model.KeyType
import com.amazonaws.services.dynamodbv2.model.ListTablesRequest
import com.amazonaws.services.dynamodbv2.model.ListTablesResult
import com.amazonaws.services.dynamodbv2.model.Projection
import com.amazonaws.services.dynamodbv2.model.ProjectionType
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.Region
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.module.SimpleModule
import com.nannoq.tools.repository.dynamodb.operators.DynamoDBAggregates
import com.nannoq.tools.repository.dynamodb.operators.DynamoDBCreator
import com.nannoq.tools.repository.dynamodb.operators.DynamoDBDeleter
import com.nannoq.tools.repository.dynamodb.operators.DynamoDBParameters
import com.nannoq.tools.repository.dynamodb.operators.DynamoDBReader
import com.nannoq.tools.repository.dynamodb.operators.DynamoDBUpdater
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.DynamoDBModel
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.repository.Repository
import com.nannoq.tools.repository.repository.cache.CacheManager
import com.nannoq.tools.repository.repository.cache.ClusterCacheManagerImpl
import com.nannoq.tools.repository.repository.cache.LocalCacheManagerImpl
import com.nannoq.tools.repository.repository.etag.ETagManager
import com.nannoq.tools.repository.repository.etag.InMemoryETagManagerImpl
import com.nannoq.tools.repository.repository.etag.RedisETagManagerImpl
import com.nannoq.tools.repository.repository.redis.RedisUtils
import com.nannoq.tools.repository.repository.results.ItemListResult
import com.nannoq.tools.repository.repository.results.ItemResult
import com.nannoq.tools.repository.repository.results.UpdateResult
import com.nannoq.tools.repository.services.internal.InternalRepositoryService
import com.nannoq.tools.repository.utils.FilterParameter
import com.nannoq.tools.repository.utils.OrderByParameter
import com.nannoq.tools.repository.utils.QueryPack
import com.nannoq.tools.repository.utils.S3LinkDeserializer
import com.nannoq.tools.repository.utils.S3LinkSerializer
import com.nannoq.tools.version.manager.VersionManager
import com.nannoq.tools.version.manager.VersionManagerImpl
import com.nannoq.tools.version.models.DiffPair
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.redis.RedisClient
import io.vertx.serviceproxy.ServiceException
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.ArrayUtils
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Arrays.stream
import java.util.Calendar
import java.util.Date
import java.util.Objects
import java.util.Queue
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.stream.Collectors.toList
import java.util.stream.IntStream

/**
 * This class defines DynamoDBRepository class. It handles almost all cases of use with the DynamoDB of AWS.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@Suppress("LeakingThis", "PrivatePropertyName")
open class DynamoDBRepository<E>(
    protected var vertx: Vertx = Vertx.currentContext().owner(),
    private val TYPE: Class<E>,
    appConfig: JsonObject,
    cacheManager: CacheManager<E>?,
    eTagManager: ETagManager<E>?
) : Repository<E>, InternalRepositoryService<E>
        where E : DynamoDBModel, E : Model, E : Cacheable, E : ETagable {
    final override var isCached = false
    final override var isEtagEnabled = false
    private var isVersioned = false
    private var HASH_IDENTIFIER: String? = null
    private var IDENTIFIER: String? = null
    private var PAGINATION_IDENTIFIER: String? = null

    private var hasRangeKey: Boolean = false

    private val versionManager: VersionManager = VersionManagerImpl()
    lateinit var dynamoDbMapper: DynamoDBMapper
        protected set

    var redisClient: RedisClient? = null
        private set
    val bucketName: String

    private val parameters: DynamoDBParameters<E>
    private val aggregates: DynamoDBAggregates<E>

    private val creator: DynamoDBCreator<E>
    private val reader: DynamoDBReader<E>
    private val updater: DynamoDBUpdater<E>
    private val deleter: DynamoDBDeleter<E>

    private var cacheManager: CacheManager<E>?

    final override var etagManager: ETagManager<E>? = null
        protected set

    private val fieldMap = ConcurrentHashMap<String, Field>()
    private val typeMap = ConcurrentHashMap<String, Type>()

    val modelName: String
        get() = TYPE.simpleName

    constructor(type: Class<E>, appConfig: JsonObject) : this(Vertx.currentContext().owner(), type, appConfig, null, null)

    constructor(type: Class<E>, appConfig: JsonObject, cacheManager: CacheManager<E>?) : this(Vertx.currentContext().owner(), type, appConfig, cacheManager, null)

    constructor(type: Class<E>, appConfig: JsonObject, eTagManager: ETagManager<E>?) : this(Vertx.currentContext().owner(), type, appConfig, null, eTagManager)

    constructor(
        type: Class<E>,
        appConfig: JsonObject,
        cacheManager: CacheManager<E>?,
        eTagManager: ETagManager<E>?
    ) : this(Vertx.currentContext().owner(), type, appConfig, cacheManager, eTagManager)

    constructor(vertx: Vertx, type: Class<E>, appConfig: JsonObject) : this(vertx, type, appConfig, null, null)

    constructor(
        vertx: Vertx,
        type: Class<E>,
        appConfig: JsonObject,
        cacheManager: CacheManager<E>?
    ) : this(vertx, type, appConfig, cacheManager, null)

    constructor(
        vertx: Vertx,
        type: Class<E>,
        appConfig: JsonObject,
        eTagManager: ETagManager<E>?
    ) : this(vertx, type, appConfig, null, eTagManager)

    init {
        if (stream<Annotation>(TYPE.javaClass.annotations).anyMatch { ann -> ann is DynamoDBDocument }) {
            throw DynamoDBMappingException("This type is a document definition, should not have own repository!")
        }

        bucketName = appConfig.getString("content_bucket") ?: "default"

        setMapper(appConfig)

        val tableName = stream(TYPE.declaredAnnotations)
                .filter { a -> a is DynamoDBTable }
                .map { a -> a as DynamoDBTable }
                .map<String> { table -> table.tableName }
                .findFirst()

        @Suppress("LocalVariableName")
        val COLLECTION: String

        when {
            tableName.isPresent || stream(TYPE.declaredAnnotations)
                    .anyMatch { a -> a is DynamoDBDocument } -> {
                COLLECTION = tableName.orElseGet { TYPE.simpleName.substring(0, 1).toLowerCase() + TYPE.simpleName.substring(1) + "s" }

                if (appConfig.getString("redis_host") != null) {
                    this.redisClient = RedisUtils.getRedisClient(vertx, appConfig)
                }
            }
            else -> {
                logger.error("Models must include the DynamoDBTable annotation, with the tablename!")

                throw IllegalArgumentException("Models must include the DynamoDBTable annotation, with the tablename")
            }
        }

        when {
            eTagManager != null -> {
                this.etagManager = eTagManager
                isEtagEnabled = true
            }
            else -> if (appConfig.getString("redis_host") != null) {
                this.etagManager = RedisETagManagerImpl(TYPE, redisClient!!)
                isEtagEnabled = true
            } else {
                this.etagManager = InMemoryETagManagerImpl(vertx, TYPE)
                isEtagEnabled = true
            }
        }

        when {
            cacheManager != null -> {
                this.cacheManager = cacheManager
                isCached = true
            }
            vertx.isClustered -> {
                this.cacheManager = ClusterCacheManagerImpl(TYPE, vertx)
                isCached = true
            }
            else -> {
                this.cacheManager = LocalCacheManagerImpl(TYPE, vertx)
                isCached = true
            }
        }

        isVersioned = stream(TYPE.declaredMethods)
                .anyMatch { m ->
                    stream(m.declaredAnnotations)
                            .anyMatch { a -> a is DynamoDBVersionAttribute }
                }

        setHashAndRange(TYPE)
        @Suppress("LocalVariableName")
        val GSI_KEY_MAP = setGsiKeys(TYPE)
        if (isCached) this.cacheManager!!.initializeCache(Handler { isCached = it.succeeded() })

        this.parameters = DynamoDBParameters(TYPE, this, HASH_IDENTIFIER!!, IDENTIFIER, PAGINATION_IDENTIFIER)
        this.aggregates = DynamoDBAggregates(TYPE, this, HASH_IDENTIFIER!!, IDENTIFIER, this.cacheManager!!, etagManager!!)

        this.creator = DynamoDBCreator(TYPE, vertx, this, HASH_IDENTIFIER!!, IDENTIFIER, this.cacheManager!!, etagManager!!)
        this.reader = DynamoDBReader(TYPE, vertx, this, COLLECTION, HASH_IDENTIFIER!!, IDENTIFIER,
                PAGINATION_IDENTIFIER, GSI_KEY_MAP, parameters, this.cacheManager!!, this.etagManager!!)
        this.updater = DynamoDBUpdater(this)
        this.deleter = DynamoDBDeleter(TYPE, vertx, this, HASH_IDENTIFIER!!, IDENTIFIER, this.cacheManager!!, etagManager!!)
    }

    private fun setMapper(appConfig: JsonObject) {
        val dynamoDBId = appConfig.getString("dynamo_db_iam_id")
        val dynamoDBKey = appConfig.getString("dynamo_db_iam_key")
        val endPoint = fetchEndPoint(appConfig)
        val region = fetchRegion(appConfig)

        dynamoDbMapper = when {
            dynamoDBId != null && dynamoDBKey != null -> {
                val creds = BasicAWSCredentials(dynamoDBId, dynamoDBKey)
                val statCreds = AWSStaticCredentialsProvider(creds)

                DynamoDBMapper(AmazonDynamoDBAsyncClientBuilder.standard()
                        .withCredentials(statCreds)
                        .withEndpointConfiguration(EndpointConfiguration(endPoint, region))
                        .build(), DynamoDBMapperConfig.DEFAULT, statCreds)
            }
            else -> DynamoDBMapper(AmazonDynamoDBAsyncClientBuilder.standard()
                    .withEndpointConfiguration(EndpointConfiguration(endPoint, region))
                    .build(), DynamoDBMapperConfig.DEFAULT)
        }
    }

    private fun setHashAndRange(type: Class<E>) {
        val allMethods = getAllMethodsOnType(type)
        HASH_IDENTIFIER = ""
        IDENTIFIER = ""
        PAGINATION_IDENTIFIER = ""

        stream(allMethods).filter { method ->
            stream(method.annotations)
                    .anyMatch { annotation -> annotation is DynamoDBHashKey }
        }
                .findFirst()
                .ifPresent { method -> HASH_IDENTIFIER = stripGet(method.name) }

        stream(allMethods).filter { method ->
            stream(method.annotations)
                    .anyMatch { annotation -> annotation is DynamoDBRangeKey }
        }
                .findFirst()
                .ifPresent { method -> IDENTIFIER = stripGet(method.name) }

        stream(allMethods).filter { method ->
            stream(method.annotations)
                    .anyMatch { annotation ->
                        annotation is DynamoDBIndexRangeKey && annotation.localSecondaryIndexName
                                .equals(PAGINATION_INDEX, ignoreCase = true)
                    }
        }
                .findFirst()
                .ifPresent { method -> PAGINATION_IDENTIFIER = stripGet(method.name) }

        hasRangeKey = IDENTIFIER != ""
    }

    @Throws(IllegalArgumentException::class)
    fun getField(fieldName: String): Field {
        try {
            var field: Field? = fieldMap[fieldName]
            if (field != null) return field

            field = TYPE.getDeclaredField(fieldName)
            if (field != null) fieldMap[fieldName] = field
            field!!.isAccessible = true

            return field
        } catch (e: NoSuchFieldException) {
            if (TYPE.superclass != null && TYPE.superclass != java.lang.Object::class.java) {
                return getField(fieldName, TYPE.superclass)
            }

            throw UnknownError("Cannot get field " + fieldName + " from " + TYPE.simpleName + "!")
        } catch (e: NullPointerException) {
            if (TYPE.superclass != null && TYPE.superclass != java.lang.Object::class.java) {
                return getField(fieldName, TYPE.superclass)
            }
            throw UnknownError("Cannot get field " + fieldName + " from " + TYPE.simpleName + "!")
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun getField(fieldName: String, klazz: Class<*>): Field {
        try {
            var field: Field? = fieldMap[fieldName]
            if (field != null) return field

            field = klazz.getDeclaredField(fieldName)
            if (field != null) fieldMap[fieldName] = field
            field!!.isAccessible = true

            return field
        } catch (e: NoSuchFieldException) {
            if (klazz.superclass != null && klazz.superclass != java.lang.Object::class.java) {
                return getField(fieldName, klazz.superclass)
            } else {
                logger.error("Cannot get field " + fieldName + " from " + klazz.simpleName + "!", e)
            }

            throw UnknownError("Cannot find field!")
        } catch (e: NullPointerException) {
            if (klazz.superclass != null && klazz.superclass != java.lang.Object::class.java) {
                return getField(fieldName, klazz.superclass)
            } else {
                logger.error("Cannot get field " + fieldName + " from " + klazz.simpleName + "!", e)
            }
            throw UnknownError("Cannot find field!")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T, O : Any> getFieldAsObject(fieldName: String, `object`: O): T {
        try {
            var field: Field? = fieldMap[fieldName]
            if (field != null) return field.get(`object`) as T

            field = `object`.javaClass.getDeclaredField(fieldName)
            if (field != null) fieldMap[fieldName] = field
            field!!.isAccessible = true

            return field.get(`object`) as T
        } catch (e: Exception) {
            if (`object`.javaClass.superclass != null && `object`.javaClass.superclass != java.lang.Object::class.java) {
                return getFieldAsObject(fieldName, `object`, `object`.javaClass.superclass)
            } else {
                logger.error("Cannot get field " + fieldName + " from " + `object`.javaClass.simpleName + "!", e)
            }

            throw UnknownError("Cannot find field!")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T, O : Any> getFieldAsObject(fieldName: String, `object`: O, klazz: Class<*>): T {
        try {
            var field: Field? = fieldMap[fieldName]
            if (field != null) return field.get(`object`) as T

            field = `object`.javaClass.getDeclaredField(fieldName)
            if (field != null) fieldMap[fieldName] = field
            field!!.isAccessible = true

            return field.get(`object`) as T
        } catch (e: Exception) {
            if (klazz.superclass != null && klazz.superclass != java.lang.Object::class.java) {
                return getFieldAsObject(fieldName, `object`, klazz.superclass)
            } else {
                logger.error("Cannot get field " + fieldName + " from " + klazz.simpleName + "!", e)
            }

            throw UnknownError("Cannot find field!")
        }
    }

    fun <T : Any> getFieldAsString(fieldName: String, `object`: T): String {
        if (logger.isTraceEnabled) {
            logger.trace("Getting " + fieldName + " from " + `object`.javaClass.simpleName)
        }

        try {
            var field: Field? = fieldMap[fieldName]

            if (field != null) {
                field.isAccessible = true

                return field.get(`object`).toString()
            }

            field = TYPE.getDeclaredField(fieldName)
            if (field != null) fieldMap[fieldName] = field
            field!!.isAccessible = true

            val fieldObject = field.get(`object`)

            return fieldObject.toString()
        } catch (e: Exception) {
            return when {
                TYPE.superclass != null && TYPE.superclass != java.lang.Object::class.java ->
                    getFieldAsString(fieldName, `object`, TYPE.superclass)
                else -> {
                    logger.error("Cannot get " + fieldName + " as string from: " + Json.encodePrettily(`object`), e)

                    throw UnknownError("Cannot find field!")
                }
            }
        }
    }

    private fun <T> getFieldAsString(fieldName: String, `object`: T, klazz: Class<*>): String {
        if (logger.isTraceEnabled) {
            logger.trace("Getting " + fieldName + " from " + klazz.simpleName)
        }

        try {
            var field: Field? = fieldMap[fieldName]

            if (field != null) {
                field.isAccessible = true

                return field.get(`object`).toString()
            }

            field = klazz.getDeclaredField(fieldName)
            if (field != null) fieldMap[fieldName] = field
            field!!.isAccessible = true

            val fieldObject = field.get(`object`)

            return fieldObject.toString()
        } catch (e: Exception) {
            return when {
                klazz.superclass != null && klazz.superclass != java.lang.Object::class.java ->
                    getFieldAsString(fieldName, `object`, klazz.superclass)
                else -> {
                    logger.error("Cannot get " + fieldName + " as string from: " + Json.encodePrettily(`object`) + ", klazzwise!", e)

                    throw UnknownError("Cannot find field!")
                }
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    fun checkAndGetField(fieldName: String): Field {
        try {
            var field: Field? = fieldMap[fieldName]

            if (field == null) {
                field = TYPE.getDeclaredField(fieldName)

                if (field != null) fieldMap[fieldName] = field
            }

            var fieldType: Type? = typeMap[fieldName]

            if (fieldType == null) {
                fieldType = field!!.type

                if (fieldType != null) typeMap[fieldName] = fieldType
            }

            when {
                fieldType === java.lang.Long::class.java || fieldType === java.lang.Integer::class.java ||
                        fieldType === java.lang.Double::class.java || fieldType === java.lang.Float::class.java ||
                        fieldType === java.lang.Short::class.java ||
                        fieldType === java.lang.Long::class.javaPrimitiveType || fieldType === java.lang.Integer::class.javaPrimitiveType ||
                        fieldType === java.lang.Double::class.javaPrimitiveType || fieldType === java.lang.Float::class.javaPrimitiveType ||
                        fieldType === java.lang.Short::class.javaPrimitiveType -> {
                    field!!.isAccessible = true

                    return field
                }
                else -> throw IllegalArgumentException("Not an incrementable field!")
            }
        } catch (e: NoSuchFieldException) {
            return when {
                TYPE.superclass != null && TYPE.superclass != java.lang.Object::class.java ->
                    checkAndGetField(fieldName, TYPE.superclass)
                else -> throw IllegalArgumentException("Field does not exist!")
            }
        } catch (e: NullPointerException) {
            return when {
                TYPE.superclass != null && TYPE.superclass != java.lang.Object::class.java ->
                    checkAndGetField(fieldName, TYPE.superclass)
                else -> throw IllegalArgumentException("Field does not exist!")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun checkAndGetField(fieldName: String, klazz: Class<*>): Field {
        try {
            var field: Field? = fieldMap[fieldName]

            if (field == null) {
                field = klazz.getDeclaredField(fieldName)

                if (field != null) fieldMap[fieldName] = field
            }

            var fieldType: Type? = typeMap[fieldName]

            if (fieldType == null) {
                fieldType = field!!.type

                if (fieldType != null) typeMap[fieldName] = fieldType
            }

            when {
                fieldType === java.lang.Long::class.java || fieldType === java.lang.Integer::class.java ||
                        fieldType === java.lang.Double::class.java || fieldType === java.lang.Float::class.java ||
                        fieldType === java.lang.Short::class.java ||
                        fieldType === java.lang.Long::class.javaPrimitiveType || fieldType === java.lang.Integer::class.javaPrimitiveType ||
                        fieldType === java.lang.Double::class.javaPrimitiveType || fieldType === java.lang.Float::class.javaPrimitiveType ||
                        fieldType === java.lang.Short::class.javaPrimitiveType -> {
                    field!!.isAccessible = true

                    return field
                }
                else -> throw IllegalArgumentException("Not an incrementable field!")
            }
        } catch (e: NoSuchFieldException) {
            return when {
                klazz.superclass != null && klazz.superclass != java.lang.Object::class.java ->
                    checkAndGetField(fieldName, klazz)
                else -> throw IllegalArgumentException("Field does not exist!")
            }
        }
    }

    fun hasField(fields: Array<Field>, key: String): Boolean {
        val hasField = stream(fields).anyMatch { field -> field.name.equals(key, ignoreCase = true) }

        return hasField || hasField(TYPE.superclass, key)
    }

    private fun hasField(klazz: Class<*>, key: String): Boolean {
        try {
            var field: Field? = fieldMap[key]

            if (field == null) {
                field = klazz.getDeclaredField(key)

                if (field != null) fieldMap[key] = field
            }

            val hasField = field != null

            return hasField || hasField(klazz.superclass, key)
        } catch (e: NoSuchFieldException) {
            return false
        } catch (e: NullPointerException) {
            return false
        }
    }

    fun getAlternativeIndexIdentifier(indexName: String): String? {
        val identifier = arrayOfNulls<String>(1)

        stream(TYPE.methods).filter { method ->
            stream(method.annotations)
                    .anyMatch { annotation ->
                        annotation is DynamoDBIndexRangeKey && annotation.localSecondaryIndexName
                                .equals(indexName, ignoreCase = true)
                    }
        }
                .findFirst()
                .ifPresent { method -> identifier[0] = stripGet(method.name) }

        return identifier[0]
    }

    fun <T : Any> getIndexValue(alternateIndex: String, `object`: T): AttributeValue {
        try {
            var field: Field? = fieldMap[alternateIndex]

            if (field == null) {
                field = `object`.javaClass.getDeclaredField(alternateIndex)

                if (field != null) fieldMap[alternateIndex] = field
            }

            field?.isAccessible = true

            var fieldType: Type? = typeMap[alternateIndex]

            if (fieldType == null) {
                fieldType = field?.type

                if (fieldType != null) typeMap[alternateIndex] = fieldType
            }

            return when {
                fieldType === java.util.Date::class.java -> {
                    val dateObject = field?.get(`object`) as Date

                    createAttributeValue(alternateIndex, dateObject.time.toString())
                }
                else -> createAttributeValue(alternateIndex, field?.get(`object`).toString())
            }
        } catch (e: NoSuchFieldException) {
            if (`object`.javaClass.superclass != null && `object`.javaClass.superclass != java.lang.Object::class.java) {
                return getIndexValue(alternateIndex, `object`, `object`.javaClass.superclass)
            }
        } catch (e: NullPointerException) {
            if (`object`.javaClass.superclass != null && `object`.javaClass.superclass != java.lang.Object::class.java) {
                return getIndexValue(alternateIndex, `object`, `object`.javaClass.superclass)
            }
        } catch (e: IllegalAccessException) {
            if (`object`.javaClass.superclass != null && `object`.javaClass.superclass != java.lang.Object::class.java) {
                return getIndexValue(alternateIndex, `object`, `object`.javaClass.superclass)
            }
        }

        throw UnknownError("Cannot find field!")
    }

    private fun <T> getIndexValue(alternateIndex: String, `object`: T, klazz: Class<*>): AttributeValue {
        try {
            var field: Field? = fieldMap[alternateIndex]

            if (field == null) {
                field = klazz.getDeclaredField(alternateIndex)

                if (field != null) fieldMap[alternateIndex] = field
            }

            field!!.isAccessible = true

            var fieldType: Type? = typeMap[alternateIndex]

            if (fieldType == null) {
                fieldType = field.type

                if (fieldType != null) typeMap[alternateIndex] = fieldType
            }

            return when {
                fieldType === java.util.Date::class.java -> {
                    val dateObject = field.get(`object`) as Date

                    createAttributeValue(alternateIndex, dateObject.time.toString())
                }
                else -> createAttributeValue(alternateIndex, field.get(`object`).toString())
            }
        } catch (e: NoSuchFieldException) {
            if (klazz.superclass != null && klazz.superclass != java.lang.Object::class.java) {
                return getIndexValue(alternateIndex, `object`, klazz.superclass)
            }
        } catch (e: NullPointerException) {
            if (klazz.superclass != null && klazz.superclass != java.lang.Object::class.java) {
                return getIndexValue(alternateIndex, `object`, klazz.superclass)
            }
        } catch (e: IllegalAccessException) {
            if (klazz.superclass != null && klazz.superclass != java.lang.Object::class.java) {
                return getIndexValue(alternateIndex, `object`, klazz.superclass)
            }
        }

        throw UnknownError("Cannot find field!")
    }

    @JvmOverloads
    fun createAttributeValue(fieldName: String?, valueAsString: String, modifier: ComparisonOperator? = null): AttributeValue {
        if (fieldName == null) throw IllegalArgumentException("Fieldname cannot be null!")

        val field = getField(fieldName)
        var fieldType: Type? = typeMap[fieldName]

        if (fieldType == null) {
            fieldType = field.type

            if (fieldType != null) typeMap[fieldName] = fieldType
        }

        when {
            fieldType === java.lang.String::class.java -> return AttributeValue().withS(valueAsString)
            fieldType === java.lang.Integer::class.java ||
                    fieldType === java.lang.Double::class.java ||
                    fieldType === java.lang.Long::class.java -> {
                try {
                    if (fieldType === java.lang.Integer::class.java) {
                        var value = Integer.parseInt(valueAsString)

                        if (modifier == ComparisonOperator.GE) value -= 1
                        if (modifier == ComparisonOperator.LE) value += 1

                        return AttributeValue().withN(value.toString())
                    }

                    if (fieldType === java.lang.Double::class.java) {
                        var value = java.lang.Double.parseDouble(valueAsString)

                        if (modifier == ComparisonOperator.GE) value -= 0.1
                        if (modifier == ComparisonOperator.LE) value += 0.1

                        return AttributeValue().withN(value.toString())
                    }

                    if (fieldType === java.lang.Long::class.java) {
                        var value = java.lang.Long.parseLong(valueAsString)

                        if (modifier == ComparisonOperator.GE) value -= 1
                        if (modifier == ComparisonOperator.LE) value += 1

                        return AttributeValue().withN(value.toString())
                    }
                } catch (nfe: NumberFormatException) {
                    logger.error("Cannot recreate attribute!", nfe)
                }

                return AttributeValue().withN(valueAsString)
            }
            fieldType === java.lang.Boolean::class.java -> {
                if (valueAsString.equals("true", ignoreCase = true)) {
                    return AttributeValue().withN("1")
                } else if (valueAsString.equals("false", ignoreCase = true)) {
                    return AttributeValue().withN("0")
                }

                try {
                    val boolValue = Integer.parseInt(valueAsString)

                    if (boolValue == 1 || boolValue == 0) {
                        return AttributeValue().withN(boolValue.toString())
                    }

                    throw UnknownError("Cannot create AttributeValue!")
                } catch (nfe: NumberFormatException) {
                    logger.error("Cannot rceate attribute!", nfe)
                }
            }
            else -> return when {
                fieldType === java.util.Date::class.java -> try {
                    if (logger.isDebugEnabled) {
                        logger.debug("Date received: $valueAsString")
                    }

                    val date: Date

                    date = try {
                        Date(java.lang.Long.parseLong(valueAsString))
                    } catch (nfe: NumberFormatException) {
                        val df1 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                        df1.parse(valueAsString)
                    }

                    val calendar = Calendar.getInstance()
                    calendar.time = date

                    if (modifier == ComparisonOperator.LE) calendar.add(Calendar.MILLISECOND, 1)
                    if (modifier == ComparisonOperator.GE) calendar.time = Date(calendar.time.time - 1)

                    val df2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                    df2.timeZone = TimeZone.getTimeZone("Z")
                    if (logger.isDebugEnabled) {
                        logger.debug("DATE IS: " + df2.format(calendar.time))
                    }

                    AttributeValue().withS(df2.format(calendar.time))
                } catch (e: ParseException) {
                    AttributeValue().withS(valueAsString)
                }
                else -> AttributeValue().withS(valueAsString)
            }
        }

        throw UnknownError("Cannot create attributevalue!")
    }

    fun fetchNewestRecord(type: Class<E>, hash: String, range: String?): E? {
        when {
            range != null && hasRangeKey -> {
                if (logger.isDebugEnabled) {
                    logger.debug("Loading newest with range!")
                }

                return dynamoDbMapper.load(TYPE, hash, range)
            }
            else -> try {
                if (logger.isDebugEnabled) {
                    logger.debug("Loading newest by hash query!")
                }

                val query = DynamoDBQueryExpression<E>()
                val keyObject = type.getDeclaredConstructor().newInstance()
                keyObject.hash = hash
                query.isConsistentRead = true
                query.hashKeyValues = keyObject
                query.limit = 1

                val timeBefore = System.currentTimeMillis()

                val items = dynamoDbMapper.query(TYPE, query)

                if (logger.isDebugEnabled) {
                    logger.debug("Results received in: " + (System.currentTimeMillis() - timeBefore) + " ms")
                }

                return when {
                    !items.isEmpty() -> items[0]
                    else -> null
                }
            } catch (e: Exception) {
                logger.error("Error fetching newest!", e)

                return null
            }
        }
    }

    fun buildExpectedAttributeValue(value: String, exists: Boolean): ExpectedAttributeValue {
        val exp = ExpectedAttributeValue(exists)
        if (exp.isExists!!) exp.value = AttributeValue().withS(value)

        return exp
    }

    @Throws(IllegalArgumentException::class)
    override fun incrementField(record: E, fieldName: String): Function<E, E> {
        return Function {
            updater.incrementField(it, fieldName)

            it
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun decrementField(record: E, fieldName: String): Function<E, E> {
        return Function {
            updater.decrementField(it, fieldName)

            it
        }
    }

    override fun update(record: E, resultHandler: Handler<AsyncResult<UpdateResult<E>>>) {
        when {
            isVersioned -> resultHandler.handle(ServiceException.fail(
                    400, "This model is versioned, use the updateLogic method!"))
            else -> super.update(record, resultHandler)
        }
    }

    override fun update(record: E): Future<UpdateResult<E>> {
        return when {
            !isVersioned -> throw IllegalArgumentException("This model is versioned, use the updateLogic method!")
            else -> super.update(record)
        }
    }

    override fun read(identifiers: JsonObject, resultHandler: Handler<AsyncResult<ItemResult<E>>>) {
        reader.read(identifiers, resultHandler)
    }

    override fun read(identifiers: JsonObject, projections: Array<String>, resultHandler: Handler<AsyncResult<ItemResult<E>>>) {
        reader.read(identifiers, true, projections, resultHandler)
    }

    override fun read(identifiers: JsonObject, consistent: Boolean, projections: Array<String>?, resultHandler: Handler<AsyncResult<ItemResult<E>>>) {
        reader.read(identifiers, consistent, projections, resultHandler)
    }

    override fun readAll(resultHandler: Handler<AsyncResult<List<E>>>) {
        reader.readAll(resultHandler)
    }

    override fun readAll(identifiers: JsonObject, filterParameterMap: Map<String, List<FilterParameter>>, resultHandler: Handler<AsyncResult<List<E>>>) {
        reader.readAll(identifiers, filterParameterMap, resultHandler)
    }

    override fun readAll(identifiers: JsonObject?, pageToken: String?, queryPack: QueryPack?, projections: Array<String>?, resultHandler: Handler<AsyncResult<ItemListResult<E>>>) {
        reader.readAll(identifiers ?: JsonObject(), pageToken, queryPack ?: QueryPack(), projections ?: arrayOf(), resultHandler)
    }

    override fun readAll(pageToken: String?, queryPack: QueryPack, projections: Array<String>, resultHandler: Handler<AsyncResult<ItemListResult<E>>>) {
        reader.readAll(pageToken, queryPack, projections, resultHandler)
    }

    @Suppress("unused")
    fun readAll(identifiers: JsonObject, queryPack: QueryPack, GSI: String, resultHandler: Handler<AsyncResult<ItemListResult<E>>>) {
        reader.readAll(identifiers, queryPack.pageToken, queryPack, queryPack.projections ?: arrayOf(), GSI, resultHandler)
    }

    fun readAll(identifiers: JsonObject, pageToken: String?, queryPack: QueryPack, projections: Array<String>, GSI: String, resultHandler: Handler<AsyncResult<ItemListResult<E>>>) {
        reader.readAll(identifiers, pageToken, queryPack, projections, GSI, resultHandler)
    }

    override fun aggregation(identifiers: JsonObject, queryPack: QueryPack, projections: Array<String>?, resultHandler: Handler<AsyncResult<String>>) {
        aggregation(identifiers, queryPack, projections, null, resultHandler)
    }

    fun aggregation(identifiers: JsonObject, queryPack: QueryPack, GSI: String? = null, resultHandler: Handler<AsyncResult<String>>) {
        aggregates.aggregation(identifiers, queryPack, queryPack.projections ?: arrayOf(), GSI, resultHandler)
    }

    fun aggregation(identifiers: JsonObject, queryPack: QueryPack, projections: Array<String>?, GSI: String? = null, resultHandler: Handler<AsyncResult<String>>) {
        aggregates.aggregation(identifiers, queryPack, projections ?: arrayOf(), GSI, resultHandler)
    }

    override fun buildParameters(
        queryMap: Map<String, List<String>>,
        fields: Array<Field>,
        methods: Array<Method>,
        errors: JsonObject,
        params: Map<String, List<FilterParameter>>,
        limit: IntArray,
        orderByQueue: Queue<OrderByParameter>,
        indexName: Array<String>
    ): JsonObject {
        return parameters.buildParameters(queryMap, fields, methods, errors, params.toMutableMap(), limit, orderByQueue, indexName)
    }

    override fun readAllWithoutPagination(identifier: String, resultHandler: Handler<AsyncResult<List<E>>>) {
        reader.readAllWithoutPagination(identifier, resultHandler)
    }

    override fun readAllWithoutPagination(identifier: String, queryPack: QueryPack, resultHandler: Handler<AsyncResult<List<E>>>) {
        reader.readAllWithoutPagination(identifier, queryPack, resultHandler)
    }

    override fun readAllWithoutPagination(identifier: String, queryPack: QueryPack, projections: Array<String>, resultHandler: Handler<AsyncResult<List<E>>>) {
        readAllWithoutPagination(identifier, queryPack, projections, null, resultHandler)
    }

    fun readAllWithoutPagination(
        identifier: String,
        queryPack: QueryPack,
        projections: Array<String>,
        GSI: String?,
        resultHandler: Handler<AsyncResult<List<E>>>
    ) {
        reader.readAllWithoutPagination(identifier, queryPack, projections, GSI, resultHandler)
    }

    override fun readAllWithoutPagination(queryPack: QueryPack, projections: Array<String>?, resultHandler: Handler<AsyncResult<List<E>>>) {
        readAllWithoutPagination(queryPack, projections, null, resultHandler)
    }

    fun readAllWithoutPagination(queryPack: QueryPack, projections: Array<String>?, GSI: String?, resultHandler: Handler<AsyncResult<List<E>>>) {
        reader.readAllWithoutPagination(queryPack, projections, GSI, resultHandler)
    }

    fun readAllPaginated(resultHandler: Handler<AsyncResult<PaginatedParallelScanList<E>>>) {
        reader.readAllPaginated(resultHandler)
    }

    override fun doWrite(create: Boolean, records: Map<E, Function<E, E>>, resultHandler: Handler<AsyncResult<List<E>>>) {
        creator.doWrite(create, records, resultHandler)
    }

    override fun doDelete(identifiers: List<JsonObject>, resultHandler: Handler<AsyncResult<List<E>>>) {
        deleter.doDelete(identifiers, resultHandler)
    }

    override fun remoteCreate(record: E, resultHandler: Handler<AsyncResult<E>>): InternalRepositoryService<E> {
        create(record, Handler {
            when {
                it.failed() -> resultHandler.handle(Future.failedFuture(it.cause()))
                else -> resultHandler.handle(Future.succeededFuture(it.result().item))
            }
        })

        return this
    }

    override fun remoteRead(identifiers: JsonObject, resultHandler: Handler<AsyncResult<E>>): InternalRepositoryService<E> {
        read(identifiers, Handler {
            when {
                it.failed() -> resultHandler.handle(Future.failedFuture(it.cause()))
                else -> resultHandler.handle(it.map(it.result().item))
            }
        })

        return this
    }

    override fun remoteIndex(identifier: JsonObject, resultHandler: Handler<AsyncResult<List<E>>>): InternalRepositoryService<E> {
        readAllWithoutPagination(identifier.getString("hash"), resultHandler)

        return this
    }

    override fun remoteUpdate(record: E, resultHandler: Handler<AsyncResult<E>>): InternalRepositoryService<E> {
        read(JsonObject()
                .put("hash", record.hash)
                .put("range", record.range), Handler { readRes ->
            when {
                readRes.failed() -> resultHandler.handle(Future.failedFuture(readRes.cause()))
                else -> {
                    logger.info(Json.encodePrettily(readRes.result().item))
                    logger.info(Json.encodePrettily(record))
                    val diff = DiffPair(current = readRes.result().item, updated = record)

                    versionManager.extractVersion(diff, Handler { versionRes ->
                        when {
                            versionRes.failed() -> resultHandler.handle(Future.failedFuture(versionRes.cause()))
                            else -> {
                                logger.info(Json.encodePrettily(versionRes.result()))

                                update(record, Function { record ->
                                    runBlocking(vertx.dispatcher()) {
                                        versionManager.applyStateBlocking(versionRes.result(), record)
                                    }
                                }, Handler { res ->
                                    when {
                                        res.failed() -> resultHandler.handle(Future.failedFuture(res.cause()))
                                        else -> resultHandler.handle(Future.succeededFuture(res.result().item))
                                    }
                                })
                            }
                        }
                    })
                }
            }
        })

        return this
    }

    override fun remoteDelete(identifiers: JsonObject, resultHandler: Handler<AsyncResult<E>>): InternalRepositoryService<E> {
        delete(identifiers, Handler {
            when {
                it.failed() -> resultHandler.handle(Future.failedFuture(it.cause()))
                else -> resultHandler.handle(Future.succeededFuture(it.result().item))
            }
        })

        return this
    }

    fun buildEventbusProjections(projectionArray: JsonArray?): Array<String> {
        if (projectionArray == null) return arrayOf()

        val projections = projectionArray.stream()
                .map { it.toString() }
                .collect(toList())

        val projectionArrayStrings = arrayOfNulls<String>(projections?.size ?: 0)

        if (projections != null) {
            IntStream.range(0, projections.size).forEach { i -> projectionArrayStrings[i] = projections[i] }
        }

        return projectionArrayStrings.requireNoNulls()
    }

    fun hasRangeKey(): Boolean {
        return hasRangeKey
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DynamoDBRepository::class.java.simpleName)

        const val PAGINATION_INDEX = "PAGINATION_INDEX"

        val s3DynamoDbMapper: DynamoDBMapper
            get() = getS3DynamoDbMapper(Objects.requireNonNull<JsonObject>(
                    if (Vertx.currentContext() == null) null else Vertx.currentContext().config()))

        private fun getS3DynamoDbMapper(config: JsonObject): DynamoDBMapper {
            val dynamoDBId = config.getString("dynamo_db_iam_id")
            val dynamoDBKey = config.getString("dynamo_db_iam_key")
            val endPoint = fetchEndPoint(config)
            val region = fetchRegion(config)

            if (dynamoDBId == null || dynamoDBKey == null) {
                throw IllegalArgumentException("Must supply keys for S3!")
            }

            val creds = BasicAWSCredentials(dynamoDBId, dynamoDBKey)
            val statCreds = AWSStaticCredentialsProvider(creds)

            return DynamoDBMapper(AmazonDynamoDBAsyncClientBuilder.standard()
                    .withCredentials(statCreds)
                    .withEndpointConfiguration(EndpointConfiguration(endPoint, region))
                    .build(),
                    DynamoDBMapperConfig.DEFAULT,
                    statCreds)
        }

        private fun fetchEndPoint(appConfig: JsonObject?): String {
            val config = appConfig ?: if (Vertx.currentContext() == null) null else Vertx.currentContext().config()
            val endPoint: String

            endPoint = when (config) {
                null -> "http://localhost:8001"
                else -> config.getString("dynamo_endpoint")
            }

            return endPoint
        }

        private fun fetchRegion(appConfig: JsonObject?): String {
            val config = appConfig ?: if (Vertx.currentContext() == null) null else Vertx.currentContext().config()
            var region: String?

            when (config) {
                null -> region = "eu-west-1"
                else -> {
                    region = config.getString("dynamo_signing_region")
                    if (region == null) region = "eu-west-1"
                }
            }

            return region
        }

        private fun <E> setGsiKeys(type: Class<E>): Map<String, JsonObject> {
            val allMethods = getAllMethodsOnType(type)
            val gsiMap = ConcurrentHashMap<String, JsonObject>()

            stream(allMethods).forEach { method ->
                if (stream(method.declaredAnnotations)
                                .anyMatch { annotation -> annotation is DynamoDBIndexHashKey }) {
                    val hashName = method.getDeclaredAnnotation<DynamoDBIndexHashKey>(DynamoDBIndexHashKey::class.java)
                            .globalSecondaryIndexName
                    val hash = stripGet(method.name)
                    val range = arrayOfNulls<String>(1)

                    if (hashName != "") {
                        stream(allMethods).forEach { rangeMethod ->
                            if (stream(rangeMethod.declaredAnnotations)
                                            .anyMatch { annotation -> annotation is DynamoDBIndexRangeKey }) {
                                val rangeIndexName = rangeMethod.getDeclaredAnnotation<DynamoDBIndexRangeKey>(DynamoDBIndexRangeKey::class.java)
                                        .globalSecondaryIndexName

                                if (rangeIndexName == hashName) {
                                    range[0] = stripGet(rangeMethod.name)
                                }
                            }
                        }

                        val hashKeyObject = JsonObject()
                                .put("hash", hash)

                        if (range[0] != null) hashKeyObject.put("range", range[0])

                        gsiMap[hashName] = hashKeyObject

                        logger.debug("Detected GSI: " + hashName + " : " + hashKeyObject.encodePrettily())
                    }
                }
            }

            return gsiMap
        }

        private fun getAllMethodsOnType(klazz: Class<*>): Array<Method> {
            val methods = klazz.declaredMethods

            return if (klazz.superclass != null && klazz.superclass != java.lang.Object::class.java) {
                ArrayUtils.addAll(methods, *getAllMethodsOnType(klazz.superclass))
            } else methods
        }

        fun stripGet(string: String): String {
            val newString = string.replace("get", "")
            val c = newString.toCharArray()
            c[0] = c[0] + 32

            return String(c)
        }

        fun initializeDynamoDb(
            appConfig: JsonObject,
            collectionMap: Map<String, Class<*>>,
            resultHandler: Handler<AsyncResult<Void>>
        ) {
            if (logger.isDebugEnabled) {
                logger.debug("Initializing DynamoDB")
            }

            try {
                silenceDynamoDBLoggers()
                val futures = ArrayList<Future<*>>()

                collectionMap.forEach { (k, v) -> futures.add(initialize(appConfig, k, v)) }

                CompositeFuture.all(futures).setHandler { res ->
                    if (logger.isDebugEnabled) {
                        logger.debug("Preparing S3 Bucket")
                    }

                    val s3LinkModule = SimpleModule("MyModule", Version(1, 0, 0, null, null, null))
                    s3LinkModule.addSerializer(S3LinkSerializer())
                    s3LinkModule.addDeserializer(S3Link::class.java, S3LinkDeserializer(appConfig))

                    Json.mapper.registerModule(s3LinkModule)

                    if (logger.isDebugEnabled) {
                        logger.debug("DynamoDB Ready")
                    }

                    when {
                        res.failed() -> resultHandler.handle(Future.failedFuture(res.cause()))
                        else -> resultHandler.handle(Future.succeededFuture())
                    }
                }
            } catch (e: Exception) {
                logger.error("Unable to initialize!", e)
            }
        }

        private fun silenceDynamoDBLoggers() {
            java.util.logging.Logger.getLogger("com.amazonaws").level = java.util.logging.Level.WARNING
        }

        private fun initialize(appConfig: JsonObject, COLLECTION: String, TYPE: Class<*>): Future<Void> {
            val future = Future.future<Void>()

            val dynamoDBId = appConfig.getString("dynamo_db_iam_id")
            val dynamoDBKey = appConfig.getString("dynamo_db_iam_key")
            val endPoint = fetchEndPoint(appConfig)
            val region = fetchRegion(appConfig)

            val amazonDynamoDBAsyncClientBuilder = AmazonDynamoDBAsyncClientBuilder.standard()
                    .withEndpointConfiguration(EndpointConfiguration(endPoint, region))
            val amazonDynamoDBAsync: AmazonDynamoDBAsync
            val dynamoDBMapper: DynamoDBMapper

            when {
                dynamoDBId == null || dynamoDBKey == null -> {
                    logger.warn("S3 Creds unavailable for initialize")

                    amazonDynamoDBAsync = amazonDynamoDBAsyncClientBuilder
                            .build()
                    dynamoDBMapper = DynamoDBMapper(amazonDynamoDBAsync, DynamoDBMapperConfig.DEFAULT)
                }
                else -> {
                    val creds = BasicAWSCredentials(dynamoDBId, dynamoDBKey)
                    val statCreds = AWSStaticCredentialsProvider(creds)

                    amazonDynamoDBAsync = amazonDynamoDBAsyncClientBuilder
                            .withCredentials(statCreds)
                            .build()
                    dynamoDBMapper = DynamoDBMapper(amazonDynamoDBAsync, DynamoDBMapperConfig.DEFAULT, statCreds)
                }
            }

            initialize(amazonDynamoDBAsync, dynamoDBMapper, COLLECTION, TYPE, future)

            return future
        }

        @Suppress("MayBeConstant")
        private fun initialize(
            client: AmazonDynamoDBAsync,
            mapper: DynamoDBMapper,
            COLLECTION: String,
            TYPE: Class<*>,
            resultHandler: Handler<AsyncResult<Void>>
        ) {
            client.listTablesAsync(object : AsyncHandler<ListTablesRequest, ListTablesResult> {
                private val DEFAULT_WRITE_TABLE = 100L
                private val DEFAULT_READ_TABLE = 100L
                private val DEFAULT_WRITE_GSI = 100L
                private val DEFAULT_READ_GSI = 100L

                override fun onError(e: Exception) {
                    logger.error("Cannot use this repository for creation, no connection: $e")

                    resultHandler.handle(Future.failedFuture(e))
                }

                override fun onSuccess(request: ListTablesRequest, listTablesResult: ListTablesResult) {
                    val tableExists = listTablesResult.tableNames.contains(COLLECTION)

                    if (logger.isDebugEnabled) {
                        logger.debug("Table is available: $tableExists")
                    }

                    when {
                        tableExists -> {
                            if (logger.isDebugEnabled) {
                                logger.debug("Table exists for: $COLLECTION, doing nothing...")
                            }

                            resultHandler.handle(Future.succeededFuture())
                        }
                        else -> {
                            val req = mapper.generateCreateTableRequest(TYPE)
                                    .withProvisionedThroughput(ProvisionedThroughput()
                                            .withWriteCapacityUnits(DEFAULT_WRITE_TABLE)
                                            .withReadCapacityUnits(DEFAULT_READ_TABLE))

                            val allProjection = Projection().withProjectionType(ProjectionType.ALL)
                            req.setLocalSecondaryIndexes(req.localSecondaryIndexes.stream()
                                    .peek { lsi -> lsi.projection = allProjection }
                                    .collect(toList()))
                            setAnyGlobalSecondaryIndexes(req, DEFAULT_READ_GSI, DEFAULT_WRITE_GSI)

                            client.createTableAsync(req, object : AsyncHandler<CreateTableRequest, CreateTableResult> {
                                override fun onError(e: Exception) {
                                    logger.error(e.toString() + " : " + e.message + " : " + toString())
                                    if (logger.isDebugEnabled) {
                                        logger.debug("Could not remoteCreate table for: $COLLECTION")
                                    }

                                    resultHandler.handle(Future.failedFuture(e))
                                }

                                override fun onSuccess(request: CreateTableRequest, createTableResult: CreateTableResult) {
                                    if (logger.isDebugEnabled) {
                                        logger.debug("Table creation for: $COLLECTION is success: " + (
                                                createTableResult.tableDescription
                                                        .tableName == COLLECTION))
                                    }

                                    waitForTableAvailable(createTableResult, Handler { res ->
                                        when {
                                            res.failed() -> resultHandler.handle(Future.failedFuture<Void>(res.cause()))
                                            else -> resultHandler.handle(Future.succeededFuture())
                                        }
                                    })
                                }
                            })
                        }
                    }
                }

                @Suppress("SameParameterValue")
                private fun setAnyGlobalSecondaryIndexes(
                    req: CreateTableRequest,
                    readProvisioning: Long = 100,
                    writeProvisioning: Long = 100
                ) {
                    @Suppress("UNCHECKED_CAST")
                    val map = setGsiKeys(TYPE as Class<Any>)

                    if (map.isNotEmpty()) {
                        val gsis = ArrayList<GlobalSecondaryIndex>()

                        map.forEach { (k, v) ->
                            gsis.add(GlobalSecondaryIndex()
                                    .withIndexName(k)
                                    .withProjection(Projection()
                                            .withProjectionType(ProjectionType.ALL))
                                    .withKeySchema(KeySchemaElement()
                                            .withKeyType(KeyType.HASH)
                                            .withAttributeName(v.getString("hash")),
                                            KeySchemaElement()
                                                    .withKeyType(KeyType.RANGE)
                                                    .withAttributeName(v.getString("range")))
                                    .withProvisionedThroughput(ProvisionedThroughput()
                                            .withReadCapacityUnits(readProvisioning)
                                            .withWriteCapacityUnits(writeProvisioning)))
                        }

                        req.withGlobalSecondaryIndexes(gsis)
                    }
                }

                private fun waitForTableAvailable(
                    createTableResult: CreateTableResult,
                    resultHandler: Handler<AsyncResult<Void>>
                ) {
                    val tableName = createTableResult.tableDescription.tableName

                    val describeTableResult = client.describeTable(tableName)

                    when {
                        tableReady(describeTableResult) -> {
                            logger.debug("$tableName created and active: " + Json.encodePrettily(
                                    describeTableResult.table))

                            resultHandler.handle(Future.succeededFuture())
                        }
                        else -> waitForActive(tableName, Handler {
                            when {
                                it.failed() -> waitForTableAvailable(createTableResult, resultHandler)
                                else -> resultHandler.handle(Future.succeededFuture())
                            }
                        })
                    }
                }

                private fun tableReady(describeTableResult: DescribeTableResult): Boolean {
                    return describeTableResult.table.tableStatus.equals("ACTIVE", ignoreCase = true) &&
                            describeTableResult.table.globalSecondaryIndexes.stream()
                                    .allMatch { it.indexStatus == "ACTIVE" }
                }

                private fun waitForActive(tableName: String, resHandle: Handler<AsyncResult<Void>>) {
                    when {
                        client.describeTable(tableName).table.tableStatus == "ACTIVE" ->
                            resHandle.handle(Future.succeededFuture())
                        else -> resHandle.handle(Future.failedFuture("Not active!"))
                    }
                }
            })
        }

        fun createS3Link(dynamoDBMapper: DynamoDBMapper, S3BucketName: String, path: String): S3Link {
            return dynamoDBMapper.createS3Link(Region.EU_Ireland, S3BucketName, path)
        }

        fun createSignedUrl(dynamoDBMapper: DynamoDBMapper, file: S3Link): String {
            return createSignedUrl(dynamoDBMapper, 7, file)
        }

        fun createSignedUrl(dynamoDBMapper: DynamoDBMapper, days: Int, file: S3Link): String {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DATE, days)

            val signReq = GeneratePresignedUrlRequest(file.bucketName, file.key)
            signReq.method = com.amazonaws.HttpMethod.GET
            signReq.expiration = calendar.time

            val url = dynamoDBMapper.s3ClientCache.getClient(Region.EU_Ireland).generatePresignedUrl(signReq)

            return url.toString()
        }
    }
}
