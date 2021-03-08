package com.squareup.workflow1.ui.modal

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.savedstate.SavedStateRegistry
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewStateFrame
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.SavedStateRegistryClient
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.compositeViewIdKey
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.createSavedStateRegistryClient
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.lifecycleOwnerFromViewTreeOrContext
import com.squareup.workflow1.ui.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.compatible
import kotlin.LazyThreadSafetyMode.NONE

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

  /**
   * Wrap the [baseViewStub] in another view, since [WorkflowViewStub] swaps itself out for its
   * child view, but we need a stable reference to its view subtree to set up ViewTreeOwners.
   * Wrapping with a container view is simpler than trying to make always make sure we keep
   * [baseViewStub]'s [actual][WorkflowViewStub.actual] view updated.
   *
   * Note that this isn't a general problem that needs to be solved for WVS, it's only because we're
   * using it as a direct child of a container. This can't happen through the ViewRegistry since
   * no view factories should ever return a WVS.
   */
  private val baseContainer = FrameLayout(context).also {
    // We need to install here so that the baseStateFrame can read it.
    // We never need to call destroyOnDetach for this owner though, since we never replace it and
    // so the only way it can be destroyed is if our parent lifecycle gets destroyed.
    WorkflowLifecycleOwner.installOn(it)
    addView(it, LayoutParams(MATCH_PARENT, MATCH_PARENT))
  }

  private val baseViewStub: WorkflowViewStub = WorkflowViewStub(context).also {
    baseContainer.addView(it, LayoutParams(MATCH_PARENT, MATCH_PARENT))
  }

  private var dialogs: List<DialogRef<ModalRenderingT>> = emptyList()
  private val parentLifecycleOwner by lazy(NONE) { WorkflowLifecycleOwner.get(this) }

  private val baseStateFrame = ViewStateFrame("base").also {
    it.attach(baseContainer)
  }

  private var isRestored = false
  private val stateRegistryKey by lazy(NONE) {
    val stateRegistryPrefix = compositeViewIdKey(this)
    "$stateRegistryPrefix/${ModalContainer::class.java.simpleName}"
  }
  private lateinit var parentStateRegistry: SavedStateRegistry
  private val registryClient = createSavedStateRegistryClient(
    object : SavedStateRegistryClient {
      override val savedStateRegistryKey: String
        get() = stateRegistryKey

      override fun onSaveToRegistry(bundle: Bundle) {
        saveToBundle(bundle)
      }

      override fun onRestoreFromRegistry(bundle: Bundle?) {
        restoreFromBundle(bundle)
      }
    }
  ).also { addOnAttachStateChangeListener(it) }

  private var parentLifecycle: Lifecycle? = null
  private val lifecycleListener = LifecycleEventObserver { _, event ->
    if (event == ON_START) {
      onLifecycleStarted()
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  internal val dialogDecorViews: List<View?>
    get() = dialogs.map { it.dialog.decorView }

  protected fun update(
    newScreen: HasModals<*, ModalRenderingT>,
    viewEnvironment: ViewEnvironment
  ) {
    baseViewStub.update(newScreen.beneathModals, viewEnvironment)

    val newDialogs = mutableListOf<DialogRef<ModalRenderingT>>()
    for ((i, modal) in newScreen.modals.withIndex()) {
      newDialogs += if (i < dialogs.size && compatible(dialogs[i].modalRendering, modal)) {
        dialogs[i].withUpdate(modal, viewEnvironment)
          .also {
            updateDialog(it)
          }
      } else {
        buildDialog(modal, viewEnvironment).also { ref ->
          ref.dialog.decorView?.let { dialogView ->
            // Implementations of buildDialog may use ViewRegistry.buildView, which will set the
            // WorkflowLifecycleOwner on the content view, but since we can't rely on that we also
            // set it here. When the views are attached, this will become the parent lifecycle of
            // the one from buildDialog if any, and so we can use our lifecycle to destroy-on-detach
            // the dialog hierarchy.
            WorkflowLifecycleOwner.installOn(
              dialogView,
              findParentLifecycle = { parentLifecycleOwner?.lifecycle }
            )

            ref.initFrame()
            // This must be done after installing the WLO, because it assumes that the WLO has
            // already been installed.
            ref.frame.attach(dialogView)
            if (isRestored) {
              // Need to initialize the saved state registry even though we're not actually
              // restoring. However if we haven't been restored yet, then this call will be made
              // by restoreFromBundle.
              ref.frame.restoreAndroidXStateRegistry()
            }

            dialogView.addOnAttachStateChangeListener(
              object : OnAttachStateChangeListener {
                val onDestroy = OnDestroy { ref.dismiss() }
                var lifecycle: Lifecycle? = null
                override fun onViewAttachedToWindow(v: View) {
                  // Note this is a different lifecycle than the WorkflowLifecycleOwner – it will
                  // probably be the owning AppCompatActivity.
                  lifecycle = ref.dialog.decorView
                    ?.let(::lifecycleOwnerFromViewTreeOrContext)
                    ?.lifecycle
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
          }
          ref.dialog.show()
        }
      }
    }

    linkModalViewTreeOwners(newDialogs)

    (dialogs - newDialogs).forEach { it.dismiss() }
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

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    linkModalViewTreeOwners(dialogs)
    parentLifecycle = lifecycleOwnerFromViewTreeOrContext(this)!!.lifecycle
      .apply { addObserver(lifecycleListener) }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    parentLifecycle?.removeObserver(lifecycleListener)
  }

  private fun saveToBundle(bundle: Bundle) {
    // Don't need to save the base view's hierarchy state, the view system will automatically do
    // that.
    baseStateFrame.performSave(saveViewHierarchyState = false)
    bundle.putParcelable("base", baseStateFrame)

    dialogs.forEachIndexed { index, dialogRef ->
      val windowState = dialogRef.dialog.window!!.saveHierarchyState()
      bundle.putBundle("$index-window", windowState)

      // Don't save the hierarchy state since we had to make that call ourselves on the window
      // instead of the view, immediately above.
      dialogRef.frame.performSave(saveViewHierarchyState = false)
      bundle.putParcelable("$index-frame", dialogRef.frame)
    }
  }

  /**
   * This is called as soon as the view is attached and the lifecycle is in the CREATED state.
   */
  private fun restoreFromBundle(bundle: Bundle?) {
    require(!isRestored) { "Expected restoreFromBundle to only be called once." }
    isRestored = true

    if (bundle == null) {
      // This always has to be called so consume doesn't throw, even if we don't actually have
      // anything to restore.
      baseStateFrame.restoreAndroidXStateRegistry()
    } else {
      val restoredBaseFrame = bundle.getParcelable<ViewStateFrame>("base")!!
      baseStateFrame.loadAndroidXStateRegistryFrom(restoredBaseFrame)
      baseStateFrame.restoreAndroidXStateRegistry()
      // Don't need to restore the hierarchy state, the view system will automatically do that.
    }

    dialogs.forEachIndexed { index, dialogRef ->
      val frame = bundle?.getParcelable<ViewStateFrame>("$index-frame")

      // This will no-op if there's no frame to restore…
      dialogRef.frame.loadAndroidXStateRegistryFrom(frame)
      // …but this must be called every time to ensure that consume doesn't throw.
      dialogRef.frame.restoreAndroidXStateRegistry()

      // Can't call dialogRef.frame.restoreViewHierarchyState() yet. We get called when the
      // lifecycle becomes CREATED, but we can only safely call restoreHierarchyState after it
      // becomes STARTED.
      dialogRef.hierarchyState = bundle?.getBundle("$index-window")
    }
  }

  /** @see [linkViewTreeOwners] */
  private fun linkModalViewTreeOwners(dialogs: List<DialogRef<*>>) {
    linkViewTreeOwners(this, dialogs.asSequence().map {
      Pair(it.dialog.decorView!!, it.frame)
    })
  }

  /**
   * Restore all our modal views' hierarchy state. Note that we don't override
   * [onSaveInstanceState] because we just rely on the [SavedStateRegistry] save hook to save
   * hierarchy state. The only reason we need to override this is because the order of calls for
   * restoration is more sensitive – the registry gets restored before `onStart`, but hierarchy
   * state gets restored _after_ `onStart`.
   *
   * We can't override [onRestoreInstanceState] since we have no state of our own to save, so we
   * won't even get that call. We can't override [dispatchRestoreInstanceState] because that call
   * comes too early – before we're attached and have restored ourselves from the registry.
   *
   * Note that this will call [Window.restoreHierarchyState] _after_ an entire restore pass has
   * already been done. In our tests, so far, this doesn't seem to be an issue, but theoretically
   * if a view assumed [onRestoreInstanceState] would only be called once we could break it.
   */
  private fun onLifecycleStarted() {
    // By this time, restoreFromBundle will have already been called so the frame is already
    // restored and just needs to be applied to the view.
    dialogs.forEach { dialogRef ->
      dialogRef.hierarchyState?.let {
        dialogRef.dialog.window!!.restoreHierarchyState(it)
      }
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
    private val key get() = Named.keyFor(modalRendering, "modal")

    internal lateinit var frame: ViewStateFrame

    /**
     * The [Bundle] returned from [Window.saveHierarchyState] on the [Dialog.getWindow].
     */
    internal var hierarchyState: Bundle? = null

    internal fun initFrame() {
      frame = ViewStateFrame(key)
    }

    internal fun withUpdate(
      rendering: ModalRenderingT,
      environment: ViewEnvironment
    ) = DialogRef(rendering, environment, dialog, extra).also {
      it.frame = frame
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
}

private class OnDestroy(private val block: () -> Unit) : LifecycleObserver {
  @OnLifecycleEvent(ON_DESTROY)
  fun onDestroy() = block()
}

private val Dialog.decorView: View?
  get() = window?.decorView
