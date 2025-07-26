package com.squareup.workflow1.traceviewer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okio.IOException
import java.net.Socket

internal suspend fun streamRenderPassesFromDevice(device: String, parseOnNewRenderPass: (String) -> Unit) {
  val renderPassChannel: Channel<String> = Channel(Channel.BUFFERED)
  coroutineScope {
    launch {
      try {
        pollSocket(device = device, onNewRenderPass = renderPassChannel::send)
      } finally {
        renderPassChannel.close()
      }
    }

    // Since channel implements ChannelIterator, we can for-loop through on the receiver end.
    for (renderPass in renderPassChannel) {
      parseOnNewRenderPass(renderPass)
    }
  }
}

/**
 * Collects data from a server socket and serves them back to the caller via callback.
 *
 * Two cases that are guaranteed to fail:
 * 1) The app is not running
 * 2) A reattempt at establishing socket connection without restarting the app
 *
 * @param onNewRenderPass is called from an arbitrary thread, so it is important to ensure that the
 *        caller is thread safe
 */
private suspend fun pollSocket(device: String, onNewRenderPass: suspend (String) -> Unit) {
  withContext(Dispatchers.IO) {
    try {
      runForwardingPortThroughAdb(device) { port ->
        Socket("localhost", port).useWithCancellation { socket ->
          val reader = socket.getInputStream().bufferedReader()
          do {
            ensureActive()
            val input = reader.readLine()
            if (input != null) {
              onNewRenderPass(input)
            }
          } while (input != null)
        }
      }
    } catch (e: IOException) {
      // NoOp
    }
  }
}

/**
 * Force [pollSocket] to exit with exception if the coroutine is cancelled. See comment below.
 */
private suspend fun Socket.useWithCancellation(block: suspend (Socket) -> Unit) {
  val socket = this
  coroutineScope {
    // This coroutine is responsible for forcibly closing the socket when the coroutine is
    // cancelled. This causes any code reading from the socket to throw a CancellationException.
    // We also need to explicitly cancel this coroutine if the block returns on its own, otherwise
    // the coroutineScope will never exit.
    val socketJob = launch {
      socket.use {
        awaitCancellation()
      }
    }

    block(socket)
    socketJob.cancel()
  }
}

/**
 * Call adb to setup a port forwarding to the server socket, and calls block with the allocated
 * port number if successful.
 *
 * If block throws or returns on finish, the port forwarding is removed via adb (best effort).
 */
@Suppress("BlockingMethodInNonBlockingContext")
private suspend inline fun runForwardingPortThroughAdb(device: String, block: (port: Int) -> Unit) {
  val process = ProcessBuilder(
    "adb", "-s", device, "forward", "tcp:0", "localabstract:workflow-trace"
  ).start()

  // The adb forward command will output the port number it picks to connect.
  val forwardReturnCode = runInterruptible {
    process.waitFor()
  }
  if (forwardReturnCode != 0) {
    return
  }

  val port = process.inputStream.bufferedReader().readText()
    .trim().toInt()

  try {
    block(port)
  } finally {
    // We don't care if this fails since there's nothing we can do then anyway. It just means
    // there's an extra forward left open, but that's not a big deal.
    runCatching {
      ProcessBuilder(
        "adb", "forward", "--remove", "tcp:$port"
      ).start()
    }
  }
}
