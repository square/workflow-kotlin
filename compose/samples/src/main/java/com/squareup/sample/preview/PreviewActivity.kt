@file:OptIn(
  WorkflowUiExperimentalApi::class,
  WorkflowUiExperimentalApi::class,
)

package com.squareup.sample.preview

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squareup.workflow.ui.compose.WorkflowRendering
import com.squareup.workflow.ui.compose.composedViewFactory
import com.squareup.workflow.ui.compose.tooling.preview
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

class PreviewActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      PreviewApp()
    }
  }
}

val previewContactRendering = ContactRendering(
  name = "Dim Tonnelly",
  details = ContactDetailsRendering(
    phoneNumber = "555-555-5555",
    address = "1234 Main St."
  )
)

@Composable fun PreviewApp() {
  MaterialTheme {
    Surface {
      contactViewFactory.preview(previewContactRendering)
    }
  }
}

data class ContactRendering(
  val name: String,
  val details: ContactDetailsRendering
)

data class ContactDetailsRendering(
  val phoneNumber: String,
  val address: String
)

private val contactViewFactory = composedViewFactory<ContactRendering> { rendering, environment ->
  Card(
    modifier = Modifier
      .padding(8.dp)
      .clickable { /* handle click */ }
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = spacedBy(8.dp),
    ) {
      Text(rendering.name, style = MaterialTheme.typography.body1)
      WorkflowRendering(
        rendering = rendering.details,
        viewEnvironment = environment,
        modifier = Modifier
          .aspectRatio(1f)
          .border(0.dp, Color.LightGray)
          .padding(8.dp)
      )
    }
  }
}
