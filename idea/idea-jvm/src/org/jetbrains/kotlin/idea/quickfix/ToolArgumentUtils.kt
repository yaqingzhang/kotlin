/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments
import org.jetbrains.kotlin.cli.common.arguments.ManualLanguageFeatureSetting
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.cli.common.arguments.CliArgumentStringBuilder.buildArgumentString

internal fun CommonToolArguments.updateFeature(feature: LanguageFeature, state: LanguageFeature.State) {
    val featureArgumentString = feature.buildArgumentString(state)
    internalArguments = internalArguments.filter {
        it !is ManualLanguageFeatureSetting || it.languageFeature != feature
    } + ManualLanguageFeatureSetting(feature, state, featureArgumentString)

    // TODO: for project, we have an exception (?) here
    // TODO: for module, we have no exception, but empty tag is written into kotlinc.xml
}