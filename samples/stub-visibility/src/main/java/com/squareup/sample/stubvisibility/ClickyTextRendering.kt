package com.squareup.sample.stubvisibility

import android.view.Gravity.CENTER
import android.view.View
import android.view.View.GONE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering

@OptIn(WorkflowUiExperimentalApi::class)
data class ClickyTextRendering(
  val message: String,
  val visible: Boolean = true,
  val onClick: (() -> Unit)? = null
) : AndroidViewRendering<ClickyTextRendering> {
  override val viewFactory = BuilderViewFactory(
    type = ClickyTextRendering::class,
    viewConstructor = { initialRendering, initialEnv, context, _ ->
      TextView(context).also { textView ->
        textView.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        textView.gravity = CENTER

        textView.bindShowRendering() { clickyText, _ ->
          textView.text = clickyText.message
          textView.isVisible = clickyText.visible
          textView.setOnClickListener(
            clickyText.onClick?.let { oc -> OnClickListener { oc() } }
          )
        }
      }
    }
  )
}

private var View.isVisible: Boolean
  get() = visibility == VISIBLE
  set(value) {
    visibility = if (value) VISIBLE else GONE
  }
