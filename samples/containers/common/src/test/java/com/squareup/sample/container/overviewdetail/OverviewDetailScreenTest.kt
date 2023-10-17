package com.squareup.sample.container.overviewdetail

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackScreen
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class OverviewDetailScreenTest {
  data class FooScreen<T>(val value: T) : Screen
  data class BarScreen<T>(val value: T) : Screen

  @Test fun `minimal structure`() {
    val screen = OverviewDetailScreen(BackStackScreen(FooScreen(1)))
    assertThat(screen.overviewRendering).isEqualTo(BackStackScreen(FooScreen(1)))
    assertThat(screen.detailRendering).isNull()
    assertThat(screen.selectDefault).isNull()
  }

  @Test fun `minimal equality`() {
    val screen = OverviewDetailScreen(BackStackScreen(FooScreen(1)))
    assertThat(screen).isEqualTo(OverviewDetailScreen(BackStackScreen(FooScreen(1))))
    assertThat(screen).isNotEqualTo(OverviewDetailScreen(BackStackScreen(FooScreen(2))))
  }

  @Test fun `minimal hash`() {
    val screen = OverviewDetailScreen(BackStackScreen(FooScreen(1)))
    assertThat(screen.hashCode()).isEqualTo(
      OverviewDetailScreen(BackStackScreen(FooScreen(1))).hashCode()
    )
    assertThat(screen.hashCode())
      .isNotEqualTo(OverviewDetailScreen(BackStackScreen(FooScreen(2))).hashCode())
  }

  @Test fun `combine minimal`() {
    val left = OverviewDetailScreen(BackStackScreen(FooScreen(1), FooScreen(2)))
    val right = OverviewDetailScreen(BackStackScreen(FooScreen(11), FooScreen(12)))

    assertThat(left + right)
      .isEqualTo(
        OverviewDetailScreen(
          BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(11), FooScreen(12))
        )
      )
  }

  @Test fun `full structure`() {
    val screen = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(FooScreen(3), FooScreen(4))
    )

    assertThat(screen.overviewRendering).isEqualTo(BackStackScreen(FooScreen(1), FooScreen(2)))
    assertThat(screen.detailRendering).isEqualTo(BackStackScreen(FooScreen(3), FooScreen(4)))
    assertThat(screen.selectDefault).isNull()
  }

  @Test fun `full equality`() {
    val screen1 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(FooScreen(3), FooScreen(4))
    )

    val screen2 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(FooScreen(3), FooScreen(4))
    )

    val screen3 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(FooScreen(3), FooScreen(4), FooScreen(5))
    )

    assertThat(screen1).isEqualTo(screen2)
    assertThat(screen1).isNotEqualTo(screen3)
  }

  @Test fun `full hash`() {
    val screen1 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(FooScreen(3), FooScreen(4))
    )

    val screen2 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(FooScreen(3), FooScreen(4))
    )

    val screen3 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(FooScreen(3), FooScreen(4), FooScreen(5))
    )

    assertThat(screen1.hashCode()).isEqualTo(screen2.hashCode())
    assertThat(screen1.hashCode()).isNotEqualTo(screen3.hashCode())
  }

  @Test fun `combine full`() {
    val left = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(FooScreen(3), FooScreen(4))
    )
    val right = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(11), FooScreen(12)),
      detailRendering = BackStackScreen(FooScreen(13), FooScreen(14))
    )

    assertThat(left + right).isEqualTo(
      OverviewDetailScreen(
        overviewRendering = BackStackScreen(
          FooScreen(1),
          FooScreen(2),
          FooScreen(11),
          FooScreen(12)
        ),
        detailRendering = BackStackScreen(FooScreen(3), FooScreen(4), FooScreen(13), FooScreen(14))
      )
    )
  }

  @Test fun `selectDefault structure`() {
    val selectDefault = {}
    val screen = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      selectDefault = selectDefault
    )

    assertThat(screen.overviewRendering).isEqualTo(BackStackScreen(FooScreen(1), FooScreen(2)))
    assertThat(screen.detailRendering).isNull()
    assertThat(screen.selectDefault).isEqualTo(selectDefault)
  }

  @Test fun `selectDefault equality`() {
    val selectDefault = {}

    val screen1 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      selectDefault = selectDefault
    )

    val screen2 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      selectDefault = selectDefault
    )

    val screen3 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      selectDefault = {}
    )

    assertThat(screen1).isEqualTo(screen2)
    assertThat(screen1).isNotEqualTo(screen3)
  }

  @Test fun `selectDefault hash`() {
    val selectDefault = {}

    val screen1 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      selectDefault = selectDefault
    )

    val screen2 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      selectDefault = selectDefault
    )

    val screen3 = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      selectDefault = {}
    )

    assertThat(screen1.hashCode()).isEqualTo(screen2.hashCode())
    assertThat(screen1.hashCode()).isNotEqualTo(screen3.hashCode())
  }

  @Test fun `combine selectDefault`() {
    val selectDefaultLeft = {}
    val left = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      selectDefault = selectDefaultLeft
    )
    val selectDefaultRight = {}
    val right = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(11), FooScreen(12)),
      selectDefault = selectDefaultRight
    )

    assertThat(left + right).isEqualTo(
      OverviewDetailScreen(
        overviewRendering = BackStackScreen(
          FooScreen(1),
          FooScreen(2),
          FooScreen(11),
          FooScreen(12)
        ),
        selectDefault = selectDefaultRight
      )
    )
  }

  @Test fun `can combine heterogenous content`() {
    val left = OverviewDetailScreen(
      overviewRendering = BackStackScreen(FooScreen(1), FooScreen(2)),
      detailRendering = BackStackScreen(BarScreen(3), BarScreen(4))
    )
    val right = OverviewDetailScreen(
      overviewRendering = BackStackScreen(BarScreen(11), BarScreen(12)),
      detailRendering = BackStackScreen(FooScreen(13), FooScreen(14))
    )

    assertThat(left + right).isEqualTo(
      OverviewDetailScreen(
        overviewRendering = BackStackScreen(
          FooScreen(1),
          FooScreen(2),
          BarScreen(11),
          BarScreen(12)
        ),
        detailRendering = BackStackScreen(BarScreen(3), BarScreen(4), FooScreen(13), FooScreen(14))
      )
    )
  }
}
