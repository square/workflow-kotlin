package com.squareup.sample.timemachine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TimeSeriesTest {

  private val series = TimeSeries<String>()

  @Test fun `duration is initially zero`() {
    assertThat(series.duration).isEqualTo(Duration.ZERO)
  }

  @Test fun `duration increases after append`() {
    series.append("foo", Duration.milliseconds(42))
        .let {
          assertThat(it.duration).isEqualTo(Duration.milliseconds(42))
        }
  }

  @Test fun `duration increases after multiple appends`() {
    series.append("foo", Duration.milliseconds(2))
        .append("bar", Duration.milliseconds(42))
        .let {
          assertThat(it.duration).isEqualTo(Duration.milliseconds(42))
        }
  }

  @Test fun `throws when appending value from the past`() {
    val series1 = series.append("foo", Duration.milliseconds(42))

    assertFailsWith<IllegalArgumentException> {
      series1.append("bar", Duration.milliseconds(41))
    }
  }

  @Test fun `allows appending value with last timestamp`() {
    series.append("foo", Duration.milliseconds(42))
        .append("bar", Duration.milliseconds(42))
        .let {
          assertThat(it.duration).isEqualTo(Duration.milliseconds(42))
        }
  }

  @Test fun `findValueNearest with empty list`() {
    assertFailsWith<NoSuchElementException> {
      series.findValueNearest(Duration.milliseconds(42))
    }
  }

  @Test fun `findValueNearest with single value`() {
    series.append("foo", Duration.milliseconds(42))
        .let {
          assertThat(it.findValueNearest(Duration.milliseconds(0))).isEqualTo("foo")
          assertThat(it.findValueNearest(Duration.milliseconds(42))).isEqualTo("foo")
          assertThat(it.findValueNearest(Duration.days(100))).isEqualTo("foo")
        }
  }

  @Test fun `findValueNearest with multiple values`() {
    series.append("foo", Duration.milliseconds(41))
        .append("bar", Duration.milliseconds(43))
        .let {
          assertThat(it.findValueNearest(Duration.milliseconds(0))).isEqualTo("foo")
          assertThat(it.findValueNearest(Duration.milliseconds(41))).isEqualTo("foo")
          assertThat(it.findValueNearest(Duration.milliseconds(42))).isEqualTo("foo")
          assertThat(it.findValueNearest(Duration.milliseconds(43))).isEqualTo("bar")
          assertThat(it.findValueNearest(Duration.days(100))).isEqualTo("bar")
        }
  }
}
