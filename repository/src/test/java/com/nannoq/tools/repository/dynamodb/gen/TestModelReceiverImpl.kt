package com.nannoq.tools.repository.dynamodb.gen

import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.dynamodb.gen.models.TestModel
import com.nannoq.tools.repository.dynamodb.service.TestModelInternalService
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

class TestModelReceiverImpl(vertx: Vertx, config: JsonObject) : TestModelInternalService, DynamoDBRepository<TestModel>(vertx, TestModel::class.java, config) {
    override fun remoteIndex(identifier: JsonObject, resultHandler: Handler<AsyncResult<List<TestModel>>>): TestModelReceiverImpl {
        return super.remoteIndex(identifier, resultHandler) as TestModelReceiverImpl
    }
}