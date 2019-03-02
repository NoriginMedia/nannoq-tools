package com.nannoq.tools.version.manager

import com.nannoq.tools.version.VersionUtils
import com.nannoq.tools.version.models.DiffPair
import com.nannoq.tools.version.models.Version
import com.nannoq.tools.version.operators.IteratorIdManager
import com.nannoq.tools.version.operators.StateApplier
import com.nannoq.tools.version.operators.StateExtractor
import io.vertx.core.json.Json
import java.util.Collections.singletonMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors.toList

class VersionManager {
    private val versionUtils: VersionUtils = VersionUtils()
    private val stateApplier: StateApplier = StateApplier(Json.mapper, versionUtils)
    private val stateExtractor: StateExtractor = StateExtractor(Json.mapper, versionUtils)
    private val iteratorIdManager: IteratorIdManager = IteratorIdManager(versionUtils)

    @Throws(IllegalStateException::class)
    fun <T: Any> applyState(version: Version, obj: T): T {
        return applyState(singletonMap(obj, listOf(version)), listOf(obj))[0]
    }

    @Throws(IllegalStateException::class)
    fun <T: Any> applyState(versionList: List<Version>, obj: T): T {
        return applyState(singletonMap(obj, versionList), listOf(obj))[0]
    }

    @Throws(IllegalStateException::class)
    fun <T: Any> applyState(versionMap: Map<T, List<Version>>, objs: List<T>): List<T> {
        val failedApply = AtomicBoolean()
        val collect = objs.parallelStream()
                .peek { obj ->
                    versionMap[obj]?.forEach { version ->
                        try {
                            stateApplier.applyState(version, obj)
                        } catch (ise: IllegalStateException) {
                            failedApply.set(true)
                        }
                    }
                }
                .collect(toList())

        if (failedApply.get()) throw IllegalStateException("Error in state application!")

        return collect
    }

    @Throws(IllegalStateException::class)
    fun <T: Any> extractVersion(pair: DiffPair<T>): Version {
        return extractVersion(listOf(pair))[0]
    }

    @Throws(IllegalStateException::class)
    fun <T: Any> extractVersion(pairs: List<DiffPair<T>>): List<Version> {
        val failedExtract = AtomicBoolean()
        val collect = pairs.parallelStream()
                .map<Version> { pair ->
                    try {
                        stateExtractor.extractVersion(pair)
                    } catch (ise: IllegalStateException) {
                        failedExtract.set(true)

                        null
                    }
                }
                .collect(toList())

        if (failedExtract.get()) throw IllegalStateException("Error in state extract!")

        return collect
    }

    @Throws(IllegalStateException::class)
    fun <T: Any> setIteratorIds(obj: T): T {
        return setIteratorIds(listOf(obj))[0]
    }

    @Throws(IllegalStateException::class)
    fun <T: Any> setIteratorIds(objs: List<T>): List<T> {
        return iteratorIdManager.setIteratorIds(objs) as List<T>
    }
}
