/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.smartcasts

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.isSafeCall
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.receivers.TransientReceiver
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.expressions.ExpressionTypingUtils
import org.jetbrains.kotlin.types.isError

class DataFlowValueFactoryImpl
@Deprecated("Please, avoid to use that implementation explicitly. If you need DataFlowValueFactory, use injection")
constructor(private val languageVersionSettings: LanguageVersionSettings) : DataFlowValueFactory {

    // Receivers
    override fun createDataFlowValue(
        receiverValue: ReceiverValue,
        resolutionContext: ResolutionContext<*>
    ) = createDataFlowValue(receiverValue, resolutionContext.trace.bindingContext, resolutionContext.scope.ownerDescriptor)

    override fun createDataFlowValue(
        receiverValue: ReceiverValue,
        bindingContext: BindingContext,
        containingDeclarationOrModule: DeclarationDescriptor
    ) = when (receiverValue) {
        is TransientReceiver, is ImplicitReceiver -> createDataFlowValueForStableReceiver(receiverValue)
        is ExpressionReceiver -> createDataFlowValue(
            receiverValue.expression,
            receiverValue.getType(),
            bindingContext,
            containingDeclarationOrModule
        )
        else -> throw UnsupportedOperationException("Unsupported receiver value: " + receiverValue::class.java.name)
    }

    override fun createDataFlowValueForStableReceiver(receiver: ReceiverValue) =
        DataFlowValue(IdentifierInfo.Receiver(receiver), receiver.type)


    // Property
    override fun createDataFlowValueForProperty(
        property: KtProperty,
        variableDescriptor: VariableDescriptor,
        bindingContext: BindingContext,
        usageContainingModule: ModuleDescriptor?
    ): DataFlowValue {
        val identifierInfo = IdentifierInfo.Variable(
            variableDescriptor,
            variableDescriptor.variableKind(usageContainingModule, bindingContext, property, languageVersionSettings),
            bindingContext[BindingContext.BOUND_INITIALIZER_VALUE, variableDescriptor]
        )
        return DataFlowValue(identifierInfo, variableDescriptor.type)
    }


    // Expressions
    override fun createDataFlowValue(
        expression: KtExpression,
        type: KotlinType,
        resolutionContext: ResolutionContext<*>
    ) = createDataFlowValue(expression, type, resolutionContext.trace.bindingContext, resolutionContext.scope.ownerDescriptor)

    override fun createDataFlowValue(
        expression: KtExpression,
        type: KotlinType,
        bindingContext: BindingContext,
        containingDeclarationOrModule: DeclarationDescriptor
    ): DataFlowValue {
        return when {
            expression is KtConstantExpression && expression.node.elementType === KtNodeTypes.NULL ->
                DataFlowValue.nullValue(containingDeclarationOrModule.builtIns)

            type.isError -> DataFlowValue.ERROR

            KotlinBuiltIns.isNullableNothing(type) ->
                DataFlowValue.nullValue(containingDeclarationOrModule.builtIns) // 'null' is the only inhabitant of 'Nothing?'

        // In most cases type of `E!!`-expression is strictly not nullable and we could get proper Nullability
        // by calling `getImmanentNullability` (as it happens below).
        //
        // But there are some problem with types built on type parameters, e.g.
        // fun <T : Any?> foo(x: T) = x!!.hashCode() // there no way in type system to denote that `x!!` is not nullable
            ExpressionTypingUtils.isExclExclExpression(KtPsiUtil.deparenthesize(expression)) ->
                DataFlowValue(IdentifierInfo.Expression(expression), type, Nullability.NOT_NULL)

            isComplexExpression(expression) ->
                DataFlowValue(IdentifierInfo.Expression(expression, stableComplex = true), type)

            else -> {
                val result = getIdForStableIdentifier(expression, bindingContext, containingDeclarationOrModule, languageVersionSettings)
                DataFlowValue(if (result === IdentifierInfo.NO) IdentifierInfo.Expression(expression) else result, type)
            }
        }
    }

    private fun isComplexExpression(expression: KtExpression): Boolean = when (expression) {
        is KtBlockExpression, is KtIfExpression, is KtWhenExpression -> true

        is KtBinaryExpression -> expression.operationToken === KtTokens.ELVIS

        is KtParenthesizedExpression -> {
            val deparenthesized = KtPsiUtil.deparenthesize(expression)
            deparenthesized != null && isComplexExpression(deparenthesized)
        }

        else -> false
    }

    private fun getIdForStableIdentifier(
        expression: KtExpression?,
        bindingContext: BindingContext,
        containingDeclarationOrModule: DeclarationDescriptor,
        languageVersionSettings: LanguageVersionSettings
    ): IdentifierInfo {
        if (expression != null) {
            val deparenthesized = KtPsiUtil.deparenthesize(expression)
            if (expression !== deparenthesized) {
                return getIdForStableIdentifier(deparenthesized, bindingContext, containingDeclarationOrModule, languageVersionSettings)
            }
        }
        return when (expression) {
            is KtQualifiedExpression -> {
                val receiverExpression = expression.receiverExpression
                val selectorExpression = expression.selectorExpression
                val receiverInfo = getIdForStableIdentifier(receiverExpression, bindingContext, containingDeclarationOrModule, languageVersionSettings)
                val selectorInfo = getIdForStableIdentifier(selectorExpression, bindingContext, containingDeclarationOrModule, languageVersionSettings)

                qualified(
                    receiverInfo, bindingContext.getType(receiverExpression),
                    selectorInfo, expression.operationSign === KtTokens.SAFE_ACCESS
                )
            }

            is KtBinaryExpressionWithTypeRHS -> {
                val subjectExpression = expression.left
                val targetTypeReference = expression.right
                val operationToken = expression.operationReference.getReferencedNameElementType()
                if (operationToken == KtTokens.IS_KEYWORD || operationToken == KtTokens.AS_KEYWORD) {
                    IdentifierInfo.NO
                } else {
                    IdentifierInfo.SafeCast(
                        getIdForStableIdentifier(subjectExpression, bindingContext, containingDeclarationOrModule, languageVersionSettings),
                        bindingContext.getType(subjectExpression),
                        bindingContext[BindingContext.TYPE, targetTypeReference]
                    )
                }
            }

            is KtSimpleNameExpression ->
                getIdForSimpleNameExpression(expression, bindingContext, containingDeclarationOrModule, languageVersionSettings)

            is KtThisExpression -> {
                val declarationDescriptor = bindingContext.get(BindingContext.REFERENCE_TARGET, expression.instanceReference)
                getIdForThisReceiver(declarationDescriptor)
            }

            is KtPostfixExpression -> {
                val operationType = expression.operationReference.getReferencedNameElementType()
                if (operationType === KtTokens.PLUSPLUS || operationType === KtTokens.MINUSMINUS)
                    postfix(
                        getIdForStableIdentifier(
                            expression.baseExpression,
                            bindingContext,
                            containingDeclarationOrModule,
                            languageVersionSettings
                        ),
                        operationType
                    )
                else
                    IdentifierInfo.NO
            }

            else -> IdentifierInfo.NO
        }
    }

    private fun getIdForSimpleNameExpression(
        simpleNameExpression: KtSimpleNameExpression,
        bindingContext: BindingContext,
        containingDeclarationOrModule: DeclarationDescriptor,
        languageVersionSettings: LanguageVersionSettings
    ): IdentifierInfo {
        val declarationDescriptor = bindingContext.get(BindingContext.REFERENCE_TARGET, simpleNameExpression)
        return when (declarationDescriptor) {
            is VariableDescriptor -> {
                val resolvedCall = simpleNameExpression.getResolvedCall(bindingContext)

                // todo uncomment assert
                // KT-4113
                // for now it fails for resolving 'invoke' convention, return it after 'invoke' algorithm changes
                // assert resolvedCall != null : "Cannot create right identifier info if the resolved call is not known yet for
                val usageModuleDescriptor = DescriptorUtils.getContainingModuleOrNull(containingDeclarationOrModule)
                val selectorInfo = IdentifierInfo.Variable(
                    declarationDescriptor,
                    declarationDescriptor.variableKind(usageModuleDescriptor, bindingContext, simpleNameExpression, languageVersionSettings),
                    bindingContext[BindingContext.BOUND_INITIALIZER_VALUE, declarationDescriptor]
                )

                val implicitReceiver = resolvedCall?.dispatchReceiver
                if (implicitReceiver == null) {
                    selectorInfo
                } else {
                    val receiverInfo = getIdForImplicitReceiver(implicitReceiver, simpleNameExpression)

                    if (receiverInfo == null) {
                        selectorInfo
                    } else {
                        qualified(
                            receiverInfo, implicitReceiver.type,
                            selectorInfo, resolvedCall.call.isSafeCall()
                        )
                    }
                }
            }

            is ClassDescriptor -> {
                if (declarationDescriptor.kind == ClassKind.ENUM_ENTRY && languageVersionSettings.supportsFeature(LanguageFeature.SoundSmartcastForEnumEntries))
                    IdentifierInfo.EnumEntry(declarationDescriptor)
                else
                    IdentifierInfo.PackageOrClass(declarationDescriptor)
            }

            is PackageViewDescriptor -> IdentifierInfo.PackageOrClass(declarationDescriptor)

            else -> IdentifierInfo.NO
        }
    }

    private fun getIdForImplicitReceiver(receiverValue: ReceiverValue?, expression: KtExpression?) =
        when (receiverValue) {
            is ImplicitReceiver -> getIdForThisReceiver(receiverValue.declarationDescriptor)

            is TransientReceiver ->
                throw AssertionError("Transient receiver is implicit for an explicit expression: $expression. Receiver: $receiverValue")

            else -> null
        }

    private fun getIdForThisReceiver(descriptorOfThisReceiver: DeclarationDescriptor?) = when (descriptorOfThisReceiver) {
        is CallableDescriptor -> {
            val receiverParameter = descriptorOfThisReceiver.extensionReceiverParameter
                ?: error("'This' refers to the callable member without a receiver parameter: $descriptorOfThisReceiver")
            IdentifierInfo.Receiver(receiverParameter.value)
        }

        is ClassDescriptor -> IdentifierInfo.Receiver(descriptorOfThisReceiver.thisAsReceiverParameter.value)

        else -> IdentifierInfo.NO
    }

    private fun postfix(argumentInfo: IdentifierInfo, op: KtToken): IdentifierInfo =
        if (argumentInfo == IdentifierInfo.NO) IdentifierInfo.NO else IdentifierInfo.PostfixIdentifierInfo(argumentInfo, op)

    private fun qualified(receiverInfo: IdentifierInfo, receiverType: KotlinType?, selectorInfo: IdentifierInfo, safe: Boolean) =
        when (receiverInfo) {
            IdentifierInfo.NO -> IdentifierInfo.NO
            is IdentifierInfo.PackageOrClass -> selectorInfo
            else -> IdentifierInfo.Qualified(receiverInfo, selectorInfo, safe, receiverType)
        }
}
