package dev.naijun.dununlocker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak

// Phrase-based word breaking (API 33+) wraps Korean between words instead of between
// syllables, so lines no longer end with a dangling syllable such as "다." or "요".
private val BodyLineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
private val HeadingLineBreak = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase)

private fun TextStyle.body() = copy(lineBreak = BodyLineBreak)
private fun TextStyle.heading() = copy(lineBreak = HeadingLineBreak)

val Typography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.heading(),
        displayMedium = displayMedium.heading(),
        displaySmall = displaySmall.heading(),
        headlineLarge = headlineLarge.heading(),
        headlineMedium = headlineMedium.heading(),
        headlineSmall = headlineSmall.heading(),
        titleLarge = titleLarge.heading(),
        titleMedium = titleMedium.heading(),
        titleSmall = titleSmall.heading(),
        bodyLarge = bodyLarge.body(),
        bodyMedium = bodyMedium.body(),
        bodySmall = bodySmall.body(),
        labelLarge = labelLarge.heading(),
        labelMedium = labelMedium.heading(),
        labelSmall = labelSmall.heading(),
        displayLargeEmphasized = displayLargeEmphasized.heading(),
        displayMediumEmphasized = displayMediumEmphasized.heading(),
        displaySmallEmphasized = displaySmallEmphasized.heading(),
        headlineLargeEmphasized = headlineLargeEmphasized.heading(),
        headlineMediumEmphasized = headlineMediumEmphasized.heading(),
        headlineSmallEmphasized = headlineSmallEmphasized.heading(),
        titleLargeEmphasized = titleLargeEmphasized.heading(),
        titleMediumEmphasized = titleMediumEmphasized.heading(),
        titleSmallEmphasized = titleSmallEmphasized.heading(),
        bodyLargeEmphasized = bodyLargeEmphasized.body(),
        bodyMediumEmphasized = bodyMediumEmphasized.body(),
        bodySmallEmphasized = bodySmallEmphasized.body(),
        labelLargeEmphasized = labelLargeEmphasized.heading(),
        labelMediumEmphasized = labelMediumEmphasized.heading(),
        labelSmallEmphasized = labelSmallEmphasized.heading(),
    )
}
