import { SELF } from "cloudflare:test";
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
async function join(code: string, v = 1): Promise<WebSocket> {
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

/** Vraca [beli, crni] soket. clock = sat poziva (i prihvatanja). */
async function pair(aCode: string, bCode: string, av: number, bv: number, clock?: object) {
  const a = await join(aCode, av);
  const b = await join(bCode, bv);
  const invited = next(b);
  await ask(a, clock ? { t: "invite", to: bCode, clock } : { t: "invite", to: bCode });
  await invited;
  const sa = next(a);
  const sb = ask(b, clock ? { t: "accept", from: aCode, clock } : { t: "accept", from: aCode });
  const [ma, mb] = [await sa, await sb];
  return ma.color === "w" ? { w: a, b, ma, mb } : { w: b, b: a, ma: mb, mb: ma };
}

const C = { base: 180, inc: 2 };

describe("protokol v2", () => {
  it("hello v:2 daje welcome, v:3 daje error version", async () => {
    const ok = await connect();
    expect(await ask(ok, { t: "hello", v: 2, code: "CAAAA2" })).toEqual({ t: "welcome", code: "CAAAA2" });
    const bad = await connect();
    expect(await ask(bad, { t: "hello", v: 3, code: "CAAAA3" })).toEqual({ t: "error", reason: "version" });
    ok.close(); bad.close();
  });

  it("v2 poziva v2 sa satom: invited nosi clock", async () => {
    const a = await join("CBAAA2", 2);
    const b = await join("CBBBB2", 2);
    const inv = next(b);
    await ask(a, { t: "invite", to: "CBBBB2", clock: C });
    expect(await inv).toEqual({ t: "invited", from: "CBAAA2", clock: C });
    a.close(); b.close();
  });

  it("nedozvoljen sat daje bad_clock, pozvani ne dobija ništa", async () => {
    const a = await join("CCAAA2", 2);
    const b = await join("CCBBB2", 2);
    const quiet = silent(b);
    expect(await ask(a, { t: "invite", to: "CCBBB2", clock: { base: 180, inc: 3 } })).toEqual({ t: "error", reason: "bad_clock" });
    expect(await quiet).toBe(true);
    a.close(); b.close();
  });

  it("v2 poziva v1 sa satom: needs_update, pozvani ćuti", async () => {
    const a = await join("CDAAA2", 2);
    const b = await join("CDBBB2", 1);
    const quiet = silent(b);
    expect(await ask(a, { t: "invite", to: "CDBBB2", clock: C })).toEqual({ t: "error", reason: "needs_update" });
    expect(await quiet).toBe(true);
    a.close(); b.close();
  });

  it("accept bez sata ili sa drugim satom je ignorisan; isti sat startuje", async () => {
    const a = await join("CEAAA2", 2);
    const b = await join("CEBBB2", 2);
    const inv = next(b);
    await ask(a, { t: "invite", to: "CEBBB2", clock: C });
    await inv;
    const qa = silent(a);
    b.send(JSON.stringify({ t: "accept", from: "CEAAA2" }));
    b.send(JSON.stringify({ t: "accept", from: "CEAAA2", clock: { base: 600, inc: 5 } }));
    expect(await qa).toBe(true);
    const sa = next(a);
    const sb = ask(b, { t: "accept", from: "CEAAA2", clock: C });
    const [ma, mb] = [await sa, await sb];
    for (const m of [ma, mb]) {
      expect(m.t).toBe("start");
      expect(m.clock.base).toBe(180);
      expect(m.clock.inc).toBe(2);
      expect(m.clock.w).toBe(180000);
      expect(m.clock.b).toBe(180000);
      expect(m.clock.run).toBeNull();
      expect(Math.abs(m.clock.first - 30000)).toBeLessThanOrEqual(1000);
    }
    a.close(); b.close();
  });

  it("v1 i v2 bez sata: start je tačno staro, move/ack bez clock i end", async () => {
    for (const [i, [av, bv]] of ([[1, 2], [2, 1], [1, 1], [2, 2]] as [number, number][]).entries()) {
      const g = await pair(`CF${i + 2}AA2`, `CF${i + 2}BB2`, av, bv);
      for (const m of [g.ma, g.mb]) {
        expect(Object.keys(m).sort()).toEqual(["color", "game", "opponent", "t", "token"]);
      }
      const got = next(g.b);
      expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "ack", ply: 0 });
      expect(await got).toEqual({ t: "move", ply: 0, uci: "e2e4" });
      g.w.close(); g.b.close();
    }
  });

  it("partija sa satom: ack i move nose clock; crni prvi potez pokreće sat belog", async () => {
    const g = await pair("CGAAA2", "CGBBB2", 2, 2, C);
    const got = next(g.b);
    const ack = await ask(g.w, { t: "move", ply: 0, uci: "e2e4" });
    expect(ack.t).toBe("ack");
    expect(ack.clock.run).toBeNull();
    expect(Math.abs(ack.clock.first - 30000)).toBeLessThanOrEqual(1000);
    const mv = await got;
    expect(mv).toMatchObject({ t: "move", ply: 0, uci: "e2e4" });
    expect(mv.clock.run).toBeNull();
    expect(Math.abs(mv.clock.first - 30000)).toBeLessThanOrEqual(1000);
    expect(mv.end).toBeUndefined();

    const got2 = next(g.w);
    const ack2 = await ask(g.b, { t: "move", ply: 1, uci: "e7e5", end: true });
    expect(ack2.t).toBe("ack");
    expect(ack2.clock.run).toBe("w");
    expect(ack2.clock.first).toBeNull();
    const mv2 = await got2;
    expect(mv2.clock.run).toBe("w");
    expect(mv2.clock.first).toBeNull();
    expect(mv2.end).toBe(true);
    g.w.close(); g.b.close();
  });
});
