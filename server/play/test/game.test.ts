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

/** Vraca [beli, crni] soket, bez obzira ko je pozvao. */
async function pair(aCode: string, bCode: string) {
  const a = await join(aCode);
  const b = await join(bCode);
  const invited = next(b);
  await ask(a, { t: "invite", to: bCode });
  await invited;
  const sa = next(a);
  const sb = ask(b, { t: "accept", from: aCode });
  const [ma, mb] = [await sa, await sb];
  return ma.color === "w" ? { w: a, b, wCode: aCode, bCode, ma, mb } : { w: b, b: a, wCode: bCode, bCode: aCode, ma: mb, mb: ma };
}

describe("game", () => {
  it("potez belog na ply 0 stiže crnom, a beli dobija ack 0", async () => {
    const g = await pair("GAAAA2", "GBBBB2");
    const got = next(g.b);
    expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "ack", ply: 0 });
    expect(await got).toEqual({ t: "move", ply: 0, uci: "e2e4" });
    g.w.close(); g.b.close();
  });

  it("potez crnog kad je beli na redu daje sync i ne prosleđuje se", async () => {
    const g = await pair("GAAAA3", "GBBBB3");
    const quiet = silent(g.w);
    expect(await ask(g.b, { t: "move", ply: 0, uci: "e7e5" })).toEqual({ t: "sync", moves: [] });
    expect(await quiet).toBe(true);
    g.w.close(); g.b.close();
  });

  it("potez sa pogrešnim ply daje sync", async () => {
    const g = await pair("GAAAA4", "GBBBB4");
    const fwd = next(g.b);
    await ask(g.w, { t: "move", ply: 0, uci: "e2e4" });
    await fwd;
    expect(await ask(g.b, { t: "move", ply: 5, uci: "e7e5" })).toEqual({ t: "sync", moves: ["e2e4"] });
    g.w.close(); g.b.close();
  });

  it("promocija e7e8q se prosleđuje neizmenjena", async () => {
    const g = await pair("GAAAA5", "GBBBB5");
    const f1 = next(g.b);
    await ask(g.w, { t: "move", ply: 0, uci: "e2e4" });
    await f1;
    const f2 = next(g.w);
    await ask(g.b, { t: "move", ply: 1, uci: "e7e5" });
    await f2;
    const got = next(g.b);
    await ask(g.w, { t: "move", ply: 2, uci: "e7e8q" });
    expect(await got).toEqual({ t: "move", ply: 2, uci: "e7e8q" });
    g.w.close(); g.b.close();
  });

  it("resign šalje resigned protivniku i briše partiju", async () => {
    const g = await pair("GAAAA6", "GBBBB6");
    const got = next(g.b);
    g.w.send(JSON.stringify({ t: "resign" }));
    expect(await got).toEqual({ t: "resigned" });
    // oba koda su opet slobodna za poziv
    const invited = next(g.b);
    expect(await ask(g.w, { t: "invite", to: g.bCode })).toEqual({ t: "invite_sent", to: g.bCode });
    expect(await invited).toEqual({ t: "invited", from: g.wCode });
    g.w.close(); g.b.close();
  });

  it("potez posle resign daje error game_gone", async () => {
    const g = await pair("GAAAA7", "GBBBB7");
    const got = next(g.b);
    g.w.send(JSON.stringify({ t: "resign" }));
    await got;
    expect(await ask(g.b, { t: "move", ply: 0, uci: "e7e5" })).toEqual({ t: "error", reason: "game_gone" });
    expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "error", reason: "game_gone" });
    g.w.close(); g.b.close();
  });

  it("draw_offer pa draw_reply accept:true briše partiju", async () => {
    const g = await pair("GAAAA8", "GBBBB8");
    const offer = next(g.b);
    g.w.send(JSON.stringify({ t: "draw_offer" }));
    expect(await offer).toEqual({ t: "draw_offer" });
    const reply = next(g.w);
    g.b.send(JSON.stringify({ t: "draw_reply", accept: true }));
    expect(await reply).toEqual({ t: "draw_reply", accept: true });
    expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "error", reason: "game_gone" });
    g.w.close(); g.b.close();
  });

  it("draw_reply accept:false ne briše partiju", async () => {
    const g = await pair("GAAAA9", "GBBBB9");
    const offer = next(g.b);
    g.w.send(JSON.stringify({ t: "draw_offer" }));
    await offer;
    const reply = next(g.w);
    g.b.send(JSON.stringify({ t: "draw_reply", accept: false }));
    expect(await reply).toEqual({ t: "draw_reply", accept: false });
    expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "ack", ply: 0 });
    g.w.close(); g.b.close();
  });

  it("finished od bilo koje strane briše partiju", async () => {
    const g = await pair("GAAAAA", "GBBBBA");
    g.b.send(JSON.stringify({ t: "finished" }));
    expect(await ask(g.w, { t: "move", ply: 0, uci: "e2e4" })).toEqual({ t: "error", reason: "game_gone" });
    g.w.close(); g.b.close();
  });
});
