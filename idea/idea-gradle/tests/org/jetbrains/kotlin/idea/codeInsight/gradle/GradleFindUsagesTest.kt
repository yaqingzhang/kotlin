/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.util.*

class GradleFindUsagesTest : GradleImportingTestCase() {
    override fun testDataDirName(): String = "findUsages"

    @TargetVersions("4.9+")
    @Test
    fun testProjectWithBuildSrc() {
        importProjectFromTestData()

        assertModules(
            "myProject",
            "myProject_buildSrc", "myProject_buildSrc_main", "myProject_buildSrc_test"
        )

        assertEquals(1, findUsages("org.buildsrc.BuildSrcClass"))
        assertEquals(1, findUsages("org.buildsrc.BuildSrcClass", "sayHello"))
    }

    @TargetVersions("4.10+")
    @Test
    fun testProjectWithIncludedBuild() {
        configureByFiles()

        importProject()
        assertModules(
            "multiproject",
            "gradle-plugin", "gradle-plugin_test", "gradle-plugin_main"
        )

        assertEquals(1, findUsages("org.included.IncludedBuildClass"))
    }

    private fun findUsages(
        className: String,
        declarationName: String? = null
    ): Int {
        val psiClass = runInEdtAndGet {
            JavaPsiFacade.getInstance(myProject).findClasses(className, GlobalSearchScope.allScope(myProject)).singleOrNull()
        } ?: error("Cannot find $className")

        val ktClass = (psiClass as? KtLightClass)?.kotlinOrigin as? KtClass ?: error("$className isn't a kotlin class")
        return if (declarationName != null) {
            runInEdtAndGet { ktClass.declarations.filter { it.name == declarationName } }.sumBy { findUsages(it).size }
        } else {
            findUsages(ktClass).size
        }
    }

    private fun assertModules(vararg expectedNames: String) {
        val actual = ModuleManager.getInstance(myProject).modules
        val actualNames = ArrayList<String>()
        for (m in actual) {
            actualNames.add(m.name)
        }
        assertEquals(expectedNames.toHashSet(), actualNames.toHashSet())
    }

    private fun findUsages(element: PsiElement): Collection<PsiReference> {
        return ProgressManager.getInstance()
            .run(object : Task.WithResult<Collection<PsiReference>, Exception>(element.project, "", false) {
                override fun compute(indicator: ProgressIndicator): Collection<PsiReference> {

                    return runReadAction {
                        ReferencesSearch.search(element).findAll()
                    }
                }
            })
    }
}
