/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString

/**
 * Defines how to serialize and deserialize a [WorkflowState].
 */
interface StateSaver<T> {
  fun toByteString(value: T): ByteString
  fun fromByteString(bytes: ByteString): T
}

abstract class BufferStateSaver<T> : StateSaver<T> {
  final override fun toByteString(value: T): ByteString = Buffer().run {
    write(value)
    readByteString()
  }

  final override fun fromByteString(bytes: ByteString): T = Buffer().run {
    write(bytes)
    read()
  }

  protected abstract fun BufferedSink.write(value: T)
  protected abstract fun BufferedSource.read(): T
}

@PublishedApi
internal object IntStateSaver : BufferStateSaver<Int>() {
  override fun BufferedSink.write(value: Int) {
    writeInt(value)
  }

  override fun BufferedSource.read(): Int = readInt()
}

@PublishedApi
internal object LongStateSaver : BufferStateSaver<Long>() {
  override fun BufferedSink.write(value: Long) {
    writeLong(value)
  }

  override fun BufferedSource.read(): Long = readLong()
}

@PublishedApi
internal object StringStateSaver : BufferStateSaver<String>() {
  override fun BufferedSink.write(value: String) {
    writeUtf8(value)
  }

  override fun BufferedSource.read(): String = readUtf8()
}

@PublishedApi
internal class ListSaver<T>(private val itemSaver: StateSaver<T>) : StateSaver<List<T>> {
  override fun toByteString(value: List<T>): ByteString = Buffer().run {
    writeInt(value.size)
    value.forEach {
      writeByteStringWithLength(itemSaver.toByteString(it))
    }
    readByteString()
  }

  override fun fromByteString(bytes: ByteString): List<T> = Buffer().run {
    write(bytes)
    val listSize = readInt()
    List(listSize) {
      itemSaver.fromByteString(readByteStringWithLength())
    }
  }
}
