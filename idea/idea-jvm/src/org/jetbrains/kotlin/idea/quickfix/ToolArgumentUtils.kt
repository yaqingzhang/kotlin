/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments
import org.jetbrains.kotlin.cli.common.arguments.ManualLanguageFeatureSetting
import org.jetbrains.kotlin.config.LanguageFeature

internal fun CommonToolArguments.updateFeature(feature: LanguageFeature, featureSupport: LanguageFeature.State) {
    val sign = when (featureSupport) {
        LanguageFeature.State.ENABLED -> "+"
        LanguageFeature.State.DISABLED -> "-"
        else -> return
    }
    val stringRepresentation = "-XXLanguage:$sign${feature.name}"
    internalArguments = internalArguments.filter {
        it !is ManualLanguageFeatureSetting || it.languageFeature != feature
    } + ManualLanguageFeatureSetting(feature, featureSupport, stringRepresentation)

    // TODO: for project, we have an exception (?) here
    // TODO: for module, we have no exception, but empty tag is written into kotlinc.xml
}