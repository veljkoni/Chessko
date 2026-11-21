import { SELF, env as rawEnv, runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { describe, expect, it } from "vitest";

// Kraj partije na vreme: istek (flag), rok prvog poteza, kraj potezom (end:true),
// odsustvo u partiji sa satom i povratak posle isteka.

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
/** Sledecih `n` poruka, redom. */
function take(ws: WebSocket, n: number): Promise<any[]> {
  return new Promise((resolve) => {
    const got: any[] = [];
    const h = (e: MessageEvent) => {
      got.push(JSON.parse(e.data as string));
      if (got.length === n) {
        ws.removeEventListener("message", h);
        resolve(got);
      }
    };
    ws.addEventListener("message", h);
  });
}
async function ask(ws: WebSocket, msg: object): Promise<any> {
  const p = next(ws);
  ws.send(JSON.stringify(msg));
  return p;
}
async function join(code: string, v = 2): Promise<WebSocket> {
  const ws = await connect();
  expect(await ask(ws, { t: "hello", v, code })).toEqual({ t: "welcome", code });
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
const settle = () => new Promise((r) => setTimeout(r, 100));

const C = { base: 180, inc: 2 };

async function pair(aCode: string, bCode: string, clock: object | null = C, av = 2, bv = 2) {
  const a = await join(aCode, av);
  const b = await join(bCode, bv);
  const invited = next(b);
  await ask(a, clock ? { t: "invite", to: bCode, clock } : { t: "invite", to: bCode });
  await invited;
  const sa = next(a);
  const sb = ask(b, clock ? { t: "accept", from: aCode, clock } : { t: "accept", from: aCode });
  const [ma, mb] = [await sa, await sb];
  return ma.color === "w"
    ? { w: a, b, mw: ma, mb, game: ma.game as string, aIsWhite: true }
    : { w: b, b: a, mw: mb, mb: ma, game: ma.game as string, aIsWhite: false };
}
type Pair = Awaited<ReturnType<typeof pair>>;

const stub = () => {
  const hub = (rawEnv as unknown as { HUB: DurableObjectNamespace }).HUB;
  return hub.get(hub.idFromName("hub"));
};

// Skladiste se deli izmedju testova ovog fajla, pa sve ide po id-ju partije.
async function sql(q: string, ...args: unknown[]): Promise<void> {
  await runInDurableObject(stub(), (_i, state) => {
    state.storage.sql.exec(q, ...args);
  });
}
async function row(game: string): Promise<Record<string, any> | undefined> {
  return runInDurableObject(stub(), (_i, state) =>
    state.storage.sql.exec("SELECT * FROM games WHERE id = ?", game).toArray()[0] as Record<string, any> | undefined);
}
const exists = async (game: string) => (await row(game)) !== undefined;

/** e2e4 pa e7e5: beli je na potezu i njegov sat tece. */
async function openTwo(g: Pair) {
  const f0 = next(g.b);
  await ask(g.w, { t: "move", ply: 0, uci: "e2e4" });
  await f0;
  const f1 = next(g.w);
  await ask(g.b, { t: "move", ply: 1, uci: "e7e5" });
  await f1;
}

describe("istek vremena (flag)", () => {
  it("potez posle roka: obe strane dobijaju flag za onoga ko je igrao, protivnik ne dobija move, red obrisan", async () => {
    const g = await pair("TAAAA2", "TBBBB2");
    await openTwo(g);
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 181_000, g.game);
    const fb = next(g.b);
    expect(await ask(g.w, { t: "move", ply: 2, uci: "g1f3" })).toEqual({ t: "flag", loser: "w" });
    expect(await fb).toEqual({ t: "flag", loser: "w" });
    expect(await silent(g.b)).toBe(true);
    expect(await exists(g.game)).toBe(false);
    g.w.close(); g.b.close();
  });

  it("alarm posle roka strane na potezu (obe na vezi): obe dobijaju flag, red obrisan", async () => {
    const g = await pair("TAAAA3", "TBBBB3");
    await openTwo(g);
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 181_000, g.game);
    const fw = next(g.w);
    const fb = next(g.b);
    await runDurableObjectAlarm(stub());
    expect(await fw).toEqual({ t: "flag", loser: "w" });
    expect(await fb).toEqual({ t: "flag", loser: "w" });
    expect(await exists(g.game)).toBe(false);
    g.w.close(); g.b.close();
  });

  it("alarm pre roka ne radi ništa", async () => {
    const g = await pair("TAAAA4", "TBBBB4");
    await openTwo(g);
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 60_000, g.game);
    const qw = silent(g.w);
    const qb = silent(g.b);
    await runDurableObjectAlarm(stub());
    expect(await qw).toBe(true);
    expect(await qb).toBe(true);
    expect(await exists(g.game)).toBe(true);
    g.w.close(); g.b.close();
  });

  it("obe strane odsutne pri isteku: svaka posle povratka dobija resumed pa flag; posle druge red obrisan", async () => {
    const g = await pair("TAAAA5", "TBBBB5");
    await openTwo(g);
    const gone = next(g.w);
    g.b.close();
    expect(await gone).toEqual({ t: "opponent", state: "away" });
    g.w.close();
    await settle();
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 181_000, g.game);
    await runDurableObjectAlarm(stub());
    expect(await exists(g.game)).toBe(true);

    const w2 = await join(g.mb.opponent);
    const mw = take(w2, 2);
    w2.send(JSON.stringify({ t: "resume", game: g.game, token: g.mw.token }));
    const [rw, fw] = await mw;
    expect(rw).toMatchObject({ t: "resumed", game: g.game, color: "w", moves: ["e2e4", "e7e5"], opponent: "away" });
    expect(rw.clock.w).toBe(0);
    expect(fw).toEqual({ t: "flag", loser: "w" });
    expect(await exists(g.game)).toBe(true);

    const b2 = await join(g.mw.opponent);
    const mb = take(b2, 2);
    b2.send(JSON.stringify({ t: "resume", game: g.game, token: g.mb.token }));
    const [rb, fb] = await mb;
    expect(rb).toMatchObject({ t: "resumed", game: g.game, color: "b", moves: ["e2e4", "e7e5"] });
    expect(fb).toEqual({ t: "flag", loser: "w" });
    expect(await exists(g.game)).toBe(false);

    const x = await connect();
    expect(await ask(x, { t: "resume", game: g.game, token: g.mw.token })).toEqual({ t: "error", reason: "game_gone" });
    w2.close(); b2.close(); x.close();
  });

  it("strana koja je izgubila na vreme dok je bila odsutna dobija flag posle povratka; prisutna je odmah slobodna", async () => {
    const g = await pair("TAAAA6", "TBBBB6");
    await openTwo(g);
    const gone = next(g.b);
    g.w.close();
    expect(await gone).toEqual({ t: "opponent", state: "away" });
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 181_000, g.game);
    const fb = next(g.b);
    await runDurableObjectAlarm(stub());
    expect(await fb).toEqual({ t: "flag", loser: "w" });
    // prisutna strana nema vise partiju
    expect(await ask(g.b, { t: "move", ply: 2, uci: "g8f6" })).toEqual({ t: "error", reason: "game_gone" });

    const w2 = await join(g.mb.opponent);
    const mw = take(w2, 2);
    w2.send(JSON.stringify({ t: "resume", game: g.game, token: g.mw.token }));
    const [rw, fw] = await mw;
    expect(rw).toMatchObject({ t: "resumed", color: "w" });
    expect(fw).toEqual({ t: "flag", loser: "w" });
    expect(await exists(g.game)).toBe(false);
    expect(await ask(g.b, { t: "resume", game: g.game, token: g.mb.token })).toEqual({ t: "error", reason: "game_gone" });
    w2.close(); g.b.close();
  });
});

