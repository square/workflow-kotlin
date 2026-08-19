package com.squareup.workflow1.presenter

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class ThreadLocal<T>(initialValue: T): ReadWriteProperty<Any, T> {

  override fun getValue(
    thisRef: Any,
    property: KProperty<*>
  ): T {
    TODO("Not yet implemented")
  }

  override fun setValue(
    thisRef: Any,
    property: KProperty<*>,
    value: T
  ) {
    TODO("Not yet implemented")
  }
}
