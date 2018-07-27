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

package com.nannoq.tools.repository.models

import com.nannoq.tools.repository.dynamodb.DynamoDBRepository.Companion.PAGINATION_INDEX
import com.nannoq.tools.repository.dynamodb.gen.models.TestModel
import com.nannoq.tools.repository.utils.*
import com.nannoq.tools.repository.utils.AggregateFunctions.MAX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

class QueryPackTest {
    @Test
    @Throws(Exception::class)
    fun getBaseEtagKey() {
        val queryPack = QueryPack.builder(TestModel::class.java).build()

        assertNotNull(queryPack.baseEtagKey)
    }

    @Test
    @Throws(Exception::class)
    fun testBaseKey() {
        val queryPack = makePack("NoTag", null)
        val queryPackTwo = makePack("62ecb02196f36ddbb81c72b1513bb81", null)

        assertEquals(queryPack.baseEtagKey, queryPackTwo.baseEtagKey)
    }

    private fun makePack(etag: String, pageToken: String?): QueryPack {
        val queue = ConcurrentLinkedDeque<OrderByParameter>()
        queue.add(OrderByParameter.builder()
                .withField("someLong")
                .build())

        val aggregateFunction = AggregateFunction.builder()
                .withAggregateFunction(MAX)
                .withField("someLong")
                .withGroupBy(listOf(GroupingConfiguration.builder()
                        .withGroupBy("someLong")
                        .withGroupByUnit("INTEGER")
                        .withGroupByRange(10000)
                        .build()))
                .build()

        return QueryPack.builder(TestModel::class.java)
                .withCustomRoute("/parent/testString/testModels")
                .withCustomQuery("")
                .withPageToken(pageToken)
                .withRequestEtag(etag)
                .withOrderByQueue(queue)
                .withFilterParameters(Collections.singletonMap("someBoolean",
                        mutableListOf(FilterParameter.builder("someBoolean")
                                .withEq("true")
                                .build())).toMap())
                .withAggregateFunction(aggregateFunction)
                .withProjections(arrayOf("someStringOne"))
                .withIndexName(PAGINATION_INDEX)
                .withLimit(0)
                .build()
    }
}