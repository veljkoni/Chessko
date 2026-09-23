package com.veljkoni.chessko.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - Dizajn tokeni
//
// Jedan izvor istine za boje, razmake i radijuse. Pravac „Tiho i precizno"
// (spec 5.6): neutralne podloge, jedan uzdrzan akcent, tabla je jedina
// zasicena stvar na ekranu.
//
// Akcent je FIKSAN — ne menja se sa temom table. Boje vezane za tablu (polja,
// poslednji potez, legalni potezi, sah) i dalje dolaze iz `BoardTheme`.
//
// Vrednosti su ISTE kao iOS (`Chessko/Views/DesignSystem.swift`). Kurikulum,
// lekcije i baza zadataka se vec dele; paleta je cetvrta zajednicka stvar.

data class ChesskoColors(
    val accent: Color,
    val ground: Color,
    val surface: Color,
    val navBar: Color,
    val fill: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Semanticke boje — nose znacenje, nisu ukras. */
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Zatamnjenje ispod modalnih preklopa. Namerno isto u obe teme. */
    val scrim: Color,
    /** Tekst i ikone NA scrim-u. Namerno bela u obe teme. */
    val onScrim: Color,
    /** Taman tekst koji se NE invertuje — za povrsine koje same ne prate temu. */
    val inkFixed: Color,
    /**
     * Tekst i ikone NA `accent` podlozi. MORA biti adaptivan: `accent` menja
     * svetlinu izmedju tema, pa bela na TAMNOJ varijanti (svetloplavi
     * `#7EA0E8`) daje 2,6:1 — ispod AA (CLAUDE.md, Faza 2 changelog).
     * Cuva ga `ContrastTest.plainWhiteWouldFailOnTheDarkAccent`.
     */
    val onAccent: Color
)

val LightColors = ChesskoColors(
    accent   = Color(0xFF2E4A8A),
    ground   = Color(0xFFF2F3F7),
    surface  = Color(0xFFFFFFFF),
    navBar   = Color(0xFFFFFFFF),
    fill     = Color(0xFFE7EAF1),
    line     = Color(0xFFDFE3EC),
    ink      = Color(0xFF161A22),
    inkMuted = Color(0xFF6B7280),
    success  = Color(0xFF2F7D4F),
    warning  = Color(0xFFB07D2B),
    danger   = Color(0xFFB3261E),
    scrim    = Color(0x8C000000),
    onScrim  = Color(0xFFFFFFFF),
    inkFixed = Color(0xFF161A22),
    onAccent = Color(0xFFFFFFFF)
)

val DarkColors = ChesskoColors(
    accent   = Color(0xFF7EA0E8),
    ground   = Color(0xFF0E1428),
    surface  = Color(0xFF161D33),
    navBar   = Color(0xFF131A2E),
    fill     = Color(0xFF1E2740),
    line     = Color(0xFF232C46),
    ink      = Color(0xFFEEF1F7),
    inkMuted = Color(0xFF8B93A7),
    success  = Color(0xFF6FCF97),
    warning  = Color(0xFFE0B252),
    danger   = Color(0xFFF2857A),
    scrim    = Color(0x8C000000),
    onScrim  = Color(0xFFFFFFFF),
    inkFixed = Color(0xFF161A22),
    onAccent = Color(0xFF161A22)
)

/**
 * `staticCompositionLocalOf`, ne `compositionLocalOf`: paleta se menja retko
 * (promena teme), a citaju je stotine mesta. Staticna varijanta ne prati
 * citaoce pojedinacno nego rekomponuje ceo podstablo — jeftinije za ovaj odnos.
 */
val LocalChesskoColors = staticCompositionLocalOf { DarkColors }

/**
 * Pozivna mesta citaju `DS.accent`, isto kao na iOS-u. Geteri su
 * `@ReadOnlyComposable` da ne prave sopstvenu grupu u kompoziciji.
 */
