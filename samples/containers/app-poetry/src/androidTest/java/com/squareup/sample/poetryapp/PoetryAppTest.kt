package com.squareup.sample.poetryapp

import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.sample.container.poetryapp.R
import com.squareup.workflow1.ui.internal.test.onWorkflowView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoetryAppTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(PoetryActivity::class.java)

  @Test fun launches() {
    onWorkflowView(withText(R.string.poems))
        .check(matches(isDisplayed()))
  }
}
