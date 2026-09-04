package com.squareup.sample.compose.presenterdemo.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastMap
import com.squareup.sample.compose.presenterdemo.structurednav.BackstackPresenter
import com.squareup.workflow1.presenter.NavigationPresenter
import com.squareup.workflow1.presenter.ViewModelRef
import com.squareup.workflow1.presenter.defaultViewModel
import com.squareup.workflow1.presenter.read
import com.squareup.workflow1.presenter.ui.ViewModel
import com.squareup.workflow1.ui.compose.ComposeScreen

interface Applet {
  val name: String

  @Composable
  fun Content()
}

class AppletPresenter(private val applets: List<Applet>) {

  @Composable
  operator fun invoke() {
    var selectedAppletIndex by remember { mutableIntStateOf(0) }

    NavigationPresenter(
      presenterPolicy = { children ->
        val bodyPresenter = children.single()
        defaultViewModel = AppletViewModel(
          appletNames = applets.fastMap { it.name },
          selectedAppletIndex = selectedAppletIndex,
          selectedAppletBody = bodyPresenter.read()!!,
          onAppletSelected = { selectedAppletIndex = it }
        )
        TODO("forward any other slots from the child")
      }
    ) {
      key(selectedAppletIndex) {
        BackstackPresenter {
          applets[selectedAppletIndex].Content()
        }
      }
    }
  }
}

data class AppletViewModel(
  val appletNames: List<String>,
  val selectedAppletIndex: Int,
  val selectedAppletBody: ViewModelRef<*>,
  val onAppletSelected: (Int) -> Unit,
) : ComposeScreen {

  @Composable override fun Content() {
    Row {
      LazyColumn(
        Modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        itemsIndexed(appletNames) { i, name ->
          Text(
            name,
            modifier = Modifier.selectable(
              selected = selectedAppletIndex == i,
              onClick = { onAppletSelected(i) },
            )
          )
        }
      }

      ViewModel(
        selectedAppletBody,
        modifier = Modifier
          .weight(3f)
          .fillMaxHeight()
      )
    }
  }
}
