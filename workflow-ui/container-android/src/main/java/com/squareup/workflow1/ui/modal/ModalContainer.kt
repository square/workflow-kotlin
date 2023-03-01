@file:Suppress("DEPRECATION")

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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.getRendering

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

  /**
   * Stores the result of looking for the nearest [LifecycleOwner] that should be the parent of all
   * this container's modals. Only valid after the the view has been attached.
   */
  private val parentLifecycleOwner by lazy(mode = LazyThreadSafetyMode.NONE) {
    WorkflowLifecycleOwner.get(this) ?: error(
      "Expected to find either a ViewTreeLifecycleOwner in the view tree, or for the " +
        "context to be a LifecycleOwner, in $this"
    )
  }

  /**
   * Provides a new `SavedStateRegistryOwner` for each dialog,
   * which will save to the `SavedStateRegistryOwner` of this container view.
   */
  private val stateRegistryAggregator = WorkflowSavedStateRegistryAggregator()

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
        buildDialog(modal, viewEnvironment).also { ref ->
          // This is the unique id we'll use to make stateRegistryAggregator put a
          // SavedStateRegistryOwner on the dialog's decorView, as required by Compose.
          // We put it in this sketchy lateInit field on DialogRef rather than passing
          // it through abstract buildDialog method so that subclasses can't screw it up.
          ref.savedStateRegistryKey = Compatible.keyFor(modal, i.toString())

          ref.dialog.decorView?.let { dialogView ->
            // Implementations of buildDialog may set their own WorkflowLifecycleOwner on the
            // content view, so to avoid interfering with them we also set it here. When the views
            // are attached, this will become the parent lifecycle of the one from buildDialog if
            // any, and so we can use our lifecycle to destroy-on-detach the dialog hierarchy.
            WorkflowLifecycleOwner.installOn(
              dialogView,
              findParentLifecycle = { parentLifecycleOwner.lifecycle }
            )
            // Ensure that each dialog has its own SavedStateRegistryOwner,
            // so views in each dialog layer don't clash with other layers.
            stateRegistryAggregator.installChildRegistryOwnerOn(
              view = dialogView,
              key = ref.savedStateRegistryKey
            )

            dialogView.addOnAttachStateChangeListener(
              object : OnAttachStateChangeListener {
                val dismissOnDestroy = object : DefaultLifecycleObserver {
                  override fun onDestroy(owner: LifecycleOwner) = ref.dismiss()
                }
                var lifecycle: Lifecycle? = null
                override fun onViewAttachedToWindow(v: View) {
                  // Note this is a different lifecycle than the WorkflowLifecycleOwner – it will
                  // probably be the owning AppCompatActivity.
                  lifecycle = parentLifecycleOwner.lifecycle.also {
                    // Android makes a lot of logcat noise if it has to close the window for us. :/
                    // https://github.com/square/workflow/issues/51
                    it.addObserver(dismissOnDestroy)
                  }
                }

                override fun onViewDetachedFromWindow(v: View) {
                  lifecycle?.removeObserver(dismissOnDestroy)
                  lifecycle = null
                }
              }
            )
          }
          ref.dialog.show()
        }
      }
    }

    (dialogs - newDialogs).forEach { it.dismiss() }
    // Drop the state registries for any keys that no longer exist since the last save.
    // Or really, drop everything except the remaining ones.
    stateRegistryAggregator.pruneAllChildRegistryOwnersExcept(
      keysToKeep = newDialogs.map { it.savedStateRegistryKey }
    )
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
      ?: super.onRestoreInstanceState(super.onSaveInstanceState())
    // ?: Some other class wrote state, but we're not allowed to skip
    // the call to super. Make a no-op call.
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    val parentRegistry = stateRegistryOwnerFromViewTreeOrContext(this)
    val key = Compatible.keyFor(this.getRendering()!!)
    stateRegistryAggregator.attachToParentRegistry(key, parentRegistry)
  }

  override fun onDetachedFromWindow() {
    stateRegistryAggregator.detachFromParentRegistry()
    super.onDetachedFromWindow()
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
  protected class DialogRef<ModalRenderingT : Any>(
    public val modalRendering: ModalRenderingT,
    public val viewEnvironment: ViewEnvironment,
    public val dialog: Dialog,
    public val extra: Any? = null
  ) {
    /**
     * The unique id of the `SavedStateRegistryOwner` that will be placed
     * on the dialog's decor view by [stateRegistryAggregator].
     */
    internal lateinit var savedStateRegistryKey: String

    public fun copy(
      modalRendering: ModalRenderingT = this.modalRendering,
      viewEnvironment: ViewEnvironment = this.viewEnvironment,
      dialog: Dialog = this.dialog,
      extra: Any? = this.extra
    ): DialogRef<ModalRenderingT> = DialogRef(modalRendering, viewEnvironment, dialog, extra).also {
      it.savedStateRegistryKey = savedStateRegistryKey
    }

    internal fun save(): KeyAndBundle {
      val saved = dialog.window!!.saveHierarchyState()
      return KeyAndBundle(Compatible.keyFor(modalRendering), saved)
    }

    internal fun restore(keyAndBundle: KeyAndBundle) {
      if (Compatible.keyFor(modalRendering) == keyAndBundle.compatibilityKey) {
        dialog.window!!.restoreHierarchyState(keyAndBundle.bundle)
      }
    }

    /**
     * Call this instead of calling `dialog.dismiss()` directly – this method ensures that the modal's
     * [WorkflowLifecycleOwner] is destroyed correctly.
     */
    internal fun dismiss() {
      // The dialog's views are about to be detached, and when that happens we want to transition
      // the dialog view's lifecycle to a terminal state even though the parent is probably still
      // alive.
      dialog.decorView?.let(WorkflowLifecycleOwner::get)?.destroyOnDetach()
      dialog.dismiss()
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
      dialogBundles = mutableListOf<KeyAndBundle>().apply {
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

private val Dialog.decorView: View?
  get() = window?.decorView
