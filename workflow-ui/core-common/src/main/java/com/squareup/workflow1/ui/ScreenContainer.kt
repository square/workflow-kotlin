package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
public interface ScreenContainer<out C : Screen> : Container<Screen, C>, Screen {
  public override fun <D : Screen> map(transform: (C) -> D): ScreenContainer<D>
}

@WorkflowUiExperimentalApi
public interface ScreenWrapper<out C : Screen> : ScreenContainer<C>, Wrapper<Screen, C>, Screen {
  public override fun <D : Screen> map(transform: (C) -> D): ScreenWrapper<D>
}

/**
 * Applies [transform] to the receiver unless it is a [ScreenContainer]. In that case,
 * makes a recursive call to [ScreenContainer.map] and applies [deepMap] to its
 * contents.
 *
 * For example, consider this snippet:
 *
 *    val backStack = BackStackScreen(SomeWrapper(theRealScreen))
 *    val loggingBackStack = backStack.deepMap { WithLogging(it) }
 *
 * `loggingBackStack` will have a structure like so:
 *
 *    BackStackScreen(SomeWrapper(WithLogging(theRealScreen)))
 */
@WorkflowUiExperimentalApi
public fun Screen.deepMap(transform: (Screen) -> Screen): Screen {
  return if (this is ScreenContainer<*>) map { it.deepMap(transform) }
  else transform(this)
}
