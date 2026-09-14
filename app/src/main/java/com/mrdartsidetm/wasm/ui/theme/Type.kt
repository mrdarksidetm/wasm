package com.mrdartsidetm.wasm.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mrdartsidetm.wasm.R

/**
 * San Francisco Pro Display Font Family.
 * Configured with all provided weights and italics.
 */
val SfProDisplayFontFamily = FontFamily(
    Font(R.font.sf_pro_display_thin, FontWeight.Thin),
    Font(R.font.sf_pro_display_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.sf_pro_display_ultralight, FontWeight.ExtraLight),
    Font(R.font.sf_pro_display_ultralight_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.sf_pro_display_light, FontWeight.Light),
    Font(R.font.sf_pro_display_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_regular_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.sf_pro_display_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.sf_pro_display_heavy, FontWeight.ExtraBold),
    Font(R.font.sf_pro_display_heavy_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.sf_pro_display_black, FontWeight.Black),
    Font(R.font.sf_pro_display_black_italic, FontWeight.Black, FontStyle.Italic)
)

/**
 * Creates a composite FontFamily where San Francisco Pro is the primary font
 * and the bundled iOS emoji font (iOS 26.4 CBDT/CBLC) is configured as the
 * custom fallback font on Android 10+ (API 29+) via Typeface.CustomFallbackBuilder.
 */
fun getAppFontFamily(context: Context): FontFamily {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val primaryFont = android.graphics.fonts.Font.Builder(context.resources, R.font.sf_pro_display_regular).build()
            val primaryFamily = android.graphics.fonts.FontFamily.Builder(primaryFont).build()

            // Custom fallback with bundled Apple color emoji font from assets
            val emojiFont = android.graphics.fonts.Font.Builder(context.assets, "fonts/ios_emoji.ttf").build()
            val emojiFamily = android.graphics.fonts.FontFamily.Builder(emojiFont).build()

            val compositeTypeface = Typeface.CustomFallbackBuilder(primaryFamily)
                .addCustomFallback(emojiFamily)
                .build()

            return FontFamily(compositeTypeface)
        } catch (e: Throwable) {
            // Fallback gracefully to Compose font family
        }
    }
    return SfProDisplayFontFamily
}

/**
 * Builds Material 3 Typography utilizing the San Francisco Pro font family.
 */
fun buildWasmTypography(fontFamily: FontFamily): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp
        ),
        displayMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp
        ),
        displaySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}
