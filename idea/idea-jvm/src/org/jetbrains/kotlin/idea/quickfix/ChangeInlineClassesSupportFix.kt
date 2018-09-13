/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.findApplicableConfigurator
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.roots.invalidateProjectRoots
import org.jetbrains.kotlin.psi.KtFile

sealed class ChangeInlineClassesSupportFix(
    element: PsiElement,
    inlineClassesSupport: LanguageFeature.State
) : AbstractChangeFeatureSupportLevelFix(element, inlineClassesSupport, shortFeatureName) {

    class InModule(
        element: PsiElement,
        inlineClassesSupport: LanguageFeature.State
    ) : ChangeInlineClassesSupportFix(element, inlineClassesSupport) {
        override fun getText() = "${super.getText()} in the current module"

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return

            findApplicableConfigurator(module).changeInlineClassesConfiguration(module, featureSupport)
        }
    }

    class InProject(
        element: PsiElement,
        inineClassesSupport: LanguageFeature.State
    ) : ChangeInlineClassesSupportFix(element, inineClassesSupport) {
        override fun getText() = "${super.getText()} in the project"

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            if (featureSupportEnabled) {
                if (!checkUpdateRuntime(project, LanguageFeature.InlineClasses.sinceApiVersion)) return
            }
            KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
                updateFeature(LanguageFeature.InlineClasses, featureSupport)
            }
            project.invalidateProjectRoots()
        }

    }

    companion object : FeatureSupportIntentionActionsFactory() {
        private const val shortFeatureName = "inline classes"

        fun getFixText(state: LanguageFeature.State) = getFixText(state, shortFeatureName)

        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            val module = ModuleUtilCore.findModuleForPsiElement(diagnostic.psiElement) ?: return emptyList()

            fun shouldConfigureInProject(): Boolean {
                val facetSettings = KotlinFacet.get(module)?.configuration?.settings
                return (facetSettings == null || facetSettings.useProjectSettings) &&
                        module.getBuildSystemType() == BuildSystemType.JPS
            }

            return doCreateActions(
                diagnostic, LanguageFeature.InlineClasses, allowWarningAndErrorMode = false,
                quickFixConstructor = if (shouldConfigureInProject()) ::InProject else ::InModule
            )
        }
    }
}
