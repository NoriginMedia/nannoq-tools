package com.nannoq.tools.web.controllers.gen.models

import com.amazonaws.services.dynamodbv2.datamodeling.*
import com.fasterxml.jackson.annotation.JsonInclude
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository.Companion.PAGINATION_INDEX
import com.nannoq.tools.repository.models.*
import com.nannoq.tools.web.controllers.gen.models.TestModelConverter.fromJson
import io.vertx.codegen.annotations.DataObject
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import java.util.*

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
    private var someStringOne: String? = "default"
    private var someStringTwo: String? = "defaultRange"
    private var someStringThree: String? = null
    private var someStringFour: String? = null
    private var someDate: Date? = null
    private var someDateTwo: Date? = null
    private var someLong: Long? = null
    private var someLongTwo: Long? = null
    private var someInteger: Int? = null
    private var someIntegerTwo: Int? = null
    private var someBoolean: Boolean? = false
    private var someBooleanTwo: Boolean? = null
    private var documents: List<TestDocument>? = null
    override var createdAt: Date? = null
        get() = if (field != null) field!! else Date()
    override var updatedAt: Date? = null
        get() = if (field != null) field!! else Date()
    private var version: Long? = null

    constructor()

    constructor(jsonObject: JsonObject) {
        fromJson(jsonObject, this)

        someDate = if (jsonObject.getLong("someDate") == null) null else Date(jsonObject.getLong("someDate"))
        someDateTwo = if (jsonObject.getLong("someDateTwo") == null) null else Date(jsonObject.getLong("someDateTwo"))
        createdAt = if (jsonObject.getLong("createdAt") == null) null else Date(jsonObject.getLong("createdAt"))
        updatedAt = if (jsonObject.getLong("updatedAt") == null) null else Date(jsonObject.getLong("updatedAt"))
    }

    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    override fun setIdentifiers(identifiers: JsonObject): TestModel {
        hash = identifiers.getString("hash")
        range = identifiers.getString("range") ?: UUID.randomUUID().toString()

        return this
    }

    @DynamoDBHashKey
    fun getSomeStringOne(): String? {
        return someStringOne
    }

    @Fluent
    fun setSomeStringOne(someStringOne: String): TestModel {
        this.someStringOne = someStringOne

        return this
    }

    @DynamoDBRangeKey
    fun getSomeStringTwo(): String? {
        return someStringTwo
    }

    @Fluent
    fun setSomeStringTwo(someStringTwo: String): TestModel {
        this.someStringTwo = someStringTwo

        return this
    }

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "TEST_GSI")
    fun getSomeStringThree(): String? {
        return someStringThree
    }

    @Fluent
    fun setSomeStringThree(someStringThree: String): TestModel {
        this.someStringThree = someStringThree

        return this
    }

    fun getSomeStringFour(): String? {
        return someStringFour
    }

    @Fluent
    fun setSomeStringFour(someStringFour: String): TestModel {
        this.someStringFour = someStringFour

        return this
    }

    @DynamoDBIndexRangeKey(localSecondaryIndexName = PAGINATION_INDEX)
    fun getSomeDate(): Date? {
        return someDate
    }

    @Fluent
    fun setSomeDate(someDate: Date): TestModel {
        this.someDate = someDate

        return this
    }

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "TEST_GSI")
    fun getSomeDateTwo(): Date? {
        return someDateTwo
    }

    @Fluent
    fun setSomeDateTwo(someDateTwo: Date): TestModel {
        this.someDateTwo = someDateTwo

        return this
    }

    fun getSomeLong(): Long? {
        return if (someLong != null) someLong else 0L
    }

    @Fluent
    fun setSomeLong(someLong: Long?): TestModel {
        this.someLong = someLong

        return this
    }

    fun getSomeLongTwo(): Long? {
        return if (someLongTwo != null) someLongTwo else 0L
    }

    @Fluent
    fun setSomeLongTwo(someLongTwo: Long?): TestModel {
        this.someLongTwo = someLongTwo

        return this
    }

    fun getSomeInteger(): Int? {
        return if (someInteger != null) someInteger else 0
    }

    @Fluent
    fun setSomeInteger(someInteger: Int?): TestModel {
        this.someInteger = someInteger

        return this
    }

    fun getSomeIntegerTwo(): Int? {
        return if (someIntegerTwo != null) someIntegerTwo else 0
    }

    @Fluent
    fun setSomeIntegerTwo(someIntegerTwo: Int?): TestModel {
        this.someIntegerTwo = someIntegerTwo

        return this
    }

    fun getSomeBoolean(): Boolean? {
        return if (someBoolean != null) someBoolean else java.lang.Boolean.FALSE
    }

    @Fluent
    fun setSomeBoolean(someBoolean: Boolean?): TestModel {
        this.someBoolean = someBoolean

        return this
    }

    fun getSomeBooleanTwo(): Boolean? {
        return if (someBooleanTwo != null) someBooleanTwo else java.lang.Boolean.FALSE
    }

    @Fluent
    fun setSomeBooleanTwo(someBooleanTwo: Boolean?): TestModel {
        this.someBooleanTwo = someBooleanTwo

        return this
    }

    fun getDocuments(): List<TestDocument>? {
        return documents
    }

    @Fluent
    fun setDocuments(documents: List<TestDocument>): TestModel {
        this.documents = documents

        return this
    }

    @DynamoDBVersionAttribute
    fun getVersion(): Long? {
        return version
    }

    @Fluent
    fun setVersion(version: Long?): TestModel {
        this.version = version

        return this
    }

    override fun generateEtagKeyIdentifier(): String {
        return if (getSomeStringOne() != null && getSomeStringTwo() != null)
            "data_api_testModel_etag_" + getSomeStringOne() + "_" + getSomeStringTwo()
        else
            "NoTestModelEtag"
    }

    override fun setModifiables(newObject: Model): TestModel {
        setSomeBoolean((newObject as TestModel).someBoolean)
        setSomeLong(newObject.someLong)

        return this
    }

    override fun sanitize(): TestModel {
        return this
    }

    override fun validateCreate(): List<ValidationError> {
        return Collections.emptyList()
    }

    override fun validateUpdate(): List<ValidationError> {
        return Collections.emptyList()
    }

    @Fluent
    override fun setCreatedAt(date: Date): TestModel {
        createdAt = date

        return this
    }

    @Fluent
    override fun setUpdatedAt(date: Date): TestModel {
        updatedAt = date

        return this
    }

    @Fluent
    override fun setInitialValues(record: Model): TestModel {
        return this
    }

    override fun toJsonFormat(projections: Array<String>): JsonObject {
        return JsonObject(Json.encode(this))
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val testModel = o as TestModel?

        return Objects.equals(getSomeStringOne(), testModel!!.getSomeStringOne()) &&
                Objects.equals(getSomeStringTwo(), testModel.getSomeStringTwo()) &&
                Objects.equals(getSomeStringThree(), testModel.getSomeStringThree()) &&
                Objects.equals(getSomeStringFour(), testModel.getSomeStringFour()) &&
                Objects.equals(getSomeDate(), testModel.getSomeDate()) &&
                Objects.equals(getSomeDateTwo(), testModel.getSomeDateTwo()) &&
                Objects.equals(getSomeLong(), testModel.getSomeLong()) &&
                Objects.equals(getSomeLongTwo(), testModel.getSomeLongTwo()) &&
                Objects.equals(getSomeInteger(), testModel.getSomeInteger()) &&
                Objects.equals(getSomeIntegerTwo(), testModel.getSomeIntegerTwo()) &&
                Objects.equals(getSomeBoolean(), testModel.getSomeBoolean()) &&
                Objects.equals(getSomeBooleanTwo(), testModel.getSomeBooleanTwo()) &&
                Objects.equals(getDocuments(), testModel.getDocuments()) &&
                Objects.equals(createdAt, testModel.createdAt) &&
                Objects.equals(updatedAt, testModel.updatedAt) &&
                Objects.equals(getVersion(), testModel.getVersion())
    }

    override fun hashCode(): Int {
        return Objects.hash(someStringOne, someStringTwo, someStringThree, someStringFour, someDate, someDateTwo,
                someLong, someLongTwo, someInteger, someIntegerTwo, someBoolean, someBooleanTwo, documents, createdAt,
                updatedAt, version)
    }
}