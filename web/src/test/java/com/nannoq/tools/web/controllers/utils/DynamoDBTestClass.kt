package com.nannoq.tools.web.controllers.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.web.controllers.gen.models.TestModel
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
import redis.embedded.RedisServer
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
        val redisPort = findFreePort()
        val httpPort = findFreePort()
        dynamoDBUtils.startDynamoDB(freePort)
        context.put<String>("${name.methodName}-port", freePort)
        context.put<String>("${name.methodName}-redis-port", redisPort)
        context.put<String>("${name.methodName}-http-port", httpPort)
        context.put<String>("${name.methodName}-endpoint", "http://localhost:$freePort")

        val redisServer = RedisServer(redisPort)
        redisServer.start()

        context.put<String>("${name.methodName}-redis", redisServer)

        Json.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val async = context.async()
        val endpoint = context.get<String>("${name.methodName}-endpoint")
        val config = JsonObject()
                .put("dynamo_endpoint", endpoint)
                .put("redis_host", "localhost")
                .put("redis_port", redisPort)
        val classCollection = mapOf(Pair("testModels", TestModel::class.java))
        val finalConfig = getTestConfig().mergeIn(config)

        context.put<String>("${name.methodName}-repo", DynamoDBRepository(rule.vertx(), TestModel::class.java, finalConfig))
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

        val redis: RedisServer = context.get("${name.methodName}-redis")
        redis.stop()
    }
}