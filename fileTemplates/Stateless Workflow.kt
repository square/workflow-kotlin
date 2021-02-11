#set( $WorkflowName = "${NAME}Workflow" )
package ${PACKAGE_NAME}

import com.squareup.workflow1.StatelessWorkflow
import ${PACKAGE_NAME}.${WorkflowName}.Output
import ${PACKAGE_NAME}.${WorkflowName}.Props
import ${PACKAGE_NAME}.${WorkflowName}.Rendering

#parse("File Header.java")
class ${WorkflowName} : StatelessWorkflow<Props, Output, Rendering>() {

  data class Props
  data class Output
  data class Rendering

  override fun render(
    renderProps: Props,
    context: RenderContext
  ): Rendering {
    TODO("Render")
  }
}
