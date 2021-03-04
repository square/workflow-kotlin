package com.squareup.workflow1.ui.tests

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistry.SavedStateProvider
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.internal.test.WorkflowUiTestActivity
import com.squareup.workflow1.ui.internal.test.actuallyPressBack
import com.squareup.workflow1.ui.internal.test.inAnyView
import com.squareup.workflow1.ui.modal.HasModals
import com.squareup.workflow1.ui.modal.ModalViewContainer
import com.squareup.workflow1.ui.plus
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class ViewTreeIntegrationTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(WorkflowUiTestActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @Before fun setUp() {
    scenario.onActivity {
      it.viewEnvironment = ViewEnvironment(
        mapOf(
          ViewRegistry to ViewRegistry(
            NoTransitionBackStackContainer,
            NamedViewFactory,
            ModalViewContainer.binding<TestModals>()
          )
        )
      )
    }
  }

  @Test fun complex_modal_backstack_with_text_entry() {
    data class Modals(
      override val beneathModals: BackStackScreen<StateRegistryRendering>,
      override val modals: List<BackStackScreen<StateRegistryRendering>> = emptyList()
    ) : HasModals<BackStackScreen<StateRegistryRendering>, BackStackScreen<StateRegistryRendering>>

    data class AppState(
      val textToEnter: String,
      val screens: Modals,
    ) {
      val topScreen: StateRegistryRendering
        get() {
          return screens.modals.lastOrNull()?.frames?.lastOrNull()
            ?: screens.beneathModals.frames.last()
        }
    }

    val baseScreen1 = StateRegistryRendering("Base One")
    val baseScreen2 = StateRegistryRendering("Base Two")
    val firstModal1 = StateRegistryRendering("First Modal One")
    val firstModal2 = StateRegistryRendering("First Modal Two")
    val secondModal1 = StateRegistryRendering("Second Modal One")
    val secondModal2 = StateRegistryRendering("Second Modal Two")

    val appStates = listOf(
      AppState(
        textToEnter = "monday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1)
        )
      ),
      AppState(
        textToEnter = "tuesday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2)
        )
      ),
      AppState(
        textToEnter = "wednesday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2),
          modals = listOf(
            BackStackScreen(firstModal1)
          )
        )
      ),
      AppState(
        textToEnter = "thursday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2),
          modals = listOf(
            BackStackScreen(firstModal1, firstModal2)
          )
        )
      ),
      AppState(
        textToEnter = "friday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2),
          modals = listOf(
            BackStackScreen(firstModal1, firstModal2),
            BackStackScreen(secondModal1)
          )
        )
      ),
      AppState(
        textToEnter = "saturday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2),
          modals = listOf(
            BackStackScreen(firstModal1, firstModal2),
            BackStackScreen(secondModal1, secondModal2)
          )
        )
      ),
    )

    // Add our custom Modals screen to the view registry.
    scenario.onActivity {
      val registry = it.viewEnvironment[ViewRegistry] + ModalViewContainer.binding<Modals>()
      it.viewEnvironment = it.viewEnvironment + (ViewRegistry to registry)
    }

    // Iterate in order to set the text in all the screens.
    appStates.forEachIndexed { index, appState ->
      scenario.onActivity {
        it.setRendering(appState.screens)
      }
      // Make sure we see the header.
      val topScreenName = appState.topScreen.compatibilityKey
      inAnyView(withText("$topScreenName: 0"))
        .check(matches(isDisplayed()))
        // Click the text to focus in the modals. Otherwise, the first button click will not
        // actually click the button, it will just shift focus to the modal. Fuck Espresso.
        .perform(click())
      inAnyView(withTagValue(equalTo("increment:$topScreenName")))
        .apply {
          // Give each screen a unique counter value, to ensure screens are getting the correct
          // state restored.
          repeat(index + 1) {
            perform(click())
          }
        }

      // Wait for the typing to finish.
      inAnyView(withText("$topScreenName: ${index + 1}"))
        .check(matches(isDisplayed()))
    }

    scenario.recreate()

    // Now iterate backwards to make sure everything was restored.
    appStates.asReversed().forEachIndexed { index, appState ->
      scenario.onActivity {
        it.setRendering(appState.screens)
      }
      // Make sure we see the header.
      val topScreenName = appState.topScreen.compatibilityKey
      inAnyView(withText("$topScreenName: ${appStates.size - index}"))
        .check(matches(isDisplayed()))

      if (index < appStates.indices.last - 1) {
        // Don't press back on the initial screen, Espresso doesn't like that.
        actuallyPressBack()
      }
    }
  }

  data class StateRegistryRendering(
    override val compatibilityKey: String
  ) : Compatible,
    AndroidViewRendering<StateRegistryRendering>,
    ViewFactory<StateRegistryRendering> {
    override val type: KClass<in StateRegistryRendering> = StateRegistryRendering::class
    override val viewFactory: ViewFactory<StateRegistryRendering> = this

    override fun buildView(
      initialRendering: StateRegistryRendering,
      initialViewEnvironment: ViewEnvironment,
      contextForNewView: Context,
      container: ViewGroup?
    ): View {
      val key = initialRendering.compatibilityKey
      val screen = LinearLayout(contextForNewView).apply {
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        orientation = LinearLayout.VERTICAL
      }
      val text = TextView(contextForNewView)
      var counter = 0
      fun setCounter(value: Int) {
        counter = value
        text.text = "$key: $value"
      }
      setCounter(0)

      val button = Button(contextForNewView).apply {
        this.text = "Increment"
        tag = "increment:$key"
        setOnClickListener {
          setCounter(counter + 1)
        }
      }
      screen.addView(text, WRAP_CONTENT, WRAP_CONTENT)
      screen.addView(button, WRAP_CONTENT, WRAP_CONTENT)

      fun restoreText(bundle: Bundle) {
        setCounter(bundle.getInt("counter"))
      }

      fun saveText(bundle: Bundle) {
        bundle.putInt("counter", counter)
      }

      // TODO extract something to help with this attached-and-created dance
      val attachListener = object : OnAttachStateChangeListener,
        LifecycleEventObserver,
        SavedStateProvider {

        private lateinit var lifecycle: Lifecycle
        private lateinit var stateRegistry: SavedStateRegistry

        override fun onViewAttachedToWindow(v: View) {
          ViewTreeSavedStateRegistryOwner.get(screen)!!.also {
            lifecycle = it.lifecycle
            stateRegistry = it.savedStateRegistry
          }
          lifecycle.addObserver(this)
          stateRegistry.registerSavedStateProvider(key, this)
        }

        override fun onViewDetachedFromWindow(v: View) {
          stateRegistry.unregisterSavedStateProvider(key)
          lifecycle.removeObserver(this)
        }

        override fun onStateChanged(
          source: LifecycleOwner,
          event: Event
        ) {
          if (event == ON_CREATE) {
            stateRegistry.consumeRestoredStateForKey(key)
              ?.let(::restoreText)
            lifecycle.removeObserver(this)
          }
        }

        override fun saveState(): Bundle = Bundle().also(::saveText)
      }
      screen.addOnAttachStateChangeListener(attachListener)

      return screen.apply {
        bindShowRendering(initialRendering, initialViewEnvironment) { _, _ ->
          // Noop
        }
      }
    }
  }

  private data class TestModals(
    override val modals: List<StateRegistryRendering>
  ) : HasModals<StateRegistryRendering, StateRegistryRendering> {
    override val beneathModals: StateRegistryRendering get() = EmptyRendering
  }

  companion object {
    val EmptyRendering = StateRegistryRendering(compatibilityKey = "")
  }
}
