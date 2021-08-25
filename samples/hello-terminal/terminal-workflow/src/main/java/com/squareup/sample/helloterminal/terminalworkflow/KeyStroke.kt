package com.squareup.sample.helloterminal.terminalworkflow

import com.googlecode.lanterna.input.InputProvider
import com.googlecode.lanterna.input.KeyType.ArrowDown
import com.googlecode.lanterna.input.KeyType.ArrowLeft
import com.googlecode.lanterna.input.KeyType.ArrowRight
import com.googlecode.lanterna.input.KeyType.ArrowUp
import com.googlecode.lanterna.input.KeyType.Backspace
import com.googlecode.lanterna.input.KeyType.Character
import com.googlecode.lanterna.input.KeyType.EOF
import com.googlecode.lanterna.input.KeyType.Enter
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType
import com.squareup.sample.helloterminal.terminalworkflow.KeyStroke.KeyType.Unknown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import com.googlecode.lanterna.input.KeyStroke as LanternaKeystroke

/**
 * Represents a keyboard key being pressed.
 *
 * @param character The [Char] representing the key, or `null` if [keyType] is not [Character].
 * @param keyType The type of key that was pressed.
 */
data class KeyStroke(
  val character: Char?,
  val keyType: KeyType
) {
  enum class KeyType {
    Backspace,
    Character,
    ArrowUp,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    Enter,
    Unknown
  }
}

@ObsoleteCoroutinesApi
@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(ExperimentalCoroutinesApi::class)
internal fun InputProvider.listenForKeyStrokesOn(
  scope: CoroutineScope
): SharedFlow<KeyStroke> {
  return flow {
    while (currentCoroutineContext().isActive) {
      val keyStroke = readInput()
      if (keyStroke.keyType === EOF) {
        // EOF indicates the terminal input was closed, and we won't receive any more input, so
        // close the channel instead of sending the raw event down.
        return@flow
      }
      emit(keyStroke.toKeyStroke())
    }
  }.shareIn(scope, WhileSubscribed())
}

private fun LanternaKeystroke.toKeyStroke(): KeyStroke = KeyStroke(
  character = character,
  keyType = when (keyType) {
    Character -> KeyType.Character
    Backspace -> KeyType.Backspace
    ArrowUp -> KeyType.ArrowUp
    ArrowDown -> KeyType.ArrowDown
    ArrowLeft -> KeyType.ArrowLeft
    ArrowRight -> KeyType.ArrowRight
    Enter -> KeyType.Enter
    else -> Unknown
  }
)
