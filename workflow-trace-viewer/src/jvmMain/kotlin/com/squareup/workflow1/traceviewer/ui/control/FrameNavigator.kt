package com.squareup.workflow1.traceviewer.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A frame navigator that shows the current frame number with dropdown selection
 * and left/right navigation arrows.
 */
@Composable
internal fun FrameNavigator(
  totalFrames: Int,
  currentIndex: Int,
  onIndexChange: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  var dropdownExpanded by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier,
    color = Color.White,
    elevation = 2.dp,
    shape = RoundedCornerShape(8.dp)
  ) {
    Row(
      modifier = Modifier
        .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      // Previous frame button
      IconButton(
        onClick = {
          if (currentIndex > 0) {
            onIndexChange(currentIndex - 1)
          }
        },
        enabled = currentIndex > 0
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          contentDescription = "Previous frame",
          tint = if (currentIndex > 0) Color.Black else Color.LightGray
        )
      }

      Box {
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { dropdownExpanded = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = "Frame ${currentIndex + 1}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
          )
          Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = "Select frame",
            tint = Color.Black
          )
        }

        DropdownMenu(
          expanded = dropdownExpanded,
          onDismissRequest = { dropdownExpanded = false },
          modifier = Modifier
            .background(Color.White)
            .width(150.dp)
            .heightIn(max = 350.dp)
        ) {
          (0 until totalFrames).forEach { index ->
            DropdownMenuItem(
              onClick = {
                onIndexChange(index)
                dropdownExpanded = false
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "Frame ${index + 1}",
                color = if (index == currentIndex) Color.Black else Color.LightGray,
                fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }
      }

      IconButton(
        onClick = {
          if (currentIndex < totalFrames - 1) {
            onIndexChange(currentIndex + 1)
          }
        },
        enabled = currentIndex < totalFrames - 1
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = "Next frame",
          tint = if (currentIndex < totalFrames - 1) Color.Black else Color.LightGray
        )
      }
    }
  }
}
