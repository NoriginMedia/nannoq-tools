@file:Suppress("unused")

package com.nannoq.tools.web.controllers.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.web.controllers.gen.models.TestModel
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import java.net.ServerSocket
import java.util.Date
import java.util.UUID
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import redis.embedded.RedisServer

@ExtendWith(VertxExtension::class)
abstract class DynamoDBTestClass : ConfigSupport {
    @Suppress("unused")
    private val logger: Logger = LoggerFactory.getLogger(javaClass.simpleName)

    private val testDate = Date()
    protected val nonNullTestModel = {
        TestModel(
                someStringOne = "testString",
                someStringTwo = "testStringRange",
                someStringThree = "testStringThree",
                someStringFour = null,
                someLong = 1L,
                someLongTwo = 0L,
                someInteger = 0,
                someIntegerTwo = 1,
                someBoolean = null,
                someBooleanTwo = false,
                someDate = testDate,
                someDateTwo = Date(),
                documents = emptyList())
    }

    protected val contextObjects: MutableMap<String, Any> = HashMap()

    companion object {
        private var localPort: Int = 0
        private val dynamoDBUtils = DynamoDBUtils()
        private val mapSet = mutableSetOf<Int>()

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            localPort = getPort()
            dynamoDBUtils.startDynamoDB(localPort)
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            dynamoDBUtils.stopDynamoDB(localPort)
        }

        @JvmStatic
        @Synchronized
        private fun getPort(): Int {
            val use = ServerSocket(0).use { it.localPort }

            if (mapSet.contains(use)) {
                return getPort()
            } else {
                mapSet.add(use)

                return use
            }
        }
    }

    @BeforeEach
    fun setup(vertx: Vertx, testInfo: TestInfo, context: VertxTestContext) {
        val redisPort = getPort()
        val httpPort = getPort()

        contextObjects["${testInfo.testMethod.get().name}-port"] = localPort
        contextObjects["${testInfo.testMethod.get().name}-redis-port"] = redisPort
        contextObjects["${testInfo.testMethod.get().name}-http-port"] = httpPort
        contextObjects["${testInfo.testMethod.get().name}-endpoint"] = "http://localhost:$localPort"

        val redisServer = RedisServer(redisPort)
        redisServer.start()

        contextObjects["${testInfo.testMethod.get().name}-redis"] = redisServer

        Json.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val endpoint = contextObjects["${testInfo.testMethod.get().name}-endpoint"]
        val config = JsonObject()
                .put("dynamo_endpoint", endpoint)
                .put("dynamo_db_iam_id", UUID.randomUUID().toString())
                .put("dynamo_db_iam_key", UUID.randomUUID().toString())
                .put("redis_host", "localhost")
                .put("redis_port", redisPort)
        val classCollection = mapOf(Pair("testModels", TestModel::class.java))
        val finalConfig = getTestConfig().mergeIn(config)

        contextObjects["${testInfo.testMethod.get().name}-repo"] = DynamoDBRepository(vertx, TestModel::class.java, finalConfig)
        contextObjects["${testInfo.testMethod.get().name}-config"] = finalConfig

        DynamoDBRepository.initializeDynamoDb(finalConfig, classCollection, Handler {
            if (it.failed()) context.failNow(it.cause())
            context.completeNow()
        })
    }

    @AfterEach
    fun teardown(testInfo: TestInfo, context: VertxTestContext) {
        val redis: RedisServer = contextObjects["${testInfo.testMethod.get().name}-redis"] as RedisServer
        redis.stop()

        context.completeNow()
    }
}
