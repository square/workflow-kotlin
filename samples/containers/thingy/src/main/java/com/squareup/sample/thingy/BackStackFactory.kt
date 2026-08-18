package com.squareup.sample.thingy

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.toBackStackScreen
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

public fun interface BackStackFactory {

  /**
   * Responsible for converting a list of [Screen]s into a [BackStackScreen]. This function *must*
   * handle the case where [screens] is empty, since [BackStackScreen] must always have at least
   * one screen. It *should* handle the case where [isTopIdle] is true, which indicates that the
   * top (last) screen in [screens] is doing some work that may eventually show another screen.
   *
   * @see toBackStackScreen
   */
  fun createBackStack(
    screens: List<Screen>,
    isTopIdle: Boolean
  ): BackStackScreen<Screen>

  companion object {
    internal val ThrowOnIdle
      get() = showLoadingScreen {
        error("No BackStackFactory provided")
      }

    /**
     * Returns a [BackStackFactory] that shows a [loading screen][createLoadingScreen] when
     * [BackStackWorkflow.runBackStack] has not shown anything yet or when a workflow's output
     * handler is idle (not showing an active screen).
     */
    fun showLoadingScreen(
      name: String = "",
      createLoadingScreen: () -> Screen
    ): BackStackFactory = BackStackFactory { screens, isTopIdle ->
      val mutableScreens = screens.toMutableList()
      if (mutableScreens.isEmpty() || isTopIdle) {
        mutableScreens += createLoadingScreen()
      }
      mutableScreens.toBackStackScreen(name)
    }
  }
}

/**
 * Returns a [CoroutineContext.Element] that will store this [BackStackFactory] in a
 * [CoroutineContext] to later be retrieved by [backStackFactory].
 */
public fun BackStackFactory.asContextElement(): CoroutineContext.Element =
  BackStackFactoryContextElement(this)

/**
 * Looks for a [BackStackFactory] stored the current context via [asContextElement].
 */
public val CoroutineContext.backStackFactory: BackStackFactory?
  get() = this[BackStackFactoryContextElement]?.factory

private class BackStackFactoryContextElement(
  val factory: BackStackFactory
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<BackStackFactoryContextElement>
}
