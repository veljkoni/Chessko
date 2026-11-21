# Chessko online protocol, v2

Jedini izvor istine o porukama izmedju klijenata (iOS, Android) i servera (`server/play`).
Endpoint: `wss://play.chessko.app/ws`. Server ne cuva nista posle kraja partije.

Server prihvata `hello` sa `v:1` i `v:2`. Verzija 2 dodaje partiju na vreme (sat); sve v1 poruke i dalje rade isto (vidi „Saradnja v1 ↔ v2“).

Sve poruke su JSON objekti sa poljem `t`. Kod je 6 znakova iz abecede
`23456789ABCDEFGHJKMNPQRSTUVWXYZ`; na ekranu se pise `XXX-XXX`, a preko mreze bez crtice.

| Smer | Poruka | Odgovor / posledica |
|---|---|---|
| K→S | `{t:"hello", v:1\|2, code}` | `{t:"welcome", code}` ili `{t:"error", reason:"version"\|"bad_code"\|"code_taken"}`; `v` se pamti za vezu |
| K→S | `{t:"resume", game, token}` | `{t:"resumed", game, color:"w"\|"b", moves:[uci], opponent:"online"\|"away", clock?}` ili `{t:"error", reason:"game_gone"}`; `clock` samo vezi v2 i samo u partiji sa satom |
| K→S | `{t:"invite", to, clock?:{base,inc}}` | pozivaocu `{t:"invite_sent", to}`; pozvanom `{t:"invited", from, clock?}`; ili greška `offline`\|`busy`\|`self`\|`rate_limited`\|`bad_code`\|`bad_clock`\|`needs_update` |
| K→S | `{t:"cancel"}` | pozvanom `{t:"invite_cancelled", from}` |
| K→S | `{t:"accept", from, clock?}` | oba dobiju `{t:"start", game, color, token, opponent, clock?}`; `clock` mora da se slaže sa pozivom (poziv sa satom traži `clock`, poziv bez sata ga ne sme imati), inače se `accept` ćutke ignoriše |
| K→S | `{t:"decline", from}` | pozivaocu `{t:"declined", by}` |
| K→S | `{t:"move", ply, uci, end?:true}` | protivniku `{t:"move", ply, uci, clock?, end?}`; pošiljaocu `{t:"ack", ply, clock?}`; pogrešan `ply` ili red → pošiljaocu `{t:"sync", moves:[uci], clock?}`; potez koji stigne kad je `sada ≥ rok` se ne prihvata (vidi „Sat (v2)“) |
| K→S | `{t:"resign"}` | protivniku `{t:"resigned"}`; partija se briše (vidi „Kraj dok je protivnik odsutan“) |
| K→S | `{t:"draw_offer"}` | protivniku `{t:"draw_offer"}` |
| K→S | `{t:"draw_reply", accept}` | protivniku `{t:"draw_reply", accept}`; `accept:true` briše partiju (vidi „Kraj dok je protivnik odsutan“) |
| K→S | `{t:"finished"}` | kraj po pravilima (mat, pat, remi po pravilu), koji klijent sam vidi; partija se briše (vidi „Kraj dok je protivnik odsutan“) |
| S→K | `{t:"opponent", state:"away"\|"back"}` | prisutnoj strani, kad druga izgubi ili vrati vezu |
| S→K | `{t:"aborted"}` | prisutnoj strani, posle 30 min odsustva druge (partije bez sata i završeni redovi); partija se briše |
| S→K | `{t:"flag", loser:"w"\|"b"}` | v2, partija sa satom: istekao sat strane `loser`; prisutnim stranama odmah, odsutnoj posle `resume` (posle `resumed`) |
| S→K | `{t:"first_move_expired"}` | v2, partija sa satom: strana nije odigrala prvi potez u roku od 30 s; partija se briše bez rezultata |

`resume` ne traži prethodni `hello` (ali vidi (g) ispod: bez `hello` veza se tretira kao v1): autentikuje se samo tokenom, vezuje kod sedišta za vezu i zatvara svaku drugu vezu sa tim kodom (preuzimanje sedišta).

Verzija: `resume` ne nosi `v`. Server proverava verziju samo na `hello`, a klijenti ga uvek šalju pre `resume` na istoj vezi; klijent koji dobije `{t:"error", reason:"version"}` prestaje da se povezuje i ignoriše sve dalje poruke, pa budućem serveru je dovoljno da odbije stari `hello`.

**Kraj dok je protivnik odsutan.** Ako `resign`, `draw_reply{accept:true}` ili `finished` stigne dok protivnik nema vezu vezanu za partiju, partija se ne briše odmah: pamti se kraj (`ended`) i strana kojoj nije javljen. Strana koja je završila je odmah slobodna za nove pozive. Kad se odsutna strana vrati sa `resume`, dobija `resumed` (sve poteze; mat i pat se vide na tabli) pa poruku koju bi dobila uživo (`resigned`, odnosno `draw_reply{accept:true}`; za `finished` ništa), i partija se briše. Ako se ne vrati, partiju briše istek od 30 minuta. `resume` strane koja je završila daje `game_gone`.

**Odsustvo bez zatvaranja veze.** Veza izgubljena bez `webSocketClose` (deploy, restart servera) se otkriva kasnije: pri `resume`, potezu ili drugoj poruci druge strane, i pri pokretanju servera, sedište bez veze i bez oznake odsustva dobija oznaku tada, pa istek od 30 minuta važi i za njega.

`ply` je redni broj poteza od 0. Beli igra parne, crni neparne. `token` je 128-bitni nasumični heks i važi
samo za jednu partiju i jednu stranu.

