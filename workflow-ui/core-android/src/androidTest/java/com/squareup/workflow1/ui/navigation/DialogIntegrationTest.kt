package com.squareup.workflow1.ui.navigation

import android.app.Dialog
import android.text.SpannableStringBuilder
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.withEnvironment
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
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            // Must have an id to participate in view persistence.
            id = 65
            // Give us something to search for so that we can be sure
            // views actually get displayed
            text = SpannableStringBuilder(name)
          }
        ) { _, _ -> }
      }
  }

  private var latestDialog: Dialog? = null

  private inner class DialogRendering(
    name: String,
    override val content: ContentRendering
  ) : AndroidOverlay<DialogRendering>, ScreenOverlay<ContentRendering> {
    override fun <ContentU : Screen> map(transform: (ContentRendering) -> ContentU) =
      error("Not implemented")

    override val compatibilityKey = name

    override val dialogFactory = OverlayDialogFactory<DialogRendering> { r, e, c ->
      val dialog = ComponentDialog(c).also { latestDialog = it }
      dialog.asDialogHolderWithContent(r, e)
    }
  }

  @Test fun showOne() {
    val screen = BodyAndOverlaysScreen(
      ContentRendering("body"),
      listOf(DialogRendering("dialog", ContentRendering("content")))
    )

    scenario.onActivity { activity ->
      val root = WorkflowLayout(activity)
      activity.setContentView(root)
      root.show(screen)
    }
    onView(withText("content")).inRoot(isDialog()).check(matches(isDisplayed()))

    assertThat(latestDialog).isNotNull()
    assertThat(latestDialog!!.isShowing).isTrue()
  }

  /** https://github.com/square/workflow-kotlin/issues/825 */
  @Test fun showASecondDialog() {
    val oneDialog = BodyAndOverlaysScreen(
      ContentRendering("body"),
      listOf(DialogRendering("dialog", ContentRendering("content")))
    )
    lateinit var root: WorkflowLayout

    scenario.onActivity { activity ->
      root = WorkflowLayout(activity)
      activity.setContentView(root)
      root.show(oneDialog)
    }

    val dialog2 = DialogRendering("dialog2", ContentRendering("content2"))
    val twoDialogs = BodyAndOverlaysScreen(
      ContentRendering("body"),
      listOf(
        DialogRendering("dialog1", ContentRendering("content1")),
        dialog2
      )
    )

    scenario.onActivity {
      root.show(twoDialogs)
      val lastOverlay = latestDialog?.overlay
      assertThat(lastOverlay).isEqualTo(dialog2)
    }
    onView(withText("content2")).inRoot(isDialog()).check(matches(isDisplayed()))
  }

  // Some of us are stuck with integration setups that cache
  // ViewEnvironment when they really shouldn't. Make sure `DialogCollator`
  // is reusable.
  @Test fun toleratesCachedDialogCollator() {
    val stickyEnvironment = ViewEnvironment.EMPTY + (DialogCollator to DialogCollator())

    val oneDialog = BodyAndOverlaysScreen(
      ContentRendering("body"),
      listOf(DialogRendering("dialog", ContentRendering("content")))
    ).withEnvironment(stickyEnvironment)

    lateinit var root: WorkflowLayout

    scenario.onActivity { activity ->
      root = WorkflowLayout(activity)
      activity.setContentView(root)
      root.show(oneDialog)
    }

    val dialog2 = DialogRendering("dialog2", ContentRendering("content2"))
    val twoDialogs = BodyAndOverlaysScreen(
      ContentRendering("body"),
      listOf(
        DialogRendering("dialog1", ContentRendering("content1")),
        dialog2
      )
    ).withEnvironment(stickyEnvironment)

    scenario.onActivity {
      root.show(twoDialogs)
      val lastOverlay = latestDialog?.overlay
      assertThat(lastOverlay).isEqualTo(dialog2)
    }
    onView(withText("content2")).inRoot(isDialog()).check(matches(isDisplayed()))
  }

  @Test fun closingAnUpstreamDialogPreservesDownstream() {
    val body = ContentRendering("body")
    val overlayZero = DialogRendering("dialog0", ContentRendering("content"))
    val overlayOne = DialogRendering("dialog1", ContentRendering("content"))
    val showingBoth = BodyAndOverlaysScreen(body, listOf(overlayZero, overlayOne))
    lateinit var root: WorkflowLayout
    lateinit var originalDialogOne: Dialog
    scenario.onActivity { activity ->
      root = WorkflowLayout(activity)
      activity.setContentView(root)
      root.show(showingBoth)
      originalDialogOne = latestDialog!!
      assertThat(originalDialogOne.overlayOrNull).isSameInstanceAs(overlayOne)
    }
    val closedZero = BodyAndOverlaysScreen(body, listOf(overlayOne))
    scenario.onActivity {
      root.show(closedZero)
      assertThat(latestDialog!!.overlayOrNull).isSameInstanceAs(overlayOne)
      assertThat(latestDialog).isSameInstanceAs(originalDialogOne)
    }
  }

  @Test fun finishingActivityEarlyDismissesDialogs() {
    val screen = BodyAndOverlaysScreen(
      ContentRendering("body"),
      listOf(DialogRendering("dialog", ContentRendering("content")))
    )

    scenario.onActivity { activity ->
      val root = WorkflowLayout(activity)
      activity.setContentView(root)
      root.show(screen)
    }
    onView(withText("content")).inRoot(isDialog()).check(matches(isDisplayed()))

    scenario.moveToState(DESTROYED)
    assertThat(latestDialog?.isShowing).isFalse()
  }
}
