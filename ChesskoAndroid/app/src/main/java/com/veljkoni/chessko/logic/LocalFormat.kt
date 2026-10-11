package com.veljkoni.chessko.logic

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

// Mnozine i brojevi po jeziku aplikacije (dizajnerski pregled, spec § 3 i § 16).
// Bez ijednog `android.*` uvoza — JVM testovi (`PluralRulesTest`). iOS ima isti
// fajl sa istim pravilima: `Chessko/Logic/LocalFormat.swift`.
//
// Android `Loc` ne ide kroz `strings.xml`, pa sistemski `<plurals>` ne pomaze;
// kategorija se bira ovde, a tekst svake kategorije zivi u `Loc.pluralDictionary`.

/** CLDR kategorija mnozine za CELE brojeve. */
enum class PluralCategory { ONE, FEW, MANY, OTHER }

object PluralRules {
    /**
     * CLDR pravila za osam jezika aplikacije, samo celobrojna grana:
     * - `sr`: one (…1 osim …11), few (…2–4 osim …12–14), other
     * - `ru`: isto, ali umesto other daje many
     * - `en`, `de`, `it`: one samo za 1
     * - `fr`, `hi`: one za 0 i 1
     * - `zh-Hans` i svaki jezik bez pravila: other
     */
    fun category(languageCode: String, n: Int): PluralCategory {
        val lang = languageCode.substringBefore('-')
        val a = abs(n)
        return when (lang) {
            "sr", "ru" -> {
                val m10 = a % 10
                val m100 = a % 100
                when {
                    m10 == 1 && m100 != 11 -> PluralCategory.ONE
                    m10 in 2..4 && m100 !in 12..14 -> PluralCategory.FEW
                    lang == "ru" -> PluralCategory.MANY
                    else -> PluralCategory.OTHER
                }
            }
            "en", "de", "it" -> if (a == 1) PluralCategory.ONE else PluralCategory.OTHER
            "fr", "hi" -> if (a <= 1) PluralCategory.ONE else PluralCategory.OTHER
            else -> PluralCategory.OTHER
        }
    }
}

object LocalNumber {
    /** Lokal za brojeve; srpski na latinicu (isti razlog kao `localeForDateFormatting`). */
    fun locale(languageCode: String): Locale = when (languageCode) {
        "sr" -> Locale.forLanguageTag("sr-Latn")
        else -> Locale.forLanguageTag(languageCode)
    }

    /** Decimalni broj u lokalnom formatu („-2,3" srpski, „-2.3" engleski); `signed` dodaje „+". */
    fun decimal(value: Double, fractionDigits: Int = 1, signed: Boolean = false,
                languageCode: String = Loc.fileLanguageCode()): String {
        val f = NumberFormat.getNumberInstance(locale(languageCode)) as DecimalFormat
        f.minimumFractionDigits = fractionDigits
        f.maximumFractionDigits = fractionDigits
        f.isGroupingUsed = false
        if (signed) f.positivePrefix = "+"
        return f.format(value)
    }

    /** Procenat iz vrednosti 0…100 u formatu jezika aplikacije („41,9 %" / „41.9%"). */
    fun percent(value0to100: Double, fractionDigits: Int = 1,
                languageCode: String = Loc.fileLanguageCode()): String {
        val f = NumberFormat.getPercentInstance(locale(languageCode))
        f.minimumFractionDigits = fractionDigits
        f.maximumFractionDigits = fractionDigits
        return f.format(value0to100 / 100.0)
    }
}
