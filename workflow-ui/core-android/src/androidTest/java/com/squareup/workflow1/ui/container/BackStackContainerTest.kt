package com.squareup.workflow1.ui.container

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.util.SparseArray
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.showing
import org.junit.Rule
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackContainerTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  private data class Rendering(val name: String) : Compatible, AndroidScreen<Rendering> {
    override val compatibilityKey = name
    override val viewFactory: ScreenViewFactory<Rendering>
      get() = ScreenViewFactory.fromCode { _, initialRendering, context, _ ->
        ScreenViewHolder(
          initialRendering,
          EditText(context).apply {
            // Must have an id to participate in view persistence.
            id = 65
          }
        ) { _, _ -> }
      }
  }

  @Test fun savedStateParcelingWorks() {
    scenario.onActivity { activity ->
      val originalView = VisibleBackStackContainer(activity).apply {
        // Must have an id to participate in view persistence.
        id = 42
      }
      val holder1 = ScreenViewHolder<BackStackScreen<*>>(EMPTY, originalView) { r, e ->
        originalView.update(r, e)
      }

      // Show "able".
      holder1.show(BackStackScreen(Rendering("able")), EMPTY)
      // Type "first" into the rendered EditText.
      (originalView.getChildAt(0) as EditText).text = "first".toEditable()
      // Push "baker" on top of "able".
      holder1.show(BackStackScreen(Rendering("able"), Rendering("baker")), EMPTY)
      // Type "second" into the replacement rendered EditText.
      (originalView.getChildAt(0) as EditText).text = "second".toEditable()

      // Save the view state to a ByteArray and read it out again, exercising all of
      // the Parcel machinery.
      val savedArray = SparseArray<Parcelable>()
      originalView.saveHierarchyState(savedArray)
      val bytes = Parcel.obtain().let { parcel ->
        parcel.writeSparseArray(savedArray)
        parcel.marshall().also { parcel.recycle() }
      }
      val restoredArray = Parcel.obtain().let { parcel ->
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
          parcel.readSparseArray(this::class.java.classLoader, Parcelable::class.java)!!
            .also { parcel.recycle() }
        } else {
          @Suppress("DEPRECATION")
          parcel.readSparseArray<Parcelable>(this::class.java.classLoader)!!
            .also { parcel.recycle() }
        }
      }

      // Create a new BackStackContainer with the same id as the original
      val restoredView = VisibleBackStackContainer(activity).apply { id = 42 }
      val restoredHolder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, restoredView) { r, e ->
        restoredView.update(r, e)
      }
      // Have it render the same able > baker back stack that we last showed in the original.
      restoredHolder.show(BackStackScreen(Rendering("able"), Rendering("baker")), EMPTY)
      // Restore the view hierarchy.
      restoredView.restoreHierarchyState(restoredArray)
      // Android took care of restoring the text that was last shown.
      assertThat((restoredView.getChildAt(0) as EditText).text.toString()).isEqualTo("second")
      // Pop back to able.
      restoredHolder.show(BackStackScreen(Rendering("able")), EMPTY)
      // BackStackContainer restored the text we had typed on that.
      assertThat((restoredView.getChildAt(0) as EditText).text.toString()).isEqualTo("first")
    }
  }

  @Test fun firstScreenIsRendered() {
    scenario.onActivity { activity ->
      val view = VisibleBackStackContainer(activity)
      val holder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, view) { r, e ->
        view.update(r, e)
      }

      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      val showing = view.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("able"))
    }
  }

  @Test fun secondScreenIsRendered() {
    scenario.onActivity { activity ->
      val view = VisibleBackStackContainer(activity)
      val holder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, view) { r, e ->
        view.update(r, e)
      }

      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("baker")), EMPTY)
      val showing = view.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("baker"))
    }
  }

  @Test fun thirdScreenIsRendered() {
    scenario.onActivity { activity ->
      val view = VisibleBackStackContainer(activity)
      val holder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, view) { r, e ->
        view.update(r, e)
      }

      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("baker")), EMPTY)
      holder.show(BackStackScreen(Rendering("charlie")), EMPTY)
      val showing = view.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("charlie"))

      // This used to fail because of our naive use of TransitionManager. The
      // transition from baker view to charlie view was dropped because the
      // transition from able view to baker view was still in progress.
    }
  }

  @Test fun isDebounced() {
    scenario.onActivity { activity ->
      val view = VisibleBackStackContainer(activity)
      val holder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, view) { r, e ->
        view.update(r, e)
      }

      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("able")), EMPTY)

      assertThat(view.transitionCount).isEqualTo(1)
    }
  }

  private class VisibleBackStackContainer(context: Context) : BackStackContainer(context) {
    var transitionCount = 0

    val visibleRendering: Screen
      get() = (getChildAt(0)?.tag as NamedScreen<*>).content

    override fun performTransition(
      oldHolderMaybe: ScreenViewHolder<NamedScreen<*>>?,
      newHolder: ScreenViewHolder<NamedScreen<*>>,
      popped: Boolean
    ) {
      transitionCount++
      assertThat(newHolder.view.tag).isNull()
      newHolder.view.tag = newHolder.showing
      super.performTransition(oldHolderMaybe, newHolder, popped)
    }
  }
}

private fun String.toEditable(): Editable {
  return Editable.Factory.getInstance().newEditable(this)
}
