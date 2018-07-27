package com.nannoq.tools.web.controllers.utils

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer

class DynamoDBUtils {
    companion object {
        val dbMap = HashMap<Int, DynamoDBProxyServer>()
    }

    @Synchronized
    fun startDynamoDB(port: Int = 8000): DynamoDBProxyServer {
        val args = arrayOf("-inMemory", "-port", "$port", "-sharedDb")
        val server = ServerRunner.createServerFromCommandLineArgs(args)

        if (dbMap.containsKey(port)) throw IllegalArgumentException("Port $port is taken!")

        server.start()

        dbMap.put(port, server)

        return server
    }

    @Synchronized
    fun stopDynamoDB(port: Int = 8000) {
        if (dbMap.containsKey(port)) {
            dbMap.remove(port)?.stop()
        } else {
            throw IllegalArgumentException("No DB @ $port!")
        }
    }

    @Synchronized
    fun stopAll() {
        dbMap.forEach { it.value.stop() }
        dbMap.clear()
    }
}