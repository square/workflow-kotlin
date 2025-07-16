package com.squareup.workflow1.traceviewer.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Socket
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.workflow1.traceviewer.model.Node

class SocketClient {
  private var socket: Socket
  private val scope = CoroutineScope(Dispatchers.IO)

  init{
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val workflowList = Types.newParameterizedType(List::class.java, Node::class.java)
    val adapter: JsonAdapter<List<Node>> = moshi.adapter(workflowList)

    val process = ProcessBuilder(
      "adb", "forward", "tcp:0", "localabstract:workflow-trace"
    ).start()

    val port = run {
      process.waitFor()
      process.inputStream.bufferedReader().readText()
        .trim().toInt()
    }
    println(port)
    socket = Socket("localhost", port)
    println(socket)

    println("Connected to workflow trace server on port: $port")
    var str = ""
    scope.launch {
      val reader = socket.getInputStream().bufferedReader()
      while (true) {
        // val reader = socket.getInputStream().bufferedReader()
        val input = reader.readLine()
        str = input
        println("Received: $input")
        val renderpass = adapter.fromJson(str)
        println(renderpass)
      }
    }
  }
}
