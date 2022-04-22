package com.squareup.workflow1.ui.backstack.test

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.util.SparseArray
import android.view.View
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.getRendering
import org.junit.Rule
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackContainerTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  private data class Rendering(val name: String) : Compatible, AndroidViewRendering<Rendering> {
    override val compatibilityKey = name
    override val viewFactory: ViewFactory<Rendering>
      get() = BuilderViewFactory(Rendering::class) { r, e, ctx, _ ->
        EditText(ctx).apply {
          // Must have an id to participate in view persistence.
          id = 65
          bindShowRendering(r, e) { _, _ -> /* Noop */ }
        }
      }
  }

  @Test fun savedStateParcelingWorks() {
    scenario.onActivity { activity ->
      val originalView = VisibleBackStackContainer(activity).apply {
        // Must have an id to participate in view persistence.
        id = 42
      }

      // Show "able".
      originalView.show(BackStackScreen(Rendering("able")))
      // Type "first" into the rendered EditText.
      (originalView.getChildAt(0) as EditText).text = "first".toEditable()
      // Push "baker" on top of "able".
      originalView.show(BackStackScreen(Rendering("able"), Rendering("baker")))
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
        parcel.readSparseArray<Parcelable>(this::class.java.classLoader)!!.also { parcel.recycle() }
      }

      // Create a new BackStackContainer with the same id as the original
      val restoredView = VisibleBackStackContainer(activity).apply { id = 42 }
      // Have it render the same able > baker back stack that we last showed in the original.
      restoredView.show(BackStackScreen(Rendering("able"), Rendering("baker")))
      // Restore the view hierarchy.
      restoredView.restoreHierarchyState(restoredArray)
      // Android took care of restoring the text that was last shown.
      assertThat((restoredView.getChildAt(0) as EditText).text.toString()).isEqualTo("second")
      // Pop back to able.
      restoredView.show(BackStackScreen(Rendering("able")))
      // BackStackContainer restored the text we had typed on that.
      assertThat((restoredView.getChildAt(0) as EditText).text.toString()).isEqualTo("first")
    }
  }

  @Test fun firstScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      val showing = c.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("able"))
    }
  }

  @Test fun secondScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("baker")))
      val showing = c.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("baker"))
    }
  }

  @Test fun thirdScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("baker")))
      c.show(BackStackScreen(Rendering("charlie")))
      val showing = c.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("charlie"))

      // This used to fail because of our naive use of TransitionManager. The
      // transition from baker view to charlie view was dropped because the
      // transition from able view to baker view was still in progress.
    }
  }

  @Test fun isDebounced() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("able")))

      assertThat(c.transitionCount).isEqualTo(1)
    }
  }

  private class VisibleBackStackContainer(context: Context) : BackStackContainer(context) {
    var transitionCount = 0
    val visibleRendering: Any? get() = getChildAt(0)?.getRendering<Named<*>>()?.wrapped

    fun show(rendering: BackStackScreen<*>) {
      update(rendering, ViewEnvironment())
    }

    override fun performTransition(
      oldViewMaybe: View?,
      newView: View,
      popped: Boolean
    ) {
      transitionCount++
      super.performTransition(oldViewMaybe, newView, popped)
    }
  }
}

private fun String.toEditable(): Editable {
  return Editable.Factory.getInstance().newEditable(this)
}
