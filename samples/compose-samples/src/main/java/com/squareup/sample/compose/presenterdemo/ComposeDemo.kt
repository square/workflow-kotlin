package com.squareup.sample.compose.presenterdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.squareup.sample.compose.presenterdemo.auth.AuthService
import com.squareup.workflow1.presenter.ViewModelRefs
import com.squareup.workflow1.presenter.present
import com.squareup.workflow1.presenter.ui.RootViewModel
import com.squareup.workflow1.presenter.ui.ViewRegistry
import kotlin.reflect.typeOf

class ComposeDemoActivity(
  private val authService: AuthService,
  private val appletPresenter: AppletPresenter,
) : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val (root, resolver) = present(lifecycleScope) {
      AppPresenter(authService, appletPresenter)
    }

    val viewRegistry = ViewRegistry { ref ->
      when (ref.type) {
        typeOf<ViewModelRefs>() -> {
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
