package com.nannoq.tools.version.manager

import com.nannoq.tools.version.VersionUtils
import com.nannoq.tools.version.models.DiffPair
import com.nannoq.tools.version.models.Version
import com.nannoq.tools.version.operators.IteratorIdManager
import com.nannoq.tools.version.operators.StateApplier
import com.nannoq.tools.version.operators.StateExtractor
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.Json

class VersionManager {
    private val versionUtils: VersionUtils = VersionUtils()
    private val stateApplier: StateApplier = StateApplier(Json.mapper, versionUtils)
    private val stateExtractor: StateExtractor = StateExtractor(Json.mapper, versionUtils)
    private val iteratorIdManager: IteratorIdManager = IteratorIdManager(versionUtils)

    fun <T: Any> applyState(version: Version, obj: T, handler: Handler<AsyncResult<T>>): VersionManager {
        handler.handle(applyState(version, obj))

        return this
    }

    fun <T: Any> applyState(version: Version, obj: T): Future<T> {
        val fut = Future.future<T>()

        stateApplier.applyState(version, obj, fut.completer())

        return fut
    }

    fun <T: Any> applyState(versionList: List<Version>, obj: T, handler: Handler<AsyncResult<T>>): VersionManager {
        handler.handle(applyState(versionList, obj))

        return this
    }

    fun <T: Any> applyState(versionList: List<Version>, obj: T): Future<T> {
        val fut = Future.future<T>()
        val futList = mutableListOf<Future<T>>()

        versionList.forEach {
            futList.add(applyState(it, obj))
        }

        @Suppress("UNCHECKED_CAST")
        CompositeFuture.all(futList as List<Future<Any>>).setHandler {
            when {
                it.failed() -> fut.fail(it.cause())
                it.succeeded() -> fut.complete(obj)
            }
        }

        return fut
    }

    fun <T: Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>, handler: Handler<AsyncResult<List<T>>>)
            : VersionManager {
        handler.handle(applyState(versionMap, objs))

        return this
    }

    fun <T: Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>): Future<List<T>> {
        val fut = Future.future<List<T>>()
        val futList = mutableListOf<Future<T>>()

        objs.forEach {
            futList.add(applyState(versionMap.getValue(it), it))
        }

        @Suppress("UNCHECKED_CAST")
        CompositeFuture.all(futList as List<Future<Any>>).setHandler {
            when {
                it.failed() -> fut.fail(it.cause())
                it.succeeded() -> fut.complete(objs)
            }
        }

        return fut
    }

    fun <T: Any> extractVersion(pair: DiffPair<T>, handler: Handler<AsyncResult<Version>>): VersionManager {
        handler.handle(extractVersion(pair))

        return this
    }

    fun <T: Any> extractVersion(pair: DiffPair<T>): Future<Version> {
        val fut = Future.future<Version>()

        stateExtractor.extractVersion(pair, fut.completer())

        return fut
    }

    fun <T: Any> extractVersion(pairs: List<DiffPair<T>>, handler: Handler<AsyncResult<List<Version>>>)
            : VersionManager {
        handler.handle(extractVersion(pairs))

        return this
    }

    fun <T: Any> extractVersion(pairs: List<DiffPair<T>>): Future<List<Version>> {
        val fut = Future.future<List<Version>>()
        val futList = mutableListOf<Future<Version>>()

        pairs.forEach {
            futList.add(extractVersion(it))
        }

        @Suppress("UNCHECKED_CAST")
        CompositeFuture.all(futList as List<Future<Any>>).setHandler { result ->
            when {
                result.failed() -> fut.fail(result.cause())
                result.succeeded() -> fut.complete(futList.map { it.result() } as List<Version>?)
            }
        }

        return fut
    }

    fun <T: Any> setIteratorIds(obj: T, handler: Handler<AsyncResult<T>>): VersionManager {
        handler.handle(setIteratorIds(obj))

        return this
    }

    fun <T: Any> setIteratorIds(obj: T): Future<T> {
        val fut = Future.future<T>()

        setIteratorIds(listOf(obj)).setHandler {
            when {
                it.failed() -> fut.fail(it.cause())
                it.succeeded() -> fut.complete(it.result()[0])
            }
        }

        return fut
    }

    fun <T: Any> setIteratorIds(objs: List<T>, handler: Handler<AsyncResult<List<T>>>): VersionManager {
        handler.handle(setIteratorIds(objs))

        return this
    }

    fun <T: Any> setIteratorIds(objs: List<T>): Future<List<T>> {
        val fut = Future.future<List<T>>()

        @Suppress("UNCHECKED_CAST")
        iteratorIdManager.setIteratorIds(objs, fut.completer() as Handler<AsyncResult<Collection<T>>>)

        return fut
    }
}