Dodatno: `GET /health` vraca `200` i `ok`.

## Sat (v2)

Partija na vreme postoji samo kad pozivalac pošalje `clock` u `invite`, a pozvani ga ponovi u `accept`. Dozvoljeni satovi (`base` i `inc` u sekundama): `{180,2}` i `{600,5}`; bilo šta drugo daje `error bad_clock`. Bez `clock` partija je bez sata, tačno kao u v1.

`clock` u porukama servera (`WireClock`): `{base, inc, w, b, run, first}`.

| Polje | Značenje |
|---|---|
| `base`, `inc` | sekunde, kao u pozivu |
| `w`, `b` | preostalo vreme belog i crnog u milisekundama, u trenutku slanja poruke |
| `run` | `"w"`, `"b"` ili `null`: čiji sat teče; `null` dok je odigrano manje od 2 poteza |
| `first` | milisekunde do isteka roka za prvi potez strane na potezu, ili `null` kad je odigrano ≥ 2 poteza |

`clock` stiže u: `start` (oba igrača), `ack`, `move` (protivniku), `sync` i `resumed`. Klijent računa prikaz iz poslednjeg `clock` i vremena kad ga je primio; server je jedina istina.

Pravila:

- **Prvi potez svake strane je bez sata i bez dodatka.** Rok za njega je 30 s (`FIRST_MOVE_MS`), od trenutka kad je ta strana prvi put na potezu: beli od `start`, crni od trenutka kad server prihvati prvi potez belog. Istek roka: prisutnim stranama `{t:"first_move_expired"}`, partija se briše bez rezultata. `first_move_expired` se nikad ne pamti: strana koja je tada bila odsutna kasnije dobija `game_gone` na `resume` (f).
- Sat strane teče samo dok je ona na potezu i tek od njenog drugog poteza. Posle svakog poteza od drugog poteza te strane nadalje, njenom vremenu se oduzme potrošeno pa doda `inc`.
- **Važi potez odigran na vreme.** Potez koji stigne kad je `sada ≥ rok` (rok = početak poteza + preostalo vreme strane) se ne prihvata: to je `flag`, gubi strana koja je igrala; protivnik ne dobija `move`. Prisutne strane dobijaju `{t:"flag", loser}`.
- **Kraj potezom.** `move` sa `end:true` koji stigne na vreme zaustavlja sat i završava partiju kao `finished`: protivniku se prosleđuje `move` sa `end:true`, red se briše (ako je protivnik odsutan, pamti se kraj kao kod `finished`). Klijent mora da pošalje `end:true` na potezu koji sam vidi kao mat/pat/remi po pravilu, inače bi sat nastavio da teče.
- Partija sa satom u toku se ne briše zbog odsustva: sat teče, rok sata je jedini rok. Rok odsustva od 30 min (`AWAY_LIMIT_MS`) važi za partije bez sata i za već završene redove (`ended`).
- `resume` usred partije vraća `clock` za trenutak povratka: vreme strane na potezu je već umanjeno za odsustvo, druga strana netaknuta.

### Kraj sa satom dok je neka strana odsutna

- (c) Posle `resign`, prihvaćenog remija ili `finished` dok je strana odsutna, `clock` u njenom `resumed` je zamrznut u trenutku poslednjeg poteza (sat se ne nastavlja do povratka).
- (d) Istek sata (`flag`) dok je jedna ili obe strane odsutne: red pamti kraj, `ended_to` može da imenuje obe boje. Svaka odsutna strana pri `resume` dobija `resumed` (sa satom zamrznutim na trenutku roka, gubitnik 0) pa tačno jedan `flag`; kad su dobile obe, red se briše.
- (e) Kad se partija sa satom završi, oznaka odsustva odsutne strane se postavlja na trenutak kraja, pa se 30 min do brisanja reda računa od kraja, ne od ranijeg odlaska.
- (f) `first_move_expired` se nikad ne čuva; odsutna strana na `resume` dobija `game_gone`.

### Saradnja v1 ↔ v2

- (a) Vezi sa `v:1` (ili čiji prilog nema `v`) server nikad ne šalje nova polja ni nove poruke (`clock`, `end`). `clock` koji pošalje v1 veza se ignoriše (nikad `bad_clock`/`needs_update`), isto `end`.
- v2 pozivalac koji pošalje `invite` sa `clock` v1 vezi dobija `{t:"error", reason:"needs_update"}`; poziv bez `clock` radi među svim kombinacijama verzija, a v1 ↔ v2 partija bez sata ima tačno stari oblik poruka.
- (b) U partiji bez sata klijent ignoriše `end` na dolaznom `move`: server ga prosleđuje v2 vezi, ali mu ne daje značenje (ne završava partiju).
- (g) Veza koja pošalje `resume` bez prethodnog `hello` tretira se kao v1 (nema `v`). Isporučeni klijenti uvek šalju `hello` pre `resume`.
- Greške dodate u v2: `needs_update` (v2 poziva sa satom v1 vezu), `bad_clock` (nedozvoljen sat).

## Objava i povratak

Pre objave servera zapiši trenutnu verziju: `npx wrangler deployments list` (u `server/play`). Objava je `npx wrangler deploy`. Povratak: `npx wrangler rollback <verzija>`. Stari server nove kolone (`clock_base`, `clock_inc`, `w_ms`, `b_ms`, `turn_started`) samo ne čita, pa zapisi partija ostaju upotrebljivi posle povratka. Objava servera i smoke protiv produkcije samo uz odobrenje vlasnika.
