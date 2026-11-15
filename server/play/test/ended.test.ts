import { SELF, env as rawEnv, runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { describe, expect, it } from "vitest";

// Kraj partije dok je druga strana odsutna (I1) i sediste izgubljeno bez webSocketClose (I2).

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
const settle = () => new Promise((r) => setTimeout(r, 100));

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

// Skladiste se deli izmedju testova ovog fajla, pa sve ide po id-ju partije.
async function setAway(game: string, color: "w" | "b", since: number | null) {
  await runInDurableObject(stub(), (_i, state) => {
    const col = color === "w" ? "white_away" : "black_away";
    state.storage.sql.exec(`UPDATE games SET ${col} = ? WHERE id = ?`, since, game);
  });
}
async function away(game: string, color: "w" | "b"): Promise<number | null> {
  return runInDurableObject(stub(), (_i, state) => {
    const col = color === "w" ? "white_away" : "black_away";
    return (state.storage.sql.exec(`SELECT ${col} AS a FROM games WHERE id = ?`, game).one() as { a: number | null }).a;
  });
}
async function exists(game: string): Promise<boolean> {
  return runInDurableObject(stub(), (_i, state) =>
    (state.storage.sql.exec("SELECT COUNT(*) AS n FROM games WHERE id = ?", game).one() as { n: number }).n === 1);
}

/** Crni gubi vezu; beli to vidi. */
async function blackLeaves(g: Awaited<ReturnType<typeof pair>>) {
  const gone = next(g.w);
  g.b.close();
  expect(await gone).toEqual({ t: "opponent", state: "away" });
}

describe("kraj dok je protivnik odsutan (I1)", () => {
  it("predaja: odsutna strana posle povratka dobija resumed pa resigned, partija se briše", async () => {
    const g = await pair("EAAAA2", "EBBBB2");
    const f0 = next(g.b);
    await ask(g.w, { t: "move", ply: 0, uci: "e2e4" });
    await f0;
    await blackLeaves(g);
    const quiet = silent(g.w);
    g.w.send(JSON.stringify({ t: "resign" }));
    expect(await quiet).toBe(true);
    expect(await exists(g.mw.game)).toBe(true);
    // predavalac je slobodan: njegov potez vise nema partiju
    expect(await ask(g.w, { t: "move", ply: 1, uci: "d2d4" })).toEqual({ t: "error", reason: "game_gone" });

    const b2 = await connect();
    const msgs = take(b2, 2);
    b2.send(JSON.stringify({ t: "resume", game: g.mb.game, token: g.mb.token }));
    expect(await msgs).toEqual([
      { t: "resumed", game: g.mb.game, color: "b", moves: ["e2e4"], opponent: "away" },
      { t: "resigned" },
    ]);
    expect(await exists(g.mw.game)).toBe(false);
    expect(await ask(b2, { t: "resume", game: g.mb.game, token: g.mb.token })).toEqual({ t: "error", reason: "game_gone" });
    b2.close(); g.w.close();
  });

  it("prihvaćen remi: ponuđač koji je otišao dobija resumed pa draw_reply accept", async () => {
    const g = await pair("EAAAA3", "EBBBB3");
    const offer = next(g.b);
    g.w.send(JSON.stringify({ t: "draw_offer" }));
    expect(await offer).toEqual({ t: "draw_offer" });
    const gone = next(g.b);
    g.w.close();
    expect(await gone).toEqual({ t: "opponent", state: "away" });
    g.b.send(JSON.stringify({ t: "draw_reply", accept: true }));
    await settle();
    expect(await exists(g.mw.game)).toBe(true);

    const w2 = await connect();
    const msgs = take(w2, 2);
    w2.send(JSON.stringify({ t: "resume", game: g.mw.game, token: g.mw.token }));
    expect(await msgs).toEqual([
      { t: "resumed", game: g.mw.game, color: "w", moves: [], opponent: "away" },
      { t: "draw_reply", accept: true },
    ]);
    expect(await exists(g.mw.game)).toBe(false);
    w2.close(); g.b.close();
  });

  it("finished: odsutna strana dobija samo resumed sa svim potezima (kraj se vidi na tabli)", async () => {
    const g = await pair("EAAAA4", "EBBBB4");
    const moves = ["f2f3", "e7e5", "g2g4", "d8h4"];
    for (let ply = 0; ply < moves.length; ply++) {
      const [me, other] = ply % 2 === 0 ? [g.w, g.b] : [g.b, g.w];
      const fwd = next(other);
      expect(await ask(me, { t: "move", ply, uci: moves[ply] })).toEqual({ t: "ack", ply });
      await fwd;
    }
    const gone = next(g.b);
    g.w.close();
    await gone;
    g.b.send(JSON.stringify({ t: "finished" }));
    await settle();
    expect(await exists(g.mw.game)).toBe(true);

    const w2 = await connect();
    expect(await ask(w2, { t: "resume", game: g.mw.game, token: g.mw.token })).toEqual({
      t: "resumed", game: g.mw.game, color: "w", moves, opponent: "away",
    });
    expect(await silent(w2)).toBe(true);
    expect(await exists(g.mw.game)).toBe(false);
    // klijent posle table salje finished; partije vise nema
    expect(await ask(w2, { t: "finished" })).toEqual({ t: "error", reason: "game_gone" });
    w2.close(); g.b.close();
  });

  it("strana koja je završila partiju ne dobija kraj ponovo (game_gone, red ostaje za odsutnog)", async () => {
    const g = await pair("EAAAA5", "EBBBB5");
    await blackLeaves(g);
    g.w.send(JSON.stringify({ t: "resign" }));
    await settle();
    const w2 = await connect();
    expect(await ask(w2, { t: "resume", game: g.mw.game, token: g.mw.token })).toEqual({ t: "error", reason: "game_gone" });
    expect(await exists(g.mw.game)).toBe(true);
    w2.close(); g.w.close();
  });

  it("predavalac posle kraja odmah može da prima pozive", async () => {
    const g = await pair("EAAAA6", "EBBBB6");
    await blackLeaves(g);
    g.w.send(JSON.stringify({ t: "resign" }));
    await settle();
    const c = await join("ECCCC6");
    const invited = next(g.w);
    expect(await ask(c, { t: "invite", to: g.wCode })).toEqual({ t: "invite_sent", to: g.wCode });
    expect(await invited).toEqual({ t: "invited", from: "ECCCC6" });
    c.close(); g.w.close();
  });

  it("ako se odsutna strana ne vrati, alarm posle 30 min briše završenu partiju bez poruka", async () => {
    const g = await pair("EAAAA7", "EBBBB7");
    await blackLeaves(g);
    g.w.send(JSON.stringify({ t: "resign" }));
    await settle();
    expect(await away(g.mw.game, "b")).not.toBeNull();
    await setAway(g.mw.game, "b", Date.now() - LIMIT - 1000);
    const quiet = silent(g.w);
    expect(await runDurableObjectAlarm(stub())).toBe(true);
    expect(await quiet).toBe(true);
    expect(await exists(g.mw.game)).toBe(false);
    g.w.close();
  });
});

describe("sedište izgubljeno bez webSocketClose (I2)", () => {
  it("resume označava protivnika odsutnim kad nema ni vezu ni oznaku, pa alarm prekida partiju", async () => {
    const g = await pair("NAAAA2", "NBBBB2");
    const gone = next(g.b);
    g.w.close();
    await gone;
    await setAway(g.mw.game, "w", null); // kao da webSocketClose nikad nije stigao
    g.b.close();
    await settle();
    await setAway(g.mw.game, "w", null);
    await setAway(g.mw.game, "b", null);

    const b2 = await connect();
    expect(await ask(b2, { t: "resume", game: g.mb.game, token: g.mb.token })).toMatchObject({
      t: "resumed", opponent: "away",
    });
    const stamp = await away(g.mw.game, "w");
    expect(stamp).not.toBeNull();
    expect(await away(g.mw.game, "b")).toBeNull();

    await setAway(g.mw.game, "w", Date.now() - LIMIT - 1000);
    const got = next(b2);
    expect(await runDurableObjectAlarm(stub())).toBe(true);
    expect(await got).toEqual({ t: "aborted" });
    expect(await exists(g.mw.game)).toBe(false);
    b2.close();
  });

  it("potez prisutne strane označava protivnika bez veze i bez oznake", async () => {
    const g = await pair("NAAAA3", "NBBBB3");
    const gone = next(g.w);
    g.b.close();
    await gone;
    await setAway(g.mw.game, "b", null);
    expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "ack", ply: 0 });
    expect(await away(g.mw.game, "b")).not.toBeNull();
    g.w.close();
  });

  it("posle restarta svaka partija bez ijedne veze dobija oznake (stampOrphanSeats)", async () => {
    const g = await pair("NAAAA4", "NBBBB4");
    g.w.close();
    g.b.close();
    await settle();
    await setAway(g.mw.game, "w", null);
    await setAway(g.mw.game, "b", null);
    const stamped = await runInDurableObject(stub(), (instance) =>
      (instance as unknown as { stampOrphanSeats(): boolean }).stampOrphanSeats());
    expect(stamped).toBe(true);
    expect(await away(g.mw.game, "w")).not.toBeNull();
    expect(await away(g.mw.game, "b")).not.toBeNull();
  });

  it("stampOrphanSeats ne dira sedišta koja imaju vezu", async () => {
    const g = await pair("NAAAA5", "NBBBB5");
    const stamped = await runInDurableObject(stub(), (instance) =>
      (instance as unknown as { stampOrphanSeats(game?: string): boolean }).stampOrphanSeats(g.mw.game));
    expect(stamped).toBe(false);
    expect(await away(g.mw.game, "w")).toBeNull();
    expect(await away(g.mw.game, "b")).toBeNull();
    g.w.close(); g.b.close();
  });
});
