@file:OptIn(ExperimentalTestApi::class, ExperimentalTestApi::class)

package com.squareup.workflow1.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

internal class CompositionRootTestIos {

  @Test fun wrappedWithRootIfNecessary_wrapsWhenNecessary() = runComposeUiTest {
    val root: CompositionRoot = { content ->
      Column {
        BasicText("one")
        content()
      }
    }

    setContent {
      WrappedWithRootIfNecessary(root) {
        BasicText("two")
      }
    }

    // These semantics used to merge, but as of dev15, they don't, which seems to be a bug.
    // https://issuetracker.google.com/issues/161979921
    onNodeWithText("one").assertIsDisplayed()
    onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun wrappedWithRootIfNecessary_onlyWrapsOnce() = runComposeUiTest {
    val root: CompositionRoot = { content ->
      Column {
        BasicText("one")
        content()
      }
    }

    setContentWithLifecycle {
      WrappedWithRootIfNecessary(root) {
        BasicText("two")
        WrappedWithRootIfNecessary(root) {
          BasicText("three")
        }
      }
    }

    onNodeWithText("one").assertIsDisplayed()
    onNodeWithText("two").assertIsDisplayed()
    onNodeWithText("three").assertIsDisplayed()
  }

  @Test fun wrappedWithRootIfNecessary_seesUpdatesFromRootWrapper() = runComposeUiTest {
    val wrapperText = mutableStateOf("one")
    val root: CompositionRoot = { content ->
      Column {
        BasicText(wrapperText.value)
        content()
      }
    }

    setContentWithLifecycle {
      WrappedWithRootIfNecessary(root) {
        BasicText("two")
      }
    }

    onNodeWithText("one").assertIsDisplayed()
    onNodeWithText("two").assertIsDisplayed()
    wrapperText.value = "ENO"
    onNodeWithText("ENO").assertIsDisplayed()
    onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun wrappedWithRootIfNecessary_rewrapsWhenDifferentRoot() = runComposeUiTest {
    val root1: CompositionRoot = { content ->
      Column {
        BasicText("one")
        content()
      }
    }
    val root2: CompositionRoot = { content ->
      Column {
        BasicText("ENO")
        content()
      }
    }
    val viewEnvironment = mutableStateOf(root1)

    setContentWithLifecycle {
      WrappedWithRootIfNecessary(viewEnvironment.value) {
        BasicText("two")
      }
    }

    onNodeWithText("one").assertIsDisplayed()
    onNodeWithText("two").assertIsDisplayed()
    viewEnvironment.value = root2
    onNodeWithText("ENO").assertIsDisplayed()
    onNodeWithText("two").assertIsDisplayed()
  }
}
