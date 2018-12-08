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

package com.nannoq.tools.repository.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.models.ModelUtils
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

/**
 * This class defines the ItemList. It has x amount of items controlled by the count field, a pageTokens, and an etag.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class ItemList<E : Model> {
    var etag: String? = null
    var pageTokens: PageTokens? = null
    var count: Int = 0
    var items: List<E>? = null

    constructor()

    constructor(etagBase: String, pageTokens: PageTokens, count: Int, items: List<E>?, projections: Array<String>) {
        this.pageTokens = pageTokens
        this.count = count
        this.items = items
        val etagCode = longArrayOf(etagBase.hashCode().toLong())
        items?.forEach { item -> etagCode[0] = etagCode[0] xor item.toJsonFormat(projections).encode().hashCode().toLong() }
        etag = ModelUtils.returnNewEtag(etagCode[0])
    }

    fun toJson(projections: Array<String>): JsonObject {
        val jsonObject = JsonObject()
                .put("etag", if (etag == null) "NoTag" else etag)
                .put("pageTokens", if (pageTokens == null) PageTokens().toJson() else pageTokens?.toJson())
                .put("count", count)

        val jsonItems = JsonArray()

        if (items != null) {
            items!!.stream()
                    .map { m -> m.toJsonFormat(projections) }
                    .forEach { jsonItems.add(it) }
        }

        jsonObject.put("items", jsonItems)

        return jsonObject
    }

    @JvmOverloads
    fun toJsonString(projections: Array<String> = arrayOf()): String {
        return toJson(projections).encode()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false

        val itemList = o as ItemList<*>?

        if (count != itemList!!.count) return false
        if (if (etag != null) etag != itemList.etag else itemList.etag != null) return false
        if (if (pageTokens != null) pageTokens != itemList.pageTokens else itemList.pageTokens != null) return false
        return if (items != null) items == itemList.items else itemList.items == null
    }

    override fun hashCode(): Int {
        var result = if (etag != null) etag!!.hashCode() else 0
        result = 31 * result + if (pageTokens != null) pageTokens!!.hashCode() else 0
        result = 31 * result + count
        result = 31 * result + if (items != null) items!!.hashCode() else 0
        return result
    }
}
