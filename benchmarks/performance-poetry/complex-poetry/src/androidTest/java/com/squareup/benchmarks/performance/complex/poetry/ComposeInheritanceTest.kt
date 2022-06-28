package com.squareup.benchmarks.performance.complex.poetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.molecule.AndroidUiDispatcher
import app.cash.molecule.launchMolecule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeInheritanceTest {

  abstract class Parent<T> {
    @Composable
    open fun AComposable(
      hoistToggleState: @Composable (s: T) -> Unit
    ): Unit = throw IllegalStateException("I don't want to be a parent.")
  }

  class Child<T>(
    private val payload: T
  ): Parent<T>() {
    @Composable
    override fun AComposable(
      hoistToggleState: @Composable (s: T) -> Unit
    ) {
      println("Can you hear me now? $payload")
      hoistToggleState(payload)
    }
  }

  @Composable
  fun <T> Emitter(someObject: Parent<T>): T? {
    val payload: MutableState<T?> = remember { mutableStateOf(null) }
    someObject.AComposable {
      payload.value = it
    }
    return payload.value
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test fun testComposableOverloading() {
    val child: Parent<String> = Child<String>("a test")
    val testScope = CoroutineScope(AndroidUiDispatcher.Main)

    val testFlow = testScope.launchMolecule {
      Emitter(child)
    }

    assertThat(testFlow.value).isEqualTo("a test")
  }

}
