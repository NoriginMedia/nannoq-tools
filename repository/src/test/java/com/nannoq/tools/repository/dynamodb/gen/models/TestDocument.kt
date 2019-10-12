package com.nannoq.tools.repository.dynamodb.gen.models

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute
import com.fasterxml.jackson.annotation.JsonInclude
import com.nannoq.tools.repository.dynamodb.gen.models.TestDocumentConverter.fromJson
import com.nannoq.tools.repository.models.ETagable
import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject

@DynamoDBDocument
@DataObject(generateConverter = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class TestDocument : ETagable {
    override var etag: String? = null
    var someStringOne: String? = null
    var someStringTwo: String? = null
    var someStringThree: String? = null
    var someStringFour: String? = null
    @get:DynamoDBVersionAttribute
    var version: Long? = null

    @Suppress("unused")
    constructor()

    constructor(someStringOne: String?, someStringTwo: String?, someStringThree: String?, someStringFour: String?) {
        this.someStringOne = someStringOne
        this.someStringTwo = someStringTwo
        this.someStringThree = someStringThree
        this.someStringFour = someStringFour
    }

    constructor(jsonObject: JsonObject) {
        fromJson(jsonObject, this)
    }

    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestDocument

        if (someStringOne != other.someStringOne) return false
        if (someStringTwo != other.someStringTwo) return false
        if (someStringThree != other.someStringThree) return false
        if (someStringFour != other.someStringFour) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = someStringOne?.hashCode() ?: 0

        result = 31 * result + (someStringTwo?.hashCode() ?: 0)
        result = 31 * result + (someStringThree?.hashCode() ?: 0)
        result = 31 * result + (someStringFour?.hashCode() ?: 0)
        result = 31 * result + (version?.hashCode() ?: 0)

        return result
    }
}
