package com.squareup.workflow1.ui

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

@WorkflowUiExperimentalApi
class ModalContainerView
@JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {

  private val baseView: WorkflowViewStub = WorkflowViewStub(context).also {
    addView(it, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
  }

  private var dialogs: List<Dialog> = emptyList()

  private fun update(
    newScreen: ModalContainerViewRendering<*, *>,
    viewEnvironment: ViewEnvironment
  ) {
    baseView.show(newScreen.beneathModals, viewEnvironment)

    val updateDialogs = mutableListOf<Dialog>()
    for ((i, modalRendering) in newScreen.modals.withIndex()) {
      updateDialogs +=
        if (i < dialogs.size && compatible(dialogs[i].getDisplaying()!!, modalRendering)) {
          dialogs[i].apply { display(modalRendering, viewEnvironment) }
        } else {
          modalRendering.buildDialog(viewEnvironment, context).apply {
            show()
            // Android makes a lot of logcat noise if it has to close the window for us. :/
            // https://github.com/square/workflow/issues/51
            lifecycleOrNull()?.addObserver(OnDestroy { dismiss() })
          }
        }
    }

    (dialogs - updateDialogs).forEach { it.dismiss() }
    dialogs = updateDialogs
  }

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

  private fun Dialog.save(): KeyAndBundle {
    val saved = window!!.saveHierarchyState()
    return KeyAndBundle(NamedCompatible.keyFor(getDisplaying()!!), saved)
  }

  private fun Dialog.restore(keyAndBundle: KeyAndBundle) {
    if (NamedCompatible.keyFor(getDisplaying()!!) == keyAndBundle.compatibilityKey) {
      window!!.restoreHierarchyState(keyAndBundle.bundle)
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
      out.writeTypedList(dialogBundles)
    }

    companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState =
        SavedState(source)

      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }

  companion object : ViewBuilder<ModalContainerViewRendering<*, *>>
  by BespokeViewBuilder(
      type = ModalContainerViewRendering::class,
      constructor = { initialRendering, initialEnv, context, _ ->
        ModalContainerView(context).apply {
          id = R.id.modal_container_view
          layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
          bindShowRendering(initialRendering, initialEnv, ::update)
        }
      }
  )
}

private class OnDestroy(private val block: () -> Unit) : LifecycleObserver {
  @OnLifecycleEvent(ON_DESTROY)
  fun onDestroy() = block()
}

@WorkflowUiExperimentalApi
private fun Dialog.lifecycleOrNull(): Lifecycle? = decorView?.context?.lifecycleOrNull()

private val Dialog.decorView: View?
  get() = window?.decorView
