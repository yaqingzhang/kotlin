/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.ModuleBasedTarget
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.CompileContext
import java.io.File
import java.util.*

/**
 * Accepts files only belongs to current module or it's dependencies.
 *
 * @see [KT-17397]
 * @see org.jetbrains.jps.builders.java.JavaBuilderUtil.ModulesBasedFileFilter
 */
internal class ModuleDependenciesFileFilter(
    private val context: CompileContext,
    srcChunk: ModuleChunk
) {
    private val srcChunkTargets = srcChunk.targets
    private val buildRootIndex = context.projectDescriptor.buildRootIndex
    private val buildTargetIndex = context.projectDescriptor.buildTargetIndex
    private val allDependenciesCache = HashMap<BuildTarget<*>, Set<BuildTarget<*>>>()

    fun accept(file: File): Boolean =
        findAcceptableSrcChunkRoot(file) != null

    /**
     * Find source root of [srcChunk], that (or one of it's dependencies recursively) contains given [file].
     */
    fun findAcceptableSrcChunkRoot(file: File): BuildRootDescriptor? {
        val roots = buildRootIndex.findAllParentDescriptors<BuildRootDescriptor>(file, context)
        roots.forEach { root ->
            if (root is JavaSourceRootDescriptor) {
                val fileTarget = root.target

                if (fileTarget in srcChunkTargets || isBelongsToSrcChunkDependency(fileTarget)) {
                    return root
                }
            }
        }

        return null
    }

    private fun isBelongsToSrcChunkDependency(target: ModuleBasedTarget<*>): Boolean {
        val fileTargetDependencies = allDependenciesCache.getOrPut(target) {
            buildTargetIndex.getDependenciesRecursively(target, context)
        }

        return srcChunkTargets.any { it in fileTargetDependencies }
    }
}