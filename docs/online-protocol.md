# Chessko online protocol, v1

Jedini izvor istine o porukama izmedju klijenata (iOS, Android) i servera (`server/play`).
Endpoint: `wss://play.chessko.app/ws`. Server ne cuva nista posle kraja partije.

Sve poruke su JSON objekti sa poljem `t`. Kod je 6 znakova iz abecede
`23456789ABCDEFGHJKMNPQRSTUVWXYZ`; na ekranu se pise `XXX-XXX`, a preko mreze bez crtice.

| Smer | Poruka | Odgovor / posledica |
|---|---|---|
| K→S | `{t:"hello", v:1, code}` | `{t:"welcome", code}` ili `{t:"error", reason:"version"\|"bad_code"\|"code_taken"}` |
| K→S | `{t:"resume", game, token}` | `{t:"resumed", game, color:"w"\|"b", moves:[uci], opponent:"online"\|"away"}` ili `{t:"error", reason:"game_gone"}` |
| K→S | `{t:"invite", to}` | pozivaocu `{t:"invite_sent", to}`; pozvanom `{t:"invited", from}`; ili greška `offline`\|`busy`\|`self`\|`rate_limited`\|`bad_code` |
| K→S | `{t:"cancel"}` | pozvanom `{t:"invite_cancelled", from}` |
| K→S | `{t:"accept", from}` | oba dobiju `{t:"start", game, color, token, opponent}` |
| K→S | `{t:"decline", from}` | pozivaocu `{t:"declined", by}` |
| K→S | `{t:"move", ply, uci}` | protivniku `{t:"move", ply, uci}`; pošiljaocu `{t:"ack", ply}`; pogrešan `ply` ili red → pošiljaocu `{t:"sync", moves:[uci]}` |
| K→S | `{t:"resign"}` | protivniku `{t:"resigned"}`; partija se briše (vidi „Kraj dok je protivnik odsutan“) |
| K→S | `{t:"draw_offer"}` | protivniku `{t:"draw_offer"}` |
| K→S | `{t:"draw_reply", accept}` | protivniku `{t:"draw_reply", accept}`; `accept:true` briše partiju (vidi „Kraj dok je protivnik odsutan“) |
| K→S | `{t:"finished"}` | kraj po pravilima (mat, pat, remi po pravilu), koji klijent sam vidi; partija se briše (vidi „Kraj dok je protivnik odsutan“) |
| S→K | `{t:"opponent", state:"away"\|"back"}` | prisutnoj strani, kad druga izgubi ili vrati vezu |
| S→K | `{t:"aborted"}` | prisutnoj strani, posle 30 min odsustva druge; partija se briše |

`resume` ne traži prethodni `hello`: autentikuje se samo tokenom, vezuje kod sedišta za vezu i zatvara svaku drugu vezu sa tim kodom (preuzimanje sedišta).

Verzija: `resume` ne nosi `v`. Server proverava verziju samo na `hello`, a klijenti ga uvek šalju pre `resume` na istoj vezi; klijent koji dobije `{t:"error", reason:"version"}` prestaje da se povezuje i ignoriše sve dalje poruke, pa budućem serveru je dovoljno da odbije stari `hello`.

**Kraj dok je protivnik odsutan.** Ako `resign`, `draw_reply{accept:true}` ili `finished` stigne dok protivnik nema vezu vezanu za partiju, partija se ne briše odmah: pamti se kraj (`ended`) i strana kojoj nije javljen. Strana koja je završila je odmah slobodna za nove pozive. Kad se odsutna strana vrati sa `resume`, dobija `resumed` (sve poteze; mat i pat se vide na tabli) pa poruku koju bi dobila uživo (`resigned`, odnosno `draw_reply{accept:true}`; za `finished` ništa), i partija se briše. Ako se ne vrati, partiju briše istek od 30 minuta. `resume` strane koja je završila daje `game_gone`.

**Odsustvo bez zatvaranja veze.** Veza izgubljena bez `webSocketClose` (deploy, restart servera) se otkriva kasnije: pri `resume`, potezu ili drugoj poruci druge strane, i pri pokretanju servera, sedište bez veze i bez oznake odsustva dobija oznaku tada, pa istek od 30 minuta važi i za njega.

`ply` je redni broj poteza od 0. Beli igra parne, crni neparne. `token` je 128-bitni nasumični heks i važi
samo za jednu partiju i jednu stranu.

Dodatno: `GET /health` vraca `200` i `ok`.
