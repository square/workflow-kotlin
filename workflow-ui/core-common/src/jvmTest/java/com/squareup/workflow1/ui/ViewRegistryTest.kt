package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.ViewRegistry.Entry
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
internal class ViewRegistryTest {

  @Test fun `keys from bindings`() {
    val factory1 = TestEntry(FooRendering::class)
    val factory2 = TestEntry(BarRendering::class)
    val registry = ViewRegistry(factory1, factory2)

    assertThat(registry.keys).containsExactly(factory1.type, factory2.type)
  }

  @Test fun `constructor throws on duplicates`() {
    val factory1 = TestEntry(FooRendering::class)
    val factory2 = TestEntry(FooRendering::class)

    val error = assertFailsWith<IllegalStateException> {
      ViewRegistry(factory1, factory2)
    }
    assertThat(error).hasMessageThat()
      .endsWith("must not have duplicate entries.")
    assertThat(error).hasMessageThat()
      .contains(FooRendering::class.java.name)
  }

  @Test fun `getFactoryFor works`() {
    val fooFactory = TestEntry(FooRendering::class)
    val registry = ViewRegistry(fooFactory)

    val factory = registry[FooRendering::class]
    assertThat(factory).isSameInstanceAs(fooFactory)
  }

  @Test fun `getFactoryFor returns null on missing binding`() {
    val fooFactory = TestEntry(FooRendering::class)
    val registry = ViewRegistry(fooFactory)

    assertThat(registry[BarRendering::class]).isNull()
  }

  @Test fun `ViewRegistry with no arguments infers type`() {
    val registry = ViewRegistry()
    assertTrue(registry.keys.isEmpty())
  }

  @Test fun `merge prefers right side`() {
    val factory1 = TestEntry(FooRendering::class)
    val factory2 = TestEntry(FooRendering::class)
    val merged = ViewRegistry(factory1) merge ViewRegistry(factory2)

    assertThat(merged[FooRendering::class]).isSameInstanceAs(factory2)
  }

  @Test fun `ViewEnvironment plus ViewRegistry prefers new registry values`() {
    val leftBar = TestEntry(BarRendering::class)
    val rightBar = TestEntry(BarRendering::class)

    val env = EMPTY + ViewRegistry(leftBar)
    val merged = env + ViewRegistry(rightBar, TestEntry(FooRendering::class))

    assertThat(merged[ViewRegistry][BarRendering::class]).isSameInstanceAs(rightBar)
    assertThat(merged[ViewRegistry][FooRendering::class]).isNotNull()
  }

  @Test fun `ViewEnvironment plus ViewEnvironment prefers right ViewRegistry`() {
    val leftBar = TestEntry(BarRendering::class)
    val rightBar = TestEntry(BarRendering::class)

    val leftEnv = EMPTY + ViewRegistry(leftBar)
    val rightEnv = EMPTY + ViewRegistry(rightBar, TestEntry(FooRendering::class))
    val merged = leftEnv + rightEnv

    assertThat(merged[ViewRegistry][BarRendering::class]).isSameInstanceAs(rightBar)
    assertThat(merged[ViewRegistry][FooRendering::class]).isNotNull()
  }

  @Test fun `plus of empty returns this`() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertThat(reg + ViewRegistry()).isSameInstanceAs(reg)
  }

  @Test fun `plus to empty returns other`() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertThat(ViewRegistry() + reg).isSameInstanceAs(reg)
  }

  @Test fun `merge of empty reg returns this`() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertThat(reg merge ViewRegistry()).isSameInstanceAs(reg)
  }

  @Test fun `merge to empty reg returns other`() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertThat(ViewRegistry() merge reg).isSameInstanceAs(reg)
  }

  @Test fun `env plus empty reg returns env`() {
    val env = EMPTY + ViewRegistry(TestEntry(FooRendering::class))
    assertThat(env + ViewRegistry()).isSameInstanceAs(env)
  }

  @Test fun `env plus same reg returns self`() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    val env = EMPTY + reg
    assertThat(env + reg).isSameInstanceAs(env)
  }

  @Test fun `reg plus self throws dup entries`() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertFailsWith<IllegalArgumentException> {
      reg + reg
    }
  }

  @Test fun `registry merge self returns self`() {
    val reg = ViewRegistry(TestEntry(FooRendering::class))
    assertThat(reg merge reg).isSameInstanceAs(reg)
  }

  private class TestEntry<T : Any>(
    override val type: KClass<in T>
  ) : Entry<T>

  private object FooRendering
  private object BarRendering
}
