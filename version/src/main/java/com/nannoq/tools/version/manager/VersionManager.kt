package com.nannoq.tools.version.manager

import com.nannoq.tools.version.models.DiffPair
import com.nannoq.tools.version.models.Version
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler

interface VersionManager {
    fun <T: Any> applyState(version: Version, obj: T, handler: Handler<AsyncResult<T>>): VersionManager
    fun <T: Any> applyState(versionList: List<Version>, obj: T, handler: Handler<AsyncResult<T>>): VersionManager
    fun <T: Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>, handler: Handler<AsyncResult<List<T>>>): VersionManager
    fun <T: Any> applyState(version: Version, obj: T): Future<T>
    fun <T: Any> applyState(versionList: List<Version>, obj: T): Future<T>
    fun <T: Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>): Future<List<T>>
    suspend fun <T: Any> applyStateBlocking(version: Version, obj: T): T
    suspend fun <T: Any> applyStateBlocking(versionList: List<Version>, obj: T): T
    suspend fun <T: Any> applyStateBlocking(versionMap: Map<T, List<Version>>, objs: List<T>): List<T>

    fun <T: Any> extractVersion(pair: DiffPair<T>, handler: Handler<AsyncResult<Version>>): VersionManager
    fun <T: Any> extractVersion(pairs: List<DiffPair<T>>, handler: Handler<AsyncResult<List<Version>>>): VersionManager
    fun <T: Any> extractVersion(pair: DiffPair<T>): Future<Version>
    fun <T: Any> extractVersion(pairs: List<DiffPair<T>>): Future<List<Version>>
    suspend fun <T: Any> extractVersionBlocking(pair: DiffPair<T>): Version
    suspend fun <T: Any> extractVersionBlocking(pairs: List<DiffPair<T>>): List<Version>

    fun <T: Any> setIteratorIds(obj: T, handler: Handler<AsyncResult<T>>): VersionManager
    fun <T: Any> setIteratorIds(objs: List<T>, handler: Handler<AsyncResult<List<T>>>): VersionManager
    fun <T: Any> setIteratorIds(obj: T): Future<T>
    fun <T: Any> setIteratorIds(objs: List<T>): Future<List<T>>
    suspend fun <T: Any> setIteratorIdsBlocking(obj: T): T
    suspend fun <T: Any> setIteratorIdsBlocking(objs: List<T>): List<T>
}