import { SELF, env as rawEnv, runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { describe, expect, it } from "vitest";

async function connect(): Promise<WebSocket> {
  const res = await SELF.fetch("https://play.test/ws", { headers: { Upgrade: "websocket" } });
  const ws = res.webSocket!;
  ws.accept();
  return ws;
}
function next(ws: WebSocket): Promise<any> {
  return new Promise((resolve) => {
    ws.addEventListener("message", (e) => resolve(JSON.parse(e.data as string)), { once: true });
  });
}
async function ask(ws: WebSocket, msg: object): Promise<any> {
  const p = next(ws);
  ws.send(JSON.stringify(msg));
  return p;
}
async function join(code: string): Promise<WebSocket> {
  const ws = await connect();
  expect(await ask(ws, { t: "hello", v: 1, code })).toEqual({ t: "welcome", code });
  return ws;
}
function silent(ws: WebSocket, ms = 150): Promise<boolean> {
  return new Promise((resolve) => {
    const h = () => resolve(false);
    ws.addEventListener("message", h, { once: true });
    setTimeout(() => {
      ws.removeEventListener("message", h);
      resolve(true);
    }, ms);
  });
}
function closed(ws: WebSocket): Promise<void> {
  return new Promise((resolve) => ws.addEventListener("close", () => resolve(), { once: true }));
}

async function pair(aCode: string, bCode: string) {
  const a = await join(aCode);
  const b = await join(bCode);
  const invited = next(b);
  await ask(a, { t: "invite", to: bCode });
  await invited;
  const sa = next(a);
  const sb = ask(b, { t: "accept", from: aCode });
  const [ma, mb] = [await sa, await sb];
  return ma.color === "w"
    ? { w: a, b, wCode: aCode, bCode, mw: ma, mb }
    : { w: b, b: a, wCode: bCode, bCode: aCode, mw: mb, mb: ma };
}

const stub = () => {
  const hub = (rawEnv as unknown as { HUB: DurableObjectNamespace }).HUB;
  return hub.get(hub.idFromName("hub"));
};
const LIMIT = 30 * 60 * 1000;

/** Postavlja away vreme obe/jedne strane direktno u skladistu (deterministicki sat). */
async function setAway(color: "w" | "b", since: number | null) {
  await runInDurableObject(stub(), (_i, state) => {
    const col = color === "w" ? "white_away" : "black_away";
    state.storage.sql.exec(`UPDATE games SET ${col} = ?`, since);
  });
}
async function gameCount(): Promise<number> {
  return runInDurableObject(stub(), (_i, state) =>
    (state.storage.sql.exec("SELECT COUNT(*) AS n FROM games").one() as { n: number }).n);
}

describe("prekid veze i povratak", () => {
  it("zatvaranje veze igrača šalje protivniku opponent away", async () => {
    const g = await pair("RAAAA2", "RBBBB2");
    const got = next(g.b);
    g.w.close();
    expect(await got).toEqual({ t: "opponent", state: "away" });
    g.b.close();
  });

  it("resume sa ispravnim tokenom vraća boju i poteze i šalje protivniku opponent back", async () => {
    const g = await pair("RAAAA3", "RBBBB3");
    const fwd0 = next(g.b);
    await ask(g.w, { t: "move", ply: 0, uci: "e2e4" });
    await fwd0;
    const away = next(g.b);
    g.w.close();
    await away;
    const back = next(g.b);
    const w2 = await connect();
    expect(await ask(w2, { t: "resume", game: g.mw.game, token: g.mw.token })).toEqual({
      t: "resumed", game: g.mw.game, color: "w", moves: ["e2e4"], opponent: "online",
    });
    expect(await back).toEqual({ t: "opponent", state: "back" });
    // sedište je ponovo aktivno
    expect(await ask(w2, { t: "move", ply: 1, uci: "e7e5" })).toEqual({ t: "sync", moves: ["e2e4"] });
    w2.close(); g.b.close();
  });

  it("resume sa tuđim ili pogrešnim tokenom daje game_gone", async () => {
    const g = await pair("RAAAA4", "RBBBB4");
    const x = await connect();
    expect(await ask(x, { t: "resume", game: g.mw.game, token: "00".repeat(16) })).toEqual({ t: "error", reason: "game_gone" });
    expect(await ask(x, { t: "resume", game: "nepostoji", token: g.mw.token })).toEqual({ t: "error", reason: "game_gone" });
    x.close(); g.w.close(); g.b.close();
  });

  it("resume preuzima sedište: stari soket se zatvara, potez novog stiže protivniku", async () => {
    const g = await pair("RAAAA5", "RBBBB5");
    const old = closed(g.w);
    const w2 = await connect();
    expect(await ask(w2, { t: "resume", game: g.mw.game, token: g.mw.token })).toMatchObject({
      t: "resumed", color: "w", opponent: "online",
    });
    await old;
    const got = next(g.b);
    expect(await ask(w2, { t: "move", ply: 0, uci: "d2d4" })).toEqual({ t: "ack", ply: 0 });
    expect(await got).toEqual({ t: "move", ply: 0, uci: "d2d4" });
    w2.close(); g.b.close();
  });

  it("posle resign resume daje game_gone", async () => {
    const g = await pair("RAAAA6", "RBBBB6");
    const got = next(g.b);
    g.w.send(JSON.stringify({ t: "resign" }));
    await got;
    const x = await connect();
    expect(await ask(x, { t: "resume", game: g.mw.game, token: g.mw.token })).toEqual({ t: "error", reason: "game_gone" });
    x.close(); g.w.close(); g.b.close();
  });

  it("potez prisutne strane dok je druga odsutna stiže u resumed.moves", async () => {
    const g = await pair("RAAAA7", "RBBBB7");
    const away = next(g.w);
    g.b.close();
    expect(await away).toEqual({ t: "opponent", state: "away" });
    expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "ack", ply: 0 });
    const b2 = await connect();
    expect(await ask(b2, { t: "resume", game: g.mb.game, token: g.mb.token })).toEqual({
      t: "resumed", game: g.mb.game, color: "b", moves: ["e2e4"], opponent: "online",
    });
    b2.close(); g.w.close();
  });

  it("resumed javlja opponent away kad je protivnik odsutan", async () => {
    const g = await pair("RAAAA8", "RBBBB8");
    const away = next(g.b);
    g.w.close();
    await away;
    const b2 = await connect();
    g.b.close();
    const r = await ask(b2, { t: "resume", game: g.mb.game, token: g.mb.token });
    expect(r).toMatchObject({ t: "resumed", opponent: "away" });
    b2.close();
  });
});

