package com.nannoq.tools.repository.dynamodb.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.dynamodb.gen.TestModelReceiverImpl
import com.nannoq.tools.repository.dynamodb.gen.models.TestModel
import io.vertx.core.Handler
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.ext.unit.junit.Timeout
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.util.*

@RunWith(VertxUnitRunner::class)
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
                .setSomeDate(testDate)
                .setSomeDateTwo(Date())
    }

    @JvmField
    @Rule
    val rule = RunTestOnContext()

    @JvmField
    @Rule
    val timeout = Timeout.seconds(30)

    @JvmField
    @Rule
    var name = TestName()

    companion object {
        private lateinit var dynamoDBUtils: DynamoDBUtils

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            dynamoDBUtils = DynamoDBUtils()
        }

        @AfterClass
        @JvmStatic
        fun teardownClass() {
            dynamoDBUtils.stopAll()
        }
    }

    @Before
    fun setup(context: TestContext) {
        val freePort = findFreePort()
        dynamoDBUtils.startDynamoDB(freePort)
        context.put<String>("${name.methodName}-port", freePort)
        context.put<String>("${name.methodName}-endpoint", "http://localhost:$freePort")

        Json.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val async = context.async()
        val endpoint = context.get<String>("${name.methodName}-endpoint")
        val config = JsonObject().put("dynamo_endpoint", endpoint)
        val classCollection = mapOf(Pair("testModels", TestModel::class.java))
        val finalConfig = getTestConfig().mergeIn(config)

        context.put<String>("${name.methodName}-repo", TestModelReceiverImpl(rule.vertx(), finalConfig))
        context.put<JsonObject>("${name.methodName}-config", finalConfig)

        DynamoDBRepository.initializeDynamoDb(finalConfig, classCollection, Handler {
            if (it.failed()) context.fail(it.cause())
            async.complete()
        })
    }

    @After
    fun teardown(context: TestContext) {
        val freePort = context.get<Int>("${name.methodName}-port")
        dynamoDBUtils.stopDynamoDB(freePort)
    }
}