@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.ianocent.musicplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ianocent.musicplayer.R

// Outfit — rounded geometric sans (Google Fonts, OFL). Dipakai sebagai default
// family di SEMUA typography biar visual nyatu (lucutin Roboto system).
// Catatan: ctor Typography di material3 1.4.0 itu `internal` dan sudah meniadakan
// param `defaultFontFamily`, jadi family di-swap per-role lewat copy dari
// instance default (base). `@Suppress(INVISIBLE_*)` hanya buat bangun base-nya.
val OutfitFontFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

/** Instance bawaan material3 (ukuran/weight/letterSpacing semua role). */
private val base = Typography()

private fun withOutfit(style: TextStyle): TextStyle = style.copy(fontFamily = OutfitFontFamily)

val Typography = Typography(
    displayLarge = withOutfit(base.displayLarge),
    displayMedium = withOutfit(base.displayMedium),
    displaySmall = withOutfit(base.displaySmall),
    headlineLarge = withOutfit(base.headlineLarge),
    headlineMedium = withOutfit(base.headlineMedium),
    headlineSmall = withOutfit(base.headlineSmall),
    titleLarge = withOutfit(base.titleLarge),
    titleMedium = withOutfit(base.titleMedium),
    titleSmall = withOutfit(base.titleSmall),
    bodyLarge = withOutfit(base.bodyLarge),
    bodyMedium = withOutfit(base.bodyMedium),
    bodySmall = withOutfit(base.bodySmall),
    labelLarge = withOutfit(base.labelLarge),
    labelMedium = withOutfit(base.labelMedium),
    labelSmall = withOutfit(base.labelSmall),
    displayLargeEmphasized = withOutfit(base.displayLargeEmphasized),
    displayMediumEmphasized = withOutfit(base.displayMediumEmphasized),
    displaySmallEmphasized = withOutfit(base.displaySmallEmphasized),
    headlineLargeEmphasized = withOutfit(base.headlineLargeEmphasized),
    headlineMediumEmphasized = withOutfit(base.headlineMediumEmphasized),
    headlineSmallEmphasized = withOutfit(base.headlineSmallEmphasized),
    titleLargeEmphasized = withOutfit(base.titleLargeEmphasized),
    titleMediumEmphasized = withOutfit(base.titleMediumEmphasized),
    titleSmallEmphasized = withOutfit(base.titleSmallEmphasized),
    bodyLargeEmphasized = withOutfit(base.bodyLargeEmphasized),
    bodyMediumEmphasized = withOutfit(base.bodyMediumEmphasized),
    bodySmallEmphasized = withOutfit(base.bodySmallEmphasized),
    labelLargeEmphasized = withOutfit(base.labelLargeEmphasized),
    labelMediumEmphasized = withOutfit(base.labelMediumEmphasized),
    labelSmallEmphasized = withOutfit(base.labelSmallEmphasized),
)