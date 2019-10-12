/*
 * MIT License
 *
 * Copyright (c) 2019 Anders Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.nannoq.tools.version.manager

import com.nannoq.tools.version.models.DiffPair
import com.nannoq.tools.version.models.Version
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler

interface VersionManager {
    fun <T : Any> applyState(version: Version, obj: T, handler: Handler<AsyncResult<T>>): VersionManager
    fun <T : Any> applyState(versionList: List<Version>, obj: T, handler: Handler<AsyncResult<T>>): VersionManager
    fun <T : Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>, handler: Handler<AsyncResult<List<T>>>): VersionManager
    fun <T : Any> applyState(version: Version, obj: T): Future<T>
    fun <T : Any> applyState(versionList: List<Version>, obj: T): Future<T>
    fun <T : Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>): Future<List<T>>
    suspend fun <T : Any> applyStateBlocking(version: Version, obj: T): T
    suspend fun <T : Any> applyStateBlocking(versionList: List<Version>, obj: T): T
    suspend fun <T : Any> applyStateBlocking(versionMap: Map<T, List<Version>>, objs: List<T>): List<T>

    fun <T : Any> extractVersion(pair: DiffPair<T>, handler: Handler<AsyncResult<Version>>): VersionManager
    fun <T : Any> extractVersion(pairs: List<DiffPair<T>>, handler: Handler<AsyncResult<List<Version>>>): VersionManager
    fun <T : Any> extractVersion(pair: DiffPair<T>): Future<Version>
    fun <T : Any> extractVersion(pairs: List<DiffPair<T>>): Future<List<Version>>
    suspend fun <T : Any> extractVersionBlocking(pair: DiffPair<T>): Version
    suspend fun <T : Any> extractVersionBlocking(pairs: List<DiffPair<T>>): List<Version>

    fun <T : Any> setIteratorIds(obj: T, handler: Handler<AsyncResult<T>>): VersionManager
    fun <T : Any> setIteratorIds(objs: List<T>, handler: Handler<AsyncResult<List<T>>>): VersionManager
    fun <T : Any> setIteratorIds(obj: T): Future<T>
    fun <T : Any> setIteratorIds(objs: List<T>): Future<List<T>>
    suspend fun <T : Any> setIteratorIdsBlocking(obj: T): T
    suspend fun <T : Any> setIteratorIdsBlocking(objs: List<T>): List<T>
}
