package com.nannoq.tools.web.controllers.utils

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer

class DynamoDBUtils {
    companion object {
        val dbMap = HashMap<Int, DynamoDBProxyServer>()
    }

    fun startDynamoDB(port: Int = 8000): DynamoDBProxyServer {
        val args = arrayOf("-inMemory", "-port", "$port")
        val server = ServerRunner.createServerFromCommandLineArgs(args)

        if (dbMap.containsKey(port)) throw IllegalArgumentException("Port $port is taken!")

        server.start()

        Thread.sleep(2000)

        dbMap[port] = server

        return server
    }

    fun stopDynamoDB(port: Int = 8000) {
        if (dbMap.containsKey(port)) {
            dbMap.remove(port)?.stop()
        } else {
            throw IllegalArgumentException("No DB @ $port!")
        }
    }
}
