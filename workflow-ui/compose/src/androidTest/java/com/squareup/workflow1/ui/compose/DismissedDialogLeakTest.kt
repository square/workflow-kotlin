package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import androidx.activity.ComponentDialog
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.WorkflowUiTestActivity
import com.squareup.workflow1.ui.navigation.AndroidOverlay
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.navigation.Overlay
import com.squareup.workflow1.ui.navigation.OverlayDialogFactory
import com.squareup.workflow1.ui.navigation.ScreenOverlay
import com.squareup.workflow1.ui.navigation.asDialogHolderWithContent
import com.squareup.workflow1.ui.plus
import java.lang.ref.WeakReference
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Dismissing a dialog from inside a composition must not leave the dialog's window reachable.
 *
 * When a [ScreenViewFactory] is shown inside a composition, [asComposableFactory] drives it from an
 * `AndroidView` `update` block, and Compose runs that block inside
 * `SnapshotStateObserver.observeReads`. Every snapshot state read made while the view is updated is
 * therefore recorded against the host window — including reads made by code that the view calls
 * synchronously.
 *
 * Dropping an [Overlay] from a [BodyAndOverlaysScreen] is exactly such an update. It ends in
 * `DialogSession.destroyDialog`, which calls `Dialog.dismiss()` and so detaches the dialog's window
 * before the update block returns. If the dismissed dialog was showing a composition, its
 * `AndroidComposeView` reads its own `viewTreeOwners` on the way out, and `viewTreeOwners` is a
 * `derivedStateOf`. The host's observer keeps that read — and so the `DerivedSnapshotState` whose
 * calculation captures the dismissed view — until the host stops reading it, which happens either
 * on a later update of the same block or when the host itself is detached. Neither is about to
 * happen here, so the dismissed `AndroidComposeView`, and through its `_rootView` the whole dialog
 * window, stays reachable from the process-wide `SnapshotKt.applyObservers` list.
 *
 * Note that showing the same [BodyAndOverlaysScreen] through `WorkflowLayout` instead cannot leak
 * this way: there is no snapshot observer around the update, so there is nothing to record the
 * read.
 */
internal class DismissedDialogLeakTest {

  // TODO(CLF-521): Migrate to the androidx.compose.ui.test.junit4.v2 rules.
  @Suppress("DEPRECATION")
  private val composeRule = createAndroidComposeRule<WorkflowUiTestActivity>()

  @get:Rule
  val rules: RuleChain =
    RuleChain.outerRule(IdleAfterTestRule).around(composeRule).around(IdlingDispatcherRule)

  private val scenario
    get() = composeRule.activityRule.scenario

  /**
   * Written by [DialogOverlay]'s factory, and cleared again as soon as the test has a
   * [WeakReference] to it — a strong reference held here would keep the window alive by itself and
   * make the test pass or fail for the wrong reason.
   */
  private var newDialogDecorView: View? = null

  @Before
  fun setUp() {
    scenario.onActivity {
      it.viewEnvironment = (ViewEnvironment.EMPTY + ViewRegistry()).withComposeInteropSupport()
    }
  }

  @Test
  fun dialog_window_is_collected_after_being_dismissed_from_a_composition() {
    val overlay = DialogOverlay(DialogContent) { newDialogDecorView = it.window!!.decorView }

    setRendering(ComposeHost(BodyAndOverlaysScreen(EmptyRendering, listOf(overlay))))
    composeRule.onNodeWithText(DIALOG_TEXT).assertIsDisplayed()

    val dialogWindow = takeWeakReferenceToDialogWindow()

    // Dropping the overlay dismisses the dialog synchronously, from inside the AndroidView update
    // block that asComposableFactory uses to show the BodyAndOverlaysScreen.
    setRendering(ComposeHost(BodyAndOverlaysScreen(EmptyRendering, emptyList<Overlay>())))
    composeRule.onNodeWithText(DIALOG_TEXT).assertDoesNotExist()

    assertWithMessage(
        "The dismissed dialog's window is still reachable. Dump the heap and look for a path" +
          " through SnapshotKt.applyObservers: it means the update that dismissed the dialog was" +
          " observed, and the host recorded the detaching view's derived state read."
      )
      .that(dialogWindow.isClearedAfterGc())
      .isTrue()
  }

