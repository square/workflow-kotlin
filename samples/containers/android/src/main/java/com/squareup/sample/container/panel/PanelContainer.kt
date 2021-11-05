package com.squareup.sample.container.panel

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.squareup.sample.container.R
import com.squareup.workflow1.ui.ManualScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.modal.ModalViewContainer

/**
 * Used by Tic Tac Workflow sample to show its [PanelContainerScreen]s.
 * Extends [ModalViewContainer] to make the dialog square on Tablets, and
 * give it an opaque background.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class PanelContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : ModalViewContainer(context, attributeSet, defStyle, defStyleRes) {
  override fun buildDialogForView(view: View): Dialog {
    return Dialog(context, R.style.PanelDialog).also { dialog ->
      dialog.setContentView(view)

      val typedValue = TypedValue()
      context.theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
      if (typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
        dialog.window!!.setBackgroundDrawable(ColorDrawable(typedValue.data))
      }

      // Use setLayout to control window size. Note that it must be
      // called after setContentView.
      //
      // Default layout values are MATCH_PARENT in both dimens, which is
      // perfect for phone.

      if (context.isTablet) {
        val displayMetrics = DisplayMetrics().also {
          @Suppress("DEPRECATION")
          dialog.context.defaultDisplay.getMetrics(it)
        }

        if (context.isPortrait) {
          dialog.window!!.setLayout(displayMetrics.widthPixels, displayMetrics.widthPixels)
        } else {
          dialog.window!!.setLayout(displayMetrics.heightPixels, displayMetrics.heightPixels)
        }
      }
    }
  }

  companion object : ScreenViewFactory<PanelContainerScreen<*, *>> by ManualScreenViewFactory(
      type = PanelContainerScreen::class,
      viewConstructor = { initialRendering, initialEnv, contextForNewView, _ ->
        PanelContainer(contextForNewView).apply {
          id = R.id.panel_container
          layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
          bindShowRendering(initialRendering, initialEnv, ::update)
        }
      }
  )
}
