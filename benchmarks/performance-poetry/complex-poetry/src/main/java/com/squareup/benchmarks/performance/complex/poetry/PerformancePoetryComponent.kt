package com.squareup.benchmarks.performance.complex.poetry

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import com.squareup.sample.poetry.PoemWorkflow
import com.squareup.sample.poetry.PoemsBrowserWorkflow
import com.squareup.sample.poetry.model.Poem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Pretend generated CI code from pretend DI framework.
 */
class PerformancePoetryComponent : ViewModel() {
  private val poemIsLoading = MutableStateFlow(false)
  private val browserIsLoading = MutableStateFlow(false)
  private val simulatedPerfConfig = SimulatedPerfConfig(
    isComplex = true,
    useInitializingState = true,
    complexityDelay = 1000L
  )

  private val poemWorkflow: PoemWorkflow = PerformancePoemWorkflow(
    simulatedPerfConfig,
    poemIsLoading
  )

  private val poemsBrowserWorkflow: PoemsBrowserWorkflow = PerformancePoemsBrowserWorkflow(
    simulatedPerfConfig,
    poemWorkflow,
    browserIsLoading
  )

  private val loadingGatekeeperForPoems = MaybeLoadingGatekeeperWorkflow(
    childWithLoading = poemsBrowserWorkflow,
    childProps = Poem.allPoems,
    browserIsLoading.combine(poemIsLoading) { one, two -> one || two }
  )

  fun poetryModelFactory(owner: AppCompatActivity): PoetryModel.Factory =
    PoetryModel.Factory(
      owner,
      loadingGatekeeperForPoems
    )
}