object DS {
    val accent: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.accent
    val ground: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.ground
    val surface: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.surface
    val navBar: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.navBar
    val fill: Color     @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.fill
    val line: Color     @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.line
    val ink: Color      @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.ink
    val inkMuted: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.inkMuted
    val success: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.success
    val warning: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.warning
    val danger: Color   @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.danger
    val scrim: Color    @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.scrim
    val onScrim: Color  @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.onScrim
    val inkFixed: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.inkFixed
    val onAccent: Color @Composable @ReadOnlyComposable get() = LocalChesskoColors.current.onAccent

    /**
     * Razmaci. Isti koraci kao iOS `DS.Space`
     * (`Chessko/Views/DesignSystem.swift`, `enum Space` na `:62-68`).
     *
     * ## Presuda Faze 10: skala uzima SVOJE, i nista preko toga
     *
     * Ovo je obrnuta presuda od `Type` (`ui/theme/Type.kt`), i razlika je
     * namerna — ali NE zato sto bi `Space` bio proizvoljan. Mreza od 4dp ima
     * svoje poreklo (Material baseline grid), isto kao sto ga ima i minimum
     * od 48dp na koji se tacka 3 ispod poziva.
     *
     * Razlika je u tome sto poreklo ovde **ne razresava pitanje**, a tamo
     * jeste. `Type` je imao JEDNU lestvicu sa poreklom (Apple-ovu, izmerenu
     * na uredjaju) naspram 161 nezavisne odluke bez ijednog pravila — tu je
     * skala jedina strana koja nesto tvrdi, pa ekrani dolaze njoj. Kod
     * razmaka postoje DVE lestvice sa poreklom, Material-ova 4dp mreza i
     * ono sto iOS stvarno crta, i one se ne poklapaju: mreza trazi 16 ili 24,
     * iOS na istom mestu pise 20. Kad se dva porekla sukobe, odlucuje
     * merenje, a merenje (tacka 4) kaze da je iOS tu gde jeste i da ga
     * zaokruzivanje pomera od nas.
     *
     * Presudu, dakle, nosi **paritet sa iOS-om**, ne odsustvo porekla.
     *
     * ### Sta je mereno
     *
     * Ceo modul, bez `ui/BoardView.kt` i `ui/BoardTheme.kt` (tabla je sadrzaj)
     * i bez ovog fajla, sa oljustenim komentarima: **530 `.dp` literala**.
     * Oni se ne dele po vrednosti nego po ULOZI, i to je cela presuda:
     *
     *   298  razmak      (padding / spacedBy / PaddingValues / Spacer)
     *   104  radijus     (RoundedCornerShape)
     *    97  dimenzija   (size / width / height, van Spacer-a)
     *    24  ivica       (debljina linije)
     *     7  ostalo      (elevacija i imenovani lokalni `val`-ovi)
     *
     * Kategorija „ostalo" je tu zato sto je klasifikator gledao OBLIK POZIVA,
     * a imenovan `val` nema poziv oko sebe. Po ULOZI ona nije homogena: od 7
     * mesta, **3 su razmaci** (`AnalysisView.kt:322` i `MainActivity.kt:745`
     * i `:1019`, sve tri `val spacing` koji ide pravo u `Spacer` ili
     * `spacedBy`), 3 su dimenzije (`evalBarWidth` ×2, `itemMinWidth`) i 1 je
     * elevacija. Posledice po presudu nema — `6.dp` nije na skali, pa bi i po
     * ulozi ostao zakucan — ali se belezi, jer je pravilo cele ove presude
     * „po ULOZI, ne po mestu zapisa", a tabela iznad ga u ovom redu ne prati.
     *
     * Task 4 je zamenio **179 od 298 razmaka**, **65 od 104 radijusa** i jedan
     * imenovan razmak iz „ostalo" (`AnalysisView.kt`, `val spacing`) — ukupno
     * **245**, tacno one koji vec padaju na skalu. To ne menja nijedan piksel:
     * 530 − 245 = 285 literala, koliko ih u modulu i ima posle zamene.
     * Presuda ispod tice se tih 285.
     *
     * ### 1. Dimenzija nije razmak (97 mesta, ostaju zakucane)
     *
     * Najveca stavka, i nije bila u zadatom razvrstavanju. `Modifier.size(16.dp)`
     * na ikoni nije razmak nego velicina stvari; `DS.Space.l` bi tu bio tvrdnja
     * da jeste. **Mereno na iOS-u, ne pretpostavljeno:** `DS.Space` ima **86**
     * pozivnih mesta u `Chessko`, od kojih je **0** u `.frame(...)` — a iOS uz
     * to nosi **40** `.frame(...)` poziva sa zakucanom sirinom ili visinom
     * (od toga 29 sa oba). Dimenzije i tamo imaju sopstvena imena ili ostaju
     * brojevi; `maxBoardSide` ispod je bas takvo ime.
     *
     * Zato NIJE tokenizovano ni 16 dimenzija koje slucajno padaju na skalu
     * (npr. `MainActivity.kt:963` `size(24.dp)`, `PuzzleView.kt:363`
     * `size(16.dp)`). Da jesu, broj bi bio lepsi a tvrdnja netacna.
     *
     * ### 2. Ivice i hairline (24 mesta, ostaju zakucane)
     *
     * 1 / 1.5 / 2 / 2.5 / 3 — debljina linije. Isto obrazlozenje.
     * (Zadato razvrstavanje je ovu grupu definisalo po VREDNOSTI i dobilo 46
     * pogodaka; po ULOZI su ivice 24 od njih. Preostalih 22 nisu ivice nego
     * 18 mikro-razmaka ispod `xs` — `padding(2.dp)`, `spacedBy(3.dp)` —
     * 3 sitna radijusa i jedna dimenzija. Njih drzi tacka 6, ne ova: skala
     * nema korak ispod `xs` = 4, a izmisliti ga zbog 18 mesta znacilo bi
     * uvesti `xxs` koji iOS nema.)
     *
     * ### 3. Touch-targeti 44 / 48 (12 mesta, ostaju zakucani)
     *
     * Svih 12 je dimenzija, nijedan razmak. Material minimum je 48dp i to je
     * pravilo platforme, ne korak ove skale.
     *
     * ### 4. „Blizu skale" 6 / 10 / 14 / 18 / 20 (157 mesta, NE zaokruzuju se)
     *
     * Najteza odluka: 96 od njih su pravi razmaci, i zaokruzivanje na najblizi
     * korak bi ih pomerilo za 2dp — osim `20.dp`, koje je **4dp** i od `l` (16)
     * i od `xl` (24), jer skali bas taj korak nedostaje; takva su 3 mesta.
     * **Ne zaokruzuju se**, iz razloga koji je
     * izmeren u iOS izvoru: iOS ima **istu** raspodelu zakucanu PORED svojih
     * tokena, i nikad je nije poravnao. `Chessko/Views/GameView.swift` nosi
     * oba u jednom fajlu — `:283` `.padding(.vertical, DS.Space.m)` i
     * `:284` `cornerRadius: DS.Radius.m`, a `:353` `.padding(.horizontal, 10)`,
     * `:587` `.padding(.vertical, 10)`, `:598` `.padding(.horizontal, 20)`,
     * `:537` i `:540` `cornerRadius: 14`. Prebrojano u celom `Chessko`:
     * 15 zakucanih `padding(10)`, 13 `padding(20)`, 12 `padding(14)`,
     * 10 `padding(6)`.
     *
     * Zaokruzivanje bi, dakle, bilo **razlaz sa iOS-om u 96 tacaka**, i to u
     * fazi cija je glavna tvrdnja bila paritet. Isti presedan koji Faza 6d-2
     * vec nosi za boje table.
     *
     * ### 5. „Krupno" 26 / 28 / 32 — `xxl` se NE uvodi (22 mesta)
     *
     * Zadato razvrstavanje je ovu grupu ponudilo kao kandidata za nov korak
     * `xxl = 32.dp`. Mereno: **20 od 22 su dimenzije** (velicine ikona i
     * ploca), a razmaka su tacno **dva** — `AnalysisView.kt:183` i `:214`,
     * oba `padding(top = 32.dp)` iznad poruke na praznom ekranu. Token ciji
     * bi 20 od 22 pozivalaca bio `size()` bio bi bas onaj mislabel zbog koga
     * dimenzije iz tacke 1 i ostaju zakucane.
     *
     * ### 6. Ostatak — citan pojedinacno, ne po grupi (28 mesta)
     *
     * Jedina grupa koju je trebalo otvoriti red po red, i to je i uradjeno.
     * Po ulozi: **23 dimenzije** (od `size(5.dp)` tackica u `UiComponents.kt`
     * do `widthIn(max = 500.dp)` u `MainActivity.kt`), **1 radijus**
     * (`50.dp`, obrazlozen uz `Radius` ispod), **1 imenovana dimenzija**
     * (`AnalysisView.kt:319` `itemMinWidth = 96.dp`) i **3 razmaka**:
     * dva `0.dp` i jedan `7.dp` (`UiComponents.kt:164`). Uz njih idu i dva
     * imenovana razmaka iz „ostalo" (`MainActivity.kt:745`, `:1019`,
     * `val spacing = 6.dp`) — oba su „blizu skale", pa ih drzi tacka 4.
     *
     * `0.dp` se NE tokenizuje jer nula nije korak skale nego odsustvo
     * razmaka; `Space.none = 0.dp` bi bio token cija je jedina informacija
     * „ovde namerno nema razmaka", a to vec kaze sam broj. `7.dp` je jedno
     * mesto, ispod `s` (8) — zaokruzivanje na `s` bi bilo tacka 4 u malom,
     * i pada iz istog razloga.
     *
     * Ovde je i jedini zateceni literal koji ova faza NIJE smela da dira a
     * skripta ga je bila dirnula: `ui/LessonDetailView.kt:74` nosi u
     * KOMENTARU `padding(16.dp)`, i to je recenica o tome sta je margina
     * bila RANIJE. Token u njoj bi bio netacna tvrdnja o istoriji, pa je
     * vracen literal. Zamena koja ne razlikuje kod od komentara ume da
     * „popravi" i ono sto opisuje proslost.
     *
     * ### Cena, izricito
     *
     * Posle ove presude **285 od 530 literala ostaje zakucano**, a skala
     * pokriva 245 (danas: 181 pozivno mesto `Space`, 65 `Radius`). Znaci:
     * `Space` NIJE jedini izvor razmaka na Androidu, i `grep` za `.dp` ce i
     * dalje davati stotine pogodaka. To je prihvaceno — jedina alternativa
     * je bila skala koja je prepis drifta (koraci 6/10/14/18/20/26/28/32),
     * dakle spisak zatecenog stanja pod imenom sistema.
     *
     * Od tih 285, **119 su pravi razmaci** i oni su jedina stavka koju bi
     * neka buduca faza mogla da zatvori — i to samo zajedno sa iOS-om, jer
     * bi inace pomerila dve platforme jednu od druge. Preostalih 166 nisu
     * razmaci uopste (97 dimenzija, 39 radijusa, 24 ivice, 6 ostalo) i za
     * njih ovde nema sta da se zatvara.
     *
     * ### Test POSTOJI, i ova alineja je ispravka sopstvene tvrdnje
     *
     * Ovde je isprva pisalo da testa „ne moze biti", jer bi test koji
     * zabranjuje `.dp` literal zabranio i ivice i touch-targete. Tvrdnja je
     * bila tacna o SIREM testu, a pogresna kao zakljucak: invarijanta nije
     * „nema `.dp`" nego **„on-scale vrednost u OBLIKU POZIVA razmaka mora
     * biti token"**. Taj oblik je uzak — `.border(1.dp, …)` i `size(48.dp)`
     * mu ne odgovaraju uopste, pa im izuzetak nije ni potreban.
     *
     * Cuva ga `SpacingTokensTest.onScaleSpacingAndRadiusValuesAreWrittenAsTokens`
     * (JVM, cita izvor). Namerno je SINTAKSNI, ne po ulozi: klasifikator uloge
     * je heuristika, a heuristika u testu sutra daje lazan pad. Dokazan
     * mutacijom — sa vracenim `MainActivity.kt` na stanje pre ovog taska
     * prijavljuje 62 mesta, sa danasnjim 0.
     *
     * Ostatak presude (dimenzije, ivice, „blizu skale", `xxl`) test NE cuva i
     * ne moze — to su odluke o ulozi. Njih cuvaju ovi komentari i vizuelni
     * prolaz.
     *
     * (Jos jedna mera, zapisana da se ne izvodi ponovo: naivno prebrojavanje
     * „na skali" nad svim `.dp` u modulu daje **275 od 550** i tako je i bilo
     * zadato. Broj je tacan ali slep za ulogu i za fajl. Razlika do 245 je
     * tacno 30 pogodaka koji se ne smeju tokenizovati: 16 dimenzija,
     * 8 definicija bas ovih tokena (5 `Space` + 3 `Radius`), 3 radijusa od
     * 4dp za koje `Radius` nema korak, 1 pogodak u `BoardView.kt`,
     * 1 `tonalElevation` i 1 pogodak unutar komentara. 245 + 30 = 275.
     * Grepovana lista se cita, ne broji.)
     */
    object Space {
        val xs: Dp = 4.dp
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
        val xl: Dp = 24.dp
    }

