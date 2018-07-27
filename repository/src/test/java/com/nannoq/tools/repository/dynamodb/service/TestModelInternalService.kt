package com.nannoq.tools.repository.dynamodb.service

import com.nannoq.tools.repository.dynamodb.gen.models.TestModel
import com.nannoq.tools.repository.services.internal.InternalRepositoryService
import io.vertx.codegen.annotations.Fluent
import io.vertx.codegen.annotations.ProxyGen
import io.vertx.codegen.annotations.VertxGen
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject

/**
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@ProxyGen
@VertxGen
interface TestModelInternalService : InternalRepositoryService<TestModel> {
    @Fluent
    override fun remoteIndex(identifier: JsonObject,
                             resultHandler: Handler<AsyncResult<List<TestModel>>>): TestModelInternalService
}
