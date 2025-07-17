package com.squareup.workflow1.traceviewer.util

import kotlinx.coroutines.channels.Channel
import java.net.Socket

/**
 * This is a client that connects to the `ActionLogger` Unix Domain Socket and listens for any new
 * render passes. Since this app is on JVM and the server is on Android, we use ADB to forward the
 * port onto the socket.
 */
internal class SocketClient {
  private lateinit var socket: Socket
  private var initialized = false
  val renderPassChannel: Channel<String> = Channel(Channel.UNLIMITED)

  /**
   * We use any available ports on the host machine to connect to the emulator.
   *
   * `workflow-trace` is the name of the unix socket created, and since Android uses
   * `LocalServerSocket` -- which creates a unix socket on the linux abstract namespace -- we use
   * `localabstract:` to connect to it.
   */
  fun start() {
    initialized = true
    val process = ProcessBuilder(
      "adb", "forward", "tcp:0", "localabstract:workflow-trace"
    ).start()

    // The adb forward command will output the port number it picks to connect.
    val port = run {
      process.waitFor()
      process.inputStream.bufferedReader().readText()
        .trim().toInt()
    }
    // println(port)
    socket = Socket("localhost", port)
    // println("Connected to workflow trace server on port: $port")
  }

  fun close() {
    if (!initialized) {
      return
    }
    socket.close()
    initialized = false
  }

  /**
   * This will always be called within an asynchronous call, so we do not need to block/launch a
   * new coroutine here.
   *
   * To better separate the responsibility of reading from the socket, we use a channel for the caller
   * to handle parsing and amalgamating the render passes.
   */
  fun beginListen() {
    val reader = socket.getInputStream().bufferedReader()
    while (true) {
      val input = reader.readLine()
      renderPassChannel.trySend(input)
    }
  }
}
