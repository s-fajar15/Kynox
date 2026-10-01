package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.KynoxOnSurfaceFaintDark
import com.kynox.gaming.ui.theme.KynoxNavBarDark

/**
 * Compact floating navigation matching the Kynox mockup.
 * The system navigation inset is kept, but the actual pill is deliberately
 * short so it does not consume a large chunk of the content area.
 */
@Composable
fun KynoxBottomBar(items: List<BarItem>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(KynoxNavBarDark)
                .padding(horizontal = 5.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.selected
                val tint = if (selected) KynoxAccentDark else KynoxOnSurfaceFaintDark
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(21.dp))
                        .background(if (selected) KynoxAccentDark.copy(alpha = 0.13f) else Color.Transparent)
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = item.onClick
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(18.dp))
                    Text(
                        item.label,
                        color = tint,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

data class BarItem(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)