    /**
     * Radijusi. Isti koraci kao iOS `DS.Radius`
     * (`Chessko/Views/DesignSystem.swift`, `enum Radius` na `:76-80`).
     *
     * ## Presuda Faze 10: korak se NE dodaje
     *
     * 104 radijusa u modulu, **65 vec pada na skalu** i ta su zamenjena.
     * Preostalih 39: `10.dp` (17x), `6.dp` (11x), `4.dp` (3x), `3.dp` (3x),
     * `20.dp` (2x), `14.dp` (2x), `50.dp` (1x).
     *
     * Ponudjeno je bilo da `10` ode na `s` (8) ili `m` (12), ili da `Radius`
     * dobije korak. Nijedno. Razlog je isti izmereni presedan kao za `Space`:
     * **iOS ima 53 zakucana `cornerRadius: N` prema 23 tokenizovana**, i medju
     * zakucanima su bas ove vrednosti — `cornerRadius: 14` 10x,
     * `cornerRadius: 6` 7x, `cornerRadius: 10` 4x, `cornerRadius: 20` 2x.
     * Konkretno mesto gde oba stoje u istom fajlu:
     * `Chessko/Views/GameView.swift:284` (`DS.Radius.m`) prema `:537` i `:540`
     * (`cornerRadius: 14`). Pomeranje `10` na `8` ili `12` bi promenilo 17
     * uglova na Androidu bez ijedne iOS promene koja ga prati.
     *
     * `50.dp` (`ui/LearnView.kt`, cip „Mat u N") **nije radijus kartice nego
     * pilula** — poluprecnik veci od visine cipa, dakle potpuno obla ivica.
     * Otvoreno i provereno: iOS na tacno tom mestu crta `Capsule()`
     * (`Chessko/Views/LessonRenderer.swift:616`), sto je isti oblik izrazen
     * imenom umesto brojem. `Radius` nema i ne treba da ima korak za to.
     */
    object Radius {
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
    }

    /**
     * Najveca stranica table. Na telefonu se nikad ne dostigne; na tabletu
     * sprecava da tabla proguta ceo ekran.
     */
    val maxBoardSide: Dp = 560.dp
}
