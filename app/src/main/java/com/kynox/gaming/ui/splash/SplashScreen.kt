package com.kynox.gaming.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kynox.gaming.ui.components.KMark
import com.kynox.gaming.ui.theme.KynoxAccentDark
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        delay(500)
        exit.animateTo(0f, tween(260))
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF123D3B), MaterialTheme.colorScheme.background), radius = 900f)
        ).alpha(exit.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(enter.value).scale(0.86f + 0.14f * enter.value)
        ) {
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFF0B171B)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(KynoxAccentDark.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    KMark(markSize = 48.dp, color = KynoxAccentDark)
                }
            }
            Text("Kynox", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 22.dp))
            Text("Monitor. Optimalkan. Kendalikan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.2.sp, modifier = Modifier.padding(top = 5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 28.dp)) {
                repeat(3) { Box(Modifier.size(if (it == 1) 18.dp else 5.dp, 5.dp).clip(CircleShape).background(if (it == 1) KynoxAccentDark else KynoxAccentDark.copy(alpha = 0.35f))) }
            }
        }
    }
}
