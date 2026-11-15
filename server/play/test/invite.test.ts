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

async function startGame(a: WebSocket, aCode: string, b: WebSocket, bCode: string) {
  const invited = next(b);
  const sent = ask(a, { t: "invite", to: bCode });
  await sent;
  await invited;
  const sa = next(a);
  const sb = ask(b, { t: "accept", from: aCode });
  return [await sa, await sb] as const;
}

describe("invites", () => {
  it("poziv povezanom kodu stiže kao invited, a pozivalac dobija invite_sent", async () => {
    const a = await join("AAAAA2");
    const b = await join("BBBBB2");
    const invited = next(b);
    expect(await ask(a, { t: "invite", to: "BBBBB2" })).toEqual({ t: "invite_sent", to: "BBBBB2" });
    expect(await invited).toEqual({ t: "invited", from: "AAAAA2" });
    a.close();
    b.close();
  });

  it("poziv kodu koji nije povezan daje offline", async () => {
    const a = await join("AAAAA3");
    expect(await ask(a, { t: "invite", to: "ZZZZZ3" })).toEqual({ t: "error", reason: "offline" });
    a.close();
  });

  it("poziv sopstvenom kodu daje self", async () => {
    const a = await join("AAAAA4");
    expect(await ask(a, { t: "invite", to: "AAAAA4" })).toEqual({ t: "error", reason: "self" });
    a.close();
  });

  it("poziv neispravnom kodu daje bad_code", async () => {
    const a = await join("AAAAA5");
    expect(await ask(a, { t: "invite", to: "abc" })).toEqual({ t: "error", reason: "bad_code" });
    a.close();
  });

  it("poziv kodu koji je već u partiji daje busy", async () => {
    const a = await join("AAAAA6");
    const b = await join("BBBBB6");
    await startGame(a, "AAAAA6", b, "BBBBB6");
    const c = await join("CCCCC6");
    expect(await ask(c, { t: "invite", to: "BBBBB6" })).toEqual({ t: "error", reason: "busy" });
    a.close();
    b.close();
    c.close();
  });

  it("drugi poziv istom kodu dok prvi čeka daje busy drugom pozivaocu", async () => {
    const a = await join("AAAAA7");
    const b = await join("BBBBB7");
    const c = await join("CCCCC7");
    expect(await ask(a, { t: "invite", to: "BBBBB7" })).toEqual({ t: "invite_sent", to: "BBBBB7" });
    expect(await ask(c, { t: "invite", to: "BBBBB7" })).toEqual({ t: "error", reason: "busy" });
    a.close();
    b.close();
    c.close();
  });

  it("accept daje start obema stranama", async () => {
    const a = await join("AAAAA8");
    const b = await join("BBBBB8");
    const [sa, sb] = await startGame(a, "AAAAA8", b, "BBBBB8");
    expect(sa.t).toBe("start");
    expect(sb.t).toBe("start");
    expect(sa.opponent).toBe("BBBBB8");
    expect(sb.opponent).toBe("AAAAA8");
    expect(sa.game).toBe(sb.game);
    expect(sa.color).not.toBe(sb.color);
    expect(["w", "b"]).toContain(sa.color);
    expect(sa.token).not.toBe(sb.token);
    expect(sa.token).toMatch(/^[0-9a-f]{32}$/);
    expect(sb.token).toMatch(/^[0-9a-f]{32}$/);
    a.close();
    b.close();
  });

  it("decline daje declined pozivaocu i oslobađa oba koda", async () => {
    const a = await join("AAAAA9");
    const b = await join("BBBBB9");
    const invited = next(b);
    await ask(a, { t: "invite", to: "BBBBB9" });
    await invited;
    const declined = next(a);
    b.send(JSON.stringify({ t: "decline", from: "AAAAA9" }));
    expect(await declined).toEqual({ t: "declined", by: "BBBBB9" });
    // oba koda su slobodna: novi poziv u oba smera prolazi
    const invited2 = next(a);
    expect(await ask(b, { t: "invite", to: "AAAAA9" })).toEqual({ t: "invite_sent", to: "AAAAA9" });
    expect(await invited2).toEqual({ t: "invited", from: "BBBBB9" });
    a.close();
    b.close();
  });

  it("cancel daje invite_cancelled pozvanom", async () => {
    const a = await join("AAAAB2");
    const b = await join("BBBBB3");
    const invited = next(b);
    await ask(a, { t: "invite", to: "BBBBB3" });
    await invited;
    const cancelled = next(b);
    a.send(JSON.stringify({ t: "cancel" }));
    expect(await cancelled).toEqual({ t: "invite_cancelled", from: "AAAAB2" });
    a.close();
    b.close();
  });

  it("11. poziv u istom minutu daje rate_limited", async () => {
    const a = await join("AAAAB3");
    for (let i = 0; i < 10; i++) {
      expect(await ask(a, { t: "invite", to: "ZZZZZ2" })).toEqual({ t: "error", reason: "offline" });
    }
    expect(await ask(a, { t: "invite", to: "ZZZZZ2" })).toEqual({ t: "error", reason: "rate_limited" });
    a.close();
  });

  it("drugi hello na istom soketu se ignoriše", async () => {
    const a = await join("AAAAB4");
    a.send(JSON.stringify({ t: "hello", v: 1, code: "CCCCB4" }));
    expect(await silent(a)).toBe(true);
    // kod je i dalje AAAAB4: drugi soket ga ne može uzeti, a CCCCB4 je slobodan
    const b = await connect();
    expect(await ask(b, { t: "hello", v: 1, code: "AAAAB4" })).toEqual({ t: "error", reason: "code_taken" });
    const c = await connect();
    expect(await ask(c, { t: "hello", v: 1, code: "CCCCB4" })).toEqual({ t: "welcome", code: "CCCCB4" });
    a.close();
    b.close();
    c.close();
  });

  it("pozvani koji ima poziv na čekanju ne može da pozove drugog (busy)", async () => {
    const a = await join("AAAAC2");
    const b = await join("BBBBC2");
    const c = await join("CCCCC2");
    const invited = next(b);
    await ask(c, { t: "invite", to: "BBBBC2" });
    await invited;
    expect(await ask(b, { t: "invite", to: "AAAAC2" })).toEqual({ t: "error", reason: "busy" });
    // B i dalje može da prihvati C-ov poziv
    const sc = next(c);
    const sb = await ask(b, { t: "accept", from: "CCCCC2" });
    expect(sb.t).toBe("start");
    expect((await sc).t).toBe("start");
    a.close();
    b.close();
    c.close();
  });

  it("kad pozvani prekine vezu, pozivalac dobija offline i oslobađa se", async () => {
    const a = await join("AAAAC3");
    const b = await join("BBBBC4");
    const d = await join("DDDDC3");
    const invited = next(b);
    await ask(a, { t: "invite", to: "BBBBC4" });
    await invited;
    const off = next(a);
    b.close();
    expect(await off).toEqual({ t: "error", reason: "offline" });
    expect(await ask(a, { t: "invite", to: "DDDDC3" })).toEqual({ t: "invite_sent", to: "DDDDC3" });
    a.close();
    d.close();
  });
});
