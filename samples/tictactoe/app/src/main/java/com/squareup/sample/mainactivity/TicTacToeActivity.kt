package com.squareup.sample.mainactivity

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.test.espresso.IdlingResource
import com.squareup.sample.authworkflow.AuthViewFactories
import com.squareup.sample.container.SampleContainers
import com.squareup.sample.gameworkflow.TicTacToeViewFactories
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.withRegistry
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class TicTacToeActivity : AppCompatActivity() {
  /** Exposed for use by espresso tests. */
  lateinit var idlingResource: IdlingResource

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val component: TicTacToeComponent by viewModels()
    val model: TicTacToeModel by viewModels { component.ticTacToeModelFactory(this) }

    idlingResource = component.idlingResource

    workflowContentView.take(lifecycle, model.renderings.map { it.withRegistry(viewRegistry) })

    lifecycleScope.launch {
      model.renderings.collect { Timber.d("rendering: %s", it) }
    }
    lifecycleScope.launch {
      model.waitForExit()
      finish()
    }
  }

  private companion object {
    val viewRegistry = SampleContainers +
      AuthViewFactories +
      TicTacToeViewFactories
  }
}
