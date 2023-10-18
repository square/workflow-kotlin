package com.squareup.sample.petfinder.domain

data class Address (
  val address1: Any? = null,
  val address2: Any? = null,
  val city: String,
  val state: String,
  val postcode: String,
  val country: String
)
