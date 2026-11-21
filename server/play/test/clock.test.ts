import { describe, expect, it } from "vitest";
import {
  applyMove,
  deadline,
  isAllowedClock,
  newClock,
  runningSide,
  snapshot,
  type ClockState,
} from "../src/clock";

const spec = { base: 180, inc: 2 };

function atPly2(T: number): ClockState {
  const c0 = newClock(spec, 0);
  const a = applyMove(c0, 1000);
  if (!a.ok) throw new Error("w1");
  const b = applyMove(a.next, 2000);
  if (!b.ok) throw new Error("b1");
  return { ...b.next, turnStarted: T };
}

describe("isAllowedClock", () => {
  it("prihvata samo dve vrednosti", () => {
    expect(isAllowedClock({ base: 180, inc: 2 })).toBe(true);
    expect(isAllowedClock({ base: 600, inc: 5 })).toBe(true);
    expect(isAllowedClock({ base: 180, inc: 3 })).toBe(false);
    expect(isAllowedClock({ base: "180", inc: 2 })).toBe(false);
    expect(isAllowedClock(null)).toBe(false);
  });
});

describe("clock", () => {
  it("novi sat", () => {
    const c = newClock(spec, 1000);
    expect(c.wMs).toBe(180000);
    expect(c.bMs).toBe(180000);
    expect(runningSide(c)).toBeNull();
    expect(deadline(c)).toBe(31000);
  });

  it("prvi potez belog je besplatan", () => {
    const r = applyMove(newClock(spec, 1000), 20000);
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.next.wMs).toBe(180000);
      expect(r.next.ply).toBe(1);
      expect(deadline(r.next)).toBe(50000);
    }
  });

  it("prvi potez tacno na roku je odbijen", () => {
    expect(applyMove(newClock(spec, 1000), 31000)).toEqual({ ok: false, reason: "first_move" });
  });

  it("posle ply=2 sat tece i dodaje se inkrement", () => {
    const c = atPly2(10000);
    expect(runningSide(c)).toBe("w");
    const r = applyMove(c, 15000);
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.next.wMs).toBe(177000);
      expect(r.next.bMs).toBe(c.bMs);
      expect(r.next.ply).toBe(3);
      expect(r.next.turnStarted).toBe(15000);
    }
  });

  it("potez tacno na roku je zastava", () => {
    const c = atPly2(10000);
    expect(applyMove(c, 10000 + c.wMs)).toEqual({ ok: false, reason: "flag" });
  });

  it("snapshot", () => {
    const c = atPly2(10000);
    const s = snapshot(c, 11500);
    expect(s).toEqual({ base: 180, inc: 2, w: c.wMs - 1500, b: c.bMs, run: "w", first: null });
    const s0 = snapshot(newClock(spec, 5000), 15000);
    expect(s0.first).toBe(20000);
    expect(s0.run).toBeNull();
    expect(s0.w).toBe(180000);
    expect(snapshot(c, 10000 + c.wMs + 999).w).toBe(0);
  });
});
