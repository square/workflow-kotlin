package com.squareup.workflow1.ui.navigation

import android.content.Context
import androidx.activity.ComponentDialog
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment

/**
 * Default [OverlayDialogFactory] for the standard [FullScreenModal] rendering class.
 * Nothing more than a direct call to [ComponentDialog.asDialogHolderWithContent].
 *
 * To provide a custom binding for [FullScreenModal], see [OverlayDialogFactoryFinder].
 */
internal class FullScreenModalFactory<C : Screen> : OverlayDialogFactory<FullScreenModal<C>> {
  override val type = FullScreenModal::class

  override fun buildDialog(
    initialRendering: FullScreenModal<C>,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<FullScreenModal<C>> =
    ComponentDialog(context).asDialogHolderWithContent(initialRendering, initialEnvironment)
}