describe("istek od 30 minuta", () => {
  it("alarm posle 30 min odsustva šalje aborted prisutnoj strani i briše partiju", async () => {
    const g = await pair("RAAAA9", "RBBBB9");
    const away = next(g.b);
    g.w.close();
    await away;
    await setAway("w", Date.now() - LIMIT - 1000);
    const got = next(g.b);
    expect(await runDurableObjectAlarm(stub())).toBe(true);
    expect(await got).toEqual({ t: "aborted" });
    expect(await gameCount()).toBe(0);
    expect(await ask(g.b, { t: "move", ply: 0, uci: "e7e5" })).toEqual({ t: "error", reason: "game_gone" });
    g.b.close();
  });

  it("alarm pre isteka ne briše partiju", async () => {
    const g = await pair("RAAAAA", "RBBBBB");
    const away = next(g.b);
    g.w.close();
    await away;
    await setAway("w", Date.now() - LIMIT + 60_000);
    const quiet = silent(g.b);
    await runDurableObjectAlarm(stub());
    expect(await quiet).toBe(true);
    expect(await gameCount()).toBe(1);
    g.b.close();
  });

  it("ako su obe strane odsutne duže od 30 min, alarm briše partiju bez poruka", async () => {
    const g = await pair("RAAAAB", "RBBBBC");
    const away = next(g.b);
    g.w.close();
    await away;
    g.b.close();
    await new Promise((r) => setTimeout(r, 100));
    await setAway("w", Date.now() - LIMIT - 2000);
    await setAway("b", Date.now() - LIMIT - 1000);
    await runDurableObjectAlarm(stub());
    expect(await gameCount()).toBe(0);
  });
});
