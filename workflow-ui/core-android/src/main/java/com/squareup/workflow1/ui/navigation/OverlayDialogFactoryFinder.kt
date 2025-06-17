package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.internal.withKey
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.ViewRegistry.Key

/**
 * [ViewEnvironment] service object used by [Overlay.toDialogFactory] to find the right
 * [OverlayDialogFactory]. The default implementation makes [AndroidOverlay]
 * work, and provides default bindings for [AlertOverlay] and [FullScreenModal].
 */
public interface OverlayDialogFactoryFinder {
  public fun <OverlayT : Overlay> getDialogFactoryForRendering(
    environment: ViewEnvironment,
    rendering: OverlayT
  ): OverlayDialogFactory<OverlayT> {
    val entry = environment[ViewRegistry]
      .getEntryFor(Key(rendering::class, OverlayDialogFactory::class))

    @Suppress("UNCHECKED_CAST")
    return entry as? OverlayDialogFactory<OverlayT>
      ?: (rendering as? AndroidOverlay<*>)?.dialogFactory as? OverlayDialogFactory<OverlayT>
      ?: (rendering as? AlertOverlay)?.let {
        AlertOverlayDialogFactory() as OverlayDialogFactory<OverlayT>
      }
      ?: (rendering as? FullScreenModal<*>)?.let {
        FullScreenModalFactory<Screen>() as OverlayDialogFactory<OverlayT>
      }
      ?: throw IllegalArgumentException(
        "An OverlayDialogFactory should have been registered to display $rendering, " +
          "or that class should implement AndroidOverlay. Instead found $entry."
      ).withKey(keyFor(rendering))
  }

  public companion object : ViewEnvironmentKey<OverlayDialogFactoryFinder>() {
    override val default: OverlayDialogFactoryFinder = object : OverlayDialogFactoryFinder {}
  }
}
