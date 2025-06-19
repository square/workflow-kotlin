package com.squareup.sample.compose.preview

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.compose.ComposeScreen
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.tooling.Preview

class PreviewActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      PreviewApp()
    }
  }
}

val previewContactScreen = ContactScreen(
  name = "Dim Tonnelly",
  details = ContactDetailsScreen(
    phoneNumber = "555-555-5555",
    address = "1234 Apgar Lane"
  )
)

@Preview
@Composable
fun PreviewApp() {
  MaterialTheme {
    Surface {
      previewContactScreen.Preview()
    }
  }
}

data class ContactScreen(
  val name: String,
  val details: ContactDetailsScreen
) : ComposeScreen {
  @Composable override fun Content() {
    Contact(this)
  }
}

// Note, not a ComposeScreen and has no view binding of any kind,
// which would normally be a runtime error. We're demonstrating that
// the preview is able to stub out the WorkflowRendering call below.
data class ContactDetailsScreen(
  val phoneNumber: String,
  val address: String
) : Screen

@Composable
private fun Contact(screen: ContactScreen) {
  Card(
    modifier = Modifier
      .padding(8.dp)
      .clickable { /* handle click */ }
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = spacedBy(8.dp),
    ) {
      Text(screen.name, style = MaterialTheme.typography.body1)
      WorkflowRendering(
        rendering = screen.details,
        modifier = Modifier
          .aspectRatio(1f)
          .border(0.dp, Color.LightGray)
          .padding(8.dp)
      )
    }
  }
}
