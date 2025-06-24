package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class WithCompositionLocalsTest {

  @Test fun reads_provided_value_inside_content() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val Local = compositionLocalOf { "default" }
      val result = test.recompose {
        withCompositionLocals(Local provides "provided") { Local.current }
      }
      assertEquals("provided", result)
    } finally {
      test.close()
    }
  }

  @Test fun returns_value_from_content_lambda() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val Local = compositionLocalOf { 0 }
      val result = test.recompose {
        withCompositionLocals(Local provides 7) { Local.current * 2 }
      }
      assertEquals(14, result)
    } finally {
      test.close()
    }
  }

  @Test fun reads_default_when_no_provider_present() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val Local = compositionLocalOf { "default" }
      val result = test.recompose { Local.current }
      assertEquals("default", result)
    } finally {
      test.close()
    }
  }

  @Test fun supports_static_composition_locals() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val Local = staticCompositionLocalOf { "default" }
      val result = test.recompose {
        withCompositionLocals(Local provides "static") { Local.current }
      }
      assertEquals("static", result)
    } finally {
      test.close()
    }
  }

  @Test fun nested_calls_resolve_to_innermost_value() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val Local = compositionLocalOf { "default" }
      val result = test.recompose {
        withCompositionLocals(Local provides "outer") {
          withCompositionLocals(Local provides "inner") { Local.current }
        }
      }
      assertEquals("inner", result)
    } finally {
      test.close()
    }
  }

  @Test fun outer_value_is_restored_after_inner_returns() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val Local = compositionLocalOf { "default" }
      val result = test.recompose {
        withCompositionLocals(Local provides "outer") {
          val inner = withCompositionLocals(Local provides "inner") { Local.current }
          inner + ":" + Local.current
        }
      }
      assertEquals("inner:outer", result)
    } finally {
      test.close()
    }
  }

  @Test fun multiple_locals_provided_at_once() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val A = compositionLocalOf { "A0" }
      val B = compositionLocalOf { "B0" }
      val result = test.recompose {
        withCompositionLocals(A provides "A1", B provides "B1") {
          A.current + "-" + B.current
        }
      }
      assertEquals("A1-B1", result)
    } finally {
      test.close()
    }
  }
}
