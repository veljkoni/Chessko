# Igra preko interneta na vreme (3+2, 10+5) — dizajn

Datum: 21. 11. 2026. Status: nacrt za odobrenje vlasnika (posle pregleda).
Nadovezuje se na `2026-11-15-igra-preko-interneta-design.md` i na protokol `docs/online-protocol.md` (v1).

## Namera i razlog

Testeri se žale da u igri preko interneta predugo čekaju da protivnik odigra potez. Rešenje je partija sa
šahovskim satom: svaki igrač ima ograničeno vreme za celu partiju, a kome istekne vreme, gubi.

Uspeh: partija sa satom ne može da stoji. Ako jedan igrač ne igra, bilo da razmišlja, izašao je iz
aplikacije ili je izgubio vezu, njegovo vreme teče i partija se završava najkasnije kad mu vreme istekne.

## Šta je vlasnik odlučio

- **Vreme bira onaj ko poziva**, a pozvani vidi izabrano vreme i mora da pristane. Ako ne pristaje, odbija poziv
  kao i danas.
- **Izbor:** `3+2`, `10+5` i `Bez sata`. Prvi broj je osnovno vreme u minutima, drugi broj sekundi dodatih posle
  svakog poteza. `Bez sata` je današnja partija, bez ikakve promene.
- **Vreme meri server** (pristup A, vidi „Pristupi“). Telefon samo prikazuje sat koji dobije od servera.
- **Sat svakom igraču kreće tek posle njegovog prvog poteza** (kao na Lichess-u).
- **Rok za prvi potez: 30 sekundi** za svaku stranu, od trenutka kad je prvi put na potezu (beli od početka
  partije, crni od prvog poteza belog). Rok teče i kad igrač nije u aplikaciji. Ako istekne, partija se **prekida
  bez rezultata**. Igrač na potezu vidi odbrojavanje („Odigraj prvi potez: 0:25“).
- **Podrazumevano vreme pri prvom pozivu je 3+2**; posle toga aplikacija pamti poslednji izbor.
- **Važi potez odigran na vreme.** Potez koji je stigao na server pre isteka vremena i završava partiju po pravilima
  (mat, pat, remi po pravilu) jači je od kasnijeg isteka. Potez koji stigne posle isteka ne važi: to je poraz na vreme.
- **Sat ne staje kad igrač izađe iz aplikacije ili izgubi vezu.** Ko ne odigra potez, gubi kad mu istekne vreme.
  Rok od 30 minuta odsustva ostaje samo za partije bez sata.
- **Istek vremena je poraz.** Šahovski izuzetak (remi kad protivnik više nema čime da da mat) se za sada ne
  primenjuje, jer server ne zna pravila šaha.
- **Stare verzije aplikacije** (iOS 1.3, Android 2.3 i starije): partija bez sata između stare i nove verzije radi
  kao danas. Partiju sa satom stara verzija ne može da dobije: onaj ko poziva dobija poruku da prijatelj treba da
  ažurira aplikaciju.

## Ciljevi, ne-ciljevi, anti-ciljevi

Ciljevi:
- partija sa satom na obe platforme i iPhone ↔ Android;
- server je jedini sudija vremena; istek javlja server, i kad nijedan telefon nije na vezi;
- nijedna postojeća partija bez sata ne menja ponašanje, ni između stare i nove verzije.

Ne-ciljevi (namerno van ovog posla):
- nadoknada kašnjenja mreže (pristup C); dodaje se kasnije ako se testeri žale na lošu vezu;
- remi zbog nedovoljnog materijala pri isteku vremena;
- proizvoljno vreme koje igrač sam ukuca, premove, revanš jednim dodirom, rejting;
- sat u igri protiv računara i u igri u dvoje na istom telefonu.

Anti-ciljevi (ovo bi bio neuspeh i kad bi tehnički radilo):
- igrač izgubi na vreme zbog greške u merenju, na primer zato što se računao njegov sat dok je protivnik bio na
  potezu ili dok se partija otvarala;
- sat na dva telefona pokazuje vidljivo različito vreme za istog igrača (više od ~1 s, ne računajući kašnjenje mreže);
- stara verzija aplikacije prestane da radi ili dobije poruku „potrebna je novija verzija“ za partiju bez sata.

