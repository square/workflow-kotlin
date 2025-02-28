package com.squareup.workflow1.compose

import androidx.compose.runtime.ComposableTargetMarker
import com.squareup.workflow1.WorkflowExperimentalApi
import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FILE
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.TYPE
import kotlin.annotation.AnnotationTarget.TYPE_PARAMETER

/**
 * An annotation that can be used to mark a composable function as being expected to be use in a
 * composable function that is also marked or inferred to be marked as a [WorkflowComposable], i.e.
 * that can be called from [BaseRenderContext.renderComposable].
 *
 * Using this annotation explicitly is rarely necessary as the Compose compiler plugin will infer
 * the necessary equivalent annotations automatically. See
 * [androidx.compose.runtime.ComposableTarget] for details.
 */
@WorkflowExperimentalApi
@ComposableTargetMarker(description = "Workflow Composable")
@Target(FILE, FUNCTION, PROPERTY_GETTER, TYPE, TYPE_PARAMETER)
@Retention(BINARY)
annotation class WorkflowComposable
