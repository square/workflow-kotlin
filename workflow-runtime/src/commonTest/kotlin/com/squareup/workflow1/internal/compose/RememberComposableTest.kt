package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class RememberComposableTest {

  @Test fun rememberSkippableAndRestartable_runs_producer_on_first_compose() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var calls = 0
      val producer: @Composable () -> Int = { calls++; 42 }
      val result = test.recompose {
        rememberSkippableAndRestartableComposable(key1 = "a", key2 = "b", producer = producer)
      }
      assertEquals(42, result)
      assertEquals(1, calls)
    } finally {
      test.close()
    }
  }

  @Test fun rememberSkippableAndRestartable_skips_producer_when_keys_unchanged() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val trigger = mutableStateOf(0)
      var calls = 0
      val producer: @Composable () -> String = { calls++; "rendering" }
      val content: @Composable () -> String = {
        // Read trigger so the caller invalidates when it changes.
        trigger.value
        rememberSkippableAndRestartableComposable("k1", "k2", producer)
      }
      test.recompose(content)
      trigger.value = 1
      val r2 = test.recompose(content)
      assertEquals(1, calls, "Producer should be skipped when keys are unchanged")
      assertEquals("rendering", r2)
    } finally {
      test.close()
    }
  }

  @Test fun rememberSkippableAndRestartable_runs_producer_when_key1_changes() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var calls = 0
      val producer: @Composable () -> Int = { calls++; calls }
      val key1Box = mutableStateOf("a")
      val content: @Composable () -> Int = {
        rememberSkippableAndRestartableComposable(key1Box.value, "fixed", producer)
      }
      test.recompose(content)
      assertEquals(1, calls)
      key1Box.value = "b"
      test.recompose(content)
      assertEquals(2, calls)
    } finally {
      test.close()
    }
  }

  @Test fun rememberSkippableAndRestartable_runs_producer_when_key2_changes() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var calls = 0
      val producer: @Composable () -> Int = { calls++; calls }
      val key2Box = mutableStateOf(0)
      val content: @Composable () -> Int = {
        rememberSkippableAndRestartableComposable("fixed", key2Box.value, producer)
      }
      test.recompose(content)
      assertEquals(1, calls)
      key2Box.value = 1
      test.recompose(content)
      assertEquals(2, calls)
    } finally {
      test.close()
    }
  }

  @Test fun rememberSkippableAndRestartable_caches_with_identity_not_equals() = runTest {
    // Key invariant: workflow renderings are allowed to have throwing equals/hashCode (this is
    // tested at the runtime level by `exceptions_from_renderings_equals_methods_do_not_fail_runtime`).
    // The cache check in rememberSkippable*Composable must therefore use identity, not equals.
    class ThrowingEquals(val v: Int) {
      override fun equals(other: Any?): Boolean = error("equals called!")
      override fun hashCode(): Int = error("hashCode called!")
    }

    val test = TestComposition(backgroundScope)
    try {
      val trigger = mutableStateOf(0)
      var producerCalls = 0
      val producer: @Composable () -> ThrowingEquals = {
        producerCalls++
        ThrowingEquals(producerCalls)
      }
      val content: @Composable () -> ThrowingEquals = {
        trigger.value
        rememberSkippableAndRestartableComposable("k1", "k2", producer)
      }
      val first = test.recompose(content)
      trigger.value = 1
      val second = test.recompose(content)
      // Producer was skipped, returning the same instance from the cache. Crucially: equals was
      // never invoked, so the throwing equals didn't fire.
      assertSame(first, second)
    } finally {
      test.close()
    }
  }

  @Test fun rememberSkippableComposable_runs_producer_on_first_compose() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var calls = 0
      val producer: @Composable () -> Int = { calls++; 7 }
      val result = test.recompose {
        rememberSkippableComposable(key1 = "a", key2 = "b", producer = producer)
      }
      assertEquals(7, result)
      assertEquals(1, calls)
    } finally {
      test.close()
    }
  }

  @Test fun rememberSkippableComposable_runs_producer_when_keys_change() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var calls = 0
      val producer: @Composable () -> Int = { calls++; calls }
      val key1Box = mutableStateOf("a")
      val content: @Composable () -> Int = {
        rememberSkippableComposable(key1Box.value, "fixed", producer)
      }
      test.recompose(content)
      assertEquals(1, calls)
      key1Box.value = "b"
      test.recompose(content)
      assertEquals(2, calls)
    } finally {
      test.close()
    }
  }

  @Test fun rememberSkippableComposable_skips_producer_when_keys_unchanged() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val trigger = mutableStateOf(0)
      var calls = 0
      val producer: @Composable () -> Int = { calls++; calls }
      val content: @Composable () -> Int = {
        trigger.value
        rememberSkippableComposable("k1", "k2", producer)
      }
      test.recompose(content)
      trigger.value = 1
      test.recompose(content)
      assertEquals(1, calls)
    } finally {
      test.close()
    }
  }
}
