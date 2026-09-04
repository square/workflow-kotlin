package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

internal class WithCompositionLocalsTest {

  @Test
  fun reads_provided_value_inside_content() = runTest {
    val Local = compositionLocalOf { "default" }
    val test =
      TestComposition(backgroundScope) {
        withCompositionLocals(Local provides "provided") { Local.current }
      }
    try {
      assertEquals("provided", test.recompose())
    } finally {
      test.close()
    }
  }

  @Test
  fun returns_value_from_content_lambda() = runTest {
    val Local = compositionLocalOf { 0 }
    val test =
      TestComposition(backgroundScope) {
        withCompositionLocals(Local provides 7) { Local.current * 2 }
      }
    try {
      assertEquals(14, test.recompose())
    } finally {
      test.close()
    }
  }

  @Test
  fun reads_default_when_no_provider_present() = runTest {
    val Local = compositionLocalOf { "default" }
    val test = TestComposition(backgroundScope) { Local.current }
    try {
      assertEquals("default", test.recompose())
    } finally {
      test.close()
    }
  }

  @Test
  fun supports_static_composition_locals() = runTest {
    val Local = staticCompositionLocalOf { "default" }
    val test =
      TestComposition(backgroundScope) {
        withCompositionLocals(Local provides "static") { Local.current }
      }
    try {
      assertEquals("static", test.recompose())
    } finally {
      test.close()
    }
  }

  @Test
  fun nested_calls_resolve_to_innermost_value() = runTest {
    val Local = compositionLocalOf { "default" }
    val test =
      TestComposition(backgroundScope) {
        withCompositionLocals(Local provides "outer") {
          withCompositionLocals(Local provides "inner") { Local.current }
        }
      }
    try {
      assertEquals("inner", test.recompose())
    } finally {
      test.close()
    }
  }

  @Test
  fun outer_value_is_restored_after_inner_returns() = runTest {
    val Local = compositionLocalOf { "default" }
    val test =
      TestComposition(backgroundScope) {
        withCompositionLocals(Local provides "outer") {
          val inner = withCompositionLocals(Local provides "inner") { Local.current }
          inner + ":" + Local.current
        }
      }
    try {
      assertEquals("inner:outer", test.recompose())
    } finally {
      test.close()
    }
  }

  @Test
  fun multiple_locals_provided_at_once() = runTest {
    val A = compositionLocalOf { "A0" }
    val B = compositionLocalOf { "B0" }
    val test =
      TestComposition(backgroundScope) {
        withCompositionLocals(A provides "A1", B provides "B1") { A.current + "-" + B.current }
      }
    try {
      assertEquals("A1-B1", test.recompose())
    } finally {
      test.close()
    }
  }
}
