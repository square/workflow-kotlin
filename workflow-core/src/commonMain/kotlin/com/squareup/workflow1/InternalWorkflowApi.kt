@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import kotlin.RequiresOptIn.Level.ERROR
import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Marks Workflow APIs that are internal and should not be used outside the library.
 */
@InternalWorkflowApi
@MustBeDocumented
@Retention(value = BINARY)
@RequiresOptIn(level = ERROR)
public annotation class InternalWorkflowApi
