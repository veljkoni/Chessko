package com.veljkoni.chessko

import androidx.compose.ui.graphics.Color
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

    @Test
    fun textOnBackgroundsMeetsAA() {
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
     * PET PAROVA JE ISPOD AA U SVETLOJ TEMI. Prva tri su NASLEDJENA iz spec
     * tabele (iOS ima iste vrednosti); poslednja dva su dodata u talasu ispravki
     * pred spajanje i danas nemaju pozivno mesto (vidi komentar uz njih).
     *
     *   inkMuted / ground : 4,359965479387139   (svetla)   5,94 (tamna)
     *   inkMuted / fill   : 4,014258257780754   (svetla)   4,81 (tamna)
     *   warning  / surface: 3,611752903947211   (svetla)   8,48 (tamna)
     *   success  / fill   : 4,184348841952418   (svetla)   7,78 (tamna)
     *   warning  / fill   : 2,9989738277199414  (svetla)   7,51 (tamna)
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
        check("svetla inkMuted/ground", LightColors, { it.inkMuted }, { it.ground }, 4.359965479387139)
        check("svetla inkMuted/fill", LightColors, { it.inkMuted }, { it.fill }, 4.014258257780754)
        check("svetla warning/surface", LightColors, { it.warning }, { it.surface }, 3.611752903947211)
        // Dva para koja DANAS nemaju nijedno pozivno mesto: statisticka kartica u
        // Podesavanjima je presla sa `fill` na `surface` bas zato sto je nad `fill`
        // merila `warning` 3,00 i `success` 4,18 (oba ispod AA za 17sp Bold, koji
        // po WCAG-u NIJE „large text"). Pinovani su svejedno, da buduci pozivalac
        // koji ipak stavi semanticku boju nad `fill` ne moze to da pogorsa u tisini.
        check("svetla success/fill", LightColors, { it.success }, { it.fill }, 4.184348841952418)
        check("svetla warning/fill", LightColors, { it.warning }, { it.fill }, 2.9989738277199414)
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
     *    — ivica kartica u Podesavanjima to i dalje radi i zato se ne vidi
     *    (upisano u „Poznata ogranicenja", ceka 6d-2).
     *
     * Negativna polovina je namerno tvrdnja o STANJU, ne prag koji se popravlja:
     * popravka bi znacila promenu vrednosti `line` ili `fill`, dakle razlaz sa
     * iOS paletom. Ako se ta dva tokena ikad razdvoje, ovaj test pada i tera na
     * razgovor — bas kao `plainWhiteWouldFailOnTheDarkAccent` iznad.
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
     * Dijalozi sata (izbor vremenske kontrole, info) stoje na `surface`, ne na `ground`.
     * `ink/surface` i `inkMuted/surface` su vec pokriveni generickim
     * `textOnBackgroundsMeetsAA`, ali ovaj test postoji da imenuje TACNO ta tri nova
     * poziva koja Task 1 uvodi (naslov dijaloga, opis vremenske kontrole, podnaslov
     * preseta) — da promena tih poziva na neki treci token bude uhvacena i po imenu,
     * ne samo posredno preko opsteg testa.
     */
    @Test
    fun clockDialogTextMeetsAA() {
        check("ink/surface", LightColors, { it.ink }, { it.surface }, 4.5)
        check("ink/surface", DarkColors, { it.ink }, { it.surface }, 4.5)
        check("inkMuted/surface", LightColors, { it.inkMuted }, { it.surface }, 4.5)
        check("inkMuted/surface", DarkColors, { it.inkMuted }, { it.surface }, 4.5)
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
}