## Pristupi koji su razmotreni

- **A — sat meri server (izabrano).** Server pamti preostalo vreme obe strane i trenutak kad je potez počeo. Kad
  potez stigne, oduzme potrošeno vreme i doda dodatak, pa stanje sata pošalje obema stranama. Istek javlja server
  svojim budilnikom (`alarm` Durable Object-a, isti mehanizam kao rok odsustva). Mana: igrač sa lošom vezom gubi
  vreme koje potez putuje do servera.
- **B — sat meri telefon.** Odbijeno: igrač koji izađe iz aplikacije ne šalje ništa, pa mu vreme nikad ne istekne,
  a to je baš problem koji se rešava. Uz to bi izmenjena aplikacija mogla da laže.
- **C — telefon meri, server proverava i presuđuje.** Pravednije na lošoj vezi, ali je složenije za testiranje na
  tri strane. Ostaje kao moguća dopuna posle A, bez promene ekrana.

## Kako radi

### Protokol (v2)

Verzija protokola postaje **2**. Server prihvata `hello` sa `v:1` i sa `v:2` i pamti verziju u prilogu veze.
Nove poruke i polja idu samo ka vezama sa `v:2`. Ka vezi sa `v:1` server nikad ne šalje ništa novo, pa se njeno
ponašanje ne menja. Sva vremena preko mreže su u milisekundama; podešavanje sata je u sekundama.

- `invite` dobija opciono polje `clock: {base, inc}` u sekundama. Dozvoljeno je samo `{180, 2}` i `{600, 5}`; bez
  polja je partija bez sata. Server odbija druge vrednosti (`error`, `reason: "bad_clock"`).
- Pozvani dobija `invited` sa istim `clock` poljem, i `accept` ga vraća serveru. Server prihvata samo ako se poklapa
  sa pozivom, da pozvani nikad ne pristane na vreme koje nije video.
- Ako je pozvani na vezi sa `v:1` a poziv ima `clock`, pozivalac dobija `{t:"error", reason:"needs_update"}` i poziv se
  ne šalje.
- `start`, `move` (ka protivniku), `ack` (ka onome ko je odigrao), `sync` i `resumed` u partiji sa satom nose
  `clock: {w, b, run}`: preostalo vreme belog i crnog u ms u trenutku slanja, i koja strana troši vreme (`"w"`,
  `"b"` ili `null` dok sat još ne teče). `start` i `resumed` nose i podešavanje (`base`, `inc`), da telefon zna
  koja je partija.
- `move` u partiji sa satom može da nosi `end: true` kad taj potez po pravilima završava partiju (mat, pat, remi po
  pravilu; to vidi telefon koji igra potez). Server tada zaustavlja sat i postupa kao da je stigao `finished`; protivnik
  dobija potez sa istim poljem.
- Nova poruka `{t:"first_move_expired"}` ka obema stranama, kad istekne rok za prvi potez; partija se briše bez
  rezultata (isti put isporuke odsutnoj strani kao `aborted`).
- Nova poruka ka obema stranama: `{t:"flag", loser:"w"|"b"}`, kad jednoj strani istekne vreme. Partija se time
  završava i važi isti mehanizam „Kraj dok je protivnik odsutan“ kao za `resign`: strani koja nije bila na vezi
  rezultat se isporučuje pri `resume`.

### Pravila sata na serveru

- Sat jedne strane teče samo dok je ona na potezu, i to tek od njenog drugog poteza. Prvi potez svake strane je
  bez sata i bez dodatka: `run = null` posle `start` i posle prvog poteza belog; posle prvog poteza crnog počinje da
  teče sat belog (`run = "w"`).
- Posle svakog poteza strane čiji sat teče: `preostalo -= (sada − početak_poteza)`, zatim `preostalo += inc`.
  Ako je `preostalo ≤ 0` pre dodatka, potez se ne prihvata, partija je završena istekom (`flag`).
