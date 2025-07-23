package com.squareup.workflow1.traceviewer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.net.Socket
import java.net.SocketException

/**
 * This is a client that can connect to any server socket that sends render pass data while using
 * the Workflow framework.
 *
 * [start] and [close] are idempotent commands, so this socket can only be started and closed once.
 *
 * Since this app is on JVM and the server is on Android, we use ADB to forward the port onto the socket.
 */
internal class SocketClient {
  private lateinit var socket: Socket
  private var initialized = false
  val renderPassChannel: Channel<String> = Channel(Channel.BUFFERED)

  /**
   * We use any available ports on the host machine to connect to the emulator.
   *
   * `workflow-trace` is the name of the unix socket created, and since Android uses
   * `LocalServerSocket` -- which creates a unix socket on the linux abstract namespace -- we use
   * `localabstract:` to connect to it.
   */
  fun open() {
    if (initialized) {
      return
    }
    initialized = true
    val process = ProcessBuilder(
      "adb", "forward", "tcp:0", "localabstract:workflow-trace"
    ).start()

    // The adb forward command will output the port number it picks to connect.
    process.waitFor()
    val port = process.inputStream.bufferedReader().readText()
      .trim().toInt()

    socket = Socket("localhost", port)
  }

  fun close() {
    if (!initialized) {
      return
    }
    socket.close()
  }

  /**
   * Polls the socket's input stream and sends the data into [renderPassChannel].
   * The caller should handle the scope of the coroutine that this function is called in.
   *
   * To better separate the responsibility of reading from the socket, we use a channel for the caller
   * to handle parsing and amalgamating the render passes.
   */
  suspend fun pollSocket() {
    withContext(Dispatchers.IO) {
      val reader = socket.getInputStream().bufferedReader()
      reader.use {
        try {
          while (true) {
            val input = reader.readLine()
            renderPassChannel.trySend(input)
          }
        } catch (e: SocketException) {
          e.printStackTrace()
        }
      }
    }
  }
}
