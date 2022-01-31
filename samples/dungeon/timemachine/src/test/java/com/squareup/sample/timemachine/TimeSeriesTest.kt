package com.squareup.sample.timemachine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TimeSeriesTest {

  private val series = TimeSeries<String>()

  @Test fun `duration is initially zero`() {
    assertThat(series.duration).isEqualTo(Duration.ZERO)
  }

  @Test fun `duration increases after append`() {
    series.append("foo", 42.milliseconds)
      .let {
        assertThat(it.duration).isEqualTo(42.milliseconds)
      }
  }

  @Test fun `duration increases after multiple appends`() {
    series.append("foo", 2.milliseconds)
      .append("bar", 42.milliseconds)
      .let {
        assertThat(it.duration).isEqualTo(42.milliseconds)
      }
  }

  @Test fun `throws when appending value from the past`() {
    val series1 = series.append("foo", 42.milliseconds)

    assertFailsWith<IllegalArgumentException> {
      series1.append("bar", 41.milliseconds)
    }
  }

  @Test fun `allows appending value with last timestamp`() {
    series.append("foo", 42.milliseconds)
      .append("bar", 42.milliseconds)
      .let {
        assertThat(it.duration).isEqualTo(42.milliseconds)
      }
  }

  @Test fun `findValueNearest with empty list`() {
    assertFailsWith<NoSuchElementException> {
      series.findValueNearest(42.milliseconds)
    }
  }

  @Test fun `findValueNearest with single value`() {
    series.append("foo", 42.milliseconds)
      .let {
        assertThat(it.findValueNearest(0.milliseconds)).isEqualTo("foo")
        assertThat(it.findValueNearest(42.milliseconds)).isEqualTo("foo")
        assertThat(it.findValueNearest(100.days)).isEqualTo("foo")
      }
  }

  @Test fun `findValueNearest with multiple values`() {
    series.append("foo", 41.milliseconds)
      .append("bar", 43.milliseconds)
      .let {
        assertThat(it.findValueNearest(0.milliseconds)).isEqualTo("foo")
        assertThat(it.findValueNearest(41.milliseconds)).isEqualTo("foo")
        assertThat(it.findValueNearest(42.milliseconds)).isEqualTo("foo")
        assertThat(it.findValueNearest(43.milliseconds)).isEqualTo("bar")
        assertThat(it.findValueNearest(100.days)).isEqualTo("bar")
      }
  }
}
