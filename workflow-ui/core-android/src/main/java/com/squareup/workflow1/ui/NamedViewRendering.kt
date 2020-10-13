package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
data class NamedViewRendering<V : ViewRendering>(
  override val wrapped: V,
  override val name: String
) : NamedCompatible<V>, ViewRendering, ViewBuilder<V> {
  init {
    require(name.isNotBlank()) { "name must not be blank." }
  }

  override fun buildView(
    initialRendering: V,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    TODO("ray")
  }

  override val type: KClass<V> = wrapped::class as KClass<V>

  override fun toString(): String {
    return "${super.toString()}: $compatibilityKey"
  }
}
