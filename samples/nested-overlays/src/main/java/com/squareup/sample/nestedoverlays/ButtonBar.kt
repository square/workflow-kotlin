package com.squareup.sample.nestedoverlays

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.EditText
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
  val showEditText: Boolean = false,
) : AndroidScreen<ButtonBar> {
  private val buttons: List<Button> = buttons.filterNotNull().toList()

  override val viewFactory =
    ScreenViewFactory.fromCode<ButtonBar> { _, initialEnvironment, context, _ ->
      LinearLayout(context).let { view ->
        // Child 0 is always an EditText, which may or may not be visible.
        val editText = EditText(context)
        editText.id = R.id.button_bar_text
        view.addView(editText)

        view.gravity = Gravity.CENTER

        ScreenViewHolder(initialEnvironment, view) { newBar, _ ->
          @Suppress("DEPRECATION")
          view.background =
            if (newBar.color > -1) ColorDrawable(view.resources.getColor(newBar.color)) else null

          editText.visibility = if (newBar.showEditText) VISIBLE else GONE

          // After the EditText, an arbitrary number of ButtonView.
          val existingButtonCount = view.childCount - 1

          newBar.buttons.forEachIndexed { index, button ->
            val buttonView = if (index < existingButtonCount) {
              view[index + 1] as ButtonView
            } else {
              ButtonView(context).also { view.addView(it) }
            }
            with(buttonView) {
              text = view.resources.getText(button.name)
              setOnClickListener { button.onClick() }
            }
          }
          for (i in newBar.buttons.size + 1 until view.childCount) view.removeViewAt(i)
        }
      }
    }
}
