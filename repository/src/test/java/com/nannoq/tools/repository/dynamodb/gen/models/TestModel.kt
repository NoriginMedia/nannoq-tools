package com.nannoq.tools.repository.dynamodb.gen.models

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute
import com.fasterxml.jackson.annotation.JsonInclude
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository.Companion.PAGINATION_INDEX
import com.nannoq.tools.repository.dynamodb.gen.models.TestModelConverter.fromJson
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.DynamoDBModel
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import java.util.Date

@DynamoDBTable(tableName = "testModels")
@DataObject(generateConverter = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class TestModel : DynamoDBModel, Model, ETagable, Cacheable {
    override var hash: String?
        get() {
            return someStringOne
        }
        set(value) {
            this.someStringOne = value
        }

    override var range: String?
        get() {
            return someStringTwo
        }
        set(value) {
            this.someStringTwo = value
        }

    override var etag: String? = null
    var someStringOne: String? = "default"
        @DynamoDBHashKey
        get() = field
    var someStringTwo: String? = "defaultRange"
        @DynamoDBRangeKey
        get() = field
    var someStringThree: String? = null
        @DynamoDBIndexHashKey(globalSecondaryIndexName = "TEST_GSI")
        get() = field
    var someStringFour: String? = null
    var someDate: Date? = null
        @DynamoDBIndexRangeKey(localSecondaryIndexName = PAGINATION_INDEX)
        get() = field
    var someDateTwo: Date? = null
        @DynamoDBIndexRangeKey(globalSecondaryIndexName = "TEST_GSI")
        get() = field
    var someLong: Long? = null
    var someLongTwo: Long? = null
    var someInteger: Int? = null
    var someIntegerTwo: Int? = null
    var someBoolean: Boolean? = null
    var someBooleanTwo: Boolean? = null
    var documents: List<TestDocument>? = null
    override var createdAt: Date? = null
        get() = if (field != null) field!! else Date()
    override var updatedAt: Date? = null
        get() = if (field != null) field!! else Date()
    var version: Long? = null
        @DynamoDBVersionAttribute
        get() = field

    constructor()

    constructor(someStringOne: String?, someStringTwo: String?, someStringThree: String?, someStringFour: String?, someDate: Date?, someDateTwo: Date?, someLong: Long?, someLongTwo: Long?, someInteger: Int?, someIntegerTwo: Int?, someBoolean: Boolean?, someBooleanTwo: Boolean?, documents: List<TestDocument>?) {
        this.someStringOne = someStringOne
        this.someStringTwo = someStringTwo
        this.someStringThree = someStringThree
        this.someStringFour = someStringFour
        this.someDate = someDate
        this.someDateTwo = someDateTwo
        this.someLong = someLong
        this.someLongTwo = someLongTwo
        this.someInteger = someInteger
        this.someIntegerTwo = someIntegerTwo
        this.someBoolean = someBoolean
        this.someBooleanTwo = someBooleanTwo
        this.documents = documents
    }

    constructor(jsonObject: JsonObject) {
        fromJson(jsonObject, this)

        someDate = if (jsonObject.getLong("someDate") == null) null else Date(jsonObject.getLong("someDate"))
        someDateTwo = if (jsonObject.getLong("someDateTwo") == null) null else Date(jsonObject.getLong("someDateTwo"))
        createdAt = if (jsonObject.getLong("createdAt") == null) null else Date(jsonObject.getLong("createdAt"))
        updatedAt = if (jsonObject.getLong("updatedAt") == null) null else Date(jsonObject.getLong("updatedAt"))
    }

    override fun setIdentifiers(identifiers: JsonObject): TestModel {
        hash = identifiers.getString("hash")
        range = identifiers.getString("range")

        return this
    }

    override fun setModifiables(newObject: Model): TestModel {
        someBoolean = ((newObject as TestModel).someBoolean)

        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestModel

        if (someStringFour != other.someStringFour) return false
        if (someLong != other.someLong) return false
        if (someLongTwo != other.someLongTwo) return false
        if (someInteger != other.someInteger) return false
        if (someIntegerTwo != other.someIntegerTwo) return false
        if (someBoolean != other.someBoolean) return false
        if (someBooleanTwo != other.someBooleanTwo) return false
        if (documents != other.documents) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = someStringFour?.hashCode() ?: 0

        result = 31 * result + (someLong?.hashCode() ?: 0)
        result = 31 * result + (someLongTwo?.hashCode() ?: 0)
        result = 31 * result + (someInteger ?: 0)
        result = 31 * result + (someIntegerTwo ?: 0)
        result = 31 * result + (someBoolean?.hashCode() ?: 0)
        result = 31 * result + (someBooleanTwo?.hashCode() ?: 0)
        result = 31 * result + (documents?.hashCode() ?: 0)
        result = 31 * result + (createdAt?.hashCode() ?: 0)
        result = 31 * result + (updatedAt?.hashCode() ?: 0)

        return result
    }
}