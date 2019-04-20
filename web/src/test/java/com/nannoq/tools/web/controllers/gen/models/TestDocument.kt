package com.nannoq.tools.web.controllers.gen.models

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute
import com.fasterxml.jackson.annotation.JsonInclude
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.web.controllers.gen.models.TestDocumentConverter.fromJson
import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import java.util.Objects

@DynamoDBDocument
@DataObject(generateConverter = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class TestDocument : ETagable {
    override var etag: String? = null
    private var someStringOne: String? = null
    private var someStringTwo: String? = null
    private var someStringThree: String? = null
    private var someStringFour: String? = null
    @get:DynamoDBVersionAttribute
    var version: Long? = null

    @Suppress("unused")
    constructor()

    @Suppress("unused")
    constructor(jsonObject: JsonObject) {
        fromJson(jsonObject, this)
    }

    @Suppress("unused")
    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    override fun generateEtagKeyIdentifier(): String {
        return if (someStringOne != null) "data_api_testDocument_etag_" + someStringOne!! else "NoDocumentTag"
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as TestDocument?

        return Objects.equals(someStringOne, that!!.someStringOne) &&
                Objects.equals(someStringTwo, that.someStringTwo) &&
                Objects.equals(someStringThree, that.someStringThree) &&
                Objects.equals(someStringFour, that.someStringFour) &&
                Objects.equals(version, that.version)
    }

    override fun hashCode(): Int {
        return Objects.hash(someStringOne, someStringTwo, someStringThree, someStringFour, version)
    }
}