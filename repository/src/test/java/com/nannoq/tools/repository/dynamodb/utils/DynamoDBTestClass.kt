package com.nannoq.tools.repository.dynamodb.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.dynamodb.gen.TestModelReceiverImpl
import com.nannoq.tools.repository.dynamodb.gen.models.TestModel
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
import java.util.*

@ExtendWith(VertxExtension::class)
abstract class DynamoDBTestClass : ConfigSupport {
    @Suppress("unused")
    private val logger: Logger = LoggerFactory.getLogger(javaClass.simpleName)

    protected val testDate = Date()
    protected val nonNullTestModel = {
        TestModel()
                .setSomeStringOne("testString")
                .setSomeStringTwo("testStringRange")
                .setSomeStringThree("testStringThree")
                .setSomeLong(1L)
                .setSomeLongTwo(0L)
                .setSomeInteger(0)
                .setSomeIntegerTwo(1)
                .setSomeBooleanTwo(false)
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
    fun setup(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        val freePort = findFreePort()
        dynamoDBUtils.startDynamoDB(freePort)
        contextObjects["${testInfo.testMethod.get().name}-port"] = freePort
        contextObjects["${testInfo.testMethod.get().name}-endpoint"] = "http://localhost:$freePort"

        Json.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val endpoint = contextObjects["${testInfo.testMethod.get().name}-endpoint"]
        val config = JsonObject().put("dynamo_endpoint", endpoint)
        val classCollection = mapOf(Pair("testModels", TestModel::class.java))
        val finalConfig = getTestConfig().mergeIn(config)

        contextObjects["${testInfo.testMethod.get().name}-repo"] = TestModelReceiverImpl(vertx, finalConfig)
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
        context.completeNow()
    }
}