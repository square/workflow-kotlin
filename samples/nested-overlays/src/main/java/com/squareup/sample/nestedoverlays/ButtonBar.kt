package com.squareup.sample.nestedoverlays

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.view.get
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import android.widget.Button as ButtonView

data class Button(
  @StringRes val name: Int,
  val onClick: () -> Unit
)

@OptIn(WorkflowUiExperimentalApi::class)
class ButtonBar(
  vararg buttons: Button?,
  @ColorRes val color: Int = -1,
) : AndroidScreen<ButtonBar> {
  val buttons: List<Button> = buttons.filterNotNull().toList()

  override val viewFactory =
    ScreenViewFactory.fromCode<ButtonBar> { _, initialEnvironment, context, _ ->
      LinearLayout(context).let { view ->
        if (color > -1) view.background = ColorDrawable(view.resources.getColor(color))

        view.gravity = Gravity.CENTER

        ScreenViewHolder(initialEnvironment, view) { bar, _ ->
          val existing = view.childCount

          bar.buttons.forEachIndexed { index, button ->
            val buttonView = if (index < existing) {
              view[index] as ButtonView
            } else {
              ButtonView(context).also { view.addView(it) }
            }
            with(buttonView) {
              text = view.resources.getText(button.name)
              setOnClickListener { button.onClick() }
            }
          }
          for (i in bar.buttons.size until view.childCount) view.removeViewAt(i)
        }
      }
    }
}
