# Faza 9 — lekcijske ikone i prečica prevlačenjem

> **Za agentske izvršioce:** OBAVEZNA POD-VEŠTINA: koristi
> superpowers:subagent-driven-development za izvođenje ovog plana task po task.

**Cilj:** Zatvoriti dve stavke odložene u fazama 6d i 8 — `lessonIcon()` mapu (49 simbola) i
uspravnu prečicu prevlačenjem koja ćuti tamo gde je tabla u vertikalnom skrolu.

**Tehnologije:** Kotlin, Jetpack Compose. **Bez ijedne nove zavisnosti** —
`material-icons-extended` je već tu.

---

## Globalna ograničenja

- **Ni jedna nova Gradle zavisnost.**
- **`Chessko/` (iOS) se čita radi poređenja, nikad ne menja.** Isto važi za
  `Chessko/Content/lessons/` — **lekcijski JSON se NE dira**; on je deljen bajt-identično sa iOS-om
  (`diff -r` je dokaz), i menja se samo na jednom mestu za obe platforme.
- Svaki `loc()` ključ literal unutar `loc()`, na svih 8 jezika, **nijedan `%lld`**.
  **U komentarima piši ime ključa BEZ `loc(` ispred** — `LocTest` čita izvor kao tekst i videće
  živog pozivaoca (to je već jednom oborilo build).
- Sav UI na `DS.*` tokenima — **nijedna nova zakučana boja**. Par se meri, ne pretpostavlja.
- `BoardView.kt` / `BoardTheme.kt` ostaju **bez ijednog `DS.`**.

---

## Izmereno pre pisanja plana

| | |
|---|---|
| unosa u `SYMBOL_TO_EMOJI` | **49** |
| simbola koje lekcije stvarno traže | **49** |
| mrtvih unosa | **0** |
| traženih a nemapiranih | **0** |
| pozivnih mesta prečice | 10 |

Mapa je **potpuna i uredna** — ovo nije čišćenje zapuštenog koda nego prevođenje ispravnog.

### Sedam simbola koji NEMAJU Material par, i zašto

Provereno otvaranjem lekcijskog JSON-a, ne po imenu simbola:

| simbol | emoji | šta lekcija stvarno kaže |
|---|---|---|
| `l.joystick.fill` | 🐴 | „Kreće se u obliku slova L" — **potez skakača** |
| `tuningfork` | 🍴 | „Jedna figura napadne dve protivničke istovremeno" — **taktika viljuške** |
| `dot.square.fill` | ⬛ | „Kreće se samo jedno polje u bilo kom pravcu" — **potez kralja** |
| `crown.fill` | 👑 | „Slova figura" — notacija |
| `rectangle.portrait.fill` | 🏰 | top |
| `rhombus.fill` | 📐 | lovac |
| `square.grid.3x3.fill` | ♟️ | pešak |

**Emoji ovde nije lenj izbor nego nosi značenje.** 🐴 za skakača i 🍴 za viljušku su semantički
tačni; Material nema ikonu ni za jednu šahovsku figuru ni za ijednu taktiku. Zamena generičkim
glifom bi **oduzela značenje**, ne dodala doslednost.

**Ruling: tih sedam ostaje emoji**, uz zapisan razlog. Aplikacija ima i `ChessPiece.symbol`
(♙♘♗♖♕♔) — ako izvršilac proceni da je Unicode figura bolja od emoji za neku od njih, **sme da
predloži**, ali neka to bude merena odluka, ne automatizam.

---

## Vodeće pravilo, plaćeno trinaest puta u Fazi 8

> Ikona uz **vidljiv tekst istog značenja** je dekorativna (`contentDescription = null`);
> ikona koja stoji **sama** traži opis. „Uz" znači **stvarno uz**.

Lekcijske ikone stoje uz naslov bloka koji im daje značenje — pa su **skoro sve dekorativne.**
Za svaku reci koje je od to dvoje.

Uz to, iz iste faze: **kad pravilo zameni tabelu usred faze, pometeš FAJLOVE kojih se stara tabela
ticala, ne prijavljenu instancu.**

---

## Ruling: emulator se diže JEDNOM, u Task-u 4

U Fazi 8 je jedan prolaz ostao upaljen **četiri sata** posle pada agenta. Zato: taskovi 1–3 se
dokazuju `assembleDebug`-om i testovima; emulator ide samo u Task 4, koji ga i stvarno traži
(prečica se ne može proveriti bez gesta).

