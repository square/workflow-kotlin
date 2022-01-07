package com.squareup.sample.stubvisibility

import android.view.Gravity.CENTER
import android.view.View
import android.view.View.GONE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class ClickyTextRendering(
  val message: String,
  val visible: Boolean = true,
  val onClick: (() -> Unit)? = null
) : AndroidScreen<ClickyTextRendering> {
  override val viewFactory = ScreenViewFactory.of<ClickyTextRendering>(
    viewConstructor = { initialRendering, initialEnv, context, _ ->
      TextView(context).let { textView ->
        textView.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        textView.gravity = CENTER
        ScreenViewHolder(initialRendering, initialEnv, textView) { r, _ -> textView.update(r) }
      }
    }
  )
}

@OptIn(WorkflowUiExperimentalApi::class)
private fun TextView.update(clickyText: ClickyTextRendering) {
  text = clickyText.message
  isVisible = clickyText.visible
  setOnClickListener(
    clickyText.onClick?.let { oc -> OnClickListener { oc() } }
  )
}

private var View.isVisible: Boolean
  get() = visibility == VISIBLE
  set(value) {
    visibility = if (value) VISIBLE else GONE
  }
