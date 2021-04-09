package com.squareup.workflow1.ui.radiography

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.NamedViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.internal.test.WorkflowUiTestActivity
import com.squareup.workflow1.ui.modal.HasModals
import com.squareup.workflow1.ui.modal.ModalViewContainer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import radiography.Radiography
import radiography.ScanScopes
import radiography.ViewStateRenderers
import radiography.ViewStateRenderers.DefaultsNoPii
import kotlin.reflect.KClass

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
internal class WorkflowViewRendererTest {

  @get:Rule val scenarioRule = ActivityScenarioRule(WorkflowUiTestActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @Test fun view_renderer_works() {
    val viewRegistry = ViewRegistry(
      ModalViewContainer.binding<TestModals>(),
      BackStackContainer,
      NamedViewFactory,
      TestRendering
    )

    scenario.onActivity {
      it.viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))
      it.setRendering(
        TestModals(
          beneathModals = BackStackScreen(TestRendering("base")),
          modals = listOf(TestRendering("modal"))
        )
      )
    }

    lateinit var hierarchy: String
    scenario.onActivity {
      hierarchy = Radiography.scan(
        // Explicitly select the view we want to avoid window focus flakiness.
        scanScope = ScanScopes.singleViewScope(it.rootRenderedView),
        viewStateRenderers = DefaultsNoPii + ViewStateRenderers.WorkflowViewRenderer
      )
    }
    val hierarchyLines = hierarchy.lines()

    assertThat(hierarchyLines.count {
      "ModalViewContainer" in it &&
        "workflow-rendering-type:${TestModals::class.java.name}" in it &&
        "workflow-compatibility-key:${TestModals::class.java.name}-Named(0)" in it
    }).isEqualTo(1)

    assertThat(hierarchyLines.count {
      "BackStackContainer" in it &&
        "workflow-rendering-type:${BackStackScreen::class.java.name}" in it
    }).isEqualTo(1)

    assertThat(hierarchyLines.count {
      "View" in it &&
        "workflow-rendering-type:${TestRendering::class.java.name}" in it &&
        "workflow-compatibility-key:${TestRendering::class.java.name}-Named(backstack)" in it
    }).isEqualTo(1)
  }

  private data class TestModals(
    override val beneathModals: BackStackScreen<Any>,
    override val modals: List<Any>
  ) : HasModals<BackStackScreen<Any>, Any>

  private data class TestRendering(val text: String) {
    companion object : ViewFactory<TestRendering> {
      override val type: KClass<in TestRendering> = TestRendering::class
      override fun buildView(
        initialRendering: TestRendering,
        initialViewEnvironment: ViewEnvironment,
        contextForNewView: Context,
        container: ViewGroup?
      ): View = View(contextForNewView).apply {
        bindShowRendering(initialRendering, initialViewEnvironment) { _, _ ->
          // Noop
        }
      }
    }
  }
}