`-gpu host`, **NIKAD `-gpu off`**. Gašenje odmah: `adb emu kill`, `./gradlew --stop`,
`pgrep -f qemu-system` prazno. **Ako te bilo šta prekine — prvo ubij emulator.**

---

## Polazno stanje

JVM **105**, instrumentisani **52**, oba 0 padova. Grana polazi od `main` = `a3a7861`.

---

## Task 0: Tip glifa — pre ijednog prevedenog simbola

**Fajlovi:** `ui/LessonRenderer.kt`, `ui/LearnView.kt`, `ui/LessonDetailView.kt`

> **Ovaj task je dodat posle pre-flight provere.** Prvo izdanje plana je reklo
> „`SYMBOL_TO_EMOJI` postaje mapa u `ImageVector`, fajl: `LessonRenderer.kt`". Oboje je bilo
> pogrešno, i to na način koji bi se video tek pri kompajliranju.

Izmereno otvaranjem koda:

- **`lessonIcon()` ima 8 pozivnih mesta u 2 fajla** (`LessonRenderer.kt` ×7,
  `LessonDetailView.kt:168`), a rezultat ulazi u **šest potpisa** u `LearnView.kt`
  (`LBox`, `LBullet`, `LSectionHeader` i tri kartice vežbi — `:319`, `:362`, `:379`, `:676`, `:792`,
  plus polje `icon: String` na `:72`).
- **Povratni tip NE SME biti `ImageVector`**, jer Task 3 ostavlja sedam simbola kao emoji. Mapa u
  `ImageVector` i emoji ne mogu da postoje u istom `Map<String, ImageVector>`.

- [ ] **Korak 1: Uvedi tip koji nosi oba slučaja**

```kotlin
internal sealed interface LessonGlyph {
    @JvmInline value class Icon(val vector: ImageVector) : LessonGlyph
    @JvmInline value class Emoji(val text: String) : LessonGlyph
    /** Nepoznat SF simbol — mora da VIKNE, ne da ostavi prazninu. */
    @JvmInline value class Unknown(val symbol: String) : LessonGlyph
}
```
Prilagodi oblik ako ti Kotlin ne dozvoli `value class` u `sealed interface` — **ali zadrži tri
slučaja**; treći nije ukras (vidi Korak 3).

- [ ] **Korak 2: Jedno mesto koje glif crta**

Napravi `@Composable fun LessonGlyphView(glyph, tint, size)` i **provuci svih šest potpisa kroz
njega**, umesto da svaki zna kako se glif crta. Bez toga bi svaki od šest morao da grana na tip.

- [ ] **Korak 3: Fallback mora ostati jednako glasan**

