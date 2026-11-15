export const PROTOCOL_VERSION = 1;
export const CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

const CODE_RE = new RegExp(`^[${CODE_ALPHABET}]{6}$`);

export function isValidCode(code: string): boolean {
  return typeof code === "string" && CODE_RE.test(code);
}

export type ErrorReason =
  | "version"
  | "bad_code"
  | "code_taken"
  | "game_gone"
  | "offline"
  | "busy"
  | "self"
  | "rate_limited";

export type ClientMessage =
  | { t: "hello"; v: number; code: string }
  | { t: "resume"; game: string; token: string }
  | { t: "invite"; to: string }
  | { t: "cancel" }
  | { t: "accept"; from: string }
  | { t: "decline"; from: string }
  | { t: "move"; ply: number; uci: string }
  | { t: "resign" }
  | { t: "draw_offer" }
  | { t: "draw_reply"; accept: boolean }
  | { t: "finished" };

export type ServerMessage =
  | { t: "welcome"; code: string }
  | { t: "error"; reason: ErrorReason }
  | { t: "resumed"; game: string; color: "w" | "b"; moves: string[]; opponent: "online" | "away" }
  | { t: "invite_sent"; to: string }
  | { t: "invited"; from: string }
  | { t: "invite_cancelled"; from: string }
  | { t: "start"; game: string; color: "w" | "b"; token: string; opponent: string }
  | { t: "declined"; by: string }
  | { t: "move"; ply: number; uci: string }
  | { t: "ack"; ply: number }
  | { t: "sync"; moves: string[] }
  | { t: "resigned" }
  | { t: "draw_offer" }
  | { t: "draw_reply"; accept: boolean }
  | { t: "opponent"; state: "away" | "back" }
  | { t: "aborted" };

/** Prilog WebSocket-a; preživljava hibernaciju (serializeAttachment). */
export interface Attachment {
  code: string | null;
  game?: string;
  invites: number[];
}
