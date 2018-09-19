/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.common.arguments

import org.jetbrains.kotlin.config.LanguageFeature

object CliArgumentStringBuilder {
    private const val languagePrefix = "-XXLanguage:"

    private val LanguageFeature.State.sign: String
        get() = when (this) {
            LanguageFeature.State.ENABLED -> "+"
            LanguageFeature.State.DISABLED -> "-"
            LanguageFeature.State.ENABLED_WITH_WARNING -> "+" // not supported normally
            LanguageFeature.State.ENABLED_WITH_ERROR -> "-" // not supported normally
        }

    fun LanguageFeature.buildArgumentString(state: LanguageFeature.State): String {
        return "$languagePrefix${state.sign}$name"
    }

    fun String.replaceLanguageFeature(
        feature: LanguageFeature,
        state: LanguageFeature.State,
        prefix: String = "",
        postfix: String = "",
        separator: String = ", ",
        quoted: Boolean = true
    ): String {
        val existingFeatureIndex = indexOf(feature.name)
        val languagePrefixIndex = lastIndexOf(languagePrefix, existingFeatureIndex)
        val featureArgumentString = feature.buildArgumentString(state)
        val quote = if (quoted) "\"" else ""
        return if (languagePrefixIndex != -1) {
            substring(0, languagePrefixIndex) + featureArgumentString + substring(existingFeatureIndex + feature.name.length)
        } else {
            val splitText = if (postfix.isNotEmpty()) split(postfix) else listOf(this, "")
            if (splitText.size != 2) {
                "$prefix$quote$featureArgumentString$quote$postfix"
            } else {
                splitText[0] + "$separator$quote$featureArgumentString$quote$postfix" + splitText[1]
            }
        }
    }
}