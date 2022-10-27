package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.view.View
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class DialogIntegrationTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  private data class ContentRendering(val name: String) :
    Compatible, AndroidScreen<ContentRendering> {
    override val compatibilityKey = name
    override val viewFactory: ScreenViewFactory<ContentRendering>
      get() = ScreenViewFactory.fromCode { _, initialRendering, context, _ ->
        ScreenViewHolder(
          initialRendering,
          EditText(context).apply {
            // Must have an id to participate in view persistence.
            id = 65
          }
        ) { _, _ -> /* Noop */ }
      }
  }

  private var latestContentView: View? = null
  private var latestDialog: Dialog? = null

  private inner class DialogRendering(
    name: String,
    override val content: ContentRendering
  ) :
    Compatible, AndroidOverlay<DialogRendering>, ScreenOverlay<ContentRendering> {
    override val compatibilityKey = name
    override val dialogFactory =
      object : ScreenOverlayDialogFactory<ContentRendering, DialogRendering>(
        type = DialogRendering::class
      ) {
        override fun buildDialogWithContent(
          initialRendering: DialogRendering,
          initialEnvironment: ViewEnvironment,
          content: ScreenViewHolder<ContentRendering>
        ): OverlayDialogHolder<DialogRendering> {
          latestContentView = content.view

          return super.buildDialogWithContent(initialRendering, initialEnvironment, content).also {
            latestDialog = it.dialog
          }
        }
      }
  }

  @Test fun showOne() {
    val screen = BodyAndOverlaysScreen(
      ContentRendering("body"),
      DialogRendering("dialog", ContentRendering("content"))
    )

    scenario.onActivity { activity ->
      val root = WorkflowLayout(activity)
      root.show(screen)

      assertThat(latestContentView).isNotNull()
      assertThat(latestDialog).isNotNull()
      assertThat(latestDialog!!.isShowing).isTrue()
    }
  }

  /** https://github.com/square/workflow-kotlin/issues/825 */
  @Test fun showASecondDialog() {
    val oneDialog = BodyAndOverlaysScreen(
      ContentRendering("body"),
      DialogRendering("dialog", ContentRendering("content"))
    )
    lateinit var root: WorkflowLayout

    scenario.onActivity { activity ->
      root = WorkflowLayout(activity)
      root.show(oneDialog)
    }

    val dialog2 = DialogRendering("dialog2", ContentRendering("content2"))
    val twoDialogs = BodyAndOverlaysScreen(
      ContentRendering("body"),
      DialogRendering("dialog1", ContentRendering("content1")),
      dialog2
    )

    scenario.onActivity {
      root.show(twoDialogs)
      val lastOverlay = latestDialog?.overlay
      assertThat(lastOverlay).isEqualTo(dialog2)
    }
  }
}
