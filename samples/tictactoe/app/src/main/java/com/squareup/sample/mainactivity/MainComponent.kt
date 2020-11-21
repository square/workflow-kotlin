package com.squareup.sample.mainactivity

import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.squareup.sample.authworkflow.AuthService
import com.squareup.sample.authworkflow.AuthService.AuthRequest
import com.squareup.sample.authworkflow.AuthService.AuthResponse
import com.squareup.sample.authworkflow.AuthService.SecondFactorRequest
import com.squareup.sample.authworkflow.AuthWorkflow
import com.squareup.sample.authworkflow.RealAuthService
import com.squareup.sample.authworkflow.RealAuthWorkflow
import com.squareup.sample.gameworkflow.RealGameLog
import com.squareup.sample.gameworkflow.RealRunGameWorkflow
import com.squareup.sample.gameworkflow.RealTakeTurnsWorkflow
import com.squareup.sample.gameworkflow.RunGameWorkflow
import com.squareup.sample.gameworkflow.TakeTurnsWorkflow
import com.squareup.sample.mainworkflow.MainWorkflow
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import timber.log.Timber

/**
 * Pretend generated code of a pretend DI framework.
 */
internal class MainComponent {
  private val countingIdlingResource = CountingIdlingResource("AuthServiceIdling")
  val idlingResource: IdlingResource = countingIdlingResource

  private val realAuthService = RealAuthService()

  private val authService = object : AuthService {
    override fun login(request: AuthRequest): Single<AuthResponse> {
      return realAuthService.login(request)
          .doOnSubscribe { countingIdlingResource.increment() }
          .doAfterTerminate { countingIdlingResource.decrement() }
    }

    override fun secondFactor(request: SecondFactorRequest): Single<AuthResponse> {
      return realAuthService.secondFactor(request)
          .doOnSubscribe { countingIdlingResource.increment() }
          .doAfterTerminate { countingIdlingResource.decrement() }
    }
  }

  private fun authWorkflow(): AuthWorkflow = RealAuthWorkflow(authService)

  private fun gameLog() = RealGameLog(mainThread())

  private fun gameWorkflow(): RunGameWorkflow = RealRunGameWorkflow(takeTurnsWorkflow(), gameLog())

  private fun takeTurnsWorkflow(): TakeTurnsWorkflow = RealTakeTurnsWorkflow()

  val mainWorkflow = MainWorkflow(authWorkflow(), gameWorkflow())

  companion object {
    init {
      Timber.plant(Timber.DebugTree())

      val stock = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        Timber.e(error)
        stock?.uncaughtException(thread, error)
      }
    }
  }
}
