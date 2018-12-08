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
import com.nannoq.tools.repository.models.ModelUtils
import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.stream.Collectors.toList

/**
 * This class defines a generic list for items. Used for aggregation.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@DataObject(generateConverter = true)
class GenericItemList {
    var etag: String? = null
    var pageToken: PageTokens? = null
    var count: Int = 0
    var items: MutableList<JsonObject>? = null

    constructor()

    constructor(jsonObject: JsonObject) {
        this.etag = jsonObject.getString("etag")
        this.pageToken = PageTokens(jsonObject.getJsonObject("pageTokens"))
        this.count = jsonObject.getInteger("count")!!
        this.items = jsonObject.getJsonArray("items").stream()
                .map { e -> e as JsonObject }
                .collect(toList())
    }

    constructor(pageTokens: PageTokens, count: Int, items: List<JsonObject>?) {
        this.pageToken = pageTokens
        this.count = count
        this.items = items?.toMutableList()
        val etagCode = longArrayOf(1234567890L)
        items?.forEach { item -> etagCode[0] = etagCode[0] xor item.encode().hashCode().toLong() }
        etag = ModelUtils.returnNewEtag(etagCode[0])
    }

    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    fun toJson(projections: Array<String>): JsonObject {
        val jsonObject = JsonObject()
                .put("etag", if (etag == null) "NoTag" else etag)
                .put("pageTokens", if (pageToken == null) "END_OF_LIST" else pageToken)
                .put("count", count)

        val jsonItems = JsonArray()

        if (items != null) {
            items!!.forEach({ jsonItems.add(it) })
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

        val itemList = o as GenericItemList?

        if (count != itemList!!.count) return false
        if (if (etag != null) etag != itemList.etag else itemList.etag != null) return false
        if (if (pageToken != null) pageToken != itemList.pageToken else itemList.pageToken != null) return false
        return if (items != null) items == itemList.items else itemList.items == null
    }

    override fun hashCode(): Int {
        var result = if (etag != null) etag!!.hashCode() else 0
        result = 31 * result + if (pageToken != null) pageToken!!.hashCode() else 0
        result = 31 * result + count
        result = 31 * result + if (items != null) items!!.hashCode() else 0
        return result
    }
}
