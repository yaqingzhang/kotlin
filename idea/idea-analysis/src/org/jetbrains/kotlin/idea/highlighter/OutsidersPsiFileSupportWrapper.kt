/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object OutsidersPsiFileSupportWrapper {
    fun isOutsiderFile(virtualFile: VirtualFile): Boolean {
        return OutsidersPsiFileSupport.isOutsiderFile(virtualFile)
    }

    private fun getOriginalFilePath(virtualFile: VirtualFile): String? {
        return OutsidersPsiFileSupport.getOriginalFilePath(virtualFile)
    }

    fun getOutsiderFileOrigin(project: Project, virtualFile: VirtualFile): VirtualFile? {
        if (!isOutsiderFile(virtualFile)) return null

        val originalFilePath = getOriginalFilePath(virtualFile) ?: return null

        return findFirstThatExists(File(originalFilePath), project)?.let { VfsUtil.findFileByIoFile(it, false) }
    }

    private fun findFirstThatExists(file: File, project: Project): File? {
        if (file.exists()) return file

        var parent: File? = file.parentFile
        while (parent != null) {
            if (file.canonicalPath == project.baseDir.canonicalPath) {
                break
            }
            if (parent.exists()) {
                return parent
            }
            parent = parent.parentFile
        }

        return null
    }

}