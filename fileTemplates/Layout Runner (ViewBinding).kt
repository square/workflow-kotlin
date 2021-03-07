## Unlike the Workflow Templates, we never generate inner classes for view bindings
## or rendering types. i.e. they are never "optional"
package ${PACKAGE_NAME}

import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

#parse("File Header.java")
@OptIn(WorkflowUiExperimentalApi::class)
class $Name(
  private val binding: $VIEW_BINDING_TYPE
) : LayoutRunner<$RENDERING_TYPE> {

  override fun showRendering(
    rendering: $RENDERING_TYPE,
    viewEnvironment: ViewEnvironment
  ) {
    TODO("Update ViewBinding from rendering")
  }

  companion object : ViewFactory<$RENDERING_TYPE> by bind(
      $VIEW_BINDING_TYPE::inflate, ::$NAME
  )
}
