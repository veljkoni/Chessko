// Probna partija: dva klijenta, poziv, prihvatanje, dva poteza, predaja, kraj.
// Zatim predaja dok je protivnik odsutan: on se vraca sa resume i dobija resumed pa resigned.
// Zatim partija na vreme (v2 ↔ v2, sat 3+2, kraj potezom) i v1 ↔ v2 bez sata (stari oblik poruka).
// Upotreba: node scripts/smoke.mjs [url]   ili   CHESSKO_WS=ws://127.0.0.1:8787/ws node scripts/smoke.mjs
// Prvenstvo: argument, pa CHESSKO_WS, pa produkcija (wss://play.chessko.app/ws).
const URL_ = process.argv[2] ?? process.env.CHESSKO_WS ?? "wss://play.chessko.app/ws";
const ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
const randCode = () => Array.from({ length: 6 }, () => ALPHABET[Math.floor(Math.random() * ALPHABET.length)]).join("");

const sockets = [];
const fail = (msg) => {
  console.error(`smoke FAIL: ${msg}`);
  for (const s of sockets) try { s.close(); } catch {}
  process.exit(1);
};
const timer = setTimeout(() => fail("timeout od 30 s"), 30_000);

function open() {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(URL_);
    const queue = [];
    const waiters = [];
    ws.addEventListener("message", (e) => {
      const m = JSON.parse(e.data);
      const w = waiters.shift();
      if (w) w(m); else queue.push(m);
    });
    ws.addEventListener("error", () => reject(new Error("veza nije uspostavljena")));
    ws.addEventListener("open", () => {
      sockets.push(ws);
      resolve({
        send: (o) => ws.send(JSON.stringify(o)),
        next: () => new Promise((r) => { const m = queue.shift(); m ? r(m) : waiters.push(r); }),
        close: () => ws.close(),
      });
    });
  });
}
async function expectMsg(c, want, label) {
  const m = await c.next();
  for (const [k, v] of Object.entries(want)) {
    if (m[k] !== v) fail(`${label}: očekivano ${JSON.stringify(want)}, stiglo t=${m.t}`);
  }
  return m;
}

