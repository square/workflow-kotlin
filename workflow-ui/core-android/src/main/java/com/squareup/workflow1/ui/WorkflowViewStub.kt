/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow1.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes

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
 * Use [replaceOldViewInParent] to customize replacing [actual] with a new view, e.g.
 * for animated transitions.
 */
@WorkflowUiExperimentalApi
class WorkflowViewStub @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : View(context, attributeSet, defStyle, defStyleRes) {
  /**
   * On-demand access to the view created by the last call to [update],
   * or this [WorkflowViewStub] instance if none has yet been made.
   */
  var actual: View = this
    private set

  /**
   * If true, the visibility of views created by [update] will be copied
   * from that of [actual]. Bear in mind that the initial value of
   * [actual] is this stub.
   */
  var updatesVisibility: Boolean = true

  /**
   * The id to be assigned to new views created by [update]. If the inflated id is
   * [View.NO_ID] (its default value), new views keep their original ids.
   */
  @IdRes var inflatedId: Int = NO_ID
    set(value) {
      require(value == NO_ID || value != id) {
        "inflatedId and id must be distinct"
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
      "id and inflatedId must be distinct"
    }
    super.setId(id)
  }

  /**
   * Function called from [update] to replace this stub, or the current [actual],
   * with a new view. Can be updated to provide custom transition effects.
   *
   * Note that this method is responsible for copying the [layoutParams][getLayoutParams]
   * from the stub to the new view. Also note that in a [WorkflowViewStub] that has never
   * been updated, [actual] is the stub itself.
   */
  var replaceOldViewInParent: (ViewGroup, View) -> Unit = { parent, newView ->
    val index = parent.indexOfChild(actual)
    parent.removeView(actual)
    actual.layoutParams
        ?.let { parent.addView(newView, index, it) }
        ?: run { parent.addView(newView, index) }
  }

  /**
   * Sets the visibility of [actual]. If [updatesVisibility] is true, the visibility of
   * new views created by [update] will copied from [actual]. (Bear in mind that the initial
   * value of [actual] is this stub.)
   */
  override fun setVisibility(visibility: Int) {
    super.setVisibility(visibility)
    // actual can be null when called from the constructor.
    @Suppress("SENSELESS_COMPARISON")
    if (actual != this && actual != null) {
      actual.visibility = visibility
    }
  }

  /**
   * Returns the visibility of [actual]. (Bear in mind that the initial value of
   * [actual] is this stub.)
   */
  override fun getVisibility(): Int {
    // actual can be null when called from the constructor.
    @Suppress("SENSELESS_NULL_IN_WHEN")
    return when (actual) {
      this, null -> super.getVisibility()
      else -> actual.visibility
    }
  }

  /**
   * Sets the background of this stub as usual, and also that of [actual]
   * if the given [background] is not null. Any new views created by [update]
   * will be assigned this background, again if it is not null.
   */
  override fun setBackground(background: Drawable?) {
    super.setBackground(background)
    // actual can be null when called from the constructor.
    @Suppress("SENSELESS_COMPARISON")
    if (actual != this && actual != null && background != null) {
      actual.background = background
    }
  }

  /**
   * Replaces this view with one that can display [rendering]. If the receiver
   * has already been replaced, updates the replacement if it [canShowRendering].
   * If the current replacement can't handle [rendering], a new view is put in its place.
   *
   * The [id][View.setId] of any view created by this method will be set to to [inflatedId],
   * unless that value is [View.NO_ID].
   *
   * The [background][setBackground] of any view created by this method will be copied
   * from [getBackground], if that value is non-null.
   *
   * If [updatesVisibility] is true, the [visibility][setVisibility] of any view created by
   * this method will be copied from [actual]. (Bear in mind that the initial value of
   * [actual] is this stub.)
   *
   * @return the view that showed [rendering]
   *
   * @throws IllegalArgumentException if no binding can be find for the type of [rendering]
   *
   * @throws IllegalStateException if the matching
   * [ViewFactory][com.squareup.workflow1.ui.ViewFactory] fails to call
   * [View.bindShowRendering][com.squareup.workflow1.ui.bindShowRendering]
   * when constructing the view
   */
  fun update(
    rendering: Any,
    viewEnvironment: ViewEnvironment
  ): View {
    actual.takeIf { it.canShowRendering(rendering) }
        ?.let {
          it.showRendering(rendering, viewEnvironment)
          return it
        }

    val parent = actual.parent as? ViewGroup
        ?: throw IllegalStateException(
            "WorkflowViewStub must have a non-null ViewGroup parent"
        )

    return viewEnvironment[ViewRegistry].buildView(rendering, viewEnvironment, parent)
        .also { newView ->
          if (inflatedId != NO_ID) newView.id = inflatedId
          if (updatesVisibility) newView.visibility = visibility
          background?.let { newView.background = it }
          replaceOldViewInParent(parent, newView)
          actual = newView
        }
  }
}
