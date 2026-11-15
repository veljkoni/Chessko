// Probna partija: dva klijenta, poziv, prihvatanje, dva poteza, predaja, kraj.
// Upotreba: node scripts/smoke.mjs [wss://play.chessko.app/ws]
const URL_ = process.argv[2] ?? "wss://play.chessko.app/ws";
const ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
const randCode = () => Array.from({ length: 6 }, () => ALPHABET[Math.floor(Math.random() * ALPHABET.length)]).join("");

const sockets = [];
const fail = (msg) => {
  console.error(`smoke FAIL: ${msg}`);
  for (const s of sockets) try { s.close(); } catch {}
  process.exit(1);
};
const timer = setTimeout(() => fail("timeout od 10 s"), 10_000);

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

  clearTimeout(timer);
  a.close();
  b.close();
  console.log("smoke ok");
  process.exit(0);
} catch (e) {
  fail(e.message);
}
