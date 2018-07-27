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
 *
 */

package com.nannoq.tools.repository.repository.results

import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.utils.ItemList

/**
 * This class defines a container for the result of an index operation.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class ItemListResult<K : Model> {
    private var etagBase: String? = null
    var count: Int = 0
    var items: List<K>? = null
    var pageToken: String? = null
    var projections: Array<String>? = null
    var itemList: ItemList<K>? = null
        get() {
            return field ?: ItemList(etagBase!!, pageToken ?: "END_OF_LIST", count, items, projections!!)
        }

    var isCacheHit: Boolean = false
    var preOperationProcessingTime: Long = 0
    var operationProcessingTime: Long = 0
    var postOperationProcessingTime: Long = 0

    constructor(etagBase: String, count: Int, items: List<K>, pageToken: String,
                projections: Array<String>, cacheHit: Boolean) {
        this.etagBase = etagBase
        this.count = count
        this.items = items
        this.pageToken = pageToken
        this.projections = projections
        this.isCacheHit = cacheHit
    }

    constructor(itemList: ItemList<K>, cacheHit: Boolean) {
        this.itemList = itemList
        this.isCacheHit = cacheHit
    }
}
