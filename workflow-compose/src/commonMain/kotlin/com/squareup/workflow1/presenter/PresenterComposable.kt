package com.squareup.workflow1.presenter

import androidx.compose.runtime.ComposableTargetMarker

/**
 * An annotation that can be used to mark a composable function as being expected to be used in a
 * composable function that is also marked or inferred to be marked as a [PresenterComposable].
 *
 * Using this annotation explicitly is rarely necessary as the Compose compiler plugin will infer
 * the necessary equivalent annotations automatically. See
 * [androidx.compose.runtime.ComposableTarget] for details.
 */
@Retention(AnnotationRetention.BINARY)
@ComposableTargetMarker(description = "UI Composable")
@Target(
  AnnotationTarget.FILE,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.TYPE,
  AnnotationTarget.TYPE_PARAMETER,
)
annotation class PresenterComposable