try {
  const codeA = randCode();
  let codeB = randCode();
  while (codeB === codeA) codeB = randCode();
  const a = await open();
  const b = await open();
  a.send({ t: "hello", v: 1, code: codeA });
  b.send({ t: "hello", v: 1, code: codeB });
  await expectMsg(a, { t: "welcome" }, "welcome A");
  await expectMsg(b, { t: "welcome" }, "welcome B");

  a.send({ t: "invite", to: codeB });
  await expectMsg(a, { t: "invite_sent" }, "invite_sent");
  await expectMsg(b, { t: "invited" }, "invited");
  b.send({ t: "accept", from: codeA });
  const sa = await expectMsg(a, { t: "start" }, "start A");
  const sb = await expectMsg(b, { t: "start" }, "start B");
  if (sa.color === sb.color) fail("oba igrača imaju istu boju");

  const [w, bl] = sa.color === "w" ? [a, b] : [b, a];
  w.send({ t: "move", ply: 0, uci: "e2e4" });
  await expectMsg(w, { t: "ack", ply: 0 }, "ack 0");
  await expectMsg(bl, { t: "move", ply: 0, uci: "e2e4" }, "potez 0 stiže crnom");
  bl.send({ t: "move", ply: 1, uci: "e7e5" });
  await expectMsg(bl, { t: "ack", ply: 1 }, "ack 1");
  await expectMsg(w, { t: "move", ply: 1, uci: "e7e5" }, "potez 1 stiže belom");

  w.send({ t: "resign" });
  await expectMsg(bl, { t: "resigned" }, "resigned");
  bl.send({ t: "finished" });
  w.send({ t: "move", ply: 2, uci: "d2d4" });
  await expectMsg(w, { t: "error", reason: "game_gone" }, "partija obrisana posle kraja");

  a.close();
  b.close();

  // Predaja dok je protivnik odsutan: rezultat ga ceka do povratka.
  const codeC = randCode();
  let codeD = randCode();
  while (codeD === codeC) codeD = randCode();
  const c = await open();
  const d = await open();
  c.send({ t: "hello", v: 1, code: codeC });
  d.send({ t: "hello", v: 1, code: codeD });
  await expectMsg(c, { t: "welcome" }, "welcome C");
  await expectMsg(d, { t: "welcome" }, "welcome D");
  c.send({ t: "invite", to: codeD });
  await expectMsg(c, { t: "invite_sent" }, "invite_sent C");
  await expectMsg(d, { t: "invited" }, "invited D");
  d.send({ t: "accept", from: codeC });
  const sc = await expectMsg(c, { t: "start" }, "start C");
  const sd = await expectMsg(d, { t: "start" }, "start D");
  const wc = sc.color === "w" ? c : d;
  wc.send({ t: "move", ply: 0, uci: "e2e4" });
  await expectMsg(wc, { t: "ack", ply: 0 }, "ack 0 (C/D)");
  await expectMsg(wc === c ? d : c, { t: "move", ply: 0 }, "potez 0 stiže (C/D)");

  d.close();
  await expectMsg(c, { t: "opponent", state: "away" }, "C vidi da je D otišao");
  c.send({ t: "resign" });
  await new Promise((r) => setTimeout(r, 500));

  const d2 = await open();
  d2.send({ t: "hello", v: 1, code: codeD });
  d2.send({ t: "resume", game: sd.game, token: sd.token });
  await expectMsg(d2, { t: "welcome" }, "welcome D2");
  const rs = await expectMsg(d2, { t: "resumed", game: sd.game }, "D2 resumed");
  if (JSON.stringify(rs.moves) !== JSON.stringify(["e2e4"])) fail(`resumed.moves: ${JSON.stringify(rs.moves)}`);
  await expectMsg(d2, { t: "resigned" }, "D2 dobija predaju posle povratka");
  d2.send({ t: "resume", game: sd.game, token: sd.token });
  await expectMsg(d2, { t: "error", reason: "game_gone" }, "partija obrisana posle isporuke");


  // Partija na vreme: dva v2 klijenta, poziv {180,2}, start sa satom, dva poteza, move sa end:true.
  const pair = async (va, vb, clock) => {
    const x = randCode();
    let y = randCode();
    while (y === x) y = randCode();
    const cx = await open();
    const cy = await open();
    cx.send({ t: "hello", v: va, code: x });
    cy.send({ t: "hello", v: vb, code: y });
    await expectMsg(cx, { t: "welcome" }, "welcome X");
    await expectMsg(cy, { t: "welcome" }, "welcome Y");
    cx.send(clock ? { t: "invite", to: y, clock } : { t: "invite", to: y });
    await expectMsg(cx, { t: "invite_sent" }, "invite_sent X");
    const inv = await expectMsg(cy, { t: "invited" }, "invited Y");
    cy.send(clock ? { t: "accept", from: x, clock } : { t: "accept", from: x });
    const sx = await expectMsg(cx, { t: "start" }, "start X");
    const sy = await expectMsg(cy, { t: "start" }, "start Y");
    return { cx, cy, sx, sy, inv };
  };
  const hasKeys = (m, ...ks) => ks.some((k) => k in m);

  const t2 = await pair(2, 2, { base: 180, inc: 2 });
  if (JSON.stringify(t2.inv.clock) !== JSON.stringify({ base: 180, inc: 2 })) fail(`invited.clock: ${JSON.stringify(t2.inv.clock)}`);
  for (const st of [t2.sx, t2.sy]) {
    const k = st.clock;
    if (!k || k.base !== 180 || k.inc !== 2 || k.w !== 180000 || k.b !== 180000 || k.run !== null ||
      typeof k.first !== "number" || k.first <= 0 || k.first > 30000) fail(`start.clock: ${JSON.stringify(k)}`);
  }
  const [tw, tb] = t2.sx.color === "w" ? [t2.cx, t2.cy] : [t2.cy, t2.cx];
  tw.send({ t: "move", ply: 0, uci: "e2e4" });
  const ack0 = await expectMsg(tw, { t: "ack", ply: 0 }, "t2 ack 0");
  if (!ack0.clock) fail("t2 ack 0 bez clock");
  const mv0 = await expectMsg(tb, { t: "move", ply: 0, uci: "e2e4" }, "t2 potez 0");
  if (!mv0.clock || mv0.clock.run !== null || typeof mv0.clock.first !== "number") fail(`t2 move.clock: ${JSON.stringify(mv0.clock)}`);
  tb.send({ t: "move", ply: 1, uci: "e7e5", end: true });
  await expectMsg(tb, { t: "ack", ply: 1 }, "t2 ack 1");
  const mv1 = await expectMsg(tw, { t: "move", ply: 1, uci: "e7e5", end: true }, "t2 potez 1 sa end");
  if (!mv1.clock) fail("t2 move 1 bez clock");
  tw.send({ t: "move", ply: 2, uci: "d2d4" });
  await expectMsg(tw, { t: "error", reason: "game_gone" }, "t2 partija obrisana posle end:true");

  // Nedozvoljen sat i v1 ↔ v2.
  {
    const cx = await open();
    const cy = await open();
    const x = randCode();
    let y = randCode();
    while (y === x) y = randCode();
    cx.send({ t: "hello", v: 2, code: x });
    cy.send({ t: "hello", v: 2, code: y });
    await expectMsg(cx, { t: "welcome" }, "welcome bad_clock X");
    await expectMsg(cy, { t: "welcome" }, "welcome bad_clock Y");
    cx.send({ t: "invite", to: y, clock: { base: 60, inc: 0 } });
    await expectMsg(cx, { t: "error", reason: "bad_clock" }, "bad_clock");
    cx.close();
    cy.close();
  }
  {
    // v2 poziva v1 sa satom: needs_update.
    const cx = await open();
    const cy = await open();
    const x = randCode();
    let y = randCode();
    while (y === x) y = randCode();
    cx.send({ t: "hello", v: 2, code: x });
    cy.send({ t: "hello", v: 1, code: y });
    await expectMsg(cx, { t: "welcome" }, "welcome nu X");
    await expectMsg(cy, { t: "welcome" }, "welcome nu Y");
    cx.send({ t: "invite", to: y, clock: { base: 180, inc: 2 } });
    await expectMsg(cx, { t: "error", reason: "needs_update" }, "needs_update");
    cx.close();
    cy.close();
  }
  // v1 ↔ v2 bez sata, oba smera poziva: stari oblik poruka, bez clock i end.
  for (const [va, vb] of [[1, 2], [2, 1]]) {
    const p = await pair(va, vb, null);
    for (const st of [p.sx, p.sy]) if (hasKeys(st, "clock")) fail(`v${va}↔v${vb} start nosi clock`);
    if (hasKeys(p.inv, "clock")) fail(`v${va}↔v${vb} invited nosi clock`);
    const [pw, pb] = p.sx.color === "w" ? [p.cx, p.cy] : [p.cy, p.cx];
    pw.send({ t: "move", ply: 0, uci: "e2e4" });
    const a = await expectMsg(pw, { t: "ack", ply: 0 }, `v${va}↔v${vb} ack`);
    const m = await expectMsg(pb, { t: "move", ply: 0, uci: "e2e4" }, `v${va}↔v${vb} move`);
    if (hasKeys(a, "clock", "end") || hasKeys(m, "clock", "end")) fail(`v${va}↔v${vb} ack/move nosi novo polje`);
    pw.send({ t: "resign" });
    await expectMsg(pb, { t: "resigned" }, `v${va}↔v${vb} resigned`);
    p.cx.close();
    p.cy.close();
  }

  clearTimeout(timer);
  c.close();
  d2.close();
  console.log("smoke ok");
  process.exit(0);
} catch (e) {
  fail(e.message);
}
