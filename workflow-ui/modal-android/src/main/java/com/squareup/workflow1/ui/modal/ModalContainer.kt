package com.squareup.workflow1.ui.modal

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.lifecycleOrNull

/**
 * Base class for containers that show [HasModals.modals] in [Dialog] windows.
 *
 * @param ModalRenderingT the type of the nested renderings to be shown in a dialog window.
 */
@WorkflowUiExperimentalApi
public abstract class ModalContainer<ModalRenderingT : Any> @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {

  private val baseViewStub: WorkflowViewStub = WorkflowViewStub(context).also {
    addView(it, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
  }

  private var dialogs: List<DialogRef<ModalRenderingT>> = emptyList()

  protected fun update(
    newScreen: HasModals<*, ModalRenderingT>,
    viewEnvironment: ViewEnvironment
  ) {
    baseViewStub.update(newScreen.beneathModals, viewEnvironment)

    val newDialogs = mutableListOf<DialogRef<ModalRenderingT>>()
    for ((i, modal) in newScreen.modals.withIndex()) {
      newDialogs += if (i < dialogs.size && compatible(dialogs[i].modalRendering, modal)) {
        dialogs[i].copy(modalRendering = modal, viewEnvironment = viewEnvironment)
            .also { updateDialog(it) }
      } else {
        buildDialog(modal, viewEnvironment).apply {
          dialog.window?.decorView?.addOnAttachStateChangeListener(
              object : OnAttachStateChangeListener {
                val onDestroy = OnDestroy { dialog.dismiss() }
                var lifecycle: Lifecycle? = null
                override fun onViewAttachedToWindow(v: View) {
                  lifecycle = dialog.lifecycleOrNull()
                  // Android makes a lot of logcat noise if it has to close the window for us. :/
                  // https://github.com/square/workflow/issues/51
                  lifecycle?.addObserver(onDestroy)
                }

                override fun onViewDetachedFromWindow(v: View) {
                  lifecycle?.removeObserver(onDestroy)
                  lifecycle = null
                }
              }
          )
          dialog.show()
        }
      }
    }

    (dialogs - newDialogs).forEach { it.dialog.dismiss() }
    dialogs = newDialogs
  }

  /**
   * Called to create (but not show) a Dialog to render [initialModalRendering].
   */
  protected abstract fun buildDialog(
    initialModalRendering: ModalRenderingT,
    initialViewEnvironment: ViewEnvironment
  ): DialogRef<ModalRenderingT>

  protected abstract fun updateDialog(dialogRef: DialogRef<ModalRenderingT>)

  override fun onSaveInstanceState(): Parcelable {
    return SavedState(
        super.onSaveInstanceState()!!,
        dialogs.map { it.save() }
    )
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    (state as? SavedState)
        ?.let {
          if (it.dialogBundles.size == dialogs.size) {
            it.dialogBundles.zip(dialogs) { viewState, dialogRef -> dialogRef.restore(viewState) }
          }
          super.onRestoreInstanceState(state.superState)
        }
        ?: super.onRestoreInstanceState(state)
  }

  internal data class KeyAndBundle(
    val compatibilityKey: String,
    val bundle: Bundle
  ) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(
      parcel: Parcel,
      flags: Int
    ) {
      parcel.writeString(compatibilityKey)
      parcel.writeBundle(bundle)
    }

    companion object CREATOR : Creator<KeyAndBundle> {
      override fun createFromParcel(parcel: Parcel): KeyAndBundle {
        val key = parcel.readString()!!
        val bundle = parcel.readBundle(KeyAndBundle::class.java.classLoader)!!
        return KeyAndBundle(key, bundle)
      }

      override fun newArray(size: Int): Array<KeyAndBundle?> = arrayOfNulls(size)
    }
  }

  /**
   * @param extra optional hook to allow subclasses to associate extra data with this dialog,
   * e.g. its content view. Not considered for equality.
   */
  @WorkflowUiExperimentalApi
  protected data class DialogRef<ModalRenderingT : Any>(
    val modalRendering: ModalRenderingT,
    val viewEnvironment: ViewEnvironment,
    val dialog: Dialog,
    val extra: Any? = null
  ) {
    internal fun save(): KeyAndBundle {
      val saved = dialog.window!!.saveHierarchyState()
      return KeyAndBundle(Named.keyFor(modalRendering), saved)
    }

    internal fun restore(keyAndBundle: KeyAndBundle) {
      if (Named.keyFor(modalRendering) == keyAndBundle.compatibilityKey) {
        dialog.window!!.restoreHierarchyState(keyAndBundle.bundle)
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as DialogRef<*>

      if (dialog != other.dialog) return false

      return true
    }

    override fun hashCode(): Int {
      return dialog.hashCode()
    }
  }

  private class SavedState : BaseSavedState {
    constructor(
      superState: Parcelable?,
      dialogBundles: List<KeyAndBundle>
    ) : super(superState) {
      this.dialogBundles = dialogBundles
    }

    constructor(source: Parcel) : super(source) {
      @Suppress("UNCHECKED_CAST")
      this.dialogBundles = mutableListOf<KeyAndBundle>().apply {
        source.readTypedList(this, KeyAndBundle)
      }
    }

    val dialogBundles: List<KeyAndBundle>

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      super.writeToParcel(out, flags)
      @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
      out.writeTypedList(dialogBundles)
    }

    companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState =
        SavedState(source)

      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }
}

private class OnDestroy(private val block: () -> Unit) : LifecycleObserver {
  @OnLifecycleEvent(ON_DESTROY)
  fun onDestroy() = block()
}

@WorkflowUiExperimentalApi
private fun Dialog.lifecycleOrNull(): Lifecycle? = decorView?.context?.lifecycleOrNull()

private val Dialog.decorView: View?
  get() = window?.decorView
