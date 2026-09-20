package com.veljkoni.chessko

import androidx.compose.ui.graphics.Color
import com.veljkoni.chessko.ui.ClockBarAccent
import com.veljkoni.chessko.ui.ClockBarBackground
import com.veljkoni.chessko.ui.ClockBarWell
import com.veljkoni.chessko.ui.theme.ChesskoColors
import com.veljkoni.chessko.ui.theme.DarkColors
import com.veljkoni.chessko.ui.theme.LightColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Kontrast se RACUNA, ne procenjuje.
 *
 * iOS je ovu lekciju platio: bela na `accent`-u daje 8,5:1 u svetloj temi ali
 * samo 2,6:1 u tamnoj (CLAUDE.md, Faza 2 changelog), pa je morao da uvede
 * poseban `onAccent` token. Ovaj test postoji da se takva greska vise ne
 * otkrije na ekranu.
 */
class ContrastTest {

    /** WCAG 2.1 relativna luminansa. */
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    /** WCAG odnos kontrasta, uvek >= 1.0. */
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, p: ChesskoColors, fg: (ChesskoColors) -> Color,
                      bg: (ChesskoColors) -> Color, min: Double) {
        val r = contrast(fg(p), bg(p))
        assertTrue("$name: %.2f, a trazi se >= %.2f".format(r, min), r >= min)
    }

    /** Isto sto radi Compose kad crta `top.copy(alpha = a)` preko `bottom`. */
    private fun over(top: Color, alpha: Float, bottom: Color): Color = Color(
        red = alpha * top.red + (1f - alpha) * bottom.red,
        green = alpha * top.green + (1f - alpha) * bottom.green,
        blue = alpha * top.blue + (1f - alpha) * bottom.blue
    )

    /** Par bez palete iza sebe — za fiksne boje sata, koje nisu ni u jednoj temi. */
    private fun checkPair(name: String, fg: Color, bg: Color, min: Double) {
        val r = contrast(fg, bg)
        assertTrue("$name: %.3f, a trazi se >= %.3f".format(r, min), r >= min)
    }

    @Test
    fun textOnBackgroundsMeetsAA() {
        // `ink`/`inkMuted` nad `surface` nose i dijaloge sata (izbor vremenske kontrole,
        // info), koje je Faza 6d-2 prevela na tokene. Zaseban test za njih je bio napisan
        // pa uklonjen: ponavljao je bas ove cetiri tvrdnje, pa nije mogao da padne a da i
        // ovaj ne padne. Test koji ne moze da doda informaciju nije dokaz nego sum.
        //
        // `ink/ground` (Task 2) nosi i tekst blokova lekcije koji sede direktno na
        // ekranskoj podlozi bez Card/Surface iza sebe -- citat (`LQuote`), naziv
        // figure u `LPieceValueTable`, poznat simbol u `LessonPieceGlyph`, naslov
        // lekcije u `LessonDetailView`.
        //
        // Task 3 (`LearnView.kt`) dodaje STVARNE potrosace za sva cetiri para u ovoj
        // petlji odjednom: `OpeningExerciseCard`/`MateExerciseCard`/`MatePuzzleCard`
        // i `PieceExplorer`-ov grid + info panel sede na `DS.surface` (birano bas
        // zato sto ovde, za razliku od `DS.fill`, ni `success` ni `inkMuted` nisu
        // sub-AA ni u jednoj temi -- vidi `knownSubAAPairsDoNotGetWorse`, gde su oba
        // sub-AA SAMO nad `fill`/`ground`). `ink/surface` nosi naslove kartica i
        // odabranu/neodabranu figuru u pikeru; `inkMuted/surface` nosi hint tekst i
        // status „u toku"; `success/surface` i `danger/surface` nose status
        // „reseno"/„pogresan potez" u sve tri kartice -- boja koja NOSI ZNACENJE.
        // Ove kartice nemaju ni Card ni Surface izmedju sebe i `DS.ground` (isto
        // stablo kao lekcijski blokovi iznad), pa je `DS.surface` bas ono sto stoji
        // ISPOD teksta, ne priblizna procena.
        //
        // Faza 8, Task 2 dodaje jos potrosaca `success/surface`, `danger/surface` i
        // `inkMuted/surface` -- status dijalog kraja partije (`MainActivity.kt`),
        // koji sedi na `DS.surface` (`Surface(color = DS.surface)`): trofej
        // (`Icons.Default.EmojiEvents`, pobeda) tint `DS.success`; izraz lica
        // (`Icons.Default.SentimentDissatisfied`, poraz matom) i zastava
        // (`Icons.Default.Flag`, poraz predajom) tint `DS.danger`; rukovanje
        // (`Icons.Default.Handshake`, remi) tint `DS.inkMuted`. Isti par, nov ekran.
        //
        // Faza 8, Task 4 dodaje jos tri potrosaca `success/surface`: bedz
        // resenosti (`Icons.Default.CheckCircle`, tint `DS.success`) u header redu
        // `OpeningExerciseCard`-a i `MateExerciseCard`-a, i trofej
        // (`Icons.Default.EmojiEvents`, isti tint) u `MatePuzzleCard`-u
        // (`LearnView.kt`) -- sve tri kartice i dalje sede direktno na
        // `DS.surface`, isti par kao status tekst ispod njih, samo drugo mesto u
        // istom stablu.
        for ((label, p) in listOf("svetla" to LightColors, "tamna" to DarkColors)) {
            check("$label ink/ground", p, { it.ink }, { it.ground }, 4.5)
            check("$label ink/surface", p, { it.ink }, { it.surface }, 4.5)
            check("$label inkMuted/surface", p, { it.inkMuted }, { it.surface }, 4.5)
            check("$label success/surface", p, { it.success }, { it.surface }, 4.5)
            check("$label danger/surface", p, { it.danger }, { it.surface }, 4.5)
        }
    }

    /**
     * Najvazniji par u celom sistemu. `accent` menja svetlinu izmedju tema, pa
     * nijedna FIKSNA boja teksta ne radi u obe — zato `onAccent` postoji.
     */
    @Test
    fun onAccentMeetsAAInBothThemes() {
        // Task 3 dodaje odabranu figuru u `PieceExplorer`-ovom pikeru i aktivno
        // dugme scenarija (`LearnView.kt`) kao stvarne potrosace -- isti par
        // (`DS.accent`/`DS.onAccent`) kao birac teme u `SettingsView.kt` i kategorije
        // u `ChessClockView.kt`.
        check("svetla onAccent/accent", LightColors, { it.onAccent }, { it.accent }, 4.5)
        check("tamna onAccent/accent", DarkColors, { it.onAccent }, { it.accent }, 4.5)
    }

    /**
     * Dokaz da je `onAccent` NEOPHODAN: fiksna bela pada u tamnoj temi (accent
     * je tu svetloplav, `#7EA0E8` — vidi CLAUDE.md, Faza 2 changelog: 2,6:1).
     * Ako ovaj test ikad prestane da vazi, `onAccent` se sme ukloniti — do tada ne.
     */
    @Test
    fun plainWhiteWouldFailOnTheDarkAccent() {
        val r = contrast(Color.White, DarkColors.accent)
        assertTrue("bela na (svetlijem, tamna tema) akcentu daje %.2f — da je >= 4.5, onAccent ne bi trebao".format(r),
            r < 4.5)
    }

    /**
     * SEST PAROVA JE ISPOD AA U SVETLOJ TEMI. Prva tri su NASLEDJENA iz spec
     * tabele (iOS ima iste vrednosti); peti (`warning/fill`) je dodat u talasu
     * ispravki pred spajanje i danas nema pozivno mesto (vidi komentar uz njega);
     * sesti je dodat u Fazi 6d-2, Task 2 (vidi komentar ispod tabele). Cetvrti
     * (`success/fill`) je TAKODJE dodat bez pozivnog mesta, ali je od Faze 8,
     * Task 3 dobio pravog potrosaca — kvacicu resenosti u `PuzzleView.kt`
     * (`Icons.Default.CheckCircle`). Prag ispod (4,5) je za TEKST i ta ikona ga
     * ne dostize u svetloj temi — nebitno, ikona je graficki objekat, WCAG-ov
     * prag za nju je 3:1 (SC 1.4.11), koji 4,18 lako prelazi; vidi
     * `puzzleSolvedCheckmarkIsReadableOnDateRow` niže za tacnu tvrdnju i za
     * TAMNU temu (7,78), koja ovde uopste nije bila pinovana.
     *
     *   inkMuted / ground : 4,359965479387139   (svetla)   5,94 (tamna)
     *   inkMuted / fill   : 4,014258257780754   (svetla)   4,81 (tamna)
     *   warning  / surface: 3,611752903947211   (svetla)   8,48 (tamna)
     *   success  / fill   : 4,184348841952418   (svetla)   7,78 (tamna)
     *   warning  / fill   : 2,9989738277199414  (svetla)   7,51 (tamna)
     *   warning  / ground : 3,257244931140301   (svetla)   9,27 (tamna)
     *
     * Sesti par je dodat u Fazi 6d-2, Task 2: `BoxStyle.RULE` kutije u
     * lekcijama (`LessonRenderer.colorFor`) crtaju `DS.warning` tekst nad
     * `DS.ground`. Ista klasa kao `warning/surface` iznad — ne popravlja se
     * ovde jer bi znacilo razlaz sa iOS paletom; tamna varijanta (9,27) ide u
     * `lessonBoxStylesMeetAA` jer prolazi.
     *
     * Pragovi ispod nose PUNU preciznost stvarno izmerenu OVIM testom na JVM-u
     * (ne zaokruzeno „4,36", i ne double-precision racun izveden nezavisno u
     * Python-u — `Color.red/green/blue` su `Float`, pa `toDouble()` nasledjuje
     * float32 zaokruzivanje pre ovog racuna; nezavisan double-precision racun
     * (bez tog koraka) daje 4.359965543674367 — vidljivo drugaciju vrednost na
     * 8. decimali). Puna preciznost ne ostavlja marginu za procenu — prag je
     * bukvalno izmerena vrednost.
     *
     * Prag se NE spusta da bi test prosao. Umesto toga se tvrdi da se stanje ne
     * POGORSAVA: ako neko promeni token i obori kontrast ispod izmerenog, test
     * pada i tera na razgovor. Popravka bi znacila razlaz sa iOS paletom, pa je
     * svesno odlozena i zapisana u „Poznata ogranicenja".
     */
    @Test
    fun knownSubAAPairsDoNotGetWorse() {
        // Task 2 dodaje jos potrosaca za `inkMuted/ground`: "Lekcija nije dostupna."
        // (`LessonDetailView`), potpis citata i sitni tekstovi ispod table
        // (`LQuote`, `LPieceRow` kolicina, `LStaticBoard` natpis i poruka o
        // interaktivnoj tabli u `LessonRenderer`). Isti vec-poznat granicni slucaj,
        // ne nov par.
        check("svetla inkMuted/ground", LightColors, { it.inkMuted }, { it.ground }, 4.359965479387139)
        check("svetla inkMuted/fill", LightColors, { it.inkMuted }, { it.fill }, 4.014258257780754)
        check("svetla warning/surface", LightColors, { it.warning }, { it.surface }, 3.611752903947211)
        // `warning/fill` DANAS nema nijedno pozivno mesto: statisticka kartica u
        // Podesavanjima je presla sa `fill` na `surface` bas zato sto je nad `fill`
        // merila `warning` 3,00 i `success` 4,18 (oba ispod AA za 17sp Bold, koji
        // po WCAG-u NIJE „large text"). Pinovan je svejedno, da buduci pozivalac
        // koji ipak stavi semanticku boju nad `fill` ne moze to da pogorsa u tisini.
        //
        // `success/fill` VISE NEMA taj status — Faza 8, Task 3 mu je dala pravog
        // potrosaca (vidi doc-komentar iznad testa i
        // `puzzleSolvedCheckmarkIsReadableOnDateRow`), samo kao IKONU (3:1), ne
        // kao 17sp Bold tekst (4,5) — ova pinovana vrednost je i dalje tacna i i
        // dalje dovoljna za tu upotrebu.
        check("svetla success/fill", LightColors, { it.success }, { it.fill }, 4.184348841952418)
        check("svetla warning/fill", LightColors, { it.warning }, { it.fill }, 2.9989738277199414)
        // Faza 8, Task 3 dodaje pravog potrosaca i za `warning/ground`: tri
        // `Icons.Default.Warning` (bivsi ⚠️) u NETWORK_ERROR ekranima
        // (`PuzzleView.kt` x2, `StepPracticeView.kt`) sede direktno na `DS.ground`
        // (isti `Box` iz `MainActivity.kt` iza sva tri taba). Ikona je graficki
        // objekat (WCAG prag 3:1), pa 3,26 ovde dostize potrebno iako ne dostize
        // AA (4,5) za tekst — ista logika kao `success/fill` iznad.
        check("svetla warning/ground", LightColors, { it.warning }, { it.ground }, 3.257244931140301)
    }

    /**
     * Faza 8, Task 3: kvačica rešenosti u traci datuma (`PuzzleView.kt`,
     * `DateNavigationRow`). Emoji (✅) je zamenjen `Icons.Default.CheckCircle`,
     * tint `DS.success`, na podlozi `DS.fill` (cela `DateNavigationRow` ima
     * `.background(DS.fill)`).
     *
     * `success/fill` u SVETLOJ temi (4,18) je vec pinovan u
     * `knownSubAAPairsDoNotGetWorse`, ali kao granica za TEKST (prag 4,5, koji
     * ta vrednost ne dostize). Ikona je graficki objekat — WCAG-ov prag je
     * 3:1 (SC 1.4.11) — pa 4,18 ovde vise nego dovoljno. TAMNA tema (7,78) do
     * ovog taska nije imala NIJEDNU tvrdnju ni u jednom testu; test ispod je
     * prvi koji je meri.
     */
    @Test
    fun puzzleSolvedCheckmarkIsReadableOnDateRow() {
        checkPair("svetla success/fill (kvačica rešenosti)", LightColors.success, LightColors.fill, 3.0)
        checkPair("tamna success/fill (kvačica rešenosti)", DarkColors.success, DarkColors.fill, 3.0)
    }

    /**
     * `DS.fill` je podloga redova u Podesavanjima, statusnih traka, akcionih
     * dugmadi, istorije poteza i trake datuma — a do ovog talasa ispravki
     * `ContrastTest` nije imao NIJEDAN par nad njom. Proveravali su se samo
     * parovi nad `surface`, `ground` i `accent`.
     *
     * Cena te rupe je naplacena na `Switch`-u: M3 podrazumevane vrednosti vode
     * iskljucen palac i ivicu na `Outline` (= `DS.line`), a traku na
     * `SurfaceContainerHighest` (= `DS.fill`), pa je ceo iskljucen prekidac bio
     * na **1,067** prema sopstvenoj traci i prema redu ispod. I komentar u
     * `Theme.kt` i pregled Task-a 6 proverili su da su uloge MAPIRANE, i to
     * tacno; nijedno nije proverilo da su mapirane vrednosti MEDJUSOBNO
     * razlicite. Analiziran je token, ne par.
     */
    @Test
    fun textOnFillMeetsAA() {
        // `ink/fill` (Task 3) nosi i „Reset"/„Ponovo" dugme u sve tri kartice vezbi
        // i neodabranu figuru u `PieceExplorer`-ovom pikeru (`LearnView.kt`) -- isti
        // par kao dugme "Nazad" u `LessonDetailView.kt` (vidi komentar tamo).
        // `accent/fill` (Faza 8, Task 1) nosi i zvezdicu (`⭐` -> `Icons.Default.Star`)
        // uz naziv izabranog preseta u `ChessClockView.kt` kad je red preseta obelezen
        // kao izabran (`.background(if (isSelected) DS.fill else Color.Transparent)`).
        // `ink/fill` (Faza 8, Task 2) nosi i cetiri ikone u `ActionsRow`-u
        // (`MainActivity.kt`, ekran Igra) u omogucenom stanju: `Icons.Default.Computer`/
        // `People` (Racunar/Prijatelj), `Icons.AutoMirrored.Filled.Undo` (Vrati),
        // `Icons.Default.Share` (Podeli) i `Icons.Default.Refresh` (Reset) -- svi citaju
        // `LocalContentColor` iz `ButtonDefaults.buttonColors(containerColor = DS.fill,
        // contentColor = DS.ink)`, isti mehanizam kao Text pored njih.
        // `ink/fill` (Faza 8, Task 3) nosi i `Icons.Default.Lightbulb` ("Prikaži
        // rešenje") i `Icons.Default.Refresh` ("Pokušaj ponovo") u
        // `PuzzleView.kt` -- isti mehanizam (bez eksplicitnog `tint`, cita
        // `LocalContentColor` iz istog `ButtonDefaults.buttonColors`). Isti
        // mehanizam nose i strelice `ChevronLeft`/`ChevronRight` u
        // `DateNavigationRow` (`PuzzleView.kt`) u omogucenom stanju. Isti par
        // nosi i `Icons.Default.Computer` u `OpponentCard`-u (`StepGameView.kt`)
        // -- dekorativna (`contentDescription = null`, vidi kod), ali i dalje
        // STVARNO nacrtana na ekranu pa i dalje treba kontrast; eksplicitan
        // `tint = DS.ink` na `Row`-u sa `.background(DS.fill)`.
        // `ink/fill` (Faza 8, Task 4) nosi i `Icons.Default.Public` u dugmadima
        // "Stockfish"/"Lichess" (`SettingsView.kt`, sekcija „O aplikaciji") --
        // dekorativna (tekst dugmeta je ceo vidljiv sadrzaj), bez eksplicitnog
        // `tint`, isti mehanizam kao ostali primeri iznad.
        for ((label, p) in listOf("svetla" to LightColors, "tamna" to DarkColors)) {
            check("$label ink/fill", p, { it.ink }, { it.fill }, 4.5)
            check("$label accent/fill", p, { it.accent }, { it.fill }, 4.5)
            check("$label danger/fill", p, { it.danger }, { it.fill }, 4.5)
        }
    }

    /**
     * Ne-tekstualni par nad `DS.fill`, WCAG prag 3:1.
     *
     * Tvrdnja ima DVA smera, i oba su nosiva:
     *  - `inkMuted` nad `fill` PRELAZI prag, pa je to jedini neutralan token
     *    kojim se nad `fill` sme povuci ivica ili nacrtati kontrola (palac
     *    prekidaca, ivica prekidaca);
     *  - `line` nad `fill` NE prelazi prag i nikad nece, jer su to dve susedne
     *    vrednosti iste palete (`#DFE3EC` / `#E7EAF1`, odnosno `#232C46` /
     *    `#1E2740`). Zato se `DS.line` ne sme koristiti kao granica NAD `DS.fill`
     *    — zbog toga je Task 4 ove faze uklonio ivicu sa cetiri kartice u
     *    Podesavanjima (odvajanje sada nosi `surface`/`ground` razlika, vidi
     *    `settingsCardsSeparateFromGround`). Raniji oblik ovog komentara je jos pisao
     *    da to „ceka 6d-2"; ovo JESTE 6d-2 i vise ne ceka.
     *
     * Negativna polovina je namerno tvrdnja o STANJU, ne prag koji se popravlja:
     * popravka bi znacila promenu vrednosti `line` ili `fill`, dakle razlaz sa
     * iOS paletom. Ako se ta dva tokena ikad razdvoje, ovaj test pada i tera na
     * razgovor — bas kao `plainWhiteWouldFailOnTheDarkAccent` iznad.
     *
     * Faza 8, Task 2 dodaje stvarnog potrosaca za pozitivnu polovinu (`inkMuted`
     * nad `fill`, 3:1): `Icons.AutoMirrored.Filled.Undo` i `Icons.Default.Share`
     * u `ActionsRow`-u (`MainActivity.kt`) su ikone unutar dugmadi cije
     * `disabledContentColor = DS.inkMuted` dok `disabledContainerColor` ostaje
     * `DS.fill` -- isti par, prvi put ne-tekstualan potrosac (ikona, ne palac
     * prekidaca ili ivica).
     *
     * Faza 8, Task 3 dodaje jos jednog: `Icons.Default.ChevronRight` ("Sledeći
     * dan") u `DateNavigationRow` (`PuzzleView.kt`) kad `!canGoNext`
     * (`disabledContentColor = DS.inkMuted`, `disabledContainerColor` ostaje
     * transparentna nad `DS.fill` podlogom reda).
     */
    @Test
    fun nonTextPairsOverFillAreDistinguishable() {
        for ((label, p) in listOf("svetla" to LightColors, "tamna" to DarkColors)) {
            check("$label inkMuted/fill (ivica i palac prekidaca)", p, { it.inkMuted }, { it.fill }, 3.0)
            val r = contrast(p.line, p.fill)
            assertTrue(
                "$label line/fill daje %.3f — da je >= 3, `DS.line` bi smeo da omedji `DS.fill`".format(r),
                r < 3.0
            )
        }
    }


    /**
     * `BoxStyle.RULE` (zlatno pravilo) i `BoxStyle.WARNING` (upozorenje) u
     * lekcijama nose znacenje (CLAUDE.md, Faza 6d-2 brief: „Dve stvari koje
     * nose znacenje i ne idu na akcent") — idu na `DS.warning`/`DS.danger`,
     * ne na akcent lekcije. Test ispod meri protiv `DS.ground`, ali stvarna
     * podloga NA EKRANU je jedan stepen gora: kutija (`LBox`/`LBullet` u
     * `LearnView.kt`) crta sopstvenu providnu tintu (`color.copy(alpha =
     * 0.1f)`) preko `DS.ground` (`LessonDetailView`-ov skrol nema Card ni
     * Surface iza sebe — sedi direktno na `DS.ground` iz `MainActivity`-jevog
     * Box-a, linija ~641), i TAJ kompozit je stvarni piksel iza teksta.
     *
     * IZMERENO (ne procenjeno), isti float32-preko-Double put racuna kao
     * `luminance()`/`contrast()` iznad, kompozit = `boja @10%` preko `ground`:
     *
     *   warning na warning@10% tintu: 2,941624 (svetla)   7,848961 (tamna)
     *   danger  na danger@10% tintu : 5,026419 (svetla)   6,371957 (tamna)
     *
     * Sve cetiri su 10-15% GORE od merenja protiv golog `ground`-a ispod
     * (svetla warning 3,26 -> 2,94, svetla danger 5,89 -> 5,03, tamna warning
     * 9,27 -> 7,85, tamna danger 7,32 -> 6,37) — providnost dosledno pomera
     * kompozit KA semantickoj boji, nikad od nje. Test ispod je zato DONJA
     * GRANICA merenja, ne tacna vrednost stvarnog piksela; gde tacna vrednost
     * i dalje prelazi 4,5 (sve osim svetle `warning`, vec poznat slucaj ispod)
     * marza je dovoljna da razlika ne menja ishod.
     *
     * `danger/surface` (oba testa u `textOnBackgroundsMeetsAA`) NIJE isti par
     * kao `danger/ground` ovde — ne preklapa se, samo je slucajno vec
     * pokriven drugom podlogom pre ovog taska.
     *
     * `warning/ground` u SVETLOJ temi pada ispod 4,5 (3,26 protiv golog
     * `ground`-a, 2,94 protiv stvarnog kompozita) — ista klasa ogranicenja kao
     * `warning/surface` (vec u `knownSubAAPairsDoNotGetWorse` ispod), pa ide
     * tamo umesto ovde. Prag se ne pomera; vidi „Poznata ogranicenja" u
     * CLAUDE.md.
     */
    @Test
    fun lessonBoxStylesMeetAA() {
        // Faza 8, Task 3 dodaje jos potrosaca za `tamna warning/ground`: isti
        // tri `Icons.Default.Warning` iz `knownSubAAPairsDoNotGetWorse` (svetla
        // varijanta) imaju istu podlogu u OBE teme — samo je tamna vrednost
        // (9,27) vec bila iznad AA praga, pa je ovaj test (ne onaj) njeno mesto.
        check("tamna warning/ground", DarkColors, { it.warning }, { it.ground }, 4.5)
        // `danger/ground` (svetla i tamna) dobija stvarnog potrosaca u Fazi 8,
        // Task 3: `Icons.Default.Flag` ("Predaj partiju") u `StepGameView.kt`
        // (`containerColor = Transparent`, `contentColor = DS.danger`, na
        // Column-u bez sopstvene pozadine -- nasledjuje `DS.ground` iz
        // `MainActivity.kt`-jevog Box-a).
        check("svetla danger/ground", LightColors, { it.danger }, { it.ground }, 4.5)
        check("tamna danger/ground", DarkColors, { it.danger }, { it.ground }, 4.5)

        // TELO kutije, dodato u talasu ispravki pred spajanje. Tvrdnje iznad mere samo
        // NASLOV (obojen bojom stila); telo nosi ceo tekst i nijedan test ga do sada
        // nije merio — a bas ono je bilo pogresno: prvi prelaz je zateceni `White@0,85`
        // preslikao na `inkMuted`, pa je u svetloj temi palo na 3,72–3,94 nad sopstvenim
        // tintom, ispod AA. Sada je `DS.ink` (`LearnView.LBox`), sto nad sva tri tinta
        // daje 13,40–14,19 u svetloj i 13,67–14,06 u tamnoj temi.
        //
        // Ovde se meri STVARAN kompozit (`boja@10%` preko `ground`), ne priblizenje nad
        // golim `ground`-om — kutija svoju podlogu sama crta.
        for ((label, p) in listOf("svetla" to LightColors, "tamna" to DarkColors)) {
            for ((styleName, styleColor) in listOf(
                "INFO/null" to p.accent, "RULE" to p.warning, "WARNING" to p.danger
            )) {
                checkPair("$label telo kutije ($styleName): ink na ${styleName}@10%% tintu",
                    p.ink, over(styleColor, 0.1f, p.ground), 4.5)
            }
        }
    }

    /**
     * Kontrolna traka sata, popravka regresije nadjene u finalnom pregledu Faze 6d-2.
     *
     * Traka stoji IZMEDJU dve polovine sata koje su namerno fiksne (prate stranu u igri,
     * ne sistemsku temu). Prvi prelaz ove faze poslao je njenu podlogu na `DS.surface` a
     * polovine ostavio kakve jesu — svaki clan para „uredno" obradjen, par nikad
     * izmeren. U svetloj temi je `DS.surface` bukvalno `#FFFFFF`, ista boja kao bela
     * polovina ispod: sav je pao sa **14,63 na 1,000**. To nije kozmetika — traka nije
     * `clickable`, pa korisnik koji cilja vrh svoje polovine pogadja inertnu traku.
     *
     * Zato je podloga vracena na fiksnu (`ClockBarBackground`), a CEO sadrzaj trake cita
     * `DarkColors`, ne `DS.*`: fiksna povrsina dobija fiksne clanove, pa je par merljiv i
     * isti u obe teme.
     *
     * Sav prema CRNOJ polovini iznad trake ostaje nevidljiv (1,281 aktivna / 1,050 mirna)
     * i to je tvrdnja o STANJU, ne prag: bilo je tako i pre ove grane, i nijedna boja ne
     * prelazi 3:1 istovremeno prema `#FFFFFF` i prema `#121212` a da ostane u registru
     * sata. Upisano u „Poznata ogranicenja" u `CLAUDE.md`.
     */
    @Test
    fun clockControlBarIsReadableOverFixedHalves() {
        val bar = ClockBarBackground
        // Sav prema beloj polovini: WCAG ne-tekstualni prag 3:1. Izmereno 14,629
        // (aktivna), 11,652 (mirna, `#E5E5EA`), 11,205 (istek, `#FADAD8`).
        checkPair("sav traka/bela aktivna polovina", bar, Color(0xFFFFFFFF), 3.0)
        checkPair("sav traka/bela mirna polovina", bar, Color(0xFFE5E5EA), 3.0)
        checkPair("sav traka/bela polovina u isteku", bar, Color(0xFFFADAD8), 3.0)

        // Sadrzaj NA traci. `DS.ink` bi ovde u svetloj temi dao 1,19 — zato `DarkColors`.
        // Faza 8, Task 1: ovaj par (`DarkColors.ink`/`bar`) je i tint `ℹ️` ->
        // `Icons.Default.Info` dugmeta (sedi direktno na traci, bez kruga iza sebe).
        checkPair("DarkColors.ink na traci", DarkColors.ink, bar, 4.5)
        checkPair("akcent trake na traci", ClockBarAccent, bar, 4.5)
        // Cip vremenske kontrole crta akcent na SOPSTVENOM tintu preko trake. Na @15%
        // je to 4,339 — ispod AA; na @12% (iOS vrednost) 4,583.
        // Faza 8, Task 1: isti par je i tint `⏱️` -> `Icons.Default.Timer` ikone u
        // istom cipu (crta se u istoj boji kao tekst do sebe).
        checkPair("akcent trake na sopstvenom @12%% cipu",
            ClockBarAccent, over(ClockBarAccent, 0.12f, bar), 4.5)

        // Negativna polovina, kao `plainWhiteWouldFailOnTheDarkAccent`: dokaz da
        // `DarkColors` ovde NIJE stvar ukusa nego jedina vrednost koja radi.
        val svetliAkcentNaTraci = contrast(LightColors.accent, bar)
        assertTrue(
            "svetli akcent na traci daje %.3f — da je >= 4.5, traka bi smela `DS.accent`"
                .format(svetliAkcentNaTraci),
            svetliAkcentNaTraci < 4.5
        )
    }

    /**
     * Faza 8, Task 1: emoji -> Material ikone na sahovskom satu. Dva dugmeta dele
     * ISTI krug (`ClockBarWell` — belo@12% preko `ClockBarBackground`) i ISTI
     * omoguceni tint (`DarkColors.ink`): dugme „Zatvori" (uvek omoguceno) i dugme
     * za resetovanje sata u omogucenom stanju. Fiksan par, isti u obe teme jer ni
     * jedan clan nije adaptivan token.
     *
     * Info dugme (`ℹ️` -> `Icons.Default.Info`) sedi direktno na traci, bez kruga
     * iza sebe — ISTI par kao „DarkColors.ink na traci" u
     * `clockControlBarIsReadableOverFixedHalves` iznad (12,929), pa ne dobija
     * sopstvenu tvrdnju ovde; ovaj komentar ga imenuje kao drugog potrosaca.
     */
    @Test
    fun clockRoundButtonIconsAreReadable() {
        val well = over(Color.White, 0.12f, ClockBarBackground)
        checkPair("DarkColors.ink na dugmetu (Zatvori / Resetuj, omoguceno)",
            DarkColors.ink, well, 4.5)
    }

    /**
     * Faza 8, Task 1: onemoguceno stanje dugmeta za resetovanje sata. Vidi
     * obrazlozenje `tint` naspram `Modifier.alpha` u komentaru uz samo dugme
     * (`ChessClockView.kt`, `ControlBar`). Prag je WCAG-ov ne-tekstualni 3:1 —
     * onemoguceni kontroli tehnicki nije potreban, ali je ovde ipak dostignut, za
     * razliku od odbacene `alpha(0.38f)` alternative (izmereno 2,75:1).
     */
    @Test
    fun clockResetIconDisabledStateIsReadable() {
        val well = over(Color.White, 0.12f, ClockBarBackground)
        checkPair("DarkColors.inkMuted na dugmetu (Resetuj, onemoguceno)",
            DarkColors.inkMuted, well, 3.0)
    }

    /**
     * Faza 8, Task 1: dugme pusti/pauziraj (`▶️`/`⏸️` -> `Icons.Default.PlayArrow`/
     * `Pause`). Krug je `ClockBarAccent` (= `DarkColors.accent`, FIKSNO — ne
     * `DS.accent`, koji bi ovde bio pogresan jer krug ne prati temu). Tint je zato
     * `DarkColors.onAccent`, ne adaptivni `DS.onAccent`: ta dva se ovde slucajno
     * poklapaju brojcano (oba `#161A22`), ali `DS.onAccent` bi bio POGRESAN IZBOR —
     * prati `DS.accent`, koji se menja sa temom, dok se krug ovde NE menja.
     */
    @Test
    fun clockPlayPauseIconIsReadableOnItsAccentCircle() {
        checkPair("DarkColors.onAccent na ClockBarAccent (pusti/pauziraj)",
            DarkColors.onAccent, ClockBarAccent, 4.5)
        // Negativna polovina, isti obrazac kao `plainWhiteWouldFailOnTheDarkAccent`:
        // dokaz da `DarkColors.onAccent` ovde NIJE proizvoljan izbor.
        val bela = contrast(Color.White, ClockBarAccent)
        assertTrue(
            "bela na ClockBarAccent daje %.2f — da je >= 4.5, DarkColors.onAccent ne bi bio potreban"
                .format(bela),
            bela < 4.5
        )
    }

    @Test
    fun everyAdaptiveTokenActuallyDiffersBetweenThemes() {
        val pairs = listOf<Pair<String, (ChesskoColors) -> Color>>(
            "accent" to { it.accent }, "ground" to { it.ground }, "surface" to { it.surface },
            "navBar" to { it.navBar }, "fill" to { it.fill }, "line" to { it.line },
            "ink" to { it.ink }, "inkMuted" to { it.inkMuted },
            "success" to { it.success }, "warning" to { it.warning },
            "danger" to { it.danger }, "onAccent" to { it.onAccent }
        )
        for ((name, get) in pairs) {
            assertTrue("$name je isti u obe teme — ili nije adaptivan, ili je greska",
                get(LightColors) != get(DarkColors))
        }
        // `scrim`, `onScrim` i `inkFixed` su NAMERNO isti u obe teme.
        assertTrue("scrim mora biti isti", LightColors.scrim == DarkColors.scrim)
        assertTrue("onScrim mora biti isti", LightColors.onScrim == DarkColors.onScrim)
        assertTrue("inkFixed mora biti isti", LightColors.inkFixed == DarkColors.inkFixed)
    }

    /**
     * Faza 6d-2, Task 4, Deo A. Cetiri kartice u Podesavanjima (jezik, statistika,
     * tezina, o aplikaciji) su izgubile `.border(1.dp, DS.line, ...)` — nijedan par u
     * paleti ne daje vidljivu ivicu (`line/fill` 1,067, `line/surface` 1,285, oba
     * daleko ispod WCAG-ovog ne-tekstualnog praga 3:1). Odvajanje od `DS.ground`
     * sada nosi ISKLJUCIVO `surface`/`ground` razlika u boji — isto sto iOS dobija
     * besplatno od nativne grouped liste.
     *
     * Prag je namerno nizak (dekorativno odvajanje, ne granica kontrole nad kojom
     * korisnik mora da vidi ivicu da bi bio funkcionalan) — donja granica je puna
     * preciznost stvarno izmerene vrednosti (isti float32-preko-Double put racuna
     * kao ostatak ove datoteke), ne WCAG prag. Ako `surface` i `ground` ikad postanu
     * ista boja, kartice nestaju bez ijedne druge posledice, i ovaj test to hvata.
     */
    @Test
    fun settingsCardsSeparateFromGround() {
        check("svetla surface/ground", LightColors, { it.surface }, { it.ground }, 1.1088367563082842)
        check("tamna surface/ground", DarkColors, { it.surface }, { it.ground }, 1.0942153907723773)
    }

    /**
     * Dopuna otkrivena u pregledu Task-a 3: `DS.accent` kao TEKST nad `DS.surface` i
     * nad `DS.ground` nije imao nijednu tvrdnju, iako pozivnih mesta ima cetiri —
     * `LessonDetailView.kt:154` (broj lekcije, direktno na `DS.ground`, skrol bez
     * Card/Surface iza sebe), `LessonRenderer.kt:150`/`:175` (`LPieceRow`/
     * `LPieceValueTable`, isto direktno na `DS.ground`) i `LearnView.kt:482`
     * (naslov info panela u `PieceExplorer`-u, na `DS.surface`).
     *
     * Bezbedno je izmereno pre ove tvrdnje (nije popravka): 8,53/6,43 (surface),
     * 7,69/7,03 (ground) — sve daleko iznad AA praga 4,5. Test svejedno postoji da
     * moze da PADNE ako se `accent` ikad promeni.
     */
    @Test
    fun accentTextOnSurfaceAndGroundMeetsAA() {
        // `accent/surface` (Faza 8, Task 1) nosi i istu zvezdicu iz `textOnFillMeetsAA`
        // iznad kad red preseta NIJE izabran (`Color.Transparent` nad dijaloga
        // `Surface`-om koji je `DS.surface`).
        for ((label, p) in listOf("svetla" to LightColors, "tamna" to DarkColors)) {
            check("$label accent/surface", p, { it.accent }, { it.surface }, 4.5)
            check("$label accent/ground", p, { it.accent }, { it.ground }, 4.5)
        }
    }
}