Zatečeni: nepoznat simbol sa tačkom u imenu daje `"•"`, inače sam simbol. Nov fallback ne sme tiho
da se pretvori u prazninu — `Unknown` daje **crveni `Warning` + ime simbola**, isto načelo kao
nepoznat naziv figure u `pieceRow` (`CLAUDE.md`, „Android čita isti JSON").

- [ ] **Korak 4: Mapa ostaje emoji, ali kroz nov tip**

U ovom tasku se **nijedan simbol ne prevodi** — svih 49 i dalje daju `Emoji`. Cilj je da ekran
izgleda **identično** pre i posle, a da tip bude spreman. Tako Task 1 i 2 menjaju **vrednosti**, ne
strukturu.

- [ ] **Korak 5: Dokaz da se ništa nije promenilo**

`assembleDebug` + JVM testovi. I reci u izveštaju kako si se uverio da se prikaz nije promenio bez
emulatora (npr. da su sve grane i dalje `Emoji(...)` sa istim stringom).

- [ ] **Korak 6: Commit**

---

## Task 1: Strelice i oznake (21 simbol)

**Fajlovi:** `ui/LessonRenderer.kt`

Task 0 je već uveo tip i mesto crtanja — ti menjaš **samo vrednosti u mapi**, `Emoji(...)` →
`Icon(...)`.

- [ ] **Korak 1: Prevedi dvadeset jednu strelicu i oznaku**

```
arrow.clockwise, arrow.triangle.2.circlepath  -> Refresh
arrow.forward.circle.fill                     -> ArrowCircleRight
arrow.left.arrow.right                        -> SwapHoriz
arrow.up, arrow.up.circle.fill                -> ArrowUpward
arrow.up.and.down                             -> SwapVert
arrow.up.left.and.arrow.up.right,
  arrow.up.right, arrow.up.right.and.arrow.up.left -> OpenInFull  (ili najblize)
arrow.up.and.down.and.arrow.left.and.right    -> OpenWith
checkmark.circle.fill, checkmark.seal.fill    -> CheckCircle
xmark.circle.fill, xmark.shield.fill          -> Cancel
minus.circle.fill                             -> RemoveCircle
exclamationmark.circle.fill                   -> Error
exclamationmark.triangle.fill                 -> Warning
exclamationmark.2                             -> PriorityHigh
info.circle.fill                              -> Info
circle.fill                                   -> Circle
```
**Nijedno ime ne uzimaj zdravo za gotovo** — ako se ne kompajlira, nađi najbliže i **zapiši šta si
zamenio i zašto**.

- [ ] **Korak 3: Boja i kontrast**

Emoji je nosio sopstvenu boju; ikona je uzima iz tokena. Utvrdi **stvarnu podlogu** ispod naslova
bloka i izmeri par. `ContrastTest` ima 15 testova i pomoćnu `check(name, p, fg, bg, prag)` —
pročitaj potpis. **Nov test samo za NOV par.**

- [ ] **Korak 4: `contentDescription` po pravilu** — ikona uz naslov bloka je dekorativna.

- [ ] **Korak 5: Testovi, build, commit**

---

## Task 2: Mapa — predmeti i pojmovi (21 simbol)

**Fajlovi:** `ui/LessonRenderer.kt`

```
bolt.fill -> Bolt            book.fill, text.book.closed.fill -> MenuBook
chart.line.uptrend.xyaxis -> TrendingUp      eye.fill -> Visibility
flag.checkered -> SportsScore                flag.fill -> Flag
flame.fill -> LocalFireDepartment            globe -> Public
hand.point.up.left.fill -> TouchApp          heart.fill -> Favorite
link -> Link                 person.fill -> Person        person.2.fill -> People
quote.opening -> FormatQuote                 ruler.fill -> Straighten
scalemass.fill -> Scale                      shield.fill -> Shield
square.grid.2x2.fill -> GridView             star.fill -> Star
trophy.fill -> EmojiEvents
```

- [ ] **Korak 1: Prevedi ih** — [ ] **Korak 2: Kontrast** — [ ] **Korak 3: `contentDescription`**
- [ ] **Korak 4: Testovi, build, commit**

---

## Task 3: Sedam šahovskih simbola ostaje emoji — i to se zapisuje

**Fajlovi:** `ui/LessonRenderer.kt`

- [ ] **Korak 1: Ne menjaj ih**

`l.joystick.fill` 🐴, `tuningfork` 🍴, `dot.square.fill` ⬛, `crown.fill` 👑,
`rectangle.portrait.fill` 🏰, `rhombus.fill` 📐, `square.grid.3x3.fill` ♟️.

- [ ] **Korak 2: Napiši ZAŠTO, na mestu**

Mapa posle ovog taska ima dva puta (ikona / emoji). Komentar mora da kaže da to **nije nedovršen
posao** nego merena odluka: Material nema ikonu ni za jednu šahovsku figuru ni za ijednu taktiku,
a 🐴 za potez skakača i 🍴 za viljušku **nose značenje** koje bi generički glif oduzeo.
Navedi i šta lekcija na tom mestu stvarno kaže (citirano iz JSON-a).

- [ ] **Korak 3: Presudi o Unicode figurama**

Aplikacija ima `ChessPiece.symbol` (♙♘♗♖♕♔). Za `rectangle.portrait.fill` (top),
`rhombus.fill` (lovac), `square.grid.3x3.fill` (pešak) i `crown.fill` **razmotri** da li je Unicode
figura bolja od emoji — ona je monohromatska, prima `tint`, i vizuelno pripada šahu.
**Izmeri i presudi**, nemoj automatski.

Pazi: `dot.square.fill` je **potez kralja**, ne kralj; `l.joystick.fill` je **potez skakača**.
Za njih figura možda nije tačan prikaz.

**Jedan od sedam nije samo u telu lekcije — stoji u ZAGLAVLJU.** Izmereno nad svih 36 fajlova
(`Chessko/Content/lessons/*.json`, zbir = 36, dakle iscrpno):

```
bolt.fill             10        flag.checkered          8
square.grid.3x3.fill   8        flag.fill               8
text.book.closed.fill  2
```

`square.grid.3x3.fill` je **doc-level `icon`**, naslovna ikona za **8 od 36 fajlova**
(`LessonDetailView.kt:168`, crta se na 24sp). Posle Task-a 2 ostale četiri naslovne vrednosti su
tintovane Material ikone — ako ovaj ostane emoji, zaglavlje osam lekcija nosi **šareni emoji među
monohromatskim ikonama**, na najvidljivijem mestu ekrana.

To je odluka koju moraš doneti izričito, ne zateći je. Tri puta, svaki sa cenom:
**(a)** ostane emoji svuda — dosledno telu lekcije, nedosledno zaglavlju;
**(b)** ikona svuda (gubi se pešak kao pešak);
**(c)** različito po mestu upotrebe — traži da mapa zna kontekst, što je nov mehanizam.
**Izmeri, presudi i zapiši šta gubiš.** Ako biraš (c), reci zašto cena novog mehanizma vredi.

> Task 1 je u svom izveštaju tvrdio da je `crown.fill` doc-level ikona. **Nije** — Task 2 je to
> proverio nezavisnom pretragom i oborio, pa je isti račun ponovljen i ovde. Ne prepisuj taj
> spisak ni iz jednog izveštaja; gornja tabela je izmerena.

- [ ] **Korak 4: Zabetoniraj ugovor mape testom**

Posle ovog taska mapa je konačna: 42 ikone + 7 emoji. Nov test u **`androidTest`** (ne `test` —
čitanje lekcijskog JSON-a ide kroz `org.json`, čiji JVM stub baca; isti razlog zbog kog
`LessonContentTest` živi tamo).

Test tvrdi **invarijantu koja preživljava prevođenje**, ne vrednosti:

1. svaki `icon` koji se stvarno javlja u `ChesskoAndroid/app/src/main/assets/lessons/*.json`
   razrešava se u `LessonGlyph` koji **nije** `Unknown`;
2. izmišljen simbol (npr. `"nema.me.fill"`) **jeste** `Unknown`.

Tvrdnja nad konkretnim emoji stringovima bi se morala prepisivati u svakom tasku koji menja
vrednosti — ova ne. Druga tvrdnja postoji jer bi bez nje prva prolazila i nad mapom koja sve
proguta.

**Dokaži mutacijom:** izbaci jedan par iz mape i pokaži da test pada.

Test se **izvršava u jedinom prolazu emulatora**, u Task-u 4 — ne diži ga ovde.

- [ ] **Korak 5: Testovi, build, commit**

---

## Task 4: Prečica prevlačenjem — i jedini prolaz emulatora

**Fajlovi:** `ui/BoardView.kt`, `logic/SettingsManager.kt`, `ui/SettingsView.kt` (po potrebi)

**Zatečeno, izmereno u Fazi 7:** uspravna prečica („prevlačenje gore-dole menja stil figura") radi
u pejzažu, a **ćuti** svuda gde je tabla u `verticalScroll` — Igra u portretu, lekcije, koraci Puta.
Uzrok: roditeljski skrol troši pokret na svom touch slop-u (~20 px), a prečici treba 100 px.
Vodoravna prečica (tema table) radi svuda, jer nijedan roditelj ne traži vodoravni pokret.

Prekidač je **podrazumevano uključen**, pa korisnik u portretu ima uključenu funkciju koja ćuti.

- [ ] **Korak 1: Pročitaj zapisano pre nego što bilo šta menjaš**

`CLAUDE.md` nosi izmerenu tabelu i izričitu napomenu: **„vrati staro" NIJE opcija** — staro je
gutalo vertikalni skrol u lekcijama sa dve table, što je bug zbog kog je popravka rađena.

- [ ] **Korak 2: Izaberi rešenje i obrazloži**

Tri puta, presudi **merenjem i obrazloženjem**, ne ukusom:

**(a) Veži prečicu za gest koji se ne sudara** — dugi pritisak pa prevlačenje, ili prevlačenje sa
dva prsta. Zadržava funkciju svuda. Cena: gest koji korisnik mora da otkrije.
**(b) Ugasi `swipeToChangePieceStyle` podrazumevano.** Iskreno prema novom korisniku. Cena:
zatečenim korisnicima u pejzažu funkcija nestaje dok je ručno ne vrate.
**(c) Ukloni uspravnu prečicu i njen prekidač.** Najmanje mrtvog koda. Cena: trajno uklanja nešto
što u pejzažu radi.

**Ne biram umesto tebe** — ali reci šta gubiš, ne samo šta dobijaš.

- [ ] **Korak 3: Prevlačenje figure NE SME da se pokvari**

Faza 7 je ovde već imala bug koji **nijedan od 153 testa nije pokrivao**: `consume()` pozvan pre
`positionChange()` vraća `Offset.Zero`, pa se figura digne ali potez nikad ne odigra. Uhvaćeno samo
na uređaju. **Proveri to izričito.**

- [ ] **Korak 4: JEDAN prolaz emulatora**, obe teme:
  1. **Lekcija sa dve table** — vertikalni skrol radi **i** prevlačenje figure radi
  2. **Igra, portret** — izabrano rešenje radi (ili je prekidač ugašen, po presudi)
  3. **Igra, pejzaž** — vodoravna prečica (tema table) i dalje radi
  4. **Korak Puta** — isto što i lekcija

  Snimci u `.superpowers/sdd/<plan>/screenshots/`, **ne u sesijski `/tmp`**.

  **U istom prolazu pokreni i `connectedDebugAndroidTest`** — Task 3 je dodao instrumentisani
  test mape glifova koji nigde drugde nema gde da se izvrši. Broj čitaj **iz XML-a**
  (`app/build/outputs/androidTest-results/connected/debug/*.xml`), ne iz izlaznog koda:
  `BUILD SUCCESSFUL` ume da znači nula pokrenutih testova. Polazno: **54** — 52 zatecenih plus **dva** koja je Task 3 dodao (test ugovora mape).
  Ako XML pokaze 52, ta dva se nisu izvrsila i to je nalaz, ne uspeh.

  Gašenje odmah po prolazu, i **zalepi izlaz `pgrep`-a**.

- [ ] **Korak 5: Testovi, build, commit**

---

## Task 5: Zatvaranje faze

**Fajlovi:** `CLAUDE.md`

- [ ] **Korak 1: Oba skupa testova**, brojevi **iz XML-a**. Polazno 105 / 52.

> **Zamka iz Faze 7:** posle dokaza mutacijom **pokreni skup ponovo** — inače na disku ostaje crven
> XML iz namerno pokvarenog prolaza, koji je tačan ali ne opisuje stablo.

- [ ] **Korak 2: Prebrojavanje emoji-ja**

```bash
grep -rnP '[\x{1F300}-\x{1FAFF}\x{2190}-\x{27BF}\x{2B00}-\x{2BFF}]' --include='*.kt' .
```
**Grep pokriva i strelice** (`U+2190–21FF`) — u Fazi 8 je bio slep za taj blok i dva glifa su
preživela. **Svaki pogodak pročitaj**; razloži po vrsti: kod, komentari, sedam šahovskih iz Task-a 3,
prevedene poruke, `ChessPiece.kt` (Unicode figure — **nisu emoji**).

- [ ] **Korak 3: `CLAUDE.md`**
  - stavku o `lessonIcon()` prepiši kao **zatvorenu za 42 simbola**, uz sedam koji **namerno ostaju**
    i razlog (Material nema šahovske figure ni taktike)
  - stavku o prečici prepiši po presudi iz Task-a 4
  - changelog sa **izmerenim** brojevima

- [ ] **Korak 4: Provere celog stabla i commit**

```bash
D=$(git diff --stat main..HEAD -- Chessko Chessko.xcodeproj); [ -z "$D" ] && echo "iOS netaknut"
diff -r Chessko/Content/lessons ChesskoAndroid/app/src/main/assets/lessons && echo "lekcije i dalje bajt-identicne"
```

---

## Samopregled

**1. Pokrivenost:** 49 simbola (20 + 22 + 7) i prečica. Taskovi 1–2 su mehanički i dele fajl, pa idu
redom; Task 3 je odluka, ne izmena.

**2. Placeholderi:** Task 4, Korak 2 namerno **ne bira rešenje** — tri puta sa cenom svakog, jer se
izbor ne može doneti bez merenja na uređaju. Task 3, Korak 3 isto.

**3. Doslednost:** `CheckCircle`, `Warning`, `Flag`, `Refresh`, `EmojiEvents`, `Public`, `Star`,
`People`, `Person`, `Info` su **već** u upotrebi u hromu posle Faze 8 — koristi ista imena, ne druga.
