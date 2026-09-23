package com.veljkoni.chessko.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

/**
 * Tipografska skala dizajn sistema — po ugledu na iOS
 * `Font.dsTitle/dsHeading/dsBody/dsCaption/dsMono`
 * (`Chessko/Views/DesignSystem.swift`). Pozivna mesta citaju `Type.title`,
 * itd., umesto da sama biraju velicinu i tezinu fonta.
 *
 * ## Presuda Faze 10: vrednosti se NE pomeraju, ekrani dolaze skali
 *
 * Zateceno stanje: skala nudi 22/17/15/12 (+13 mono), a **161 pozivno mesto**
 * na ekranima zakucava velicinu samostalno. Cetiri najcesce su 13 (31x),
 * 12 (31x), 11 (31x) i 14 (29x) — dakle oko 30% poklapanja sa skalom.
 * Pitanje je bilo da li se skala pomera ka ekranima ili ekrani ka skali.
 *
 * (Broj je 161, ne 169: u trenutku merenja — pre koraka `label` — naivan grep
 * je hvatao i 8 pogodaka u ovom fajlu: 5 definicija ispod, `bodyLarge` iznad, i 2 unutar zakomentarisanog
 * Material bloka. Grepovana lista se cita, ne broji. Uz to postoji i jedno
 * uslovno mesto koje sablon `fontSize = N.sp` uopste ne vidi:
 * `LessonRenderer.kt:174` u zatecenom stablu, danas `:189`.)
 *
 * **Mereno, ne pretpostavljeno.** Vrednosti ove skale nisu proizvoljne: to su
 * bas velicine koje iOS `Font.ds*` daje na telefonu, jer `appFont` za
 * ne-Mac uredjaje vraca `.system(style)`, dakle sistemski Dynamic Type
 * (`Chessko/Logic/PlatformHelper.swift:33-35`). Izmereno pokretanjem UIKit-a
 * na simulatoru (`UIFont.preferredFont(forTextStyle:)` pri
 * `contentSizeCategory == .large`), ne prepisano iz dokumentacije:
 *
 *   title2 22 · title3 20 · headline 17 · body 17 · callout 16
 *   subheadline 15 · footnote 13 · caption 12 · caption2 11
 *
 * Poklapanje je tacno: `title` 22 = title2, `heading` 17 = headline,
 * `body` 15 = subheadline, `caption` 12 = caption, `mono` 13 = footnote.
 *
 * Zato skala NE ide ka ekranima. Ona je jedina strana koja ima poreklo;
 * brojevi na ekranima su 161 nezavisna odluka doneta ekran po ekran, i mere
 * se kao drift, ne kao sistem:
 *
 *  - Ista uloga, tri velicine u JEDNOM fajlu (`LearnView.kt`): naslov kutije
 *    13 bold (`LBox`), naslov nabrajanja 14 bold (`LBullet`), naslov sekcije
 *    15 bold (`LSectionHeader`).
 *  - 11 i 12 nose istu ulogu (prigusen podnaslov, oznaka malog dugmeta) bez
 *    ijednog pravila koje ih razdvaja.
 *  - Samo 3 od 161 mesta uopste zadaje `lineHeight`.
 *  - Telo lekcije je 13 (`LearnView.LPara`), dok iOS `L_Para` crta `.dsBody`
 *    = 15 (`Chessko/Views/LessonRenderer.swift:394`). Android je ovde vec
 *    razidjen sa iOS-om, i to naniže; dolazak skali ga VRACA u paritet, ne
 *    odvodi iz njega.
 *
 * To je i presedan grane: Faza 6d-2 je boje table poravnala sa iOS
 * vrednostima umesto da menja paletu, uz isti razlog — promena vrednosti
 * znacila bi razlaz sa iOS-om.
 *
 * ## Sta je dodato, i zasto bas to
 *
 * `label` (11) je JEDINI nov korak. Nije ustupak driftu: 11 je Apple-ov
 * `caption2`, a iOS ga svesno koristi za **dve od tri** uloge za koje ga
 * trazi i Android — prigusen podnaslov ispod naslova
 * (`SettingsSheet.swift:156`) i oznaka ispod brojcane vrednosti (`:411`) —
 * i sam ga u lekcijama obelezava komentarom „van skale"
 * (`LessonRenderer.swift:306`, `:613`). Treca uloga (tekst uz ikonu na
 * zbijenom dugmetu) **nema iOS pandan**, jer iOS te radnje crta kao ikonu
 * bez teksta; razlika je razlozena uz samu definiciju `label` ispod.
 * Bez ovog koraka bi 31 mesto moralo na `caption` (12), cime bi nestao tier
 * koji iOS stvarno koristi.
 *
 * **Izvrseno stanje je drugacije od te recenice, i broj se ovde ispravlja, ne
 * prepisuje:** od tih 31 mesta na 11 Task 3 je po ULOZI poslao **18 na `label`
 * a 13 na `caption`** (prigusene sporedne oznake, hintovi vezbi, dugmad
 * „Reset"/„Ponovo", tekst licence). Korak i dalje zaradjuje mesto — `label`
 * danas ima **23 pozivna mesta** (18 iz 11-tiera + 5 koja su dosla sa 12 i sa
 * 9) — ali ga aritmetika „inace bi 31 mesto moralo na caption" vise ne
 * opisuje.
 *
 * Jedna posledica koju vredi znati: `materialAdvantage` („+3") i `evalText` su
 * dva SUSEDNA brojcana bedza u istoj kartici igraca i bila su iste velicine
 * (oba 11). Prvi je otisao na `caption` (12, jer iOS ga crta
 * `.caption.weight(.medium)`, `GameView.swift:423`), drugi na `label` (11, jer
 * iOS pandan nema — uloga 4 gore). Razilaze se za 1sp; branjivo, ali nov
 * razlaz koji pre Faze 10 nije postojao.
 *
 * ## Sta NAMERNO nema ime: 14
 *
 * 14 je cetvrta po ucestalosti (29 mesta) i jedina cesta velicina koje nema
 * u Apple-ovoj lestvici (13 → 15, bez medjukoraka). To je Material-ov
 * `titleSmall`/`bodyMedium`, dakle drift ka DRUGOJ lestvici, ne korak koji
 * nedostaje ovoj. Tih 29 mesta se razvrstava po ULOZI — naslov iznad tela
 * ide na `body` (iOS tu crta `.subheadline`), prigusena sporedna oznaka na
 * `caption` — a ne zaokruzuje se na najblizi korak.
 *
 * ## Cena, izricito
 *
 * Oko 60 pozivnih mesta (ceo 13-tier osim monospace i ceo 14-tier), plus
 * delovi 16/18 tiera, menjaju velicinu. Najvidljivija je promena tela
 * lekcije 13 → 15, koja preliva svaku lekciju na oba jezika. Zato vizuelna
 * provera mora da obidje SVAKI ekran, ne uzorak.
 *
 * Velicina van ove skale sme da ostane samo za sadrzaj i hrom koji nije
 * tekst interfejsa — koordinate table 9 (`BoardCoordinateSize`), cifre sata
 * 72, strelica u redu koraka Puta 20 (`StepChevronSize`) i figura u lekciji
 * 22 (`LessonPieceGlyphSize`) —
 * i tada nosi komentar zasto — isti obrazac kojim iOS obelezava svoja
 * „van skale" mesta.
 */
