package com.squareup.workflow1.visual

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.visual.VisualEnvironment.Companion.EMPTY
import org.junit.Assert.fail
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class VisualizerTest {
  private var lastLeafEnv: VisualEnvironment? = null

  private val visualizer: Visualizer<Unit, List<String>> = Visualizer {
    SequentialVisualFactory(
      listOf(leafFactory.widen(), wrapperAFactory.widen(), wrapperBFactory.widen())
    )
  }

  @Test fun seriesOfLeafUpdatesSingleViewInstance() {
    var firstView: List<String>? = null

    visualizer.show(LeafModel("hello"), Unit, EMPTY) { visual, doFirstUpdate ->
      firstView = visual
      doFirstUpdate()
    }
    assertThat(firstView).isEqualTo(listOf("hello"))

    visualizer.show(LeafModel("goodbye"), Unit, EMPTY) { _, _ ->
      fail("Should update existing view, not create a new one.")
    }
    assertThat(firstView).isEqualTo(listOf("hello", "goodbye"))
  }

  @Test fun defaultWithNameFactoryWorks() {
    var firstView: List<String>? = null
    visualizer.show(
      TestName(LeafModel("hello"), "name"), Unit, EMPTY
    ) { visual, doFirstUpdate ->
      firstView = visual
      doFirstUpdate()
    }
    assertThat(firstView).isEqualTo(listOf("hello"))
  }

  @Test fun newNameForcesNewView() {
    var firstView: List<String>? = null
    visualizer.show(
      TestName(LeafModel("hello"), "name1"), Unit, EMPTY
    ) { visual, doFirstUpdate ->
      firstView = visual
      doFirstUpdate()
    }
    visualizer.show(
      TestName(LeafModel("goodbye"), "name1"), Unit, EMPTY
    )

    var secondView: List<String>? = null
    visualizer.show(
      TestName(LeafModel("hello again!"), "name2"), Unit, EMPTY
    ) { visual, doFirstUpdate ->
      secondView = visual
      doFirstUpdate()
    }

    assertThat(firstView).isEqualTo(listOf("hello", "goodbye"))
    assertThat(secondView).isEqualTo(listOf("hello again!"))
  }

  @Test fun defaultWithEnvironmentFactoryWorks() {
    var firstView: List<String>? = null
    visualizer.show(
      TestEnv(LeafModel("hello"), EMPTY + (envKey to "yo")), Unit, EMPTY
    ) { visual, doFirstUpdate ->
      firstView = visual
      doFirstUpdate()
    }
    visualizer.show(
      TestEnv(LeafModel("goodbye"), EMPTY + (envKey to "yo")), Unit, EMPTY
    )
    // We rendered twice to demonstrate that WithEnvironment doesn't break compatibility.
    assertThat(firstView).isEqualTo(listOf("hello", "goodbye"))
    assertThat(lastLeafEnv!![envKey]).isEqualTo("yo")
  }

  @Test fun circularWrappingWorks() {
    var view: List<String>? = null
    visualizer.show(
      WrapperA(WrapperB(WrapperA(LeafModel("hi")))), Unit, EMPTY
    ) { visual, doFirstUpdate ->
      doFirstUpdate()
      view = visual
    }
    visualizer.show(
      WrapperA(WrapperB(WrapperA(LeafModel("bye")))), Unit, EMPTY
    )
    // We rendered twice to demonstrate that Wrapper doesn't break compatibility.
    assertThat(view).isEqualTo(listOf("A(B(A(hi)))", "A(B(A(bye)))"))
  }

  private data class LeafModel(val name: String)

  private class TestName(
    wrapped: Any,
    name: String
  ) : WithName<Any>(wrapped, name)

  private class TestEnv(
    wrapped: Any,
    environment: VisualEnvironment
  ) : WithEnvironment<Any>(wrapped, environment)

  private val envKey = object : VisualEnvironmentKey<String>() {
    override val default: String
      get() = error("No value set")
  }

  private class WrapperA(wrapped: Any) : Wrapper<Any>(wrapped)
  private class WrapperB(wrapped: Any) : Wrapper<Any>(wrapped)

  private val leafFactory =
    VisualFactory<Unit, LeafModel, List<String>> { _, _, env, _ ->
      lastLeafEnv = env
      val view = mutableListOf<String>()
      VisualHolder(view) {
        view += it.name
      }
    }

  private val wrapperAFactory =
    VisualFactory<Unit, WrapperA, List<String>> { rendering, context, environment, getFactory ->
      val wrappedHolder = getFactory(environment)
        .create(rendering.wrapped, context, environment, getFactory)

      val myView = mutableListOf<String>()

      VisualHolder(myView) { wrapper ->
        wrappedHolder.update(wrapper.wrapped)
        myView.clear()
        myView.addAll(wrappedHolder.visual.map { "A($it)" })
      }
    }

  private val wrapperBFactory =
    VisualFactory<Unit, WrapperB, List<String>> { rendering, context, environment, getFactory ->
      val wrappedHolder = getFactory(environment)
        .create(rendering.wrapped, context, environment, getFactory)

      val myView = mutableListOf<String>()

      VisualHolder(myView) { wrapper ->
        wrappedHolder.update(wrapper.wrapped)
        myView.clear()
        myView.addAll(wrappedHolder.visual.map { "B($it)" })
      }
    }
}
