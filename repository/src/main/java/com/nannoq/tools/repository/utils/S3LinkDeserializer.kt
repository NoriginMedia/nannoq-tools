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

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.S3ClientCache
import com.amazonaws.services.dynamodbv2.datamodeling.S3Link
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import io.vertx.core.json.JsonObject
import java.io.IOException

/**
 * This class defines an deserializer for Jackson for the S3Link class.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class S3LinkDeserializer(config: JsonObject?) : StdDeserializer<S3Link>(S3Link::class.java) {

    init {

        if (clientCache == null) {
            val endPoint: String

            if (config == null) {
                endPoint = "http://localhost:8001"
            } else {
                endPoint = config.getString("dynamo_endpoint")
            }

            val dynamoDBAsyncClient = AmazonDynamoDBAsyncClient(
                    DefaultAWSCredentialsProviderChain()).withEndpoint<AmazonDynamoDBAsyncClient>(endPoint)

            val mapper = DynamoDBMapper(dynamoDBAsyncClient, DefaultAWSCredentialsProviderChain())

            clientCache = mapper.s3ClientCache
        }
    }

    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext): S3Link {
        return S3Link.fromJson(clientCache!!, jsonParser.readValueAsTree<TreeNode>().toString())
    }

    companion object {
        private var clientCache: S3ClientCache? = null
    }
}
