@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1.ui

import kotlin.RequiresOptIn.Level.ERROR
import kotlin.annotation.AnnotationRetention.BINARY

/**
 * Marks Workflow user interface APIs which are still in flux. Annotated code SHOULD NOT be used
 * in library code or app code that you are not prepared to update when changing even minor
 * workflow versions. Proceed with caution, and be ready to have the rug pulled out from under you.
 */
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
@MustBeDocumented
@Retention(value = BINARY)
@RequiresOptIn(level = ERROR)
annotation class WorkflowUiExperimentalApi
