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

package com.nannoq.tools.repository.repository.redis

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.RedisClient
import io.vertx.redis.RedisOptions

/**
 * This class contains logic for get and performing retryable redis commands.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
object RedisUtils {
    private val logger = LoggerFactory.getLogger(RedisUtils::class.java!!.getSimpleName())

    fun getRedisClient(config: JsonObject): RedisClient {
        return getRedisClient(Vertx.currentContext().owner(), config)
    }

    fun getRedisClient(vertx: Vertx, config: JsonObject): RedisClient {
        val redisServer = config.getString("redis_host")
        val redisPort = config.getInteger("redis_port")
        val redisOptions = RedisOptions()
        redisOptions.host = redisServer

        if (redisServer != null && redisServer == "localhost") redisOptions.port = 6380
        if (redisPort != null) redisOptions.port = redisPort

        val redisClient = RedisClient.create(vertx, redisOptions)

        redisClient.info { res ->
            if (res.failed()) {
                logger.error("Error getting Redis Info, are you connected?", res.cause())
            }
        }

        return redisClient
    }

    fun performJedisWithRetry(redisClient: RedisClient, consumer: (RedisClient) -> Unit) {
        consumer(redisClient)
    }
}
