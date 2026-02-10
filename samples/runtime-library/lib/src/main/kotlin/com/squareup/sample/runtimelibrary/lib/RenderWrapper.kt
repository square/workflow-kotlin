package com.squareup.sample.runtimelibrary.lib

import com.squareup.workflow1.internal.withKey

fun checkJvmLinkage() {
  // This method is defined by both Android and JVM targets.
  RuntimeException().withKey("foo")
}
