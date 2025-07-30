package com.squareup.workflow1.traceviewer.ui.control

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.squareup.workflow1.traceviewer.model.Node

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchBox(
  nodes: List<Node>,
  onSearch: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var searchText by remember { mutableStateOf("") }
  var expanded by remember { mutableStateOf(false) }

  DockedSearchBar(
    modifier = modifier,
    inputField = {
      SearchBarDefaults.InputField(
        query = searchText,
        onQueryChange = { searchText = it },
        onSearch = {
          expanded = false
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        placeholder = { Text("search for a node...") },
        trailingIcon = {
          IconButton(
            onClick = {
              expanded = false
            }
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear search"
            )
          }
        }
      )
    },
    colors = SearchBarColors(Color.White, Color.Black),
    expanded = expanded,
    onExpandedChange = { expanded = it },
  ) {
    val relevantNodes = nodes.filter { it.name.contains(searchText, ignoreCase = true) }
    Column {
      relevantNodes.take(5).forEach { node ->
        ListItem(
          headlineContent = { Text(node.name) },
          modifier = Modifier
            .clickable {
              onSearch(node.name)
              expanded = false
            },
          colors = ListItemDefaults.colors(
            containerColor = Color.White,
            headlineColor = Color.Black
          )
        )
      }
    }
  }
}