describe("rok prvog poteza", () => {
  it("beli ne odigra prvi potez za 30 s: obe strane first_move_expired, red obrisan", async () => {
    const g = await pair("TAAAA7", "TBBBB7");
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 31_000, g.game);
    const fw = next(g.w);
    const fb = next(g.b);
    await runDurableObjectAlarm(stub());
    expect(await fw).toEqual({ t: "first_move_expired" });
    expect(await fb).toEqual({ t: "first_move_expired" });
    expect(await exists(g.game)).toBe(false);
    g.w.close(); g.b.close();
  });

  it("crni ne odigra prvi potez za 30 s posle poteza belog: first_move_expired", async () => {
    const g = await pair("TAAAA8", "TBBBB8");
    const f0 = next(g.b);
    await ask(g.w, { t: "move", ply: 0, uci: "e2e4" });
    await f0;
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 31_000, g.game);
    const fw = next(g.w);
    const fb = next(g.b);
    await runDurableObjectAlarm(stub());
    expect(await fw).toEqual({ t: "first_move_expired" });
    expect(await fb).toEqual({ t: "first_move_expired" });
    expect(await exists(g.game)).toBe(false);
    g.w.close(); g.b.close();
  });

  it("prvi potez posle roka: first_move_expired obema, protivnik ne dobija move", async () => {
    const g = await pair("TAAAA9", "TBBBB9");
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 31_000, g.game);
    const fb = next(g.b);
    expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "first_move_expired" });
    expect(await fb).toEqual({ t: "first_move_expired" });
    expect(await exists(g.game)).toBe(false);
    g.w.close(); g.b.close();
  });

  it("odsutna strana posle isteka roka prvog poteza dobija game_gone", async () => {
    const g = await pair("TAAAAA", "TBBBBA");
    const gone = next(g.b);
    g.w.close();
    expect(await gone).toEqual({ t: "opponent", state: "away" });
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 31_000, g.game);
    const fb = next(g.b);
    await runDurableObjectAlarm(stub());
    expect(await fb).toEqual({ t: "first_move_expired" });
    expect(await exists(g.game)).toBe(false);
    const w2 = await join(g.mb.opponent);
    expect(await ask(w2, { t: "resume", game: g.game, token: g.mw.token })).toEqual({ t: "error", reason: "game_gone" });
    w2.close(); g.b.close();
  });
});

