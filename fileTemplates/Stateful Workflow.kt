#set( $WorkflowName = "${NAME}Workflow" )
package ${PACKAGE_NAME}

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import ${PACKAGE_NAME}.${WorkflowName}.Output
import ${PACKAGE_NAME}.${WorkflowName}.Props
import ${PACKAGE_NAME}.${WorkflowName}.Rendering
import ${PACKAGE_NAME}.${WorkflowName}.State

#parse("File Header.java")
class ${WorkflowName} : StatefulWorkflow<Props, State, Output, Rendering>() {

  data class Props
  data class State
  data class Output
  data class Rendering

  override fun initialState(
    props: Props,
    snapshot: Snapshot?
  ): State = TODO("Initialize state")

  override fun render(
    props: Props,
    state: State,
    context: RenderContext
  ): Rendering {
    TODO("Render")
  }

  override fun snapshotState(state: State): Snapshot? = Snapshot.write {
    TODO("Save state")
  }
}
