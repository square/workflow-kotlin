package com.squareup.sample.nestedoverlays

import android.view.View.GONE
import android.view.View.VISIBLE
import com.squareup.sample.nestedoverlays.databinding.TopAndBottomBarsBinding
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromViewBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.Wrapper

@OptIn(WorkflowUiExperimentalApi::class)
data class TopAndBottomBarsScreen<T : Screen>(
  override val content: T,
  val topBar: ButtonBar? = null,
  val bottomBar: ButtonBar? = null
) : AndroidScreen<TopAndBottomBarsScreen<T>>, Wrapper<Screen, T> {
  override fun <ContentU : Screen> map(transform: (T) -> ContentU) =
    TopAndBottomBarsScreen(transform(content), topBar, bottomBar)

  override val viewFactory: ScreenViewFactory<TopAndBottomBarsScreen<T>> =
    fromViewBinding(TopAndBottomBarsBinding::inflate) { screen, environment ->
      bodyStub.show(screen.content, environment)

      screen.topBar?.let { topBarStub.show(it, environment) }
      screen.bottomBar?.let { bottomBarStub.show(it, environment) }
      topBarStub.actual.visibility = if (screen.topBar != null) VISIBLE else GONE
      bottomBarStub.actual.visibility = if (screen.bottomBar != null) VISIBLE else GONE
    }
}
