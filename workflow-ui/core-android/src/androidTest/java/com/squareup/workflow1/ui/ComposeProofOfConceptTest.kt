package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
class ComposeProofOfConceptTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  @Test fun stuff() {
    val parentView = FrameLayout(instrumentation.context)
    val stub = WorkflowViewStub(parentView.context)
    parentView.addView(stub)
    val viewRegistry = ViewRegistry(TestRendering)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    instrumentation.runOnMainSync {
      stub.update(TestRendering("hello"), viewEnvironment)
    }

    assertThat(stub.actual).isInstanceOf(TestView::class.java)
    assertThat((stub.actual as TestView).string).isEqualTo("hello")

    // TODO make this test use Compose
  }

  private class TestView(context: Context) : View(context) {
    var string: String = ""
  }

  private data class TestRendering(val string: String) {
    companion object : ViewFactory<TestRendering>
    by BuilderViewFactory(
      TestRendering::class,
      viewConstructor = { initialRendering, initialViewEnvironment, contextForNewView, container ->
        TestView(contextForNewView).apply {
          bindShowRendering(initialRendering, initialViewEnvironment) { rendering, env ->
            string = rendering.string
          }
        }
      }
    )
  }
}
