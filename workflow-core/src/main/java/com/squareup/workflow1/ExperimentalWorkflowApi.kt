@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import kotlin.RequiresOptIn.Level.ERROR
import kotlin.annotation.AnnotationRetention.BINARY

/**
 * Marks Workflow APIs that are extremely likely to change in future versions, rely themselves on
 * other unstable, experimental APIs, and SHOULD NOT be used in library code or app code that you
 * are not prepared to update when changing even minor workflow versions. Proceed with caution, and
 * be ready to have the rug pulled out from under you.
 */
@MustBeDocumented
@Retention(value = BINARY)
@RequiresOptIn(level = ERROR)
annotation class ExperimentalWorkflowApi
