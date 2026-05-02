package eu.darken.octi.screenshots

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

// @formatter:off

/**
 * Multi-preview annotation for supported Play Store locales in light mode.
 * [name] is the fastlane metadata directory; [locale] is the Android resource qualifier.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "en-US", locale = "en", device = DS)
@Preview(name = "af", locale = "af-rZA", device = DS)
@Preview(name = "ar", locale = "ar", device = DS)
@Preview(name = "ca", locale = "ca", device = DS)
@Preview(name = "cs-CZ", locale = "cs", device = DS)
@Preview(name = "da-DK", locale = "da", device = DS)
@Preview(name = "de-DE", locale = "de", device = DS)
@Preview(name = "el-GR", locale = "el", device = DS)
@Preview(name = "es-ES", locale = "es", device = DS)
@Preview(name = "fi-FI", locale = "fi", device = DS)
@Preview(name = "fr-FR", locale = "fr", device = DS)
@Preview(name = "hu-HU", locale = "hu", device = DS)
@Preview(name = "it-IT", locale = "it", device = DS)
@Preview(name = "iw-IL", locale = "iw", device = DS)
@Preview(name = "ja-JP", locale = "ja", device = DS)
@Preview(name = "ko-KR", locale = "ko", device = DS)
@Preview(name = "nl-NL", locale = "nl", device = DS)
@Preview(name = "no-NO", locale = "nb", device = DS)
@Preview(name = "pl-PL", locale = "pl", device = DS)
@Preview(name = "pt-BR", locale = "pt-rBR", device = DS)
@Preview(name = "pt-PT", locale = "pt", device = DS)
@Preview(name = "ro", locale = "ro", device = DS)
@Preview(name = "ru-RU", locale = "ru", device = DS)
@Preview(name = "sr", locale = "sr", device = DS)
@Preview(name = "sv-SE", locale = "sv", device = DS)
@Preview(name = "tr-TR", locale = "tr", device = DS)
@Preview(name = "uk", locale = "uk", device = DS)
@Preview(name = "vi", locale = "vi", device = DS)
@Preview(name = "zh-CN", locale = "zh-rCN", device = DS)
@Preview(name = "zh-TW", locale = "zh-rTW", device = DS)
annotation class PlayStoreLocales

/**
 * Same supported Play Store locales in dark mode.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "en-US", locale = "en", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "af", locale = "af-rZA", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ar", locale = "ar", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ca", locale = "ca", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "cs-CZ", locale = "cs", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "da-DK", locale = "da", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "de-DE", locale = "de", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "el-GR", locale = "el", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "es-ES", locale = "es", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "fi-FI", locale = "fi", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "fr-FR", locale = "fr", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "hu-HU", locale = "hu", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "it-IT", locale = "it", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "iw-IL", locale = "iw", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ja-JP", locale = "ja", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ko-KR", locale = "ko", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "nl-NL", locale = "nl", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "no-NO", locale = "nb", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "pl-PL", locale = "pl", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "pt-BR", locale = "pt-rBR", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "pt-PT", locale = "pt", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ro", locale = "ro", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ru-RU", locale = "ru", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "sr", locale = "sr", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "sv-SE", locale = "sv", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "tr-TR", locale = "tr", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "uk", locale = "uk", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "vi", locale = "vi", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "zh-CN", locale = "zh-rCN", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "zh-TW", locale = "zh-rTW", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PlayStoreLocalesDark

/**
 * Small representative subset for fast iteration.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "en-US", locale = "en", device = DS)
@Preview(name = "de-DE", locale = "de", device = DS)
@Preview(name = "ja-JP", locale = "ja", device = DS)
@Preview(name = "ar", locale = "ar", device = DS)
@Preview(name = "pt-BR", locale = "pt-rBR", device = DS)
@Preview(name = "zh-CN", locale = "zh-rCN", device = DS)
annotation class PlayStoreLocalesSmoke

// @formatter:on
