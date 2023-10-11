package com.squareup.sample.petfinder.domain

data class Breeds (
  val primary: String,
  val secondary: Any? = null,
  val mixed: Boolean,
  val unknown: Boolean
)
