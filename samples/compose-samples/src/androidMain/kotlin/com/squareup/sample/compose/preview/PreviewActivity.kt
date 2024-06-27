@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.compose.preview

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.tooling.Preview

class PreviewActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      PreviewApp()
    }
  }
}


@Preview
@Composable
fun PreviewApp() {
  MaterialTheme {
    Surface {
      previewContactRendering.Preview()
    }
  }
}
