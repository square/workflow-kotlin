package com.squareup.sample.dungeon

import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.internal.test.onWorkflowView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DungeonAppTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(DungeonActivity::class.java)

  @Test fun loadsBoardsList() {
   onWorkflowView(withText(R.string.boards_list_label))
        .check(matches(isDisplayed()))
  }
}
