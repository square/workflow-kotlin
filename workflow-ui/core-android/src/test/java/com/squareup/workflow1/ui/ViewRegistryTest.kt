package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

@OptIn(WorkflowUiExperimentalApi::class)
internal class ViewRegistryTest {

  @Test fun missingBindingMessage_isUseful() {
    val emptyReg = object : ViewRegistry {
      override val keys: Set<KClass<*>> = emptySet()
      override fun <RenderingT : Any> getFactoryFor(
        renderingType: KClass<out RenderingT>
      ): ViewFactory<RenderingT>? = null
    }

    val error = assertFailsWith<IllegalArgumentException> {
      emptyReg.buildView("render this, bud")
    }
    assertThat(error.message).isEqualTo(
      "A com.squareup.workflow1.ui.ViewFactory should have been registered to display " +
        "kotlin.String instances, or that class should implement AndroidViewRendering<String>."
    )
  }

  @Test fun simpleGetWorks() {
    val reg = ViewRegistry(fooFactory)
    assertThat(reg.getFactoryForRendering(Foo)).isSameInstanceAs(fooFactory)
  }

  @Test fun androidViewRenderingWorks() {
    assertThat(ViewRegistry().getFactoryForRendering(AndroidFoo))
      .isSameInstanceAs(AndroidFooFactory)
  }

  @Test fun wrappingWorks() {
    val reg = ViewRegistry(fooFactory)
    val wrapped = SimplestWrapper(SimplestWrapper(SimplestWrapper(Foo)))
    assertThat(reg.getFactoryForRendering(wrapped)).isSameInstanceAs(fooFactory)
  }

  @Test fun canWrapAndroidViewRendering() {
    assertThat(ViewRegistry().getFactoryForRendering(Named(AndroidFoo, "Bar")))
      .isSameInstanceAs(AndroidFooFactory)
  }

  private object Foo

  private val fooFactory = BuilderViewFactory(
    type = Foo::class,
    viewConstructor = { _, _, _, _ -> error("nope") }
  )

  private object AndroidFoo : AndroidViewRendering<AndroidFoo> {
    override val viewFactory = AndroidFooFactory
  }

  object AndroidFooFactory : ViewFactory<AndroidFoo> by BuilderViewFactory(
    type = AndroidFoo::class,
    viewConstructor = { _, _, _, _ -> error("still nope") }
  )

  private class SimplestWrapper<W : Any>(
    override val wrapped: W
  ) : Wrapper<W>
}
