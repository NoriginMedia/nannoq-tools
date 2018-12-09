package com.nannoq.tools.repository.utils

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject

@DataObject(generateConverter = true)
class ItemListMeta(val etag: String? = null, val count: Int? = null, val totalCount: Int? = null) {

    constructor(jsonObject: JsonObject) : this(
            etag = jsonObject.getString("etag"),
            count = jsonObject.getInteger("count"),
            totalCount = jsonObject.getInteger("totalCount")
    )

    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ItemListMeta

        if (etag != other.etag) return false
        if (count != other.count) return false
        if (totalCount != other.totalCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = etag?.hashCode() ?: 0
        result = 31 * result + (count ?: 0)
        result = 31 * result + (totalCount ?: 0)
        return result
    }
}
