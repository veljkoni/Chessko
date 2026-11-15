import { DurableObject } from "cloudflare:workers";
import {
  PROTOCOL_VERSION,
  isValidCode,
  type Attachment,
  type ClientMessage,
  type ServerMessage,
} from "./protocol";

const INVITE_LIMIT = 10;
const INVITE_WINDOW_MS = 60_000;

function randomHex(bytes: number): string {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  return Array.from(buf, (b) => b.toString(16).padStart(2, "0")).join("");
}

export class Hub extends DurableObject {
  constructor(ctx: DurableObjectState, env: unknown) {
    super(ctx, env as never);
    ctx.storage.sql.exec(
      `CREATE TABLE IF NOT EXISTS games (
        id TEXT PRIMARY KEY, white TEXT, black TEXT, white_token TEXT, black_token TEXT,
        moves TEXT, away_code TEXT, away_since INTEGER)`,
    );
  }

  async fetch(_request: Request): Promise<Response> {
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    const att: Attachment = { code: null, invites: [] };
    server.serializeAttachment(att);
    return new Response(null, { status: 101, webSocket: client });
  }

  /** Prisustvo se uvek cita iz zivih soketa i priloga (memorija ne preživljava hibernaciju). */
  private socketForCode(code: string, except?: WebSocket): WebSocket | undefined {
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === except) continue;
      const att = ws.deserializeAttachment() as Attachment | null;
      if (att?.code === code) return ws;
    }
    return undefined;
  }

  private send(ws: WebSocket, msg: ServerMessage): void {
    ws.send(JSON.stringify(msg));
  }

  async webSocketMessage(ws: WebSocket, data: string | ArrayBuffer): Promise<void> {
    if (typeof data !== "string") return;
    let msg: ClientMessage;
    try {
      msg = JSON.parse(data);
    } catch {
      return;
    }
    if (!msg || typeof msg !== "object") return;

    switch (msg.t) {
      case "hello":
        this.onHello(ws, msg);
        break;
      case "invite":
        this.onInvite(ws, msg);
        break;
      case "cancel":
        this.onCancel(ws);
        break;
      case "accept":
        this.onAccept(ws, msg);
        break;
      case "decline":
        this.onDecline(ws, msg);
        break;
      default:
        break; // ostale poruke dolaze u kasnijim taskovima
    }
  }

  private onHello(ws: WebSocket, msg: Extract<ClientMessage, { t: "hello" }>): void {
    const current = ws.deserializeAttachment() as Attachment | null;
    if (current?.code) return; // veza vec ima kod: ignorise se
    if (msg.v !== PROTOCOL_VERSION) {
      this.send(ws, { t: "error", reason: "version" });
      return;
    }
    if (typeof msg.code !== "string" || !isValidCode(msg.code)) {
      this.send(ws, { t: "error", reason: "bad_code" });
      return;
    }
    if (this.socketForCode(msg.code, ws)) {
      this.send(ws, { t: "error", reason: "code_taken" });
      return;
    }
    const att = (ws.deserializeAttachment() as Attachment | null) ?? { code: null, invites: [] };
    att.code = msg.code;
    ws.serializeAttachment(att);
    this.send(ws, { t: "welcome", code: msg.code });
  }

  private attOf(ws: WebSocket): Attachment {
    return (ws.deserializeAttachment() as Attachment | null) ?? { code: null, invites: [] };
  }

  /** Strana koja je pozvala `code` i koja jos ceka odgovor. */
  private inviterOf(code: string, except?: WebSocket): WebSocket | undefined {
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === except) continue;
      if (this.attOf(ws).invite === code) return ws;
    }
    return undefined;
  }

  private onInvite(ws: WebSocket, msg: Extract<ClientMessage, { t: "invite" }>): void {
    const att = this.attOf(ws);
    if (!att.code) return;
    const now = Date.now();
    const recent = att.invites.filter((t) => now - t < INVITE_WINDOW_MS);
    if (recent.length >= INVITE_LIMIT) {
      att.invites = recent;
      ws.serializeAttachment(att);
      this.send(ws, { t: "error", reason: "rate_limited" });
      return;
    }
    recent.push(now);
    att.invites = recent;
    ws.serializeAttachment(att);

    const to = msg.to;
    if (typeof to !== "string" || !isValidCode(to)) {
      this.send(ws, { t: "error", reason: "bad_code" });
      return;
    }
    if (to === att.code) {
      this.send(ws, { t: "error", reason: "self" });
      return;
    }
    const target = this.socketForCode(to, ws);
    if (!target) {
      this.send(ws, { t: "error", reason: "offline" });
      return;
    }
    const tatt = this.attOf(target);
    if (att.game || att.invite || this.inviterOf(att.code, ws) ||
      tatt.game || tatt.invite || this.inviterOf(to, ws)) {
      this.send(ws, { t: "error", reason: "busy" });
      return;
    }
    att.invite = to;
    ws.serializeAttachment(att);
    this.send(ws, { t: "invite_sent", to });
    this.send(target, { t: "invited", from: att.code });
  }

  private onCancel(ws: WebSocket): void {
    const att = this.attOf(ws);
    if (!att.code || !att.invite) return;
    const target = this.socketForCode(att.invite, ws);
    delete att.invite;
    ws.serializeAttachment(att);
    if (target) {
      this.send(target, { t: "invite_cancelled", from: att.code });
    }
  }

  private onAccept(ws: WebSocket, msg: Extract<ClientMessage, { t: "accept" }>): void {
    const att = this.attOf(ws);
    if (!att.code || att.game || typeof msg.from !== "string") return;
    const inviter = this.socketForCode(msg.from, ws);
    if (!inviter) return;
    const iatt = this.attOf(inviter);
    if (iatt.invite !== att.code || iatt.game) return;

    if (att.invite) {
      const own = this.socketForCode(att.invite, ws);
      delete att.invite;
      if (own) this.send(own, { t: "invite_cancelled", from: att.code });
    }
    const game = randomHex(16);
    const flip = new Uint8Array(1);
    crypto.getRandomValues(flip);
    const inviterWhite = (flip[0] & 1) === 0;
    const white = inviterWhite ? iatt.code! : att.code;
    const black = inviterWhite ? att.code : iatt.code!;
    const whiteToken = randomHex(16);
    const blackToken = randomHex(16);
    this.ctx.storage.sql.exec(
      "INSERT INTO games (id, white, black, white_token, black_token, moves, away_code, away_since) VALUES (?, ?, ?, ?, ?, '[]', NULL, NULL)",
      game,
      white,
      black,
      whiteToken,
      blackToken,
    );
    delete iatt.invite;
    iatt.game = game;
    att.game = game;
    inviter.serializeAttachment(iatt);
    ws.serializeAttachment(att);
    this.send(inviter, {
      t: "start",
      game,
      color: inviterWhite ? "w" : "b",
      token: inviterWhite ? whiteToken : blackToken,
      opponent: att.code,
    });
    this.send(ws, {
      t: "start",
      game,
      color: inviterWhite ? "b" : "w",
      token: inviterWhite ? blackToken : whiteToken,
      opponent: iatt.code!,
    });
  }

  private onDecline(ws: WebSocket, msg: Extract<ClientMessage, { t: "decline" }>): void {
    const att = this.attOf(ws);
    if (!att.code || typeof msg.from !== "string") return;
    const inviter = this.socketForCode(msg.from, ws);
    if (!inviter) return;
    const iatt = this.attOf(inviter);
    if (iatt.invite !== att.code) return;
    delete iatt.invite;
    inviter.serializeAttachment(iatt);
    this.send(inviter, { t: "declined", by: att.code });
  }

  /** Veza nestaje: pozvani dobija invite_cancelled, a pozivaoci koji su cekali ovaj kod dobijaju offline. */
  private releaseInvites(ws: WebSocket): void {
    const att = this.attOf(ws);
    if (!att.code) return;
    if (att.invite) {
      const target = this.socketForCode(att.invite, ws);
      delete att.invite;
      ws.serializeAttachment(att);
      if (target) this.send(target, { t: "invite_cancelled", from: att.code });
    }
    for (const other of this.ctx.getWebSockets()) {
      if (other === ws) continue;
      const oatt = this.attOf(other);
      if (oatt.invite === att.code) {
        delete oatt.invite;
        other.serializeAttachment(oatt);
        this.send(other, { t: "error", reason: "offline" });
      }
    }
  }

  async webSocketClose(ws: WebSocket, code: number, _reason: string, _wasClean: boolean): Promise<void> {
    // Prilog nestaje sa soketom; kod se oslobada cim soket vise nije u getWebSockets().
    this.releaseInvites(ws);
    try {
      ws.close(code === 1005 || code === 1006 ? 1000 : code);
    } catch {
      /* vec zatvoren */
    }
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    this.releaseInvites(ws);
    try {
      ws.close(1011);
    } catch {
      /* vec zatvoren */
    }
  }
}
