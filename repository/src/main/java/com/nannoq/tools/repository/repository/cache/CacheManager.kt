/*
 * MIT License
 *
 * Copyright (c) 2017 Anders Mikkelsen
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

package com.nannoq.tools.repository.repository.cache

import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.utils.ItemList
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Promise
import java.util.function.Function
import java.util.function.Supplier

/**
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
interface CacheManager<E> where E : Cacheable, E : Model {

    val isObjectCacheAvailable: Boolean
    val isItemListCacheAvailable: Boolean
    val isAggregationCacheAvailable: Boolean
    fun initializeCache(resultHandler: Handler<AsyncResult<Boolean>>)
    fun checkObjectCache(cacheId: String, resultHandler: Handler<AsyncResult<E>>)
    fun checkItemListCache(cacheId: String, projections: Array<String>, resultHandler: Handler<AsyncResult<ItemList<E>>>)
    fun checkAggregationCache(cacheKey: String, resultHandler: Handler<AsyncResult<String>>)

    fun replaceCache(
        writeProm: Promise<Boolean>,
        records: List<E>,
        shortCacheIdSupplier: Function<E, String>,
        cacheIdSupplier: Function<E, String>
    )

    fun replaceObjectCache(cacheId: String, item: E, future: Promise<E>, projections: Array<String>)
    fun replaceItemListCache(
        content: String,
        cacheIdSupplier: Supplier<String>,
        resultHandler: Handler<AsyncResult<Boolean>>
    )

    fun replaceAggregationCache(
        content: String,
        cacheIdSupplier: Supplier<String>,
        resultHandler: Handler<AsyncResult<Boolean>>
    )

    fun purgeCache(future: Promise<Boolean>, records: List<E>, cacheIdSupplier: (E) -> String)
}
