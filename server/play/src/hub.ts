import { DurableObject } from "cloudflare:workers";
import {
  PROTOCOL_VERSIONS,
  isValidCode,
  type Attachment,
  type ClientMessage,
  type ServerMessage,
} from "./protocol";
import {
  applyMove, deadline, isAllowedClock, newClock, snapshot,
  type ClockSpec, type ClockState, type Side, type WireClock,
} from "./clock";

const INVITE_LIMIT = 10;
const INVITE_WINDOW_MS = 60_000;
export const AWAY_LIMIT_MS = 30 * 60 * 1000;

/** Kraj zapisan dok strana kojoj nije javljen nema vezu. */
type Ended = "resigned" | "draw" | "finished" | "flag_w" | "flag_b";

type ClockCols = {
  clock_base: number | null; clock_inc: number | null; w_ms: number | null; b_ms: number | null;
  turn_started: number | null;
};

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
        moves TEXT, white_away INTEGER, black_away INTEGER)`,
    );
    // Kraj dok je druga strana odsutna (I1): `ended` = resigned | draw | finished | flag_w | flag_b,
    // `ended_to` = boja (ili obe, "wb", kod isteka vremena) kojoj rezultat jos nije javljen.
    // Starija tabela (v1) dobija kolone.
    for (const col of [
      "ended TEXT", "ended_to TEXT",
      "clock_base INTEGER", "clock_inc INTEGER", "w_ms INTEGER", "b_ms INTEGER", "turn_started INTEGER",
    ]) {
      try {
        ctx.storage.sql.exec(`ALTER TABLE games ADD COLUMN ${col}`);
      } catch {
        /* kolona vec postoji */
      }
    }
    // Posle restarta (deploy, izbacivanje) webSocketClose ne stize za izgubljene veze;
    // sediste bez veze i bez oznake odsustva dobija je sada, da istek od 30 min radi (I2).
    void ctx.blockConcurrencyWhile(async () => {
      if (this.stampOrphanSeats()) await this.scheduleAlarm();
    });
  }

  /**
   * Svako sediste bez zive veze vezane za partiju i bez oznake odsustva dobija oznaku
   * `Date.now()`. Vraca da li je nesto upisano (tad treba zakazati alarm).
   */
  stampOrphanSeats(game?: string): boolean {
    const rows = (game === undefined
      ? this.ctx.storage.sql.exec(
        "SELECT id, white, black, white_away, black_away FROM games WHERE white_away IS NULL OR black_away IS NULL",
      )
      : this.ctx.storage.sql.exec(
        "SELECT id, white, black, white_away, black_away FROM games WHERE id = ?", game,
      )).toArray() as unknown as {
      id: string; white: string; black: string; white_away: number | null; black_away: number | null;
    }[];
    let stamped = false;
    for (const r of rows) {
      if (r.white_away === null && !this.seatSocket(r.id, r.white)) stamped = this.stampAway(r.id, "w") || stamped;
      if (r.black_away === null && !this.seatSocket(r.id, r.black)) stamped = this.stampAway(r.id, "b") || stamped;
    }
    return stamped;
  }

  /** Oznaka odsustva samo ako je jos nema (prva oznaka odredjuje istek). */
  private stampAway(game: string, color: "w" | "b"): boolean {
    const col = color === "w" ? "white_away" : "black_away";
    const cur = this.ctx.storage.sql.exec(
      `UPDATE games SET ${col} = ? WHERE id = ? AND ${col} IS NULL`, Date.now(), game,
    );
    return cur.rowsWritten > 0;
  }

  /** Protivnik nema vezu vezanu za partiju: oznaci ga odsutnim (ako vec nije) i zakazi alarm. */
  private ensureOpponentAway(game: string, oppColor: "w" | "b"): void {
    if (this.stampAway(game, oppColor)) void this.scheduleAlarm();
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
      case "resume":
        this.onResume(ws, msg);
        break;
      case "move":
        this.onMove(ws, msg);
        break;
      case "resign":
        this.onResign(ws);
        break;
      case "draw_offer":
        this.onDrawOffer(ws);
        break;
      case "draw_reply":
        this.onDrawReply(ws, msg);
        break;
      case "finished":
        this.onFinished(ws);
        break;
      default:
        break; // ostale poruke dolaze u kasnijim taskovima
    }
  }

  private onHello(ws: WebSocket, msg: Extract<ClientMessage, { t: "hello" }>): void {
    const current = ws.deserializeAttachment() as Attachment | null;
    if (current?.code) return; // veza vec ima kod: ignorise se
    if (typeof msg.v !== "number" || !PROTOCOL_VERSIONS.includes(msg.v)) {
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
    att.v = msg.v;
    ws.serializeAttachment(att);
    this.send(ws, { t: "welcome", code: msg.code });
  }

  private clearInvite(att: Attachment): void {
    delete att.invite;
    delete att.inviteClock;
  }

  private isV2(ws: WebSocket | undefined): boolean {
    return !!ws && (this.attOf(ws).v ?? 1) >= 2;
  }

  /** Poruka sa satom samo vezama v2; v1 dobija poruku tacno kao ranije. */
  private sendClocked(ws: WebSocket, msg: ServerMessage, clock: WireClock | null, extra?: { end?: true }): void {
    if (this.isV2(ws)) {
      const m = { ...msg } as Record<string, unknown>;
      if (clock) m.clock = clock;
      if (extra?.end) m.end = true;
      this.send(ws, m as ServerMessage);
    } else {
      this.send(ws, msg);
    }
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
    // v1 ne zna za sat: njegov `clock` se ne gleda, pa nikad ne dobija bad_clock/needs_update.
    const asked = this.isV2(ws) ? msg.clock : undefined;
    if (asked !== undefined && !isAllowedClock(asked)) {
      this.send(ws, { t: "error", reason: "bad_clock" });
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
    const spec: ClockSpec | undefined = asked === undefined ? undefined : { base: asked.base, inc: asked.inc };
    if (spec && !this.isV2(target)) {
      this.send(ws, { t: "error", reason: "needs_update" });
      return;
    }
    att.invite = to;
    if (spec) att.inviteClock = spec;
    else delete att.inviteClock;
    ws.serializeAttachment(att);
    this.send(ws, { t: "invite_sent", to });
    if (spec) this.send(target, { t: "invited", from: att.code, clock: spec });
    else this.send(target, { t: "invited", from: att.code });
  }

  private onCancel(ws: WebSocket): void {
    const att = this.attOf(ws);
    if (!att.code || !att.invite) return;
    const target = this.socketForCode(att.invite, ws);
    this.clearInvite(att);
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
    const offered = iatt.inviteClock;
    const given = this.isV2(ws) ? msg.clock : undefined; // v1: `clock` se ne gleda
    if (offered) {
      if (!given || given.base !== offered.base || given.inc !== offered.inc) return;
    } else if (given !== undefined) {
      return;
    }

    if (att.invite) {
      const own = this.socketForCode(att.invite, ws);
      this.clearInvite(att);
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
    const clock = offered ? newClock(offered, Date.now()) : null;
    this.ctx.storage.sql.exec(
      `INSERT INTO games (id, white, black, white_token, black_token, moves, white_away, black_away,
        clock_base, clock_inc, w_ms, b_ms, turn_started)
       VALUES (?, ?, ?, ?, ?, '[]', NULL, NULL, ?, ?, ?, ?, ?)`,
      game,
      white,
      black,
      whiteToken,
      blackToken,
      clock?.base ?? null,
      clock?.inc ?? null,
      clock?.wMs ?? null,
      clock?.bMs ?? null,
      clock?.turnStarted ?? null,
    );
    this.clearInvite(iatt);
    iatt.game = game;
    att.game = game;
    inviter.serializeAttachment(iatt);
    ws.serializeAttachment(att);
    const wire = clock ? snapshot(clock, Date.now()) : null;
    if (clock) void this.scheduleAlarm(); // rok prvog poteza belog
    this.sendClocked(inviter, {
      t: "start",
      game,
      color: inviterWhite ? "w" : "b",
      token: inviterWhite ? whiteToken : blackToken,
      opponent: att.code,
    }, wire);
    this.sendClocked(ws, {
      t: "start",
      game,
      color: inviterWhite ? "b" : "w",
      token: inviterWhite ? blackToken : whiteToken,
      opponent: iatt.code!,
    }, wire);
  }

  private gameRow(id: string): { white: string; black: string; moves: string[]; clock: ClockState | null } | undefined {
    const rows = this.ctx.storage.sql
      .exec("SELECT white, black, moves, clock_base, clock_inc, w_ms, b_ms, turn_started FROM games WHERE id = ?", id)
      .toArray();
    if (rows.length === 0) return undefined;
    const r = rows[0] as { white: string; black: string; moves: string } & ClockCols;
    const moves = JSON.parse(r.moves) as string[];
    return { white: r.white, black: r.black, moves, clock: this.clockOf(r, moves.length) };
  }

  private clockOf(r: ClockCols, ply: number): ClockState | null {
    if (r.clock_base === null || r.clock_inc === null || r.w_ms === null || r.b_ms === null || r.turn_started === null) {
      return null;
    }
    return { base: r.clock_base, inc: r.clock_inc, wMs: r.w_ms, bMs: r.b_ms, turnStarted: r.turn_started, ply };
  }

  /** Brise partiju i oslobada obe strane (prilog `game`) za nove pozive. */
  private deleteGame(id: string): void {
    this.ctx.storage.sql.exec("DELETE FROM games WHERE id = ?", id);
    for (const ws of this.ctx.getWebSockets()) {
      const att = this.attOf(ws);
      if (att.game === id) {
        delete att.game;
        ws.serializeAttachment(att);
      }
    }
  }

  /** Partija strane koja salje poruku, ili game_gone ako je nema. */
  private ownGame(ws: WebSocket) {
    const att = this.attOf(ws);
    if (!att.code) return undefined;
    // Rok je istekao pre ove poruke: kraj (flag / first_move_expired) je vec javljen i ovoj strani.
    if (att.game && this.expireDue(att.game)) return undefined;
    const row = att.game ? this.gameRow(att.game) : undefined;
    if (!att.game || !row) {
      if (att.game) {
        delete att.game;
        ws.serializeAttachment(att);
      }
      this.send(ws, { t: "error", reason: "game_gone" });
      return undefined;
    }
    const mine: "w" | "b" = row.white === att.code ? "w" : "b";
    const oppColor: "w" | "b" = mine === "w" ? "b" : "w";
    const oppCode = mine === "w" ? row.black : row.white;
    const opp = this.seatSocket(att.game, oppCode, ws);
    if (!opp) this.ensureOpponentAway(att.game, oppColor);
    return { id: att.game, row, mine, oppColor, opp };
  }

  private onMove(ws: WebSocket, msg: Extract<ClientMessage, { t: "move" }>): void {
    const g = this.ownGame(ws);
    if (!g) return;
    const turn: "w" | "b" = g.row.moves.length % 2 === 0 ? "w" : "b";
    if (
      typeof msg.uci !== "string" || !/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(msg.uci) ||
      turn !== g.mine || msg.ply !== g.row.moves.length
    ) {
      this.sendClocked(ws, { t: "sync", moves: g.row.moves }, g.row.clock ? snapshot(g.row.clock, Date.now()) : null);
      return;
    }
    const now = Date.now();
    let wire: WireClock | null = null;
    if (g.row.clock) {
      const res = applyMove(g.row.clock, now);
      if (!res.ok) {
        // Rok je prosao izmedju provere u ownGame i ovog trenutka (sat je kucnuo): kraj istekom.
        this.endByClock(g.id, g.row.white, g.row.black, g.row.clock);
        return;
      }
      wire = snapshot(res.next, now);
      this.ctx.storage.sql.exec(
        "UPDATE games SET w_ms = ?, b_ms = ?, turn_started = ? WHERE id = ?",
        res.next.wMs, res.next.bMs, res.next.turnStarted, g.id,
      );
    }
    g.row.moves.push(msg.uci);
    this.ctx.storage.sql.exec("UPDATE games SET moves = ? WHERE id = ?", JSON.stringify(g.row.moves), g.id);
    this.sendClocked(ws, { t: "ack", ply: msg.ply }, wire);
    if (g.opp) this.sendClocked(g.opp, { t: "move", ply: msg.ply, uci: msg.uci }, wire, msg.end === true ? { end: true } : undefined);
    if (!g.row.clock) return; // bez sata `end` ne zavrsava nista na serveru
    if (msg.end === true) {
      // Zavrsni potez na vreme zaustavlja sat: isti put kao `finished`.
      if (g.opp) this.deleteGame(g.id);
      else this.endWhileAway(ws, g.id, "finished", g.oppColor);
    }
    void this.scheduleAlarm(); // rok se pomerio
  }

  /**
   * Partije sa satom u toku ciji je rok prosao (sve, ili samo `game`) zavrsava istekom.
   * Vraca da li je `game` time zavrsena.
   */
  private expireDue(game?: string): boolean {
    const rows = (game === undefined
      ? this.ctx.storage.sql.exec(
        `SELECT id, white, black, moves, clock_base, clock_inc, w_ms, b_ms, turn_started FROM games
         WHERE clock_base IS NOT NULL AND ended IS NULL`,
      )
      : this.ctx.storage.sql.exec(
        `SELECT id, white, black, moves, clock_base, clock_inc, w_ms, b_ms, turn_started FROM games
         WHERE id = ? AND clock_base IS NOT NULL AND ended IS NULL`, game,
      )).toArray() as unknown as ({ id: string; white: string; black: string; moves: string } & ClockCols)[];
    const now = Date.now();
    let hit = false;
    for (const r of rows) {
      const c = this.clockOf(r, (JSON.parse(r.moves) as string[]).length);
      if (!c || now < deadline(c)) continue;
      this.endByClock(r.id, r.white, r.black, c);
      if (r.id === game) hit = true;
    }
    return hit;
  }

  /**
   * Kraj zbog roka. Pre prvog poteza strane (ply < 2): prisutni dobijaju first_move_expired,
   * red se brise i nista se ne pamti. Inace istek: prisutni dobijaju flag i odmah su slobodni;
   * odsutne strane (jedna ili obe, `ended_to` "w" | "b" | "wb") ga dobijaju posle `resume`.
   * Oznaka odsustva odsutnih se postavlja na sada, pa 30 min za zavrseni red tece od kraja.
   */
  private endByClock(id: string, white: string, black: string, c: ClockState): void {
    const wSock = this.seatSocket(id, white);
    const bSock = this.seatSocket(id, black);
    const present = [wSock, bSock].filter((s): s is WebSocket => !!s);
    if (c.ply < 2) {
      for (const s of present) this.send(s, { t: "first_move_expired" });
      this.deleteGame(id);
    } else {
      const loser: Side = c.ply % 2 === 0 ? "w" : "b";
      for (const s of present) this.send(s, { t: "flag", loser });
      const absent = (wSock ? "" : "w") + (bSock ? "" : "b");
      if (absent === "") {
        this.deleteGame(id);
      } else {
        const now = Date.now();
        this.ctx.storage.sql.exec(
          `UPDATE games SET ended = ?, ended_to = ?,
             white_away = CASE WHEN ? THEN ? ELSE white_away END,
             black_away = CASE WHEN ? THEN ? ELSE black_away END
           WHERE id = ?`,
          `flag_${loser}`, absent, wSock ? 0 : 1, now, bSock ? 0 : 1, now, id,
        );
        for (const s of present) {
          const att = this.attOf(s);
          delete att.game;
          s.serializeAttachment(att);
        }
      }
    }
    void this.scheduleAlarm();
  }

  private onResign(ws: WebSocket): void {
    const g = this.ownGame(ws);
    if (!g) return;
    if (g.opp) {
      this.send(g.opp, { t: "resigned" });
      this.deleteGame(g.id);
    } else {
      this.endWhileAway(ws, g.id, "resigned", g.oppColor);
    }
  }

  /**
   * Kraj dok protivnik nema vezu: partija ostaje sa zapisanim krajem dok se odsutna
   * strana ne vrati (`resumed` + poruka kraja, pa brisanje) ili dok alarm ne istekne.
   * Prisutna strana je odmah slobodna za nove pozive.
   */
  private endWhileAway(ws: WebSocket, game: string, kind: "resigned" | "draw" | "finished", to: "w" | "b"): void {
    const col = to === "w" ? "white_away" : "black_away";
    // Bez sata: oznaka samo ako je jos nema (kao ranije). Sa satom odsustvo tokom partije
    // ne istice, pa stara oznaka ne sme da obrise tek zavrseni red: 30 min tece od kraja.
    this.ctx.storage.sql.exec(
      `UPDATE games SET ended = ?, ended_to = ?,
         ${col} = CASE WHEN clock_base IS NULL THEN COALESCE(${col}, ?) ELSE ? END
       WHERE id = ?`,
      kind, to, Date.now(), Date.now(), game,
    );
    const att = this.attOf(ws);
    delete att.game;
    ws.serializeAttachment(att);
    void this.scheduleAlarm();
  }

  private onDrawOffer(ws: WebSocket): void {
    const g = this.ownGame(ws);
    if (!g) return;
    if (g.opp) this.send(g.opp, { t: "draw_offer" });
  }

  private onDrawReply(ws: WebSocket, msg: Extract<ClientMessage, { t: "draw_reply" }>): void {
    const g = this.ownGame(ws);
    if (!g) return;
    const accept = msg.accept === true;
    if (g.opp) this.send(g.opp, { t: "draw_reply", accept });
    if (!accept) return;
    if (g.opp) this.deleteGame(g.id);
    else this.endWhileAway(ws, g.id, "draw", g.oppColor);
  }

  private onFinished(ws: WebSocket): void {
    const g = this.ownGame(ws);
    if (!g) return;
    if (g.opp) this.deleteGame(g.id);
    else this.endWhileAway(ws, g.id, "finished", g.oppColor);
  }

  private onDecline(ws: WebSocket, msg: Extract<ClientMessage, { t: "decline" }>): void {
    const att = this.attOf(ws);
    if (!att.code || typeof msg.from !== "string") return;
    const inviter = this.socketForCode(msg.from, ws);
    if (!inviter) return;
    const iatt = this.attOf(inviter);
    if (iatt.invite !== att.code) return;
    this.clearInvite(iatt);
    inviter.serializeAttachment(iatt);
    this.send(inviter, { t: "declined", by: att.code });
  }

  /** Veza nestaje: pozvani dobija invite_cancelled, a pozivaoci koji su cekali ovaj kod dobijaju offline. */
  private releaseInvites(ws: WebSocket): void {
    const att = this.attOf(ws);
    if (!att.code) return;
    if (att.invite) {
      const target = this.socketForCode(att.invite, ws);
      this.clearInvite(att);
      ws.serializeAttachment(att);
      if (target) this.send(target, { t: "invite_cancelled", from: att.code });
    }
    for (const other of this.ctx.getWebSockets()) {
      if (other === ws) continue;
      const oatt = this.attOf(other);
      if (oatt.invite === att.code) {
        this.clearInvite(oatt);
        other.serializeAttachment(oatt);
        this.send(other, { t: "error", reason: "offline" });
      }
    }
  }

  /** Socket vezan za istu partiju i stranu (kod), osim `except`. */
  private seatSocket(game: string, code: string, except?: WebSocket): WebSocket | undefined {
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === except) continue;
      const att = this.attOf(ws);
      if (att.game === game && att.code === code) return ws;
    }
    return undefined;
  }

  private resumeRow(id: string) {
    return this.ctx.storage.sql
      .exec(
        "SELECT white, black, white_token, black_token, moves, white_away, black_away, ended, ended_to, clock_base, clock_inc, w_ms, b_ms, turn_started FROM games WHERE id = ?",
        id,
      )
      .toArray()[0] as unknown as ({
      white: string; black: string; white_token: string; black_token: string;
      moves: string; white_away: number | null; black_away: number | null;
      ended: Ended | null; ended_to: string | null;
    } & ClockCols) | undefined;
  }

  private onResume(ws: WebSocket, msg: Extract<ClientMessage, { t: "resume" }>): void {
    const gone = () => this.send(ws, { t: "error", reason: "game_gone" });
    if (typeof msg.game !== "string" || typeof msg.token !== "string") return gone();
    let r = this.resumeRow(msg.game);
    if (!r) return gone();
    let mine: "w" | "b";
    if (msg.token === r.white_token) mine = "w";
    else if (msg.token === r.black_token) mine = "b";
    else return gone();
    const myCode = mine === "w" ? r.white : r.black;
    const oppCode = mine === "w" ? r.black : r.white;
    // Zavrsena partija: samo strana kojoj kraj nije javljen dobija ga; ona koja je
    // zavrsila je vec zavrsila lokalno.
    if (r.ended && !(r.ended_to ?? "").includes(mine)) return gone();

    // Preuzimanje sedista: svaka druga veza sa ovim kodom se zatvara.
    for (const other of this.ctx.getWebSockets()) {
      if (other === ws) continue;
      const oatt = this.attOf(other);
      if (oatt.code !== myCode) continue;
      other.serializeAttachment({ code: null, invites: [] } satisfies Attachment);
      try {
        other.close(1000, "replaced");
      } catch {
        /* vec zatvoren */
      }
    }
    // Rok istekao pre nego sto je alarm stigao: kraj se zapisuje sada (ova strana je jos
    // bez sedista, pa kraj dobija kao odsutna), a rok prvog poteza brise partiju.
    if (this.expireDue(msg.game)) {
      r = this.resumeRow(msg.game);
      if (!r) return gone();
    }
    const att = this.attOf(ws);
    att.code = myCode;
    att.game = msg.game;
    this.clearInvite(att);
    ws.serializeAttachment(att);

    const resumedMoves = JSON.parse(r.moves) as string[];
    const rclock = this.clockOf(r, resumedMoves.length);

    if (r.ended) {
      // Odsutna strana se vratila: lista poteza (mat/pat se vidi na tabli), pa poruka
      // kraja koju bi dobila uzivo, pa brisanje (kad je kraj javljen svima kojima nije).
      const flagged = r.ended === "flag_w" || r.ended === "flag_b";
      // Sat stoji: kod isteka u trenutku roka (gubitnik 0), inace u trenutku poslednjeg poteza.
      const wire = rclock ? snapshot(rclock, flagged ? deadline(rclock) : rclock.turnStarted) : null;
      this.sendClocked(ws, {
        t: "resumed", game: msg.game, color: mine, moves: resumedMoves, opponent: "away",
      }, wire);
      if (r.ended === "resigned") this.send(ws, { t: "resigned" });
      else if (r.ended === "draw") this.send(ws, { t: "draw_reply", accept: true });
      else if (flagged) this.send(ws, { t: "flag", loser: r.ended === "flag_w" ? "w" : "b" });
      const rest = (r.ended_to ?? "").replace(mine, "");
      if (rest === "") {
        this.deleteGame(msg.game);
      } else {
        this.ctx.storage.sql.exec("UPDATE games SET ended_to = ? WHERE id = ?", rest, msg.game);
        delete att.game;
        ws.serializeAttachment(att);
      }
      void this.scheduleAlarm();
      return;
    }

    const wasAway = (mine === "w" ? r.white_away : r.black_away) !== null;
    this.ctx.storage.sql.exec(
      `UPDATE games SET ${mine === "w" ? "white_away" : "black_away"} = NULL WHERE id = ?`,
      msg.game,
    );
    const opp = this.seatSocket(msg.game, oppCode, ws);
    this.sendClocked(ws, {
      t: "resumed",
      game: msg.game,
      color: mine,
      moves: resumedMoves,
      opponent: opp ? "online" : "away",
    }, rclock ? snapshot(rclock, Date.now()) : null);
    if (wasAway && opp) this.send(opp, { t: "opponent", state: "back" });
    // Protivnik bez veze a bez oznake (veza izgubljena bez webSocketClose, npr. deploy).
    if (!opp) this.stampAway(msg.game, mine === "w" ? "b" : "w");
    void this.scheduleAlarm();
  }

  /**
   * Alarm na najraniji rok: istek odsustva od 30 min za partije bez sata i za zavrsene
   * redove, i rok sata (ili rok prvog poteza) za partije sa satom u toku.
   */
  private async scheduleAlarm(): Promise<void> {
    const row = this.ctx.storage.sql
      .exec(
        `SELECT MIN(m) AS m FROM (
           SELECT white_away AS m FROM games
             WHERE white_away IS NOT NULL AND (clock_base IS NULL OR ended IS NOT NULL)
           UNION ALL SELECT black_away FROM games
             WHERE black_away IS NOT NULL AND (clock_base IS NULL OR ended IS NOT NULL))`,
      )
      .one() as { m: number | null };
    let at: number | null = row.m === null ? null : row.m + AWAY_LIMIT_MS;
    const timed = this.ctx.storage.sql
      .exec(
        `SELECT moves, clock_base, clock_inc, w_ms, b_ms, turn_started FROM games
         WHERE clock_base IS NOT NULL AND ended IS NULL`,
      )
      .toArray() as unknown as ({ moves: string } & ClockCols)[];
    for (const r of timed) {
      const c = this.clockOf(r, (JSON.parse(r.moves) as string[]).length);
      if (c) at = at === null ? deadline(c) : Math.min(at, deadline(c));
    }
    if (at === null) {
      await this.ctx.storage.deleteAlarm();
    } else {
      await this.ctx.storage.setAlarm(at);
    }
  }

  async alarm(): Promise<void> {
    // Prvo rokovi sata (oni pamte kraj sa svezom oznakom odsustva), pa istek odsustva.
    this.expireDue();
    const cutoff = Date.now() - AWAY_LIMIT_MS;
    const rows = this.ctx.storage.sql
      .exec(
        `SELECT id, white, black FROM games
         WHERE (clock_base IS NULL OR ended IS NOT NULL)
           AND ((white_away IS NOT NULL AND white_away <= ?) OR (black_away IS NOT NULL AND black_away <= ?))`,
        cutoff,
        cutoff,
      )
      .toArray() as unknown as { id: string; white: string; black: string }[];
    for (const g of rows) {
      const present = [g.white, g.black]
        .map((c) => this.seatSocket(g.id, c))
        .filter((w): w is WebSocket => !!w);
      for (const ws of present) this.send(ws, { t: "aborted" });
      this.deleteGame(g.id);
    }
    await this.scheduleAlarm();
  }

  /** Veza nestaje: partija ostaje, strana se vodi kao odsutna, protivnik dobija opponent away. */
  private markAway(ws: WebSocket): void {
    const att = this.attOf(ws);
    if (!att.code || !att.game) return;
    const row = this.gameRow(att.game);
    if (!row) return;
    if (this.seatSocket(att.game, att.code, ws)) return; // sediste je vec preuzeto drugom vezom
    const mine: "w" | "b" = row.white === att.code ? "w" : "b";
    this.ctx.storage.sql.exec(
      `UPDATE games SET ${mine === "w" ? "white_away" : "black_away"} = ? WHERE id = ?`,
      Date.now(),
      att.game,
    );
    const opp = this.seatSocket(att.game, mine === "w" ? row.black : row.white, ws);
    if (opp) this.send(opp, { t: "opponent", state: "away" });
    else this.stampAway(att.game, mine === "w" ? "b" : "w"); // i protivnik bez veze (I2)
    void this.scheduleAlarm();
  }

  async webSocketClose(ws: WebSocket, code: number, _reason: string, _wasClean: boolean): Promise<void> {
    // Prilog nestaje sa soketom; kod se oslobada cim soket vise nije u getWebSockets().
    this.releaseInvites(ws);
    this.markAway(ws);
    try {
      ws.close(code === 1005 || code === 1006 ? 1000 : code);
    } catch {
      /* vec zatvoren */
    }
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    this.releaseInvites(ws);
    this.markAway(ws);
    try {
      ws.close(1011);
    } catch {
      /* vec zatvoren */
    }
  }
}
