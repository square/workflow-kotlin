import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.squareup.sample.compose.defaultViewEnvironment
import com.squareup.sample.compose.launcher.SampleWorkflow
import com.squareup.sample.compose.states.Action
import com.squareup.sample.compose.states.Store
import com.squareup.sample.compose.states.createStore
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

fun MainViewController() = ComposeUIViewController { App() }

val store: Store = CoroutineScope(SupervisorJob()).createStore()

@OptIn(WorkflowUiExperimentalApi::class)
@Composable
fun App() {
  MaterialTheme {
    val rendering by SampleWorkflow.renderAsState(Unit) {}

    WorkflowRendering(
      rendering,
      defaultViewEnvironment()
    )
  }
}

fun onBackGesture() {
  store.send(Action.OnBackPressed)
}
