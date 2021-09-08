#set ($prefix = $NAME.replace('Workflow', '') )
## build props string
#if( $PROPS_TYPE_OPTIONAL == '')
    #set ($props_type = $prefix + "Props")
#else
    #set ($props_type = $PROPS_TYPE_OPTIONAL)
#end
## build state string 
#if( $STATE_TYPE_OPTIONAL == '')
    #set ($state_type = $prefix + "State")
#else
    #set ($state_type = $STATE_TYPE_OPTIONAL)
#end
## build output string
#if( $OUTPUT_TYPE_OPTIONAL == '')
    #set ($output_type = $prefix + "Output")
#else
    #set ($output_type = $OUTPUT_TYPE_OPTIONAL)
#end
## build rendering string
#if( $RENDERING_TYPE_OPTIONAL == '')
    #set ($rendering_type = $prefix + "Rendering")
#else
    #set ($rendering_type = $RENDERING_TYPE_OPTIONAL)
#end
package $PACKAGE_NAME

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow

#if( $PROPS_TYPE_OPTIONAL == '') ## import if we create below
import $PACKAGE_NAME.$NAME.$props_type     
#end
#if( $STATE_TYPE_OPTIONAL == '') ## import if we create below
import $PACKAGE_NAME.$NAME.$state_type     
#end
#if( $OUTPUT_TYPE_OPTIONAL == '') ## import if we create below
import $PACKAGE_NAME.$NAME.$output_type     
#end
#if( $RENDERING_TYPE_OPTIONAL == '') ## import if we create below
import $PACKAGE_NAME.$NAME.$rendering_type     
#end

#parse("File Header.java")
object $NAME : StatefulWorkflow<$props_type, $state_type, $output_type, $rendering_type>() {

   #if( $PROPS_TYPE_OPTIONAL == '') ## create if not supplied
   data class $props_type(
     // TODO add args   
   )     
   #end
   
   #if( $STATE_TYPE_OPTIONAL == '') ## create if not supplied
   data class $state_type(
     // TODO add args   
   )     
   #end
   
  #if( $OUTPUT_TYPE_OPTIONAL == '') ## create if not supplied
   data class $output_type(
     // TODO add args   
   )     
   #end
   
   #if( $RENDERING_TYPE_OPTIONAL == '') ## create if not supplied
   data class $rendering_type(
     // TODO add args   
   )     
   #end

  override fun initialState(
    props: $props_type,
    snapshot: Snapshot?
  ): $state_type = TODO("Initialize state")

  override fun render(
    renderProps: $props_type,
    renderState: $state_type,
    context: RenderContext
  ): $rendering_type {
    TODO("Render")
  }

  override fun snapshotState(state: $state_type): Snapshot? = Snapshot.write {
    TODO("Save state")
  }
}
