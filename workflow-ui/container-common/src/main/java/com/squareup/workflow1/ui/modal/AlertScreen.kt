// @file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.modal

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * **This will be deprecated in favor of
 * [AlertOverlay][com.squareup.workflow1.ui.container.AlertOverlay] very soon.**
 *
 * Models a typical "You sure about that?" alert box.
 */
@WorkflowUiExperimentalApi
// @Deprecated(
//   "Use AlertOverlay",
//   ReplaceWith(
//     "AlertOverlay(buttons, message, title, cancelable, onEvent)",
//     "com.squareup.workflow1.ui.container.AlertOverlay"
//   )
// )
public data class AlertScreen(
  val buttons: Map<Button, String> = emptyMap(),
  val message: String = "",
  val title: String = "",
  val cancelable: Boolean = true,
  val onEvent: (Event) -> Unit
) {
  public enum class Button {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
  }

  public sealed class Event {
    public data class ButtonClicked(val button: Button) : Event()

    public object Canceled : Event()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AlertScreen

    return buttons == other.buttons &&
      message == other.message &&
      title == other.title &&
      cancelable == other.cancelable
  }

  override fun hashCode(): Int {
    var result = buttons.hashCode()
    result = 31 * result + message.hashCode()
    result = 31 * result + title.hashCode()
    result = 31 * result + cancelable.hashCode()
    return result
  }
}
