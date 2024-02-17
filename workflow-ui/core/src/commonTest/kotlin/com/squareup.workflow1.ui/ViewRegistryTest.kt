package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.ViewRegistry.Entry
import com.squareup.workflow1.ui.ViewRegistry.Key
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
internal class ViewRegistryTest {

  @Test fun keys_from_bindings() {
    val factory1 = TestEntry(FooRendering::class)
    val factory2 = TestEntry(BarRendering::class)
    val registry = ViewRegistry(factory1, factory2)

    assertEquals(setOf(factory1.key, factory2.key), registry.keys)
  }

  @Test fun constructor_throws_on_duplicates() {
    val factory1 = TestEntry(FooRendering::class)
    val factory2 = TestEntry(FooRendering::class)

    val error = assertFailsWith<IllegalStateException> {
      ViewRegistry(factory1, factory2)
    }
    assertTrue { error.message!!.endsWith("must not have duplicate entries.") }
    assertTrue { error.message!!.contains(FooRendering::class.toString()) }
  }

  @Test fun getFactoryFor_works() {
    val fooFactory = TestEntry(FooRendering::class)
    val registry = ViewRegistry(fooFactory)

    val factory = registry[Key(FooRendering::class, TestEntry::class)]
    assertSame(fooFactory, factory)
  }

  @Test fun getFactoryFor_returns_null_on_missing_binding() {
    val fooFactory = TestEntry(FooRendering::class)
    val registry = ViewRegistry(fooFactory)

    assertNull(registry[Key(BarRendering::class, TestEntry::class)])
  }

  @Test fun viewRegistry_with_no_arguments_infers_type() {
    val registry = ViewRegistry()
    assertTrue(registry.keys.isEmpty())
  }

  @Test fun merge_prefers_right_side() {
    val factory1 = TestEntry(FooRendering::class)
    val factory2 = TestEntry(FooRendering::class)
    val merged = ViewRegistry(factory1) merge ViewRegistry(factory2)

    assertSame(factory2, merged[Key(FooRendering::class, TestEntry::class)])
  }

  @Test fun viewEnvironment_plus_ViewRegistry_prefers_new_registry_values() {
    val leftBar = TestEntry(BarRendering::class)
    val rightBar = TestEntry(BarRendering::class)

    val env = EMPTY + ViewRegistry(leftBar)
    val merged = env + ViewRegistry(rightBar, TestEntry(FooRendering::class))

    assertSame(rightBar, merged[ViewRegistry][Key(BarRendering::class, TestEntry::class)])
    assertNotNull(merged[ViewRegistry][Key(FooRendering::class, TestEntry::class)])
  }

  @Test fun viewEnvironment_plus_ViewEnvironment_prefers_right_ViewRegistry() {
    val leftBar = TestEntry(BarRendering::class)
    val rightBar = TestEntry(BarRendering::class)

    val leftEnv = EMPTY + ViewRegistry(leftBar)
    val rightEnv = EMPTY + ViewRegistry(rightBar, TestEntry(FooRendering::class))
    val merged = leftEnv + rightEnv

    assertSame(rightBar, merged[ViewRegistry][Key(BarRendering::class, TestEntry::class)])
    assertNotNull(merged[ViewRegistry][Key(FooRendering::class, TestEntry::class)])
  }

  @Test fun plus_of_empty_returns_this() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertSame(reg, reg + ViewRegistry())
  }

  @Test fun plus_to_empty_returns_other() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertSame(reg, ViewRegistry() + reg)
  }

  @Test fun merge_of_empty_reg_returns_this() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertSame(reg, reg merge ViewRegistry())
  }

  @Test fun merge_to_empty_reg_returns_other() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertSame(reg, ViewRegistry() merge reg)
  }

  @Test fun env_plus_empty_reg_returns_env() {
    val env = EMPTY + ViewRegistry(TestEntry(FooRendering::class))
    assertSame(env, env + ViewRegistry())
  }

  @Test fun env_plus_same_reg_returns_self() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    val env = EMPTY + reg
    assertSame(env, env + reg)
  }

  @Test fun reg_plus_self_throws_dup_entries() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertFailsWith<IllegalArgumentException> {
      reg + reg
    }
  }

  @Test fun registry_merge_self_returns_self() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertSame(reg, reg merge reg)
  }

  private class TestEntry<T : Any>(
    type: KClass<in T>
  ) : Entry<T> {
    override val key = Key(type, TestEntry::class)
  }

  private object FooRendering
  private object BarRendering
}
