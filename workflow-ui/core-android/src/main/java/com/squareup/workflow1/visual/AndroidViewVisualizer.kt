package com.squareup.workflow1.visual

import android.view.View
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * The [Visualizer] that provides general support for classic Android container [View]s,
 * used by `WorkflowViewStub` et al. Handles renderings of all types via the
 * [visualizerAndroidViewFactory].
 */
@Suppress("FunctionName")
@WorkflowUiExperimentalApi
public fun AndroidViewVisualizer(): Visualizer<ContextOrContainer, View> {
  return Visualizer { env -> env.visualizerAndroidViewFactory }
}

/**
 * Provides the integrated [AndroidViewFactory] used by the [Visualizer]
 * system to display any series of renderings in classic Android [View] instances.
 * The default instance combines the factory bound to
 * [leafAndroidViewFactory] with the [standardFactories].
 *
 * Use [withUniversalAndroidViewFactory] to replace this, and thus customize the behavior
 * of `WorkflowViewStub` and other containers. E.g., workflow-ui's optional
 * Compose integration will provide an alternative implementation that
 * extends this one to wrap Compose-specific VisualFactories in ComposeView,
 * via [VisualFactoryConverter].
 *
 * TODO: When Compose integration is added, the Compose > Classic converter
 *  will also need to pass [VisualizerAndroidViewFactoryKey] to delegate factories
 *  via the `getFactory` parameter
 */
@WorkflowUiExperimentalApi
public val VisualEnvironment.visualizerAndroidViewFactory: AndroidViewFactory<Any>
  get() = this[VisualizerAndroidViewFactoryKey]

/**
 * Returns a copy of the receiving [VisualEnvironment] that uses the given
 * [factory] as its [visualizerAndroidViewFactory].
 */
@WorkflowUiExperimentalApi
public fun VisualEnvironment.withUniversalAndroidViewFactory(
  factory: AndroidViewFactory<Any>
): VisualEnvironment {
  return this + (VisualizerAndroidViewFactoryKey to factory)
}

@WorkflowUiExperimentalApi
private object VisualizerAndroidViewFactoryKey :
  VisualEnvironmentKey<AndroidViewFactory<Any>>() {
  override val default: AndroidViewFactory<Any> =
    AndroidViewFactory { rendering, context, environment, getFactory ->
      SequentialVisualFactory(
        listOf(standardFactories(), environment.leafAndroidViewFactory)
      ).createOrNull(rendering, context, environment, getFactory)
    }
}
