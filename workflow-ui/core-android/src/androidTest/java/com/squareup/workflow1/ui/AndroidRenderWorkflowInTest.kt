package com.squareup.workflow1.ui

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@OptIn(WorkflowUiExperimentalApi::class)
internal class AndroidRenderWorkflowInTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  @Test fun removeWorkflowStateDoesWhatItSaysOnTheTin() {
    var job: Job? = null

    // Activity.onCreate(), the take() call won't start pulling yet.
    scenario.onActivity { activity ->
      val model: SomeViewModel by activity.viewModels()
      val renderings: StateFlow<Screen> = renderWorkflowIn(
        workflow = SomeWorkflow,
        scope = model.viewModelScope,
        savedStateHandle = model.savedStateHandle
      )

      val layout = WorkflowLayout(activity)
      activity.setContentView(layout)

      assertThat(model.savedStateHandle.contains(KEY)).isFalse()

      job = layout.take(activity.lifecycle, renderings)
      assertThat(model.savedStateHandle.contains(KEY)).isFalse()
    }

    // Exit onCreate() and move to CREATED status. take() starts to draw
    // and the renderWorkflowIn() call above starts pushing TreeSnapshots
    // (lazy serialization functions) to the SavedStateHandle.
    scenario.moveToState(CREATED)
    scenario.onActivity { activity ->
      val model: SomeViewModel by activity.viewModels()
      assertThat(model.savedStateHandle.contains(KEY)).isTrue()

      // The Job returned from take() is canceled. There is still a
      // TreeSnapshot and whatever pointer it captured in the SavedStateHandle.
      job?.cancel()
      assertThat(model.savedStateHandle.contains(KEY)).isTrue()

      // We can remove it.
      model.savedStateHandle.removeWorkflowState()
      assertThat(model.savedStateHandle.contains(KEY)).isFalse()
    }
  }

  object SomeScreen : AndroidScreen<SomeScreen> {
    override val viewFactory: ScreenViewFactory<SomeScreen> =
      ScreenViewFactory.fromCode { _, initialEnvironment, context, _ ->
        ScreenViewHolder(
          initialEnvironment,
          FrameLayout(context)
        ) { _, _ -> }
      }
  }

  object SomeWorkflow : StatelessWorkflow<Unit, Nothing, Screen>() {
    override fun render(
      renderProps: Unit,
      context: RenderContext
    ): Screen {
      return SomeScreen
    }
  }

  class SomeViewModel(val savedStateHandle: SavedStateHandle) : ViewModel()
}
