package com.squareup.sample.dungeon

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Custom [AndroidJUnitRunner] to use [TestApplication].
 */
class DungeonTestRunner : AndroidJUnitRunner() {

  override fun newApplication(
    cl: ClassLoader,
    className: String,
    context: Context
  ): Application {
    return super.newApplication(cl, TestApplication::class.java.name, context)
  }
}
