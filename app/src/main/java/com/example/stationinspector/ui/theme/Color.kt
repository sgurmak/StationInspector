package com.example.stationinspector.ui.theme

import androidx.compose.ui.graphics.Color

// Core Brand Palette (Originals preserved — used by Material default Theme)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ── Global Design Tokens (Semantic Rebranding) ───────────────────────────────
val ContentLight          = Color(0xFFFBF7FF)   // High-contrast primary light text / details
val ContentLightSecondary = Color(0xFFF5EDFF)   // Muted lavender text / details
val CardContent           = Color(0xFF261937)   // Deep dark violet card contents / dark buttons
val ContentDark           = Color(0xFF13111B)   // Core dark surface color (also used as NavBar bg)
val AppGradientTop        = Color(0xFF392153)   // Deep rich purple gradient start
val AppGradientBottom     = Color(0xFF13111B)   // Extremely dark violet gradient end

// Premium Accent Color Tokens
val AccentPink         = Color(0xFFCA065E)   // Core magenta/pink branding accent
val AccentGreen        = Color(0xFF4ADE80)   // Success / confirmed active state green (camera)
val AccentGreenAlt     = Color(0xFF16A34A)   // Success dark green (text / indicators)
val AccentGreenM3      = Color(0xFF47DC7A)   // Alternate success green (gallery)
val AccentGreenConfirm = Color(0xFF00C853)   // Confirm/save action green (map edit overlay)
val AccentRed          = Color(0xFFEF4444)   // Destructive alert / error actions red

// Surface Tints / Secondary Surfaces
val DestructiveBg = Color(0xFFFFD3EB)        // Pale pink — destructive button surface (Settings)

// Splash Screen — distinct gradient outside the global app gradient
val SplashGradientTop    = Color(0xFF180D2C)
val SplashGradientBottom = Color(0xFF0B0416)
val BrandViolet          = Color(0xFF6E00BD) // Splash text gradient start
val BrandLavender        = Color(0xFFF7E4FF) // Splash text gradient end
