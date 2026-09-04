package com.squareup.sample.compose.presenterdemo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.squareup.sample.compose.presenterdemo.app.auth.AuthService
import com.squareup.workflow1.presenter.DefaultPresenterSlot
import com.squareup.workflow1.presenter.present
import com.squareup.workflow1.presenter.ui.View
import com.squareup.workflow1.presenter.ui.ViewModel
import com.squareup.workflow1.presenter.ui.ViewRegistry
import com.squareup.workflow1.presenter.ui.ViewRegistryProvider
import kotlin.reflect.KClass

class ComposeDemoActivity(
  private val authService: AuthService,
  private val appletPresenter: AppletPresenter,
) : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val roots by present(lifecycleScope) {
      AppPresenter(authService, appletPresenter)
    }

    val viewRegistry = object : ViewRegistry {
      override fun <T : Any> findView(viewModelType: KClass<T>): View<T>? {
        TODO("Not yet implemented")
      }
    }

    setContent {
      ViewRegistryProvider(viewRegistry) {
        val root = roots[DefaultPresenterSlot]
        if (root != null) {
          ViewModel(root)
        }
      }
    }
  }
}
