@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.compose.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ComposeScreen

data class SampleScreen(val onSampleClicked: (Sample) -> Unit) : ComposeScreen {
  @Composable
  override fun Content(viewEnvironment: ViewEnvironment) {
    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
      items(samples) { sample ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onSampleClicked(sample) }
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(sample.name, style = MaterialTheme.typography.h6)
            Text(sample.description, style = MaterialTheme.typography.body1)
          }
        }
      }
    }
  }
}
