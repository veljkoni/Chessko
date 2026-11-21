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

function closed(ws: WebSocket): Promise<void> {
  return new Promise((resolve) => ws.addEventListener("close", () => resolve(), { once: true }));
}

describe("presence", () => {
  it("hello sa ispravnim kodom dobija welcome", async () => {
    const ws = await connect();
    expect(await ask(ws, { t: "hello", v: 1, code: "K7M4QX" })).toEqual({ t: "welcome", code: "K7M4QX" });
    ws.close();
  });

  it("hello sa nepoznatom verzijom dobija error version", async () => {
    const ws = await connect();
    expect(await ask(ws, { t: "hello", v: 3, code: "K7M4QX" })).toEqual({ t: "error", reason: "version" });
    ws.close();
  });

  it("hello sa neispravnim kodom dobija bad_code", async () => {
    for (const code of ["K7M4Q0", "k7m4qx", "K7M4QXX"]) {
      const ws = await connect();
      expect(await ask(ws, { t: "hello", v: 1, code })).toEqual({ t: "error", reason: "bad_code" });
      ws.close();
    }
  });

  it("drugi hello sa istim kodom dobija code_taken dok je prvi povezan", async () => {
    const a = await connect();
    expect(await ask(a, { t: "hello", v: 1, code: "AAAAAA" })).toEqual({ t: "welcome", code: "AAAAAA" });
    const b = await connect();
    expect(await ask(b, { t: "hello", v: 1, code: "AAAAAA" })).toEqual({ t: "error", reason: "code_taken" });
    a.close();
    b.close();
  });

  it("posle zatvaranja prve veze isti kod ponovo prolazi", async () => {
    const a = await connect();
    expect(await ask(a, { t: "hello", v: 1, code: "BBBBBB" })).toEqual({ t: "welcome", code: "BBBBBB" });
    const c = closed(a);
    a.close();
    await c;
    let reply: any;
    for (let i = 0; i < 20; i++) {
      const b = await connect();
      reply = await ask(b, { t: "hello", v: 1, code: "BBBBBB" });
      if (reply.t === "welcome") {
        b.close();
        break;
      }
      b.close();
      await new Promise((r) => setTimeout(r, 25));
    }
    expect(reply).toEqual({ t: "welcome", code: "BBBBBB" });
  });

  it("GET /health vraca 200 i ok", async () => {
    const res = await SELF.fetch("https://play.test/health");
    expect(res.status).toBe(200);
    expect(await res.text()).toBe("ok");
  });
});
