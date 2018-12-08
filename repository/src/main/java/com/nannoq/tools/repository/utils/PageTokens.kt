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
}