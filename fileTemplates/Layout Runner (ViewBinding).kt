#set( $LayoutRunnerName = "${NAME}LayoutRunner" )
#set( $ViewBindingName = "YourViewBinding" )
#set( $RenderingName = "YourRendering" )
package ${PACKAGE_NAME}

import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

#parse("File Header.java")
// TODO Change "YourViewBinding" and "YourRendering" to your actual types.
@OptIn(WorkflowUiExperimentalApi::class)
class $LayoutRunnerName(
  private val binding: ${ViewBindingName}
) : LayoutRunner<${RenderingName}> {

  override fun showRendering(
    rendering: ${RenderingName},
    viewEnvironment: ViewEnvironment
  ) {
    TODO("Update ViewBinding from rendering")
  }

  companion object : ViewFactory<${RenderingName}> by bind(
      ${ViewBindingName}::inflate, ::$LayoutRunnerName
  )
}
