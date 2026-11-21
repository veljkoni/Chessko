// Cist modul sata za partiju na vreme: bez I/O, bez Date.now() (vreme ulazi kao argument).

export type Side = "w" | "b";

export interface ClockSpec {
  base: number; // sekunde
  inc: number; // sekunde
}

export const ALLOWED_CLOCKS: readonly ClockSpec[] = [
  { base: 180, inc: 2 },
  { base: 600, inc: 5 },
];

export function isAllowedClock(x: unknown): x is ClockSpec {
  if (typeof x !== "object" || x === null) return false;
  const o = x as Record<string, unknown>;
  return ALLOWED_CLOCKS.some((c) => c.base === o.base && c.inc === o.inc);
}

export const FIRST_MOVE_MS = 30_000;

export interface ClockState {
  base: number;
  inc: number;
  wMs: number;
  bMs: number;
  turnStarted: number;
  /** Broj vec odigranih poteza; strana na potezu je ply % 2 === 0 ? "w" : "b". */
  ply: number;
}

export interface WireClock {
  base: number;
  inc: number;
  w: number;
  b: number;
  run: Side | null;
  first: number | null;
}

export function newClock(spec: ClockSpec, now: number): ClockState {
  return {
    base: spec.base,
    inc: spec.inc,
    wMs: spec.base * 1000,
    bMs: spec.base * 1000,
    turnStarted: now,
    ply: 0,
  };
}

function sideToMove(c: ClockState): Side {
  return c.ply % 2 === 0 ? "w" : "b";
}

/** Strana ciji sat tece; null dok je ply < 2 (prvi potez svake strane je besplatan). */
export function runningSide(c: ClockState): Side | null {
  return c.ply < 2 ? null : sideToMove(c);
}

export function deadline(c: ClockState): number {
  if (c.ply < 2) return c.turnStarted + FIRST_MOVE_MS;
  const left = sideToMove(c) === "w" ? c.wMs : c.bMs;
  return c.turnStarted + left;
}

export function applyMove(
  c: ClockState,
  now: number,
): { ok: true; next: ClockState } | { ok: false; reason: "flag" | "first_move" } {
  if (now >= deadline(c)) return { ok: false, reason: c.ply < 2 ? "first_move" : "flag" };
  const next: ClockState = { ...c, ply: c.ply + 1, turnStarted: now };
  if (c.ply >= 2) {
    const delta = now - c.turnStarted;
    if (sideToMove(c) === "w") next.wMs = c.wMs - delta + c.inc * 1000;
    else next.bMs = c.bMs - delta + c.inc * 1000;
  }
  return { ok: true, next };
}

export function snapshot(c: ClockState, now: number): WireClock {
  const run = runningSide(c);
  const spent = Math.max(0, now - c.turnStarted);
  return {
    base: c.base,
    inc: c.inc,
    w: run === "w" ? Math.max(0, c.wMs - spent) : c.wMs,
    b: run === "b" ? Math.max(0, c.bMs - spent) : c.bMs,
    run,
    first: c.ply < 2 ? Math.max(0, deadline(c) - now) : null,
  };
}
