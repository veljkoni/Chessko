# Igra preko interneta (kod uređaja, kao AnyDesk) — dizajn

Datum: 2026-11-15. Status: predlog za odobrenje (posle pregleda graditelja).

## Namera i zašto

Dva prijatelja koja se znaju u stvarnom životu žele da odigraju partiju jedan protiv drugog, svako na svom
telefonu, dok nisu zajedno. Danas Chessko ima samo „Igraj sa prijateljem“ na **istom** telefonu.

Vlasnikove reči i odluke:
- „dva prijatelja koja se poznaju u realnom životu“, „mogu da se upare putem koda“, „ne treba da se logujemo“;
- „svaki app ima svoj kod i kada ukucaš taj kod kod sebe oni se povežu, slično AnyDesk aplikaciji“
  (varijanta A, izabrana umesto koda koji važi samo za jednu partiju);
- partija **uživo**, obojica u isto vreme u aplikaciji: „ne treba mi ništa komplikovano“.

## Ciljevi

1. Nova opcija u meniju „+“ na ekranu Igra: **„Igraj preko interneta“** (pored „Igraj protiv računara“,
   „Igraj sa prijateljem“ i „Šahovski sat“), na iOS-u i na Androidu.
2. Svaka instalacija ima svoj **kod uređaja**. Prijatelj ukuca tvoj kod, ti dobiješ poziv i prihvatiš, i
   partija počinje.
3. iPhone i Android igraju međusobno.
4. Sve ostalo (igra protiv računara, zadaci, lekcije, sat, analiza) i dalje radi bez interneta.

## Ne-ciljevi (namerno nema)

- nalozi, prijava, imena, slike profila, spisak prijatelja na serveru;
- igra sa nepoznatima, uparivanje po rejtingu, rejting za igru preko interneta;
- partije koje traju danima, obaveštenja („tvoj je potez“), čuvanje partija na serveru;
- čet, gledaoci, turniri;
- šahovski sat u igri preko interneta (može kasnije, kao posebna odluka);
- zaštita od varanja motorom: igraju prijatelji, a Stockfish je ionako u svakoj aplikaciji.

## Anti-ciljevi (neuspeh čak i ako „radi“)

- bilo kakvo prikupljanje ličnih podataka, identifikator za reklame, analitika ili SDK treće strane
  (Firebase, Supabase i slično) u aplikaciji;
- bilo šta što pokvari rad bez interneta za postojeće funkcije;
- ekran koji traži prijavu ili e-mail;
- server koji pamti ko je s kim igrao ili koje poteze.

## Ograničenja iz postojećeg projekta

- **Obećanje aplikacije:** sajt i politika privatnosti danas kažu „bez interneta“, „bez naloga“ i
  „aplikacija ne šalje nijedan mrežni zahtev“. Android namerno nema dozvolu `INTERNET`. Ova opcija to menja
  **samo za sebe**, pa se moraju promeniti:
  - `privacy.html` na svih 8 jezika (repo `chessko-site`);
  - deklaracije u prodavnicama (vidi „Prodavnice“);
  - tekst na sajtu koji tvrdi da aplikacija nikad ne koristi internet; o tačnoj formulaciji odlučuje
    dizajn sajta.
- **Paritet platformi:** svaka izmena ide na iOS i na Android u istom koraku, kao i do sada (vidi `CLAUDE.md`).
- **Lokalizacija:** svaki nov tekst u interfejsu na 8 jezika (iOS `Localizable.xcstrings`, Android `Loc.kt`),
  uz postojeće testove kataloga.
- **Postojeći kod:** `GameMode` ima `vsComputer` / `localFriend` (iOS `GameViewModel.swift`, Android
  `GameViewModel.kt`). Statistika se upisuje samo za `vsComputer`. Partija se čuva lokalno pod ključem.
  Pravila (legalnost, mat, pat, remi) su ista na obe platforme i pokrivena perft testovima.

## Pristupi koji su razmatrani

