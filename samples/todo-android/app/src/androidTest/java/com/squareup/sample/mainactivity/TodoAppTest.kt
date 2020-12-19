package com.squareup.sample.mainactivity

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.squareup.sample.todo.R
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoAppTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(ToDoActivity::class.java)
  private val uiDevice by lazy { UiDevice.getInstance(getInstrumentation()) }

  @Test fun navigatesToListAndBack_portrait() {
    uiDevice.setOrientationNatural()

    onView(withText("Groceries"))
        .check(matches(allOf(isDisplayed())))
        .perform(click())

    onView(withId(R.id.item_container))
        .check(matches(isDisplayed()))

    pressBack()

    onView(withId(R.id.todo_lists_container))
        .check(matches(isDisplayed()))
  }
}
