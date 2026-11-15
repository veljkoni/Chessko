import { DurableObject } from "cloudflare:workers";
import {
  PROTOCOL_VERSION,
  isValidCode,
  type Attachment,
  type ClientMessage,
  type ServerMessage,
} from "./protocol";

export class Hub extends DurableObject {
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
      default:
        break; // ostale poruke dolaze u kasnijim taskovima
    }
  }

  private onHello(ws: WebSocket, msg: Extract<ClientMessage, { t: "hello" }>): void {
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

  async webSocketClose(ws: WebSocket, code: number, _reason: string, _wasClean: boolean): Promise<void> {
    // Prilog nestaje sa soketom; kod se oslobada cim soket vise nije u getWebSockets().
    try {
      ws.close(code === 1005 || code === 1006 ? 1000 : code);
    } catch {
      /* vec zatvoren */
    }
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    try {
      ws.close(1011);
    } catch {
      /* vec zatvoren */
    }
  }
}
