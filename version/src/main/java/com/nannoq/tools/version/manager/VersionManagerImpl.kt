package com.nannoq.tools.version.manager

import com.nannoq.tools.version.VersionUtils
import com.nannoq.tools.version.models.DiffPair
import com.nannoq.tools.version.models.Version
import com.nannoq.tools.version.operators.IteratorIdManager
import com.nannoq.tools.version.operators.StateApplier
import com.nannoq.tools.version.operators.StateExtractor
import io.vertx.core.*
import io.vertx.core.json.Json
import io.vertx.kotlin.coroutines.awaitResult

class VersionManagerImpl : VersionManager, AbstractVerticle() {
    private val versionUtils: VersionUtils = VersionUtils()
    private val stateApplier: StateApplier = StateApplier(Json.mapper, versionUtils)
    private val stateExtractor: StateExtractor = StateExtractor(Json.mapper, versionUtils)
    private val iteratorIdManager: IteratorIdManager = IteratorIdManager(versionUtils)

    override fun <T: Any> applyState(version: Version, obj: T, handler: Handler<AsyncResult<T>>): VersionManagerImpl {
        handler.handle(applyState(version, obj))

        return this
    }

    override fun <T: Any> applyState(version: Version, obj: T): Future<T> {
        val fut = Future.future<T>()

        stateApplier.applyState(version, obj, fut.completer())

        return fut
    }

    @Throws(IllegalStateException::class)
    override suspend fun <T : Any> applyStateBlocking(version: Version, obj: T): T {
        return awaitResult { applyState(version, obj, it) }
    }

    override fun <T: Any> applyState(versionList: List<Version>, obj: T, handler: Handler<AsyncResult<T>>)
            : VersionManagerImpl {
        handler.handle(applyState(versionList, obj))

        return this
    }

    override fun <T: Any> applyState(versionList: List<Version>, obj: T): Future<T> {
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

    @Throws(IllegalStateException::class)
    override suspend fun <T : Any> applyStateBlocking(versionList: List<Version>, obj: T): T {
        return awaitResult { applyState(versionList, obj, it) }
    }

    override fun <T: Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>,
                                     handler: Handler<AsyncResult<List<T>>>): VersionManagerImpl {
        handler.handle(applyState(versionMap, objs))

        return this
    }

    override fun <T: Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>): Future<List<T>> {
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

    @Throws(IllegalStateException::class)
    override suspend fun <T : Any> applyStateBlocking(versionMap: Map<T, List<Version>>, objs: List<T>): List<T> {
        return awaitResult { applyState(versionMap, objs, it) }
    }

    override fun <T: Any> extractVersion(pair: DiffPair<T>, handler: Handler<AsyncResult<Version>>):
            VersionManagerImpl {
        handler.handle(extractVersion(pair))

        return this
    }

    override fun <T: Any> extractVersion(pair: DiffPair<T>): Future<Version> {
        val fut = Future.future<Version>()

        stateExtractor.extractVersion(pair, fut.completer())

        return fut
    }

    @Throws(IllegalStateException::class)
    override suspend fun <T : Any> extractVersionBlocking(pair: DiffPair<T>): Version {
        return awaitResult { extractVersion(pair, it) }
    }

    override fun <T: Any> extractVersion(pairs: List<DiffPair<T>>, handler: Handler<AsyncResult<List<Version>>>)
            : VersionManagerImpl {
        handler.handle(extractVersion(pairs))

        return this
    }

    override fun <T: Any> extractVersion(pairs: List<DiffPair<T>>): Future<List<Version>> {
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

    @Throws(IllegalStateException::class)
    override suspend fun <T : Any> extractVersionBlocking(pairs: List<DiffPair<T>>): List<Version> {
        return awaitResult { extractVersion(pairs, it) }
    }

    override fun <T: Any> setIteratorIds(obj: T, handler: Handler<AsyncResult<T>>): VersionManagerImpl {
        handler.handle(setIteratorIds(obj))

        return this
    }

    override fun <T: Any> setIteratorIds(obj: T): Future<T> {
        val fut = Future.future<T>()

        setIteratorIds(listOf(obj)).setHandler {
            when {
                it.failed() -> fut.fail(it.cause())
                it.succeeded() -> fut.complete(it.result()[0])
            }
        }

        return fut
    }

    @Throws(IllegalStateException::class)
    override suspend fun <T : Any> setIteratorIdsBlocking(obj: T): T {
        return awaitResult { setIteratorIds(obj, it) }
    }

    override fun <T: Any> setIteratorIds(objs: List<T>, handler: Handler<AsyncResult<List<T>>>): VersionManagerImpl {
        handler.handle(setIteratorIds(objs))

        return this
    }

    override fun <T: Any> setIteratorIds(objs: List<T>): Future<List<T>> {
        val fut = Future.future<List<T>>()

        @Suppress("UNCHECKED_CAST")
        iteratorIdManager.setIteratorIds(objs, fut.completer() as Handler<AsyncResult<Collection<T>>>)

        return fut
    }

    @Throws(IllegalStateException::class)
    override suspend fun <T : Any> setIteratorIdsBlocking(objs: List<T>): List<T> {
        return awaitResult { setIteratorIds(objs, it) }
    }
}
