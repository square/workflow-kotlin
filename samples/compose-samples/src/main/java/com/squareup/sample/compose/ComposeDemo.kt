package com.squareup.sample.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.squareup.workflow1.RootViewModel
import com.squareup.workflow1.present
import com.squareup.workflow1.ui.compose.RootViewModel
import com.squareup.workflow1.ui.compose.ViewRegistry
import kotlin.reflect.typeOf

class ComposeDemoActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val (root, resolver) = present(lifecycleScope) {
      BackStackPresenter {

      }
    }

    val viewRegistry = ViewRegistry { ref ->
      when (ref.type) {
        typeOf<RootViewModel>() -> {
          TODO()
        }

        else -> null
      }
    }

    setContent {
      RootViewModel(
        root = root,
        resolver = resolver,
        registry = viewRegistry,
      )
    }
  }
}
