@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner

/**
 * A placeholder [View] that can replace itself with ones driven by workflow renderings,
 * similar to [android.view.ViewStub].
 *
 * ## Usage
 *
 * In the XML layout for a container view, place a [WorkflowViewStub] where
 * you want child renderings to be displayed. E.g.:
 *
 *    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *         xmlns:app="http://schemas.android.com/apk/res-auto"
 *         …>
 *
 *        <com.squareup.workflow1.WorkflowViewStub
 *            android:id="@+id/child_stub"
 *            app:inflatedId="@+id/child"
 *            />
 *       …
 *
 * Then in your [LayoutRunner],
 *   - pull the view out with [findViewById] like any other view
 *   - and [update] it in your [LayoutRunner.showRendering] method:
 *
 * ```
 *     class YourLayoutRunner(view: View) {
 *       private val childStub = view.findViewById<WorkflowViewStub>(R.id.child_stub)
 *
 *       // Totally optional, since this view is also accessible as [childStub.actual].
 *       // Note that R.id.child was set in XML via the square:inflatedId parameter.
 *       private val child: View by lazy {
 *         view.findViewById<View>(R.id.child)
 *       }
 *
 *       override fun showRendering(
 *          rendering: YourRendering,
 *          viewEnvironment: ViewEnvironment
 *       ) {
 *         childStub.update(rendering.childRendering, viewEnvironment)
 *       }
 *     }
 * ```
 *
 * **NB**: If you're using a stub in a `RelativeLayout` or `ConstraintLayout`, relationships
 * should be tied to the stub's `app:inflatedId`, not its `android:id`.
 *
 * Use [updatesVisibility] and [setBackground] for more control of how [update]
 * effects the visibility and backgrounds of created views.
 *
 * Use [replaceOldViewInParent] to customize replacing the current view with a new one during
 * [show], e.g. for animated transitions.
 */
@WorkflowUiExperimentalApi
public class WorkflowViewStub @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : View(context, attributeSet, defStyle, defStyleRes) {
  /** Returns null if [update] hasn't been called yet. */
  private val delegateOrNull: View?
    get() {
      // can be null when called from the constructor.
      @Suppress("UNNECESSARY_SAFE_CALL")
      return delegateHolder?.view?.takeUnless { it === this }
    }

  public var delegateHolder: ScreenViewHolder<Screen> = object : ScreenViewHolder<Screen> {
    override val screen: Screen = object : Screen {}
    override val environment: ViewEnvironment = ViewEnvironment(mapOf())

    override val view: View = this@WorkflowViewStub

    override fun start() {
      throw UnsupportedOperationException()
    }

    override fun showScreen(
      screen: Screen,
      environment: ViewEnvironment
    ) {
      throw UnsupportedOperationException()
    }
  }
    private set

  @Deprecated("Use delegateHolder.view", ReplaceWith("delegateHolder.view"))
  public val actual: View
    get() = delegateHolder.view

  /**
   * If true, the visibility of new delegate views created by [update] will be copied
   * from the current one. The first delegate created will copy the visibility of
   * this stub.
   */
  public var updatesVisibility: Boolean = true

  /**
   * The id to be assigned to new views created by [update]. If the inflated id is
   * [View.NO_ID] (its default value), new views keep their original ids.
   */
  @IdRes public var inflatedId: Int = NO_ID
    set(value) {
      require(value == NO_ID || value != id) {
        "inflatedId must be distinct from id: ${resources.getResourceName(id)}"
      }
      field = value
    }

  init {
    val attrs = context.obtainStyledAttributes(
      attributeSet, R.styleable.WorkflowViewStub, defStyle, defStyleRes
    )
    inflatedId = attrs.getResourceId(R.styleable.WorkflowViewStub_inflatedId, NO_ID)
    updatesVisibility = attrs.getBoolean(R.styleable.WorkflowViewStub_updatesVisibility, true)
    attrs.recycle()

    setWillNotDraw(true)
  }

  override fun setId(@IdRes id: Int) {
    require(id == NO_ID || id != inflatedId) {
      "id must be distinct from inflatedId: ${resources.getResourceName(id)}"
    }
    super.setId(id)
  }

  /**
   * Function called from [update] to replace this stub, or its current delegate,
   * with a new view. Can be updated to provide custom transition effects.
   *
   * Note that this method is responsible for copying the [layoutParams][getLayoutParams]
   * from the stub to the new view.
   */
  public var replaceOldViewInParent: (ViewGroup, View) -> Unit = { parent, newView ->
    val actual = delegateHolder.view

    val index = parent.indexOfChild(actual)
    parent.removeView(actual)
    actual.layoutParams
      ?.let { parent.addView(newView, index, it) }
      ?: run { parent.addView(newView, index) }
  }