  /**
   * The control: the same dialog, dismissed by the same code, but with no composition hosting the
   * [BodyAndOverlaysScreen]. Nothing observes the update, so there is nothing to record the
   * detaching view's read, and the window must be collected whether or not the fix is in place. If
   * this test ever fails, the one above is failing for some reason other than snapshot observation.
   */
  @Test
  fun dialog_window_is_collected_after_being_dismissed_from_a_view() {
    val overlay = DialogOverlay(DialogContent) { newDialogDecorView = it.window!!.decorView }

    setRendering(BodyAndOverlaysScreen(EmptyRendering, listOf(overlay)))
    composeRule.onNodeWithText(DIALOG_TEXT).assertIsDisplayed()

    val dialogWindow = takeWeakReferenceToDialogWindow()

    setRendering(BodyAndOverlaysScreen(EmptyRendering, emptyList<Overlay>()))
    composeRule.onNodeWithText(DIALOG_TEXT).assertDoesNotExist()

    assertWithMessage("The dismissed dialog's window is still reachable, with nothing observing it")
      .that(dialogWindow.isClearedAfterGc())
      .isTrue()
  }

  private fun setRendering(rendering: Screen) {
    scenario.onActivity { it.setRendering(rendering) }
  }

  /**
   * Takes the [WeakReference] in a frame of its own, and clears [newDialogDecorView] before
   * returning, so that the only references left to the dialog's window are the ones under test.
   *
   * This has to be its own method. ART treats every register of an interpreted frame as a GC root,
   * so a `WeakReference(newDialogDecorView!!)` written inline in a test would leave the window in a
   * register of the test's own frame and keep it alive until the test method returned — making
   * these tests fail whether or not anything leaks.
   */
  private fun takeWeakReferenceToDialogWindow(): WeakReference<View> =
    WeakReference(checkNotNull(newDialogDecorView) { "No dialog was built" }).also {
      newDialogDecorView = null
    }

  /**
   * The only way to see this leak from a test is to ask the garbage collector, because everything
   * holding the window holds it strongly. A single `gc()` is not enough to guarantee that a weakly
   * reachable object has been cleared, so retry — this is the same gc / wait / `runFinalization`
   * sequence LeakCanary's own `GcTrigger.Default` uses.
   */
  private fun WeakReference<*>.isClearedAfterGc(): Boolean {
    repeat(GC_ATTEMPTS) {
      Runtime.getRuntime().gc()
      // Give the reference processor time to enqueue the reference before looking at it.
      Thread.sleep(GC_PAUSE_MS)
      System.runFinalization()
      if (get() == null) return true
    }
    return false
  }

  /**
   * Shows [content] from inside a composition, which is what puts a `SnapshotStateObserver` around
   * the updates of the `View` that [content] is rendered by.
   */
  private data class ComposeHost(val content: Screen) : Compatible, ComposeScreen {
    override val compatibilityKey: String
      get() = "ComposeHost"

    @Composable
    override fun Content() {
      WorkflowRendering(content)
    }
  }

  /** A dialog whose content is a composition, so that its window hosts an `AndroidComposeView`. */
  private class DialogOverlay(
    override val content: Screen,
    private val onDialogBuilt: (ComponentDialog) -> Unit,
  ) : ScreenOverlay<Screen>, AndroidOverlay<DialogOverlay> {
    override fun <U : Screen> map(transform: (Screen) -> U) = error("Not implemented")

    override val dialogFactory =
      OverlayDialogFactory<DialogOverlay> { initialRendering, initialEnvironment, context: Context
        ->
        ComponentDialog(context)
          .also(onDialogBuilt)
          .asDialogHolderWithContent(initialRendering, initialEnvironment)
      }
  }

  private object DialogContent : ComposeScreen {
    @Composable
    override fun Content() {
      BasicText(DIALOG_TEXT)
    }
  }

  private object EmptyRendering : AndroidScreen<EmptyRendering> {
    override val viewFactory: ScreenViewFactory<EmptyRendering>
      get() = ScreenViewFactory.fromCode { _, e, c, _ -> ScreenViewHolder(e, View(c)) { _, _ -> } }
  }

  private companion object {
    const val DIALOG_TEXT = "Dialog content"
    const val GC_ATTEMPTS = 10
    const val GC_PAUSE_MS = 50L
  }
}