object Type {
    /** Naslov ekrana. Apple `title2`. */
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )

    /** Naslov sekcije ili kartice. Apple `headline`. */
    val heading: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    )

    /**
     * Osnovni tekst — i telo lekcije. Apple `subheadline`; iOS `L_Para` crta
     * bas ovo (`Chessko/Views/LessonRenderer.swift:394`).
     */
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )

    /** Prigusen, sitan tekst — podnaslovi, oznake. Apple `caption`. */
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    /**
     * Najsitnija oznaka interfejsa — Apple `caption2`. **Cetiri** uloge na
     * Androidu, i NISU sve cetiri paritet sa iOS-om:
     *
     *  1. Prigusen podnaslov ispod naslova reda — iOS crta bas `caption2`
     *     (`SettingsSheet.swift:156`, ispod naslova na `.subheadline`).
     *  2. Oznaka ispod brojcane vrednosti — iOS crta bas `caption2`
     *     (`SettingsSheet.swift:411`, ispod vrednosti na `.headline`).
     *     **Ali iOS tu nije jednoznacan:** istu ulogu u
     *     `AnalysisView.swift:93` („Tacnost" ispod procenta) crta
     *     `.dsCaption` (12). Task 3 je poslusao iOS na oba mesta, pa ista
     *     uloga u aplikaciji stoji na dva koraka. Zabelezeno da se ne
     *     „ujednacava" bez te informacije.
     *  3. Tekst uz ikonu na zbijenom dugmetu („Vrati" / „Predaj" / „Podeli"
     *     u `MainActivity.ActionsRow`) — **nema iOS pandan ni u kom fontu.**
     *     iOS te radnje drzi u `ToolbarItem`-ima kao IKONU BEZ TEKSTA
     *     (`GameView.swift:199-220`). A na jedinom mestu gde iOS stvarno
     *     pari ikonu s tekstom (`LessonRenderer.swift:306`), 11 dobija
     *     IKONA, dok tekst pored nje ide na `dsCaption` (12).
     *     Za ovu ulogu je `label` **android odluka, ne paritet** — drzi je
     *     doslednost sa prve dve uloge, ne iOS.
     *  4. Brojcani bedz u kartici igraca (ocena pozicije, `UiComponents.kt`)
     *     — takodje **android odluka**: iOS taj broj u kartici uopste nema,
     *     Faza 1 ga je odatle sklonila i ostavila samo na eval traci
     *     („uzima boje table umesto dupliranog broja u kartici").
     *     Ulogu je dodao Task 3; ovaj KDoc je do tada poznavao tri.
     *
     * (Provereno: `caption2` ima tacno 5 upotreba u svim `.swift` fajlovima
     * pod `Chessko/Views` — dve gore, `LessonRenderer.swift:306` i `:613`,
     * i `–` placeholder u `CapturedPiecesView.swift:34`. Broj je tacan;
     * **zakljucak da Android taj placeholder nema NIJE** — ima ga,
     * `CapturedPiecesView.kt`, i Task 3 ga je bas zato stavio na `label`,
     * dakle u paritet sa iOS-ovim `caption2`. Faza 1 je `–` uklonila sa
     * iOS-ovih KARTICA IGRACA, ne iz prikaza uzetih figura.)
     *
     * NIJE „caption koji je ispao premali" — ovo je zaseban tier, i jedini
     * korak koji Faza 10 dodaje. Ako se ikad spaja sa `caption`, spaja se
     * odlukom o ULOGAMA gore, ne zato sto su brojevi susedni.
     */
    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )

    /**
     * Monospaced cifre malog obima — trenutno rejting bedz na ekranu Zadaci.
     * Apple `footnote` u monospace varijanti.
     */
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
}