  /**
   * Sets the visibility of the delegate, or of this stub if [show] has not yet been called.
   *
   * @see updatesVisibility
   */
  override fun setVisibility(visibility: Int) {
    super.setVisibility(visibility)
    delegateOrNull?.takeUnless { it == this }?.let {
      it.visibility = visibility
    }
  }

  /**
   * Returns the visibility of the delegate, or of this stub if [show] has not yet been called.
   */
  override fun getVisibility(): Int {
    return delegateOrNull?.visibility ?: super.getVisibility()
  }

  /**
   * Sets the background of this stub as usual, and also that of the delegate view,
   * if the given [background] is not null. Any new delegates created by [update]
   * will be assigned this background, again if it is not null.
   */
  override fun setBackground(background: Drawable?) {
    super.setBackground(background)
    if (background != null) delegateOrNull?.background = background
  }

  @Deprecated("Use show()", ReplaceWith("show(rendering, viewEnvironment)"))
  public fun update(
    rendering: Any,
    viewEnvironment: ViewEnvironment
  ): View {
    @Suppress("DEPRECATION")
    show(asScreen(rendering), viewEnvironment)
    return delegateHolder.view
  }

  /**
   * Replaces this view with a [delegate][delegateHolder] that can display [screen].
   * If [show] has already been called previously, updates the current delegate if it
   * [canShowScreen]. If the current delegate can't handle [screen], a new view
   * is put in its place.
   *
   * The [id][View.setId] of any delegate view created by this method will be set to to [inflatedId],
   * unless that value is [View.NO_ID].
   *
   * The [background][setBackground] of any delegate view created by this method will be copied
   * from [getBackground], if that value is non-null.
   *
   * If [updatesVisibility] is true, the [visibility][setVisibility] of any delegate view created
   * by this method will be copied from [getVisibility].
   *
   * @return the view that showed [screen]
   *
   * @throws IllegalArgumentException if no binding can be found for the type of [screen]
   */
  public fun show(
    screen: Screen,
    viewEnvironment: ViewEnvironment
  ) {
    delegateHolder.takeIf { it.canShowScreen(screen) }
      ?.let {
        it.showScreen(screen, viewEnvironment)
        return
      }

    val parent = delegateHolder.view.parent as? ViewGroup
      ?: throw IllegalStateException("WorkflowViewStub must have a non-null ViewGroup parent")

    // If we have a delegate view, then the old delegate is going to eventually be detached by
    // replaceOldViewInParent. When that happens, it's not just a regular detach, it's a navigation
    // event that effectively says that view will never come back. Thus, we want its Lifecycle to
    // move to permanently destroyed, even though the parent lifecycle is still probably alive.
    //
    // If there isn't a delegate, we're a child of another container which set a
    // WorkflowLifecycleOwner on this view, this get() call will return the WLO owned by that
    // parent. We noop in that case since destroying that lifecycle is our parent's responsibility
    // in that case, not ours.
    delegateOrNull?.let {
      WorkflowLifecycleOwner.get(it)?.destroyOnDetach()
    }

    val newWorkflowView = screen.buildView(
      viewEnvironment,
      parent.context,
      parent
    ).withStarter { view, doStart ->
      WorkflowLifecycleOwner.installOn(view.view)
      doStart()
    }
    newWorkflowView.start()

    val newAndroidView = newWorkflowView.view

    if (inflatedId != NO_ID) newAndroidView.id = inflatedId
    if (updatesVisibility) newAndroidView.visibility = visibility
    background?.let { newAndroidView.background = it }
    propagateSavedStateRegistryOwner(newAndroidView)
    replaceOldViewInParent(parent, newAndroidView)
    delegateHolder = newWorkflowView
  }

  /**
   * If a [ViewTreeSavedStateRegistryOwner] was set on this [WorkflowViewStub], sets that owner on
   * [newView]. Note that this _only_ copies an owner if it was set _directly_ on this view with
   * [ViewTreeSavedStateRegistryOwner.set]. If [ViewTreeSavedStateRegistryOwner.get] would return an
   * owner that was set on a parent view, this method does nothing.
   *
   * Must be called before [newView] gets attached to the window.
   */
  private fun propagateSavedStateRegistryOwner(newView: View) {
    // There's no way to ask for the owner only on this view, without looking up the tree, so
    // we have to compare the results from searching from this view to searching from our parent
    // (if we have a parent) to determine if we have our own owner.
    val myStateRegistryOwner = ViewTreeSavedStateRegistryOwner.get(this)
    val parentStateRegistryOwner =
      (this.parent as? ViewGroup)?.let(ViewTreeSavedStateRegistryOwner::get)
    if (myStateRegistryOwner !== parentStateRegistryOwner) {
      // Someone has set an owner on the stub itself, so we need to also set it on the new
      // subview.
      ViewTreeSavedStateRegistryOwner.set(newView, myStateRegistryOwner)
    }
  }
}
