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

package com.nannoq.tools.repository.repository.etag

import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.utils.ItemList
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler

/**
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
interface ETagManager<E> where E : Model, E : ETagable {
    fun removeProjectionsEtags(hash: Int, resultHandler: Handler<AsyncResult<Boolean>>)
    fun destroyEtags(hash: Int, resultHandler: Handler<AsyncResult<Boolean>>)
    fun replaceAggregationEtag(
        etagItemListHashKey: String,
        etagKey: String,
        newEtag: String,
        resultHandler: Handler<AsyncResult<Boolean>>
    )

    fun setSingleRecordEtag(etagMap: Map<String, String>, resultHandler: Handler<AsyncResult<Boolean>>)
    fun setProjectionEtags(projections: Array<String>, hash: Int, item: E)
    fun setItemListEtags(etagItemListHashKey: String, etagKey: String, itemList: ItemList<E>, itemListEtagFuture: Future<Boolean>)

    fun checkItemEtag(etagKeyBase: String, key: String, requestEtag: String, resultHandler: Handler<AsyncResult<Boolean>>)
    fun checkItemListEtag(etagItemListHashKey: String, etagKey: String, etag: String, resultHandler: Handler<AsyncResult<Boolean>>)
    fun checkAggregationEtag(etagItemListHashKey: String, etagKey: String, etag: String, resultHandler: Handler<AsyncResult<Boolean>>)
}
