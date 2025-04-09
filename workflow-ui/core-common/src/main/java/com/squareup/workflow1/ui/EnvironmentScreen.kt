package com.squareup.workflow1.ui

/**
 * Pairs a [content] rendering with a [environment] to support its display.
 * Typically the rendering type (`RenderingT`) of the root of a UI workflow,
 * but can be used at any point to modify the [ViewEnvironment] received from
 * a parent view.
 *
 * UI kits are expected to provide handling for this class by default.
 */
public class EnvironmentScreen<out C : Screen>(
  public override val content: C,
  public val environment: ViewEnvironment = ViewEnvironment.EMPTY
) : Wrapper<Screen, C>, Screen {
  override fun <D : Screen> map(transform: (C) -> D): EnvironmentScreen<D> =
    EnvironmentScreen(transform(content), environment)
}

/**
 * Returns an [EnvironmentScreen] derived from the receiver, whose
 * [EnvironmentScreen.environment] includes [viewRegistry].
 *
 * If the receiver is an [EnvironmentScreen], uses
 * [ViewRegistry.merge][com.squareup.workflow1.ui.merge] to preserve the [ViewRegistry]
 * entries of both.
 */
public fun Screen.withRegistry(viewRegistry: ViewRegistry): EnvironmentScreen<*> {
  return withEnvironment(ViewEnvironment.EMPTY + viewRegistry)
}

/**
 * Returns an [EnvironmentScreen] derived from the receiver,
 * whose [EnvironmentScreen.environment] includes the values in the given [environment].
 *
 * If the receiver is an [EnvironmentScreen], uses
 * [ViewRegistry.merge][com.squareup.workflow1.ui.merge] to preserve the [ViewRegistry]
 * entries of both.
 */
public fun Screen.withEnvironment(
  environment: ViewEnvironment = ViewEnvironment.EMPTY
): EnvironmentScreen<*> {
  return when (this) {
    is EnvironmentScreen<*> -> {
      if (environment.map.isEmpty()) {
        this
      } else {
        EnvironmentScreen(content, this.environment + environment)
      }
    }
    else -> EnvironmentScreen(this, environment)
  }
}

/**
 * Returns an [EnvironmentScreen] derived from the receiver,
 * whose [EnvironmentScreen.environment] includes the given entry.
 */
public fun <T : Any> Screen.withEnvironment(
  entry: Pair<ViewEnvironmentKey<T>, T>
): EnvironmentScreen<*> = withEnvironment(ViewEnvironment.EMPTY + entry)
