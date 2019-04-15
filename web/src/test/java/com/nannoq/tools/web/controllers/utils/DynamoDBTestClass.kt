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
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import redis.embedded.RedisServer
import java.util.*
import kotlin.collections.HashMap

@ExtendWith(VertxExtension::class)
abstract class DynamoDBTestClass : ConfigSupport {
    @Suppress("unused")
    private val logger: Logger = LoggerFactory.getLogger(javaClass.simpleName)

    private val testDate = Date()
    protected val nonNullTestModel = {
        TestModel()
                .setSomeStringOne("testString")
                .setSomeStringTwo("testStringRange")
                .setSomeStringThree("testStringThree")
                .setSomeLong(1L)
                .setSomeDate(testDate)
                .setSomeDateTwo(Date())
    }
    
    protected val contextObjects: MutableMap<String, Any> = HashMap()

    companion object {
        private lateinit var dynamoDBUtils: DynamoDBUtils

        @BeforeAll
        @JvmStatic
        fun setupClass() {
            dynamoDBUtils = DynamoDBUtils()
        }

        @AfterAll
        @JvmStatic
        fun teardownClass() {
            dynamoDBUtils.stopAll()
        }
    }

    @BeforeEach
    fun setup(vertx: Vertx, testInfo: TestInfo, context: VertxTestContext) {
        val freePort = findFreePort()
        val redisPort = findFreePort()
        val httpPort = findFreePort()
        dynamoDBUtils.startDynamoDB(freePort)
        contextObjects["${testInfo.testMethod.get().name}-port"] = freePort
        contextObjects["${testInfo.testMethod.get().name}-redis-port"] = redisPort
        contextObjects["${testInfo.testMethod.get().name}-http-port"] = httpPort
        contextObjects["${testInfo.testMethod.get().name}-endpoint"] = "http://localhost:$freePort"

        val redisServer = RedisServer(redisPort)
        redisServer.start()

        contextObjects["${testInfo.testMethod.get().name}-redis"] = redisServer

        Json.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val endpoint = contextObjects["${testInfo.testMethod.get().name}-endpoint"]
        val config = JsonObject()
                .put("dynamo_endpoint", endpoint)
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
        val freePort = contextObjects["${testInfo.testMethod.get().name}-port"] as Int
        dynamoDBUtils.stopDynamoDB(freePort)

        val redis: RedisServer = contextObjects["${testInfo.testMethod.get().name}-redis"] as RedisServer
        redis.stop()
        context.completeNow()
    }
}