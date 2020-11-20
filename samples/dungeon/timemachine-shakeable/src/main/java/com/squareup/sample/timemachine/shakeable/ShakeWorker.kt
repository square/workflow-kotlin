package com.squareup.sample.timemachine.shakeable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import com.squareup.seismic.ShakeDetector
import com.squareup.workflow1.Worker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf

/**
 * [Worker] that listens for shake gestures.
 *
 * When running in an emulator, you can fake a shake by running the following command:
 *
 *     adb shell am broadcast -a com.squareup.sample.timemachine.SHAKE
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShakeWorker(private val context: Context) : Worker<Unit> {

  private val sensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager

  private val realShakes = callbackFlow {
    val shakeDetector = ShakeDetector { offer(Unit) }
    shakeDetector.start(sensorManager)
    awaitClose { shakeDetector.stop() }
  }

  private val fakeShakes = callbackFlow<Unit> {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(
        context: Context,
        intent: Intent
      ) {
        offer(Unit)
      }
    }
    val intentFilter = IntentFilter(ACTION_FAKE_SHAKE)
    context.registerReceiver(receiver, intentFilter)
    awaitClose { context.unregisterReceiver(receiver) }
  }

  @OptIn(FlowPreview::class)
  override fun run(): Flow<Unit> = flowOf(realShakes, fakeShakes).flattenMerge()

  override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = otherWorker is ShakeWorker

  companion object {
    private const val ACTION_FAKE_SHAKE = "com.squareup.sample.timemachine.SHAKE"
  }
}
