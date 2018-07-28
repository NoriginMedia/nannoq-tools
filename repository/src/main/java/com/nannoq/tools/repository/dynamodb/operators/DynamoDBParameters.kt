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

package com.nannoq.tools.repository.dynamodb.operators

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.Condition
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.dynamodb.DynamoDBRepository.Companion.PAGINATION_INDEX
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.DynamoDBModel
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.repository.Repository.Companion.LIMIT_KEY
import com.nannoq.tools.repository.repository.Repository.Companion.MULTIPLE_IDS_KEY
import com.nannoq.tools.repository.repository.Repository.Companion.ORDER_BY_KEY
import com.nannoq.tools.repository.repository.Repository.Companion.PROJECTION_KEY
import com.nannoq.tools.repository.utils.FilterParameter
import com.nannoq.tools.repository.utils.OrderByParameter
import io.vertx.core.json.*
import io.vertx.core.logging.LoggerFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors.joining
import java.util.stream.Collectors.toList
import java.util.stream.IntStream

/**
 * This class is used to define the parameters for various operations for the DynamoDBRepository.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class DynamoDBParameters<E>(private val TYPE: Class<E>, private val db: DynamoDBRepository<E>,
                            private val HASH_IDENTIFIER: String, private val IDENTIFIER: String?,
                            private val PAGINATION_IDENTIFIER: String?)
        where E : ETagable, E : Cacheable, E : DynamoDBModel, E : Model {
    private val paginationIndex: String?
        get() = if (PAGINATION_IDENTIFIER != null && PAGINATION_IDENTIFIER != "") PAGINATION_INDEX else null

    fun buildParameters(queryMap: Map<String, List<String>>,
                        fields: Array<Field>, methods: Array<Method>,
                        errors: JsonObject,
                        params: MutableMap<String, List<FilterParameter>>, limit: IntArray,
                        orderByQueue: Queue<OrderByParameter>, indexName: Array<String>): JsonObject {
        queryMap.keys.forEach { key ->
            when {
                key.equals(LIMIT_KEY, ignoreCase = true) ||
                        key.equals(MULTIPLE_IDS_KEY, ignoreCase = true) ||
                        key.equals(ORDER_BY_KEY, ignoreCase = true) ||
                        key.equals(PROJECTION_KEY, ignoreCase = true) ||
                        db.hasField(fields, key) -> {
                    val values = queryMap[key]

                    when {
                        key.equals(PROJECTION_KEY, ignoreCase = true) ||
                                key.equals(MULTIPLE_IDS_KEY, ignoreCase = true) -> when {
                                    logger.isDebugEnabled -> logger.debug("Ignoring Projection Key...")
                                }
                        else -> when {
                            values == null -> errors.put("field_null", "Value in '$key' cannot be null!")
                            key.equals(ORDER_BY_KEY, ignoreCase = true) -> {
                                var jsonArray: JsonArray

                                try {
                                    jsonArray = JsonArray(values[0])
                                } catch (arrayParseException: Exception) {
                                    jsonArray = JsonArray()
                                    jsonArray.add(JsonObject(values[0]))
                                }

                                val orderByParamCount = intArrayOf(0)

                                when {
                                    jsonArray.size() == 1 -> jsonArray.stream().forEach { orderByParam ->
                                        val orderByParameter = Json.decodeValue<OrderByParameter>(orderByParam.toString(), OrderByParameter::class.java)

                                        when {
                                            orderByParameter.isValid -> {
                                                Arrays.stream(methods).forEach { method ->
                                                    val indexRangeKey = method.getDeclaredAnnotation<DynamoDBIndexRangeKey>(DynamoDBIndexRangeKey::class.java)

                                                    if (indexRangeKey != null && stripGet(method.name) == orderByParameter.field) {
                                                        indexName[0] = indexRangeKey.localSecondaryIndexName
                                                        orderByQueue.add(orderByParameter)
                                                    }
                                                }

                                                if (indexName.isEmpty() || orderByQueue.isEmpty()) {
                                                    errors.put("orderBy_parameter_" + orderByParamCount[0] + "_error",
                                                            "This is not a valid remoteIndex!")
                                                }
                                            }
                                            else -> errors.put("orderBy_parameter_" + orderByParamCount[0] + "_error",
                                                    "Field cannot be null!")
                                        }

                                        orderByParamCount[0]++
                                    }
                                    else -> errors.put("orderBy_limit_error", "You must and may only order by a single remoteIndex!")
                                }
                            }
                            key.equals(LIMIT_KEY, ignoreCase = true) -> try {
                                if (logger.isDebugEnabled) {
                                    logger.debug("Parsing limit..")
                                }

                                limit[0] = Integer.parseInt(values[0])

                                if (logger.isDebugEnabled) {
                                    logger.debug("Limit is: " + limit[0])
                                }

                                when {
                                    limit[0] < 1 -> errors.put(key + "_negative_error", "Limit must be a whole positive Integer!")
                                    limit[0] > 100 -> errors.put(key + "_exceed_max_error", "Maximum limit is 100!")
                                }
                            } catch (nfe: NumberFormatException) {
                                errors.put(key + "_error", "Limit must be a whole positive Integer!")
                            }
                            else -> try {
                                db.parseParam(TYPE, values[0], key, params, errors)
                            } catch (e: Exception) {
                                if (logger.isDebugEnabled) {
                                    logger.debug("Could not parse filterParams as a JsonObject, attempting array...", e)
                                }

                                try {
                                    val jsonArray = JsonArray(values[0])

                                    jsonArray.forEach { jsonObjectAsString -> db.parseParam(TYPE, jsonObjectAsString.toString(), key, params, errors) }
                                } catch (arrayException: Exception) {
                                    logger.error("Unable to parse json as array:" + values[0])

                                    errors.put(key + "_value_json_error", "Unable to parse this json...")
                                }
                            }
                        }
                    }
                }
                else -> errors.put(key + "_field_error", "This field does not exist on the selected resource.")
            }
        }

        return errors
    }

    private fun stripGet(string: String): String {
        val newString = string.replace("get", "")
        val c = newString.toCharArray()
        c[0] = c[0] + 32

        return String(c)
    }

    internal fun applyParameters(peek: OrderByParameter?,
                                 params: Map<String, List<FilterParameter>>?): DynamoDBQueryExpression<E> {
        val filterExpression = DynamoDBQueryExpression<E>()

        if (params != null) {
            val keyConditionString = arrayOf("")
            val ean = HashMap<String, String>()
            val eav = HashMap<String, AttributeValue>()
            val paramCount = intArrayOf(0)
            val count = intArrayOf(0)
            val paramSize = params.keys.size

            params.keys.stream().map<List<FilterParameter>>({ params[it] }).forEach { paramList ->
                val orderCounter = intArrayOf(0)
                keyConditionString[0] += "("

                when {
                    paramList.size > 1 && isRangeKey(peek, paramList[0]) &&
                            !isIllegalRangedKeyQueryParams(paramList) ->
                        buildMultipleRangeKeyCondition(filterExpression, params.size, paramList,
                                if (peek != null) peek.field else paramList[0].field)
                    else -> {
                        paramList.forEach { param ->
                            val fieldName = if (param.field!![param.field!!.length - 2] == '_')
                                param.field!!.substring(0, param.field!!.length - 2)
                            else
                                param.field
                            if (fieldName != null) ean["#name" + count[0]] = fieldName

                            when {
                                param.isEq -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.EQ, param.type,
                                            db.createAttributeValue(fieldName, param.eq!!.toString()))
                                } else {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.eq!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " = :val" + curCount
                                }
                                param.isNe -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.NE, param.type,
                                            db.createAttributeValue(fieldName, param.eq!!.toString()))
                                } else {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.ne!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " <> :val" + curCount
                                }
                                param.isBetween -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.BETWEEN, param.type,
                                            db.createAttributeValue(fieldName, param.gt!!.toString()),
                                            db.createAttributeValue(fieldName, param.lt!!.toString()))
                                } else {
                                    val curCount = count[0]
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.gt!!.toString())
                                    eav[":val" + (curCount + 1)] = db.createAttributeValue(fieldName, param.lt!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " > :val" + curCount + " and " +
                                            " #name" + curCount + " < " + " :val" + (curCount + 1)

                                    count[0] = curCount + 2
                                }
                                param.isGt -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.GT, param.type,
                                            db.createAttributeValue(fieldName, param.gt!!.toString()))
                                } else {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.gt!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " > :val" + curCount
                                }
                                param.isLt -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.LT, param.type,
                                            db.createAttributeValue(fieldName, param.lt!!.toString()))
                                } else {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.lt!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " < :val" + curCount
                                }
                                param.isInclusiveBetween -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.BETWEEN, param.type,
                                            db.createAttributeValue(fieldName, param.ge!!.toString(), ComparisonOperator.GE),
                                            db.createAttributeValue(fieldName, param.le!!.toString(), ComparisonOperator.LE))
                                } else {
                                    val curCount = count[0]
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.ge!!.toString())
                                    eav[":val" + (curCount + 1)] = db.createAttributeValue(fieldName, param.le!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " >= :val" + curCount + " and " +
                                            " #name" + curCount + " =< " + " :val" + (curCount + 1)

                                    count[0] = curCount + 2
                                }
                                param.isGeLtVariableBetween -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.BETWEEN, param.type,
                                            db.createAttributeValue(fieldName, param.ge!!.toString(), ComparisonOperator.GE),
                                            db.createAttributeValue(fieldName, param.lt!!.toString()))
                                } else {
                                    val curCount = count[0]
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.ge!!.toString())
                                    eav[":val" + (curCount + 1)] = db.createAttributeValue(fieldName, param.lt!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " >= :val" + curCount + " and " +
                                            " #name" + curCount + " < " + " :val" + (curCount + 1)

                                    count[0] = curCount + 2
                                }
                                param.isLeGtVariableBetween -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.BETWEEN, param.type,
                                            db.createAttributeValue(fieldName, param.gt!!.toString()),
                                            db.createAttributeValue(fieldName, param.le!!.toString(), ComparisonOperator.LE))
                                } else {
                                    val curCount = count[0]
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.le!!.toString())
                                    eav[":val" + (curCount + 1)] = db.createAttributeValue(fieldName, param.gt!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " <= :val" + curCount + " and " +
                                            " #name" + curCount + " > " + " :val" + (curCount + 1)

                                    count[0] = curCount + 2
                                }
                                param.isGe -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.GE, param.type,
                                            db.createAttributeValue(fieldName, param.ge!!.toString()))
                                } else {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.ge!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " >= :val" + curCount
                                }
                                param.isLe -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.LE, param.type,
                                            db.createAttributeValue(fieldName, param.le!!.toString()))
                                } else {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.le!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " <= :val" + curCount
                                }
                                param.isContains -> {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.contains!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") +
                                            "contains(" + "#name" + curCount + ", :val" + curCount + ")"
                                }
                                param.isNotContains -> {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.notContains!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") +
                                            "not contains(" + "#name" + curCount + ", :val" + curCount + ")"
                                }
                                param.isBeginsWith -> if (isRangeKey(peek, fieldName, param)) {
                                    buildRangeKeyCondition(filterExpression, params.size,
                                            if (peek != null) peek.field else param.field,
                                            ComparisonOperator.BEGINS_WITH, param.type,
                                            db.createAttributeValue(fieldName, param.beginsWith!!.toString()))
                                } else {
                                    val curCount = count[0]++
                                    eav[":val$curCount"] = db.createAttributeValue(fieldName, param.beginsWith!!.toString())

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") +
                                            "begins_with(" + "#name" + curCount + ", :val" + curCount + ")"
                                }
                                param.isIn -> {
                                    val inList = inQueryToStringChain(fieldName!!, param.`in`)

                                    val keys = ConcurrentLinkedQueue<String>()
                                    val valCounter = AtomicInteger()
                                    inList.l.forEach { av ->
                                        val currentValKey = ":inVal" + valCounter.getAndIncrement()
                                        keys.add(currentValKey)
                                        eav[currentValKey] = av
                                    }

                                    val curCount = count[0]++

                                    keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                            curCount + " IN (" + keys.stream().collect(joining(", ")) + ")"
                                }
                            }

                            orderCounter[0]++
                        }

                        when {
                            keyConditionString[0].endsWith("(") -> when {
                                keyConditionString[0].equals("(", ignoreCase = true) -> keyConditionString[0] = ""
                                else -> keyConditionString[0] = keyConditionString[0].substring(0, keyConditionString[0].lastIndexOf(")"))
                            }
                            else -> {
                                keyConditionString[0] += ")"

                                if (paramSize > 1 && paramCount[0] < paramSize - 1) {
                                    keyConditionString[0] += " AND "
                                }
                            }
                        }

                        paramCount[0]++
                    }
                }
            }

            if (ean.size > 0 && eav.size > 0) {
                filterExpression.withFilterExpression(keyConditionString[0])
                        .withExpressionAttributeNames(ean)
                        .withExpressionAttributeValues(eav)
            }

            if (logger.isDebugEnabled) {
                logger.debug("RANGE KEY EXPRESSION IS: " + filterExpression.rangeKeyConditions)
                logger.debug("FILTER EXPRESSION IS: " + filterExpression.filterExpression)
                logger.debug("NAMES: " + Json.encodePrettily(ean))
                logger.debug("Values: " + Json.encodePrettily(eav))
            }
        }

        return filterExpression
    }

    private fun inQueryToStringChain(fieldName: String, `in`: Array<Any>?): AttributeValue {
        return AttributeValue().withL(Arrays.stream(`in`!!)
                .map { o -> db.createAttributeValue(fieldName, o.toString()) }
                .collect(toList()).toList())
    }

    private fun isRangeKey(peek: OrderByParameter?, param: FilterParameter): Boolean {
        return isRangeKey(peek, if (param.field!![param.field!!.length - 2] == '_')
            param.field!!.substring(0, param.field!!.length - 2)
        else
            param.field, param)
    }

    private fun isRangeKey(peek: OrderByParameter?, fieldName: String?, param: FilterParameter): Boolean {
        return peek != null && peek.field!!.equals(fieldName!!, ignoreCase = true) || peek == null && param.field!!.equals(PAGINATION_IDENTIFIER!!, ignoreCase = true)
    }

    internal fun applyParameters(params: Map<String, List<FilterParameter>>?): DynamoDBScanExpression {
        val filterExpression = DynamoDBScanExpression()

        if (params != null) {
            val keyConditionString = arrayOf("")
            val ean = HashMap<String, String>()
            val eav = HashMap<String, AttributeValue>()
            val paramCount = intArrayOf(0)
            val count = intArrayOf(0)
            val paramSize = params.keys.size

            params.keys.stream().map<List<FilterParameter>>({ params[it] }).forEach { paramList ->
                keyConditionString[0] += "("
                val orderCounter = intArrayOf(0)

                paramList.forEach { param ->
                    val fieldName = if (param.field!![param.field!!.length - 2] == '_')
                        param.field!!.substring(0, param.field!!.length - 2)
                    else
                        param.field
                    if (fieldName != null) ean["#name" + count[0]] = fieldName

                    when {
                        param.isEq -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.eq!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " = :val" + curCount
                        }
                        param.isNe -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.ne!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " <> :val" + curCount
                        }
                        param.isBetween -> {
                            val curCount = count[0]
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.gt!!.toString())
                            eav[":val" + (curCount + 1)] = db.createAttributeValue(fieldName, param.lt!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " > :val" + curCount + " and " +
                                    " #name" + curCount + " < " + " :val" + (curCount + 1)

                            count[0] = curCount + 2
                        }
                        param.isGt -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.gt!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " > :val" + curCount
                        }
                        param.isLt -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.lt!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " < :val" + curCount
                        }
                        param.isInclusiveBetween -> {
                            val curCount = count[0]
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.ge!!.toString())
                            eav[":val" + (curCount + 1)] = db.createAttributeValue(fieldName, param.le!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " >= :val" + curCount + " and " +
                                    " #name" + curCount + " =< " + " :val" + (curCount + 1)

                            count[0] = curCount + 2
                        }
                        param.isGeLtVariableBetween -> {
                            val curCount = count[0]
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.ge!!.toString())
                            eav[":val" + (curCount + 1)] = db.createAttributeValue(fieldName, param.lt!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " >= :val" + curCount + " and " +
                                    " #name" + curCount + " < " + " :val" + (curCount + 1)

                            count[0] = curCount + 2
                        }
                        param.isLeGtVariableBetween -> {
                            val curCount = count[0]
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.le!!.toString())
                            eav[":val" + (curCount + 1)] = db.createAttributeValue(fieldName, param.gt!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " <= :val" + curCount + " and " +
                                    " #name" + curCount + " > " + " :val" + (curCount + 1)

                            count[0] = curCount + 2
                        }
                        param.isGe -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.ge!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " >= :val" + curCount
                        }
                        param.isLe -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.le!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " <= :val" + curCount
                        }
                        param.isContains -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.contains!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") +
                                    "contains(" + "#name" + curCount + ", :val" + curCount + ")"
                        }
                        param.isNotContains -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.notContains!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") +
                                    "not contains(" + "#name" + curCount + ", :val" + curCount + ")"
                        }
                        param.isBeginsWith -> {
                            val curCount = count[0]++
                            eav[":val$curCount"] = db.createAttributeValue(fieldName, param.beginsWith!!.toString())

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") +
                                    "begins_with(" + "#name" + curCount + ", :val" + curCount + ")"
                        }
                        param.isIn -> {
                            val inList = inQueryToStringChain(fieldName!!, param.`in`)

                            val keys = ConcurrentLinkedQueue<String>()
                            val valCounter = AtomicInteger()
                            inList.l.forEach { av ->
                                val currentValKey = ":inVal" + valCounter.getAndIncrement()
                                keys.add(currentValKey)
                                eav[currentValKey] = av
                            }

                            val curCount = count[0]++

                            keyConditionString[0] += (if (orderCounter[0] == 0) "" else " " + param.type + " ") + "#name" +
                                    curCount + " IN (" + keys.stream().collect(joining(", ")) + ")"
                        }
                    }

                    orderCounter[0]++
                }

                keyConditionString[0] += ")"

                if (paramSize > 1 && paramCount[0] < paramSize - 1) {
                    keyConditionString[0] += " AND "
                }

                paramCount[0]++
            }

            if (ean.size > 0 && eav.size > 0) {
                filterExpression.withFilterExpression(keyConditionString[0])
                        .withExpressionAttributeNames(ean)
                        .withExpressionAttributeValues(eav)
            }

            if (logger.isDebugEnabled) {
                logger.debug("SCAN EXPRESSION IS: " + filterExpression.filterExpression)
            }
            if (logger.isDebugEnabled) {
                logger.debug("NAMES: " + Json.encodePrettily(ean))
            }
            if (logger.isDebugEnabled) {
                logger.debug("Values: " + Json.encodePrettily(eav))
            }
        }

        return filterExpression
    }

    private fun buildRangeKeyConditionForMultipleIds(filterExpression: DynamoDBQueryExpression<E>,
                                                     field: String, ids: List<String>) {
        val idsAsAttributeValues = ids.stream()
                .map { id -> AttributeValue().withS(id) }
                .collect(toList())

        filterExpression.withRangeKeyCondition(field, Condition()
                .withComparisonOperator(ComparisonOperator.IN)
                .withAttributeValueList(idsAsAttributeValues))
    }

    private fun buildRangeKeyCondition(filterExpression: DynamoDBQueryExpression<E>, count: Int,
                                       field: String?, comparator: ComparisonOperator, type: String?,
                                       vararg attributeValues: AttributeValue) {
        filterExpression.withRangeKeyCondition(field, Condition()
                .withComparisonOperator(comparator)
                .withAttributeValueList(*attributeValues))

        if (count > 1) filterExpression.withConditionalOperator(type)
    }

    private fun buildMultipleRangeKeyCondition(filterExpression: DynamoDBQueryExpression<E>, count: Int,
                                               paramList: List<FilterParameter>,
                                               rangeKeyName: String?) {
        if (paramList.size > 2) throw IllegalArgumentException("Cannot query on more than two params on a range key!")

        val paramOne = paramList[0]
        val paramTwo = paramList[1]
        val condition = Condition()

        when {
            paramOne.isGt && paramTwo.isLt -> condition.withComparisonOperator(ComparisonOperator.BETWEEN)
                    .withAttributeValueList(
                            db.createAttributeValue(paramOne.field, paramOne.gt!!.toString()),
                            db.createAttributeValue(paramTwo.field, paramTwo.lt!!.toString()))
            paramOne.isLt && paramTwo.isGt -> condition.withComparisonOperator(ComparisonOperator.BETWEEN)
                    .withAttributeValueList(
                            db.createAttributeValue(paramTwo.field, paramTwo.gt!!.toString()),
                            db.createAttributeValue(paramOne.field, paramOne.lt!!.toString()))
            paramOne.isLe && paramTwo.isGe -> condition.withComparisonOperator(ComparisonOperator.BETWEEN)
                    .withAttributeValueList(
                            db.createAttributeValue(paramTwo.field, paramTwo.ge!!.toString()),
                            db.createAttributeValue(paramOne.field, paramOne.le!!.toString()))
            paramOne.isGe && paramTwo.isLe -> condition.withComparisonOperator(ComparisonOperator.BETWEEN)
                    .withAttributeValueList(
                            db.createAttributeValue(paramOne.field, paramOne.ge!!.toString()),
                            db.createAttributeValue(paramTwo.field, paramTwo.le!!.toString()))
            paramOne.isGe && paramTwo.isLt -> condition.withComparisonOperator(ComparisonOperator.BETWEEN)
                    .withAttributeValueList(
                            db.createAttributeValue(paramOne.field, paramOne.ge!!.toString()),
                            db.createAttributeValue(paramTwo.field, paramTwo.lt!!.toString()))
            paramOne.isLt && paramTwo.isGe -> condition.withComparisonOperator(ComparisonOperator.BETWEEN)
                    .withAttributeValueList(
                            db.createAttributeValue(paramTwo.field, paramTwo.ge!!.toString()),
                            db.createAttributeValue(paramOne.field, paramOne.lt!!.toString()))
            paramOne.isLe && paramTwo.isGt -> condition.withComparisonOperator(ComparisonOperator.BETWEEN)
                    .withAttributeValueList(
                            db.createAttributeValue(paramTwo.field, paramTwo.gt!!.toString()),
                            db.createAttributeValue(paramOne.field, paramOne.le!!.toString()))
            paramOne.isGt && paramTwo.isLe -> condition.withComparisonOperator(ComparisonOperator.BETWEEN)
                    .withAttributeValueList(
                            db.createAttributeValue(paramOne.field, paramOne.gt!!.toString()),
                            db.createAttributeValue(paramTwo.field, paramTwo.le!!.toString()))
            else -> throw IllegalArgumentException("This is an invalid query!")
        }

        filterExpression.withRangeKeyCondition(rangeKeyName, condition)

        if (count > 1) filterExpression.withConditionalOperator("AND")
    }

    internal fun applyOrderBy(orderByQueue: Queue<OrderByParameter>?, GSI: String?, indexName: String?,
                              filterExpression: DynamoDBQueryExpression<E>): DynamoDBQueryExpression<E> {
        when {
            orderByQueue == null || orderByQueue.size == 0 -> {
                when {
                    GSI != null -> filterExpression.setIndexName(GSI)
                    else -> filterExpression.setIndexName(paginationIndex)
                }

                filterExpression.setScanIndexForward(false)
            }
            else -> orderByQueue.forEach { orderByParameter ->
                when {
                    GSI != null -> filterExpression.setIndexName(GSI)
                    else -> filterExpression.setIndexName(indexName)
                }

                filterExpression.setScanIndexForward(orderByParameter.isAsc)
            }
        }

        return filterExpression
    }

    internal fun isIllegalRangedKeyQueryParams(nameParams: List<FilterParameter>): Boolean {
        return nameParams.stream()
                .anyMatch({ it.isIllegalRangedKeyParam })
    }

    internal fun buildProjections(projections: Array<String>?, indexName: String): Array<String>? {
        var projections = projections
        if (projections == null || projections.isEmpty()) return projections
        val finalProjections = projections

        if (Arrays.stream(finalProjections).noneMatch { p -> p.equals(HASH_IDENTIFIER, ignoreCase = true) }) {
            val newProjectionArray = arrayOfNulls<String>(finalProjections.size + 1)
            IntStream.range(0, finalProjections.size).forEach { i -> newProjectionArray[i] = finalProjections[i] }
            newProjectionArray[finalProjections.size] = HASH_IDENTIFIER
            projections = newProjectionArray.requireNoNulls()
        }

        val finalProjections2 = projections

        if (db.hasRangeKey()) {
            if (Arrays.stream(finalProjections2).noneMatch { p -> p.equals(IDENTIFIER, ignoreCase = true) }) {
                val newProjectionArray = arrayOfNulls<String>(finalProjections2.size + 1)
                IntStream.range(0, finalProjections2.size).forEach { i -> newProjectionArray[i] = finalProjections2[i] }
                newProjectionArray[finalProjections2.size] = IDENTIFIER
                projections = newProjectionArray.requireNoNulls()
            }
        }

        val finalProjections3 = projections

        if (Arrays.stream(finalProjections3).noneMatch { p -> p.equals(indexName, ignoreCase = true) }) {
            val newProjectionArray = arrayOfNulls<String>(finalProjections3.size + 1)
            IntStream.range(0, finalProjections3.size).forEach { i -> newProjectionArray[i] = finalProjections3[i] }
            newProjectionArray[finalProjections3.size] = indexName
            projections = newProjectionArray.requireNoNulls()
        }

        return projections
    }

    internal fun createPageTokenMap(encodedPageToken: String?,
                                    hashIdentifier: String,
                                    rangeIdentifier: String?,
                                    paginationIdentifier: String?,
                                    GSI: String?,
                                    GSI_KEY_MAP: Map<String, JsonObject>): Map<String, AttributeValue>? {
        var pageTokenMap: MutableMap<String, AttributeValue>? = null
        var pageToken: JsonObject? = null

        if (encodedPageToken != null) {
            pageToken = try {
                JsonObject(String(Base64.getUrlDecoder().decode(encodedPageToken)))
            } catch (e: EncodeException) {
                null
            } catch (e: DecodeException) {
                null
            }

        }

        if (pageToken != null) {
            pageTokenMap = HashMap()

            if (pageToken.getString("hash") != null) {
                val hashValue = AttributeValue().withS(pageToken.getString("hash"))
                (pageTokenMap as MutableMap<String, AttributeValue>).putIfAbsent(hashIdentifier, hashValue)
            }

            if (rangeIdentifier != null && rangeIdentifier != "" && pageToken.getString("range") != null) {
                val rangeValue = AttributeValue().withS(pageToken.getString("range"))
                (pageTokenMap as MutableMap<String, AttributeValue>).putIfAbsent(rangeIdentifier, rangeValue)
            }

            if (paginationIdentifier != null && paginationIdentifier != "" && pageToken.getString("indexValue") != null) {
                val pageValue = db.createAttributeValue(paginationIdentifier, pageToken.getString("indexValue"))
                (pageTokenMap as MutableMap<String, AttributeValue>).putIfAbsent(paginationIdentifier, pageValue)
            }

            if (GSI != null) {
                val keyObject = GSI_KEY_MAP[GSI]
                val hash = keyObject?.getString("hash")
                val range = keyObject?.getString("range")

                if (pageToken.getString("GSIH") != null) {
                    val gsiHash = AttributeValue().withS(pageToken.getString("GSIH"))
                    (pageTokenMap as MutableMap<String, AttributeValue>).putIfAbsent(hash!!, gsiHash)
                }

                if (range != null && pageToken.getString("GSIR") != null) {
                    val gsiRange = AttributeValue().withS(pageToken.getString("GSIR"))
                    (pageTokenMap as MutableMap<String, AttributeValue>).putIfAbsent(range, gsiRange)
                }
            }
        }

        if (logger.isDebugEnabled) {
            logger.debug("PageTokenMap is: " + Json.encodePrettily(pageTokenMap))
        }

        return pageTokenMap
    }

    internal fun createNewPageToken(hashIdentifier: String, identifier: String, index: String?,
                                    lastEvaluatedKey: Map<String, AttributeValue>, GSI: String?,
                                    GSI_KEY_MAP: Map<String, JsonObject>, alternateIndex: String?): String {
        if (logger.isDebugEnabled) {
            logger.debug("Last key is: $lastEvaluatedKey")
        }

        val pageToken = JsonObject()
        val indexValue: Any? = when {
            index == null && alternateIndex == null -> null
            else -> extractIndexValue(lastEvaluatedKey[alternateIndex ?: index])
        }

        if (logger.isDebugEnabled) {
            logger.debug("Index value is: " + indexValue!!)
        }

        val identifierValue = lastEvaluatedKey[identifier]
        pageToken.put("hash", lastEvaluatedKey[hashIdentifier]?.s)
        if (identifierValue != null) pageToken.put("range", identifierValue.s)
        if (indexValue != null) pageToken.put("indexValue", indexValue)

        if (GSI != null) {
            val keyObject = GSI_KEY_MAP[GSI]
            val hash = keyObject?.getString("hash")
            val range = keyObject?.getString("range")

            pageToken.put("GSIH", lastEvaluatedKey[hash]?.s)

            if (range != null && lastEvaluatedKey[range] != null) {
                pageToken.put("GSIR", lastEvaluatedKey[range]?.s)
            }
        }

        if (logger.isDebugEnabled) {
            logger.debug("PageToken is: " + pageToken.encodePrettily())
        }

        return Base64.getUrlEncoder().encodeToString(pageToken.encode().toByteArray())
    }

    private fun extractIndexValue(attributeValue: AttributeValue?): Any? {
        if (attributeValue == null) return null

        return when {
            attributeValue.s != null -> attributeValue.s
            attributeValue.bool != null -> attributeValue.bool
            attributeValue.n != null -> attributeValue.n
            else -> throw UnknownError("Cannot find indexvalue!")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DynamoDBParameters::class.java.simpleName)
    }
}
