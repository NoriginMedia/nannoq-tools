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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import java.net.ServerSocket
import java.util.Date
import java.util.UUID

@ExtendWith(VertxExtension::class)
abstract class DynamoDBTestClass : ConfigSupport {
    @Suppress("unused")
    private val logger: Logger = LoggerFactory.getLogger(javaClass.simpleName)

    protected val testDate = Date()
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

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            localPort = ServerSocket(0).use { it.localPort }
            dynamoDBUtils.startDynamoDB(localPort)
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            dynamoDBUtils.stopDynamoDB(localPort)
        }
    }

    @BeforeEach
    fun setup(testInfo: TestInfo, vertx: Vertx, context: VertxTestContext) {
        contextObjects["${testInfo.testMethod.get().name}-port"] = localPort
        contextObjects["${testInfo.testMethod.get().name}-endpoint"] = "http://localhost:$localPort"

        Json.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val endpoint = contextObjects["${testInfo.testMethod.get().name}-endpoint"]
        val config = JsonObject()
                .put("dynamo_endpoint", endpoint)
                .put("dynamo_db_iam_id", UUID.randomUUID().toString())
                .put("dynamo_db_iam_key", UUID.randomUUID().toString())
        val classCollection = mapOf(Pair("testModels", TestModel::class.java))
        val finalConfig = getTestConfig().mergeIn(config)

        contextObjects["${testInfo.testMethod.get().name}-repo"] = TestModelReceiverImpl(vertx, finalConfig)
        contextObjects["${testInfo.testMethod.get().name}-config"] = finalConfig

        DynamoDBRepository.initializeDynamoDb(finalConfig, classCollection, Handler {
            if (it.failed()) context.failNow(it.cause())
            context.completeNow()
        })
    }
}