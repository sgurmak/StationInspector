package com.example.stationinspector.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stationinspector.R
import com.example.stationinspector.ui.theme.BrandLavender
import com.example.stationinspector.ui.theme.BrandViolet
import com.example.stationinspector.ui.theme.SplashGradientBottom
import com.example.stationinspector.ui.theme.SplashGradientTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  FleetWay Branded Premium SplashScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    // ── Animation states ─────────────────────────────────────────────────────
    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0.0f) }

    LaunchedEffect(Unit) {
        // Run scale and alpha transitions concurrently
        launch {
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = 1100,
                    easing = FastOutSlowInEasing
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = 900,
                    easing = LinearOutSlowInEasing
                )
            )
        }
        // Branded display duration (1.8 seconds)
        delay(1800)
        onSplashComplete()
    }

    // ── Root viewport with premium dark gradient background ───────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SplashGradientTop, SplashGradientBottom)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .alpha(alpha.value)
                .scale(scale.value)
        ) {
            // ── FleetWay Logo ────────────────────────────────────────────────
            Image(
                painter = painterResource(id = R.drawable.ic_fleetway_logo),
                contentDescription = "FleetWay Logo",
                modifier = Modifier.size(220.dp)
            )

            // Spacing set to exactly 56dp per user cosmetic adjustment request
            Spacer(modifier = Modifier.height(0.dp))

            // ── FleetWay Branded Gradient Text ──────────────────────────────
            // Solid, vibrant diagonal linear gradient matching the screenshot
            val textGradient = Brush.linearGradient(
                colors = listOf(BrandViolet, BrandLavender),
                start = Offset(0f, 150f),
                end = Offset(300f, 0f)
            )

            Text(
                text = "FleetWay",
                style = TextStyle(
                    brush = textGradient,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}