| Pristup | Za | Protiv | Odluka |
|---|---|---|---|
| **Mali posrednik na Cloudflare-u** (Worker + Durable Object, WebSocket) | Ista platforma kao sajt; besplatni nivo dovoljan; radi kroz svaku mrežu; iOS i Android isto; aplikacija bez SDK-a treće strane | Novi mali servis koji treba održavati | **Izabran** |
| Firebase / Supabase realtime | Bez sopstvenog servera | SDK treće strane u aplikaciji, Google zavisnost, kosi se sa „bez praćenja“ | Odbačen (anti-cilj) |
| Direktna veza telefon–telefon (WebRTC) | Potezi ne idu preko servera | Svejedno traži server za povezivanje i relej kad mreža ne pušta direktnu vezu; složenije | Odbačen |
| Game Center / Google Play Games | Gotova infrastruktura | Traže naloge, ne povezuju iOS i Android | Odbačen |

Unutar izabranog pristupa razmatrane su dve varijante koda:
- **A: stalan kod uređaja**, kao AnyDesk;
- **B: kod jedne partije**, koji važi nekoliko minuta.

**Vlasnik je izabrao A.**

## Kako radi (odlučeno)

### Kod uređaja
- Aplikacija pri prvom otvaranju ekrana „Igraj preko interneta“ napravi nasumičan kod i čuva ga **lokalno**
  (iOS `UserDefaults`, Android `SharedPreferences`). Kod ne nosi nikakav podatak o osobi ni uređaju.
- Dugme „Novi kod“ pravi nov kod. Stari prestaje da važi, jer ga server zna samo dok je aplikacija povezana.

### Tok
1. **A** otvori „Igraj preko interneta“. Aplikacija se poveže na server pod svojim kodom, prikaže taj kod
   (veliko, sa dugmetom za kopiranje i deljenje) i polje „Kod prijatelja“.
2. **A** ukuca kod prijatelja **B** i dodirne „Pozovi“.
3. Ako je **B** u tom trenutku na istom ekranu, dobije poziv „Kod K7M-4QX te poziva na partiju“ sa
   „Prihvati“ i „Odbij“. Ako **B** nije na tom ekranu, **A** dobije poruku da prijatelj trenutno nije dostupan
   i da treba da otvori „Igraj preko interneta“.
4. Posle prihvatanja počinje partija na postojećem ekranu Igra. Tabla je okrenuta prema boji igrača.
   **Boje se dele nasumično** (odluka pri pisanju dokumenta, vlasnik može da promeni).
   **Partija u toku na tabu Igra se ne gubi:** igra preko interneta ima sopstveni primerak modela i sopstveni
   ključ za čuvanje, po istom obrascu po kom koraci Puta već imaju svoj (`GameViewModel(saveKey:)` /
   `chessko.savedGame.step.<id>`). Posle partije preko interneta vraća se prethodna partija.
5. Kraj partije: mat, pat ili remi po pravilima, predaja, prihvaćen remi, ili istek čekanja posle prekida
   veze (vidi „Prekid veze“). Posle kraja oba igrača vide rezultat. Lokalna analiza partije radi kao i do sada.

### Pravila igre preko interneta
- Svaka aplikacija proverava legalnost poteza svojim, istim pravilima. Na tabli se može odigrati samo
  sopstveni potez, i to samo kad je igrač na potezu.
- Server **ne zna šah**: samo prosleđuje poteze između dve strane i pamti potez kao redni broj, da bi
  otkrio duple ili izgubljene poruke.
- Nema „Vrati potez“. Postoje **Predaj** i **Ponudi remi**; druga strana remi prihvata ili odbija.
- Rezultat igre preko interneta se ne upisuje u statistiku igre protiv računara.

### Prekid veze (vlasnik: „partija čeka“)
Partija mora da preživi ne samo kratak prekid mreže nego i **izlazak iz aplikacije**: zaključan ekran,
prelazak u drugu aplikaciju, telefonski poziv, pa i gašenje aplikacije. iOS prekida vezu čim aplikacija ode u
pozadinu, pa:
- Telefon **lokalno pamti partiju u toku**: oznaka partije, boja, potezi i tajni ključ za ponovno
  povezivanje. Kad se aplikacija ponovo otvori, sama se vraća u partiju i ponovo poveže.
- Ako jedna strana izgubi vezu, druga vidi „Prijatelj je izgubio vezu, čekam…“.
- Za vreme partije server drži listu poteza te partije u skladištu Durable Object-a, a ne samo u memoriji
  (memorija se gubi pri hibernaciji i pri svakoj objavi servera). **Briše je čim se partija završi ili
  istekne čekanje** (odluka pri pisanju dokumenta). Time „server ne pamti partije“ ostaje tačno: ništa ne
  preživi kraj partije.
