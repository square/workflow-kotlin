package com.squareup.benchmark.composeworkflow.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeWorkflowMicroBenchmark {

  @get:Rule val benchmarkRule = BenchmarkRule()

  @Test fun foo() {
    benchmarkRule.measureRepeated {
      Thread.sleep(100)
    }
  }
}
