package com.nannoq.tools.repository.utils

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject

@DataObject(generateConverter = true)
class PageTokens(val self: String? = null, val next: String = "END_OF_LIST", val previous: String? = null) {

    constructor(jsonObject: JsonObject) : this(
            self = jsonObject.getString("self"),
            next = jsonObject.getString("next"),
            previous = jsonObject.getString("previous")
    )

    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PageTokens

        if (self != other.self) return false
        if (next != other.next) return false
        if (previous != other.previous) return false

        return true
    }

    override fun hashCode(): Int {
        var result = self?.hashCode() ?: 0
        result = 31 * result + next.hashCode()
        result = 31 * result + (previous?.hashCode() ?: 0)
        return result
    }
}