- Kad se aplikacija ponovo poveže, dobije listu poteza i nastavi.
- Rok čekanja je dug, reda veličine desetina minuta, a ne sekundi. Tačan rok je ostavljen graditelju. Ako se
  strana ne vrati u roku, partija se završava bez pobednika („Partija je prekinuta“), server je zaboravlja, a
  oba telefona brišu lokalni zapis.
- Tajni ključ za povezivanje sprečava da neko ko zna tvoj kod uređaja „preuzme“ tvoju partiju.

### Server
- Cloudflare Worker sa Durable Object-om po kodu ili po partiji, na zasebnoj adresi (npr.
  `wss://play.chessko.app`), na istom nalogu kao sajt.
- **Cena (vlasnik):** kreće se na besplatnom nivou Cloudflare-a. Server mora da bude napravljen tako da
  prelazak na plaćeni Workers nivo (oko 5 $ mesečno), ako zatreba, ne traži nikakvu izmenu koda. Štedi se na
  porukama koliko je razumno (npr. hibernacija WebSocket-a), ali ne po cenu složenosti.
- Poruke su JSON i nose **verziju protokola**. Starija aplikacija koja ne zna noviju verziju dobije jasnu
  poruku „Ažuriraj aplikaciju“, a ne tihu grešku.
- Server ne čuva ništa posle partije: nema baze, nema istorije. Lista poteza postoji samo dok traje
  partija ili čekanje na povratak.
- Zaštita od zloupotrebe:
  - ograničen broj poziva po vezi u minuti;
  - kod može da primi poziv samo dok je njegov ekran otvoren;
  - jedan kod može biti u samo jednoj partiji u isto vreme.

### Mreža u aplikaciji
- **iOS:** `URLSessionWebSocketTask`, deo sistema; nema nove zavisnosti.
- **Android:**
  - dodaje se dozvola `INTERNET`;
  - WebSocket klijent je OkHttp, koji je već u aplikaciji preko Coil-a; deklariše se izričito, kao nova
    navedena zavisnost.
- Mreža se koristi **samo** dok je otvoren ekran ili partija „preko interneta“. Nijedan drugi deo aplikacije
  ne dobija mrežni kod.

### Prodavnice i privatnost
- **Redosled izdanja** (odluka pri pisanju dokumenta): nova `privacy.html` na svih 8 jezika je deo ovog
  posla i mora biti objavljena **pre** nego što izdanje sa ovom opcijom ode u prodavnice. Marketinški tekst
  sajta menja dizajn sajta posebno; deklaracije u prodavnicama popunjava vlasnik.
- Politika privatnosti, svih 8 jezika, treba da kaže:
  - jedna opcija, igra preko interneta, šalje poteze i kod uređaja preko servera, samo dok traje partija;
  - server ne čuva ništa;
  - Cloudflare tehnički vidi IP adresu;
  - sve ostalo ostaje bez interneta.
- App Store „App Privacy“ i Google Play „Data safety“: proveriti po njihovim definicijama da li prolazni
  prenos bez čuvanja i dalje znači „podaci se ne prikupljaju“. Odluku donosi vlasnik pri slanju izdanja.

## Ostavljeno graditelju

- tačan format i abeceda koda (predlog: 6 znakova bez slova i cifara koje se mešaju, prikaz `K7M-4QX`) i
  šta se radi pri sudaru dva ista koda na serveru;
- tačan rok čekanja posle prekida veze (desetine minuta, vidi „Prekid veze“) i ograničenje broja poziva;
- da li server ima jedan Durable Object po kodu i jedan po partiji, ili drugačiju raspodelu;
- izgled ekrana „Igraj preko interneta“ u postojećem dizajn sistemu (`DS.*`), na obe platforme;
- kako se igra preko interneta prikazuje u postojećem ekranu Igra (kartice igrača: „Ti“ / „Prijatelj“);
- testovi:
  - server: protokol, poziv, prihvatanje, potezi, prekid, ponovno povezivanje, istek;
  - aplikacija: čista logika toka, u postojećim testnim okvirima;
  - jedna ručna provera iPhone simulator ↔ Android emulator preko pravog servera.
