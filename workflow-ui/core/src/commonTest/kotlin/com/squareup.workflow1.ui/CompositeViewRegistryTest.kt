package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewRegistry.Entry
import com.squareup.workflow1.ui.ViewRegistry.Key
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
internal class CompositeViewRegistryTest {

  @Test fun constructor_throws_on_duplicates() {
    val fooBarRegistry = TestRegistry(setOf(FooRendering::class, BarRendering::class))
    val barBazRegistry = TestRegistry(setOf(BarRendering::class, BazRendering::class))

    val error = assertFailsWith<IllegalArgumentException> {
      fooBarRegistry + barBazRegistry
    }
    assertTrue { error.message!!.startsWith("Must not have duplicate entries: ") }
    assertTrue { error.message!!.contains(BarRendering::class.toString()) }
  }

  @Test fun getFactoryFor_delegates_to_composite_registries() {
    val fooFactory = TestEntry(FooRendering::class)
    val barFactory = TestEntry(BarRendering::class)
    val bazFactory = TestEntry(BazRendering::class)
    val fooBarRegistry = TestRegistry(
      mapOf(
        fooFactory.key to fooFactory,
        barFactory.key to barFactory
      )
    )
    val bazRegistry = TestRegistry(factories = mapOf(bazFactory.key to bazFactory))
    val registry = fooBarRegistry + bazRegistry

    assertSame(fooFactory, registry.getEntryFor(Key(FooRendering::class, TestEntry::class)))
    assertSame(barFactory, registry.getEntryFor(Key(BarRendering::class, TestEntry::class)))
    assertSame(bazFactory, registry.getEntryFor(Key(BazRendering::class, TestEntry::class)))
  }

  @Test fun getFactoryFor_returns_null_on_missing_registry() {
    val fooRegistry = TestRegistry(setOf(FooRendering::class))
    val registry = CompositeViewRegistry(ViewRegistry(), fooRegistry)

    assertNull(registry.getEntryFor(Key(BarRendering::class, TestEntry::class)))
  }

  @Test fun keys_includes_all_composite_registries_keys() {
    val fooBarRegistry = TestRegistry(setOf(FooRendering::class, BarRendering::class))
    val bazRegistry = TestRegistry(setOf(BazRendering::class))
    val registry = CompositeViewRegistry(fooBarRegistry, bazRegistry)

    assertEquals(
      setOf(
        Key(FooRendering::class, TestEntry::class),
        Key(BarRendering::class, TestEntry::class),
        Key(BazRendering::class, TestEntry::class)
      ),
      registry.keys
    )
  }

  private class TestEntry<T : Any>(type: KClass<in T>) : Entry<T> {
    override val key = Key(type, TestEntry::class)
  }

  private object FooRendering
  private object BarRendering
  private object BazRendering

  private class TestRegistry(private val factories: Map<Key<*, *>, Entry<*>>) : ViewRegistry {
    constructor(keys: Set<KClass<*>>) : this(
      keys.associate {
        val entry = TestEntry(it)
        entry.key to entry
      }
    )

    override val keys: Set<Key<*, *>> get() = factories.keys

    @Suppress("UNCHECKED_CAST")
    override fun <RenderingT : Any, FactoryT : Any> getEntryFor(
      key: Key<RenderingT, FactoryT>
    ): Entry<RenderingT> = factories.getValue(key) as Entry<RenderingT>
  }
}
