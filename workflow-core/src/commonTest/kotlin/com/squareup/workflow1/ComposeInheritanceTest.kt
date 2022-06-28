package com.squareup.workflow1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.BroadcastFrameClock
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import org.junit.Test

class ComposeInheritanceTest {
  abstract class Abstract<I1, I2, T> {
    @Composable
    public abstract fun AComposable(
      input1: I1,
      input2: I2,
      hoistState: @Composable (s: T) -> Unit
    ): Unit

    abstract fun someOtherFunction(): T
  }


  public class Concrete(
    private val payload: String
  ) : Abstract<Unit, String, String>() {
    @Composable
    public override fun AComposable(
      input1: Unit,
      input2: String,
      hoistState: @Composable (s: String) -> Unit
    ) {
      println("Can you hear me now? $payload")
      hoistState(payload + input2)
    }

    override fun someOtherFunction(): String {
      return payload
    }
  }

  @Composable
  public fun <I1, I2, T> Emitter(
    i1: I1,
    i2: I2,
    theObjectHoldingComposables: Abstract<I1, I2, T>
  ): T? {
    val payload: MutableState<T?> = remember { mutableStateOf(null) }
    theObjectHoldingComposables.AComposable(i1, i2) @Composable {
      payload.value = it
    }
    return payload.value
  }

  @Test fun testInheritance() {
    val objectUnderTest = Concrete("a test")
    val broadcastFrameClock = BroadcastFrameClock {}
    val testScope = CoroutineScope(broadcastFrameClock)

    val testFlow = testScope.launchMolecule {
      Emitter(Unit, " again", objectUnderTest)
    }

    assert(testFlow.value.contentEquals("a test again"))
  }
}

