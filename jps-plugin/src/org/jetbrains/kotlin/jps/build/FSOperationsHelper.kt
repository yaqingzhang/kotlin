/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.fs.CompilationRound
import java.io.File

/**
 * Entry point for safely marking files as dirty.
 */
class FSOperationsHelper(
    private val compileContext: CompileContext,
    private val chunk: ModuleChunk,
    private val dirtyFilesHolder: KotlinDirtySourceFilesHolder,
    private val log: Logger
) {
    private val moduleDependenciesFileFilter = ModuleDependenciesFileFilter(compileContext, chunk)

    internal var hasMarkedDirtyForNextRound = false
        private set

    private val buildLogger = compileContext.testingContext?.buildLogger

    /**
     * Marks given [files] as dirty for any target of [chunk] or it's dependencies.
     */
    internal fun markFilesForCurrentRound(files: Iterable<File>) {
        val filteredFiles = mutableSetOf<File>()
        files.forEach {
            val root = moduleDependenciesFileFilter.findAcceptableSrcChunkRoot(it)
            if (root != null) {
                root as JavaSourceRootDescriptor

                filteredFiles.add(it)
                dirtyFilesHolder.byTarget[root.target]?._markDirty(it, root)
            }
        }

        markAndLogFiles(filteredFiles, currentRound = true)
    }

    /**
     * Marks given [files] as dirty for current round and given [target] of [chunk].
     */
    fun markFilesForCurrentRound(target: ModuleBuildTarget, files: Iterable<File>) {
        require(target in chunk.targets)

        val buildRootIndex = compileContext.projectDescriptor.buildRootIndex
        val targetDirtyFiles = dirtyFilesHolder.byTarget[target]!!
        val filteredFiles = mutableSetOf<File>()
        files.forEach {
            if (it.exists()) {
                val roots = buildRootIndex.findAllParentDescriptors<BuildRootDescriptor>(it, compileContext)

                roots.forEach { root ->
                    if (root.target == target) {
                        targetDirtyFiles._markDirty(it, root as JavaSourceRootDescriptor)
                        filteredFiles.add(it)
                    }
                }
            }
        }

        markAndLogFiles(filteredFiles, currentRound = true)
    }

    fun markChunkForNextRound(recursively: Boolean, kotlinOnly: Boolean, excludeFiles: Set<File> = setOf()) {
        fun shouldMark(file: File): Boolean {
            if (kotlinOnly && !file.isKotlinSourceFile) return false

            if (file in excludeFiles) return false

            hasMarkedDirtyForNextRound = true
            return true
        }

        if (recursively) {
            FSOperations.markDirtyRecursively(compileContext, CompilationRound.NEXT, chunk, ::shouldMark)
        } else {
            FSOperations.markDirty(compileContext, CompilationRound.NEXT, chunk, ::shouldMark)
        }
    }

    fun markForNextRound(files: Iterable<File>, excludeFiles: Set<File>) {
        val filteredFiles = files.filterTo(mutableSetOf()) {
            it !in excludeFiles && it.exists() && moduleDependenciesFileFilter.accept(it)
        }

        markAndLogFiles(filteredFiles, currentRound = false)
    }

    private fun markAndLogFiles(files: Set<File>, currentRound: Boolean) {
        if (files.isEmpty()) return

        val compilationRound = if (currentRound) {
            buildLogger?.markedAsDirtyBeforeRound(files)
            CompilationRound.CURRENT
        } else {
            buildLogger?.markedAsDirtyAfterRound(files)
            hasMarkedDirtyForNextRound = true
            CompilationRound.NEXT
        }

        for (fileToMark in files) {
            FSOperations.markDirty(compileContext, compilationRound, fileToMark)
        }

        log.debug("Mark dirty: $files ($compilationRound)")
    }
}
