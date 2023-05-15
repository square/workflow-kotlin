@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui.container

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.RealScreenViewHolder
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun mockView(): View {
  return mock<View>().also { view ->
    val tags = mutableMapOf<Int, Any>()
    whenever(view.getTag(isA())).thenAnswer { invocation ->
      val id = invocation.arguments[0] as Int
      tags[id]
    }
    whenever(view.setTag(isA(), isA())).thenAnswer { invocation ->
      val id = invocation.arguments[0] as Int
      val value = invocation.arguments[1] as Any
      tags[id] = value
      Unit
    }
  }
}

internal object SomeEnvValue : ViewEnvironmentKey<String>() {
  override val default: String get() = error("Unset")
}

internal class WrappedScreen : AndroidScreen<WrappedScreen> {
  override val viewFactory = WrappedFactory()
}

internal class WrappedFactory : ScreenViewFactory<WrappedScreen> {
  override val type = WrappedScreen::class

  var lastEnv: ViewEnvironment? = null
  var lastView: View? = null

  override fun buildView(
    initialRendering: WrappedScreen,
    initialEnvironment: ViewEnvironment,
    context: Context,
    container: ViewGroup?
  ): ScreenViewHolder<WrappedScreen> {
    lastEnv = initialEnvironment
    return RealScreenViewHolder(
      view = mockView().also { lastView = it },
      initialEnvironment = initialEnvironment
    ) { _, newEnvironment ->
      lastEnv = newEnvironment
    }
  }
}
