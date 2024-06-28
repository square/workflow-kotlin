@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.compose.preview

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ComposeScreen
import com.squareup.workflow1.ui.compose.WorkflowRendering

data class ContactRendering(
  val name: String,
  val details: ContactDetailsRendering
) : ComposeScreen {
  @Composable override fun Content(viewEnvironment: ViewEnvironment) {
    ContactDetails(this, viewEnvironment)
  }
}

data class ContactDetailsRendering(
  val phoneNumber: String,
  val address: String
) : Screen

@Composable
private fun ContactDetails(
  rendering: ContactRendering,
  environment: ViewEnvironment
) {
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

val previewContactRendering = ContactRendering(
  name = "Dim Tonnelly",
  details = ContactDetailsRendering(
    phoneNumber = "555-555-5555",
    address = "1234 Apgar Lane"
  )
)