describe("kraj potezom (end:true)", () => {
  it("potez sa end:true na vreme: protivnik dobija move sa end:true, red obrisan, kasniji alarm ništa ne šalje", async () => {
    const g = await pair("TAAAAB", "TBBBBB");
    await openTwo(g);
    const fb = next(g.b);
    const ack = await ask(g.w, { t: "move", ply: 2, uci: "d1h5", end: true });
    expect(ack).toMatchObject({ t: "ack", ply: 2 });
    const mv = await fb;
    expect(mv).toMatchObject({ t: "move", ply: 2, uci: "d1h5", end: true });
    expect(await exists(g.game)).toBe(false);
    const qw = silent(g.w);
    const qb = silent(g.b);
    await runDurableObjectAlarm(stub());
    expect(await qw).toBe(true);
    expect(await qb).toBe(true);
    g.w.close(); g.b.close();
  });

  it("end:true dok je protivnik odsutan: posle povratka resumed bez dodatne poruke, pa brisanje", async () => {
    const g = await pair("TAAAAC", "TBBBBC");
    await openTwo(g);
    const gone = next(g.w);
    g.b.close();
    expect(await gone).toEqual({ t: "opponent", state: "away" });
    expect(await ask(g.w, { t: "move", ply: 2, uci: "d1h5", end: true })).toMatchObject({ t: "ack", ply: 2 });
    expect(await exists(g.game)).toBe(true);
    const b2 = await join(g.mw.opponent);
    const r = await ask(b2, { t: "resume", game: g.game, token: g.mb.token });
    expect(r).toMatchObject({ t: "resumed", moves: ["e2e4", "e7e5", "d1h5"] });
    expect(await silent(b2)).toBe(true);
    expect(await exists(g.game)).toBe(false);
    b2.close(); g.w.close();
  });
});

describe("odsustvo u partiji sa satom", () => {
  it("30 min odsustva ne briše partiju sa satom čiji rok nije istekao", async () => {
    const g = await pair("TAAAAD", "TBBBBD");
    const gone = next(g.b);
    g.w.close();
    expect(await gone).toEqual({ t: "opponent", state: "away" });
    await sql("UPDATE games SET white_away = ?, turn_started = ? WHERE id = ?", Date.now() - 31 * 60_000, Date.now(), g.game);
    const quiet = silent(g.b);
    await runDurableObjectAlarm(stub());
    expect(await quiet).toBe(true);
    expect(await exists(g.game)).toBe(true);
    g.b.close();
  });

  it("sat odsutne strane na potezu teče: resume posle 4 s daje w ≈ wMs − 4000, b netaknut", async () => {
    const g = await pair("TAAAAE", "TBBBBE");
    await openTwo(g);
    const gone = next(g.b);
    g.w.close();
    expect(await gone).toEqual({ t: "opponent", state: "away" });
    const r0 = (await row(g.game))!;
    await sql("UPDATE games SET turn_started = ? WHERE id = ?", Date.now() - 4000, g.game);
    const w2 = await join(g.mb.opponent);
    const r = await ask(w2, { t: "resume", game: g.game, token: g.mw.token });
    expect(r.t).toBe("resumed");
    expect(r.clock.run).toBe("w");
    expect(Math.abs(r.clock.w - (r0.w_ms - 4000))).toBeLessThanOrEqual(1000);
    expect(r.clock.b).toBe(r0.b_ms);
    w2.close(); g.b.close();
  });
});

