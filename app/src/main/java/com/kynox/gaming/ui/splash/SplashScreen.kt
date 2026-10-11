package com.kynox.gaming.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.rememberMotionScale
import kotlinx.coroutines.delay

/**
 * Splash 4.0: hanya tulisan "Kynox" di atas latar tema, memudar masuk lalu keluar (total di bawah satu detik,
 * mengikuti skala animasi sistem). Tanpa logo kotak, tanpa huruf kapital, tanpa bilah kemajuan.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = rememberMotionScale()
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween((220 * scale).toInt(), easing = FastOutSlowInEasing))
        delay((420 * scale).toLong())
        exit.animateTo(0f, tween((200 * scale).toInt()))
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).alpha(exit.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(enter.value)
        ) {
            Text(
                "Kynox",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Performa dan sistem Android",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
