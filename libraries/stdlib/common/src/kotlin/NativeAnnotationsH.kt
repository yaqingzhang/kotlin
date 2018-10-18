/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.native

/**
 * Top level variable or object is thread local, and so could be mutable.
 * One may use this annotation as the stopgap measure for singleton
 * object immutability.
 * PLEASE NOTE THAT THIS ANNOTATION MAY GO AWAY IN UPCOMING RELEASES.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@OptionalExpectation
public expect annotation class ThreadLocal

/**
 * Top level variable is immutable and so could be shared.
 * PLEASE NOTE THAT THIS ANNOTATION MAY GO AWAY IN UPCOMING RELEASES.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
@OptionalExpectation
public expect annotation class SharedImmutable