- Potez sa `end: true` koji stigne na vreme zaustavlja sat; posle njega nema isteka.
- Rok za prvi potez: 30 s od trenutka kad strana prvi put dođe na potez. Za belog to je trenutak `start`, za crnog
  trenutak kad server prihvati prvi potez belog.
- Budilnik se postavlja na najraniji rok u svim partijama: za partiju sa satom rok je `početak_poteza + preostalo`
  strane na potezu, odnosno rok za prvi potez dok sat još ne teče; za partiju bez sata ostaje današnji rok odsustva od 30 minuta. Jedan budilnik obrađuje oba.
- Odsustvo u partiji sa satom: protivnik i dalje dobija `opponent away/back`, ali odsustvo ne briše partiju. Istek
  vremena je jedini kraj zbog neigranja.

### Telefoni (iOS i Android, isto ponašanje)

- **Poziv:** na ekranu poziva izbor `3+2` / `10+5` / `Bez sata`. Prvi put je izabrano 3+2, posle toga poslednji izbor. Pozvani u
  dijalogu poziva vidi vreme („Partija: 3+2“ ili „Bez sata“).
- **Partija:** pored imena svakog igrača sat u obliku `M:SS`; ispod 10 sekundi `S.d` (desetinke), kao samostalni
  šahovski sat u aplikaciji. Aktivan sat je istaknut, a ispod 10 s u boji `DS.danger`. Dok sat ne teče, oba sata
  stoje na početnom vremenu.
- **Kraj na tabli** u partiji sa satom: telefon koji je odigrao završni potez proglašava kraj tek kad stigne `ack`;
  protivnik ga proglašava čim primi potez (server ga je već prihvatio).
- **Odbrojavanje** ide lokalno od poslednjeg stanja koje je stiglo sa servera. Telefon ne javlja istek sam: kad
  lokalno odbroji do nule, prikazuje `0:00` i čeka `flag` sa servera.
- **Kraj:** „Isteklo ti je vreme.“ / „Prijatelju je isteklo vreme.“ (rodno neutralno, „ti“, 8 jezika); dugme za
  analizu i povratak u lobi kao kod drugih krajeva.
- **Greška `needs_update`:** „Prijatelj treba da ažurira aplikaciju za igru na vreme.“ U lobiju, kao druge greške poziva.
- **Čista mašina stanja** (`OnlineMachine`, iOS i Android 1:1) dobija stanje sata i događaje `flag`/stanje sata,
  i pokrivena je testovima kao i sada. Rezultat se i dalje ne upisuje u statistiku.

## Ograničenja

- Server ne sme da prestane da služi klijente sa `v:1` (iOS 1.3 je javan).
- Server se objavljuje pre aplikacija i mora da radi sa obe verzije klijenata od prvog dana.
- Nijedna nova Gradle ni SPM zavisnost; Android ostaje bez `ACCESS_NETWORK_STATE`.
- Tekst: srpski je izvor, svih 8 jezika, obraćanje sa „ti“, rodno neutralno.
- Politika privatnosti: server i dalje ne čuva ništa posle kraja partije; vreme po potezu se ne čuva posle kraja.

## Šta je ostavljeno onome ko gradi

- Tačni nazivi tipova i polja u kodu, raspored sata u kartici igrača, animacija isteka.
- Kako se `0:00` prikazuje dok se čeka `flag`, i da li telefon posle nekoliko sekundi bez `flag`-a radi `resume`.
- Testovi servera sa lažnim vremenom (`vi.useFakeTimers` ili ubačen sat) i provera uživo (`npm run smoke`) sa
  partijom na vreme.
- Redosled objave: server, pa iOS (1.4) i Android (sledeća verzija posle 2.4, koja nosi i popravke iz 2.4).

## Odluke koje je doneo projektant (vlasnik ih može promeniti)

- Pravilo „važi potez odigran na vreme“ i polje `end: true` na potezu; telefon u partiji sa satom proglašava kraj
  tek posle `ack`.
- `accept` vraća `clock`, da pozvani ne pristane na vreme koje nije video.
- Server prihvata samo dve vrednosti sata (`{180,2}`, `{600,5}`).
- Telefon ne javlja istek sam; pokazuje `0:00` i čeka `flag`.
