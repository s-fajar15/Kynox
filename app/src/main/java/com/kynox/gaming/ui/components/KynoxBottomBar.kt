package com.kynox.gaming.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kynox.gaming.ui.theme.KynoxMotion
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.theme.rememberMotionScale

/**
 * Bottom bar 4 tab: latar surface, tanpa garis. Item aktif ditandai indikator pil 60x32 (primaryContainer)
 * dan label tebal; label selalu huruf biasa (tanpa all-caps). Setiap item berukuran >= 48 dp.
 */
@Composable
fun KynoxBottomBar(items: List<BarItem>, modifier: Modifier = Modifier) {
    val scale = rememberMotionScale()
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(76.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val indicator by animateColorAsState(
                    targetValue = if (item.selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
                    animationSpec = tween((KynoxMotion.FAST * scale).toInt()),
                    label = "navIndicator"
                )
                val iconTint = if (item.selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                val labelColor = if (item.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .height(76.dp)
                        .selectable(
                            selected = item.selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = item.onClick
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Box(
                        Modifier.width(60.dp).height(32.dp).clip(KynoxShapes.pill).background(indicator),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

data class BarItem(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)
