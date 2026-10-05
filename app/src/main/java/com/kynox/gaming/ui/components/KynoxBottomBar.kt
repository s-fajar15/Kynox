package com.kynox.gaming.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kynox.gaming.ui.theme.KynoxBrushes
import com.kynox.gaming.ui.theme.KynoxNavBarDark
import com.kynox.gaming.ui.theme.KynoxOnSurfaceFaintDark
import com.kynox.gaming.ui.theme.KynoxOutlineDark
import com.kynox.gaming.ui.theme.KynoxOutlineStrongDark

/**
 * Navigasi melayang 2.2: kapsul gelap bertepi gradien. Hanya tab aktif yang menampilkan label
 * (kapsul gradien yang melebar); tab lain berupa ikon saja, jadi bar tetap ramping.
 */
@Composable
fun KynoxBottomBar(items: List<BarItem>, modifier: Modifier = Modifier) {
    val barShape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(KynoxNavBarDark.copy(alpha = 0.97f))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(KynoxOutlineStrongDark.copy(alpha = 0.6f), KynoxOutlineDark.copy(alpha = 0.25f))),
                    barShape
                )
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val grow by animateFloatAsState(if (item.selected) 2.3f else 1f, tween(240), label = "navGrow")
                val tint = if (item.selected) Color.White else KynoxOnSurfaceFaintDark
                Row(
                    Modifier
                        .weight(grow)
                        .height(48.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .then(if (item.selected) Modifier.background(KynoxBrushes.accent) else Modifier)
                        .selectable(
                            selected = item.selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = item.onClick
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
                    if (item.selected) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.label,
                            color = tint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

data class BarItem(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)
