package workflow.tutorial

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(workflow.tutorial.views.R.layout.welcome_view)
  }
}