describe("v1 ostaje bez sata", () => {
  it("v1 sa clock u invite: nema bad_clock ni needs_update, poziv bez sata, partija bez sata", async () => {
    const a = await join("TAAAAF", 1);
    const b = await join("TBBBBF", 2);
    const inv = next(b);
    expect(await ask(a, { t: "invite", to: "TBBBBF", clock: { base: 180, inc: 3 } })).toEqual({ t: "invite_sent", to: "TBBBBF" });
    expect(await inv).toEqual({ t: "invited", from: "TAAAAF" });
    const sa = next(a);
    const sb = ask(b, { t: "accept", from: "TAAAAF" });
    const [ma, mb] = [await sa, await sb];
    expect(ma.clock).toBeUndefined();
    expect(mb.clock).toBeUndefined();
    const r = await row(ma.game);
    expect(r!.clock_base).toBeNull();
    a.close(); b.close();
  });

  it("v1 poziva v1 sa ispravnim satom: nema needs_update, poziv bez sata", async () => {
    const a = await join("TAAAAG", 1);
    const b = await join("TBBBBG", 1);
    const inv = next(b);
    expect(await ask(a, { t: "invite", to: "TBBBBG", clock: C })).toEqual({ t: "invite_sent", to: "TBBBBG" });
    expect(await inv).toEqual({ t: "invited", from: "TAAAAG" });
    a.close(); b.close();
  });

  it("v1 accept sa clock se ignoriše kao da clock nije poslat", async () => {
    const a = await join("TAAAAH", 2);
    const b = await join("TBBBBH", 1);
    const inv = next(b);
    await ask(a, { t: "invite", to: "TBBBBH" });
    await inv;
    const sa = next(a);
    const sb = ask(b, { t: "accept", from: "TAAAAH", clock: C });
    const [ma, mb] = [await sa, await sb];
    expect(ma.t).toBe("start");
    expect(mb.t).toBe("start");
    expect(ma.clock).toBeUndefined();
    expect((await row(ma.game))!.clock_base).toBeNull();
    a.close(); b.close();
  });

  it("end:true od v2 u partiji bez sata stiže v1 protivniku bez end i ništa ne završava", async () => {
    for (let i = 0; i < 4; i++) {
      // "a" je v2, "b" je v1; boje su nasumicne, pa se ponavlja dok se ne vide obe
      const g = await pair(`TJ${i + 2}AA2`, `TJ${i + 2}BB2`, null, 2, 1);
      const [v2, v1] = g.aIsWhite ? [g.w, g.b] : [g.b, g.w];
      if (!g.aIsWhite) {
        const f0 = next(g.b);
        await ask(g.w, { t: "move", ply: 0, uci: "e2e4" });
        await f0;
      }
      const ply = g.aIsWhite ? 0 : 1;
      const uci = g.aIsWhite ? "e2e4" : "e7e5";
      const got = next(v1);
      expect(await ask(v2, { t: "move", ply, uci, end: true })).toEqual({ t: "ack", ply });
      expect(await got).toEqual({ t: "move", ply, uci });
      expect(await exists(g.game)).toBe(true);
      const back = next(v2);
      const reply = g.aIsWhite ? "e7e5" : "g1f3";
      expect(await ask(v1, { t: "move", ply: ply + 1, uci: reply })).toEqual({ t: "ack", ply: ply + 1 });
      expect(await back).toEqual({ t: "move", ply: ply + 1, uci: reply });
      g.w.close(); g.b.close();
    }
  });
});
