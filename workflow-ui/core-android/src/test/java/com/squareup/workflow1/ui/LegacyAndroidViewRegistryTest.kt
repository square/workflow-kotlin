@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewRegistry.Entry
import com.squareup.workflow1.ui.container.mockView
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
internal class LegacyAndroidViewRegistryTest {

  @OptIn(WorkflowUiExperimentalApi::class)
  @Test fun missingBindingMessage_isUseful() {
    val emptyReg = object : ViewRegistry {
      override val keys: Set<KClass<*>> = emptySet()
      override fun <RenderingT : Any> getEntryFor(
        renderingType: KClass<out RenderingT>
      ): Entry<RenderingT>? = null
    }

    val error = assertFailsWith<IllegalArgumentException> {
      emptyReg.buildView("render this, bud")
    }
    assertThat(error.message).isEqualTo(
      "A ViewFactory should have been registered to display " +
        "render this, bud, or that class should implement AndroidViewRendering."
    )
  }

  @Test fun `getFactoryFor delegates to composite registries`() {
    val fooFactory = TestViewFactory(FooRendering::class)
    val barFactory = TestViewFactory(BarRendering::class)
    val bazFactory = TestViewFactory(BazRendering::class)
    val fooBarRegistry = TestRegistry(
      mapOf(
        FooRendering::class to fooFactory,
        BarRendering::class to barFactory
      )
    )
    val bazRegistry = TestRegistry(factories = mapOf(BazRendering::class to bazFactory))
    val registry = fooBarRegistry + bazRegistry

    assertThat(registry.getEntryFor(FooRendering::class))
      .isSameInstanceAs(fooFactory)
    assertThat(registry.getEntryFor(BarRendering::class))
      .isSameInstanceAs(barFactory)
    assertThat(registry.getEntryFor(BazRendering::class))
      .isSameInstanceAs(bazFactory)
  }

  @Test fun `getFactoryFor returns null on missing registry in composite`() {
    val fooRegistry = TestRegistry(setOf(FooRendering::class))
    val bazRegistry = TestRegistry(setOf(BazRendering::class))
    val registry = bazRegistry + fooRegistry

    assertThat(registry.getEntryFor(BarRendering::class)).isNull()
  }

  @Test fun `keys includes all composite registries' keys`() {
    val fooBarRegistry = TestRegistry(setOf(FooRendering::class, BarRendering::class))
    val bazRegistry = TestRegistry(setOf(BazRendering::class))
    val registry = fooBarRegistry + bazRegistry

    assertThat(registry.keys).containsExactly(
      FooRendering::class,
      BarRendering::class,
      BazRendering::class
    )
  }

  @Test fun `keys from bindings`() {
    val factory1 = TestViewFactory(FooRendering::class)
    val factory2 = TestViewFactory(BarRendering::class)
    val registry = ViewRegistry(factory1, factory2)

    assertThat(registry.keys).containsExactly(factory1.type, factory2.type)
  }

  @Test fun `constructor throws on duplicates`() {
    val factory1 = TestViewFactory(FooRendering::class)
    val factory2 = TestViewFactory(FooRendering::class)

    val error = assertFailsWith<IllegalStateException> {
      ViewRegistry(factory1, factory2)
    }
    assertThat(error).hasMessageThat()
      .endsWith("must not have duplicate entries.")
    assertThat(error).hasMessageThat()
      .contains(FooRendering::class.java.name)
  }

  @Test fun `getFactoryFor works`() {
    val fooFactory = TestViewFactory(FooRendering::class)
    val registry = ViewRegistry(fooFactory)

    val factory = registry.getEntryFor(FooRendering::class)
    assertThat(factory).isSameInstanceAs(fooFactory)
  }

  @Test fun `getFactoryFor returns null on missing binding`() {
    val fooFactory = TestViewFactory(FooRendering::class)
    val registry = ViewRegistry(fooFactory)

    assertThat(registry.getEntryFor(BarRendering::class)).isNull()
  }

  @Test fun `buildView honors AndroidViewRendering`() {
    val registry = ViewRegistry()
    registry.buildView(ViewRendering)
    assertThat(ViewRendering.viewFactory.called).isTrue()
  }

  @Test fun `buildView prefers registry entries to AndroidViewRendering`() {
    val registry = ViewRegistry(overrideViewRenderingFactory)
    registry.buildView(ViewRendering)
    assertThat(ViewRendering.viewFactory.called).isFalse()
    assertThat(overrideViewRenderingFactory.called).isTrue()
  }

  @Test fun `buildView auto converts unwrapped Screen and updates screenOrNull correctly`() {
    val registry = ViewRegistry()
    val view = registry.buildView(ScreenRendering)
    view.start()
    assertThat(view.getRendering<Any>()).isSameInstanceAs(ScreenRendering)
    assertThat(view.screenOrNull).isSameInstanceAs(ScreenRendering)
  }

  @Test fun `buildView auto converts wrapped Screen and updates screen correctly`() {
    val registry = ViewRegistry()
    val rendering = Named(ScreenRendering, "fnord")
    val view = registry.buildView(rendering)
    view.start()
    assertThat(compatible(view.getRendering()!!, rendering)).isTrue()
    assertThat(compatible(view.screen, asScreen(rendering))).isTrue()
  }

  @Test fun `ViewRegistry with no arguments infers type`() {
    val registry = ViewRegistry()
    assertTrue(registry.keys.isEmpty())
  }

  private object FooRendering
  private object BarRendering
  private object BazRendering

  private object ViewRendering : AndroidViewRendering<ViewRendering> {
    override val viewFactory = TestViewFactory(ViewRendering::class)
  }

  private object ScreenRendering : AndroidScreen<ScreenRendering> {
    override val viewFactory = TestScreenViewFactory(ScreenRendering::class)
  }

  private val overrideViewRenderingFactory = TestViewFactory(ViewRendering::class)

  private class TestRegistry(private val factories: Map<KClass<*>, ViewFactory<*>>) : ViewRegistry {
    constructor(keys: Set<KClass<*>>) : this(keys.associateWith { TestViewFactory(it) })

    override val keys: Set<KClass<*>> get() = factories.keys

    @Suppress("UNCHECKED_CAST")
    override fun <RenderingT : Any> getEntryFor(
      renderingType: KClass<out RenderingT>
    ): Entry<RenderingT> = factories.getValue(renderingType) as Entry<RenderingT>
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  private fun <R : Any> ViewRegistry.buildView(rendering: R): View =
    buildView(rendering, ViewEnvironment.EMPTY + this, mock())

  @OptIn(WorkflowUiExperimentalApi::class)
  private class TestViewFactory<R : Any>(override val type: KClass<R>) : ViewFactory<R> {
    var called = false

    override fun buildView(
      initialRendering: R,
      initialViewEnvironment: ViewEnvironment,
      contextForNewView: Context,
      container: ViewGroup?
    ): View {
      called = true
      return mockView().also {
        it.bindShowRendering(initialRendering, initialViewEnvironment) { _, _ -> }
      }
    }
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  private class TestScreenViewFactory<R : Screen>(
    override val type: KClass<R>
  ) : ScreenViewFactory<R> {
    override fun buildView(
      initialRendering: R,
      initialEnvironment: ViewEnvironment,
      context: Context,
      container: ViewGroup?
    ): ScreenViewHolder<R> {
      return ScreenViewHolder(initialEnvironment, mockView()) { _, _ -> }
    }
  }
}
