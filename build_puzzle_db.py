#!/usr/bin/env python3
"""
build_puzzle_db.py — generiše Chessko/puzzles.sqlite iz Lichess puzzle baze.

Ulaz: https://database.lichess.org/lichess_db_puzzle.csv.zst (CC0), CSV header:
PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags,DailyDate

Upotreba (strimovano, bez raspakivanja na disk):
    curl -sL https://database.lichess.org/lichess_db_puzzle.csv.zst | zstd -dc | \
        python3 build_puzzle_db.py --stdin --out Chessko/puzzles.sqlite

Upotreba (lokalni već-raspakovan CSV, radi ponovnog pokretanja bez preuzimanja):
    python3 build_puzzle_db.py --csv /putanja/do/lichess_db_puzzle.csv --out Chessko/puzzles.sqlite

Algoritam:
  1. Filter kvaliteta: 600 <= rating <= 2200, nb_plays >= 200, popularity >= 90,
     rating_deviation <= 80. (izmereno: propušta ~31% na uzorku)
  2. Kandidati koji prođu filter grupišu se u 8 opsega rejtinga od po 200 poena
     (600-799 ... 2000-2199); zadaci sa rating == 2200 ne upadaju ni u jedan opseg
     i prirodno se odbacuju (edge slučaj, zanemarljivo redak).
  3. Unutar svakog opsega bira se do PER_BAND (podrazumevano 2500) zadataka tako
     da teme budu ravnomerno zastupljene: "lenjiv" (lazy) heap sa min-prioritetom
     min(broj_već_odabranih_za_svaku_temu_zadatka). Kad opseg ima manje kandidata
     od cilja, uzima se koliko ima — manjak se NE nadoknađuje iz drugih opsega.
  4. Determinizam: random.seed(SEED) — koristi se samo za mešanje redosleda
     tie-breaka unutar heap-a (svi kandidati kreću sa prioritetom 0, pa je
     redosled biranja među jednakim prioritetima inače zavisio od redosleda
     u fajlu). Isti ulazni fajl + isti seed => bit-identičan izlaz.

Šema izlazne baze — vidi CREATE TABLE ispod (spec 5.3 + puzzle_themes tabela za
brzu pretragu po temi bez LIKE nad tekstom).
"""

from __future__ import annotations

import argparse
import csv
import heapq
import random
import sqlite3
import sys
from collections import defaultdict
from dataclasses import dataclass

EXPECTED_HEADER = [
    "PuzzleId", "FEN", "Moves", "Rating", "RatingDeviation",
    "Popularity", "NbPlays", "Themes", "GameUrl", "OpeningTags", "DailyDate",
]

SEED = 20260906
BAND_WIDTH = 200
BAND_START = 600
NUM_BANDS = 8  # 600-799 ... 2000-2199
DEFAULT_PER_BAND = 2500

MIN_RATING = 600
MAX_RATING = 2200
MIN_NB_PLAYS = 200
MIN_POPULARITY = 90
MAX_RATING_DEVIATION = 80


@dataclass(frozen=True, slots=True)
class Candidate:
    puzzle_id: str
    fen: str
    moves: str
    rating: int
    themes: str  # razmakom razdvojen string, kao u izvoru


def passes_quality_filter(rating: int, rating_deviation: int, popularity: int, nb_plays: int) -> bool:
    return (
        MIN_RATING <= rating <= MAX_RATING
        and nb_plays >= MIN_NB_PLAYS
        and popularity >= MIN_POPULARITY
        and rating_deviation <= MAX_RATING_DEVIATION
    )


def band_index(rating: int) -> int | None:
    idx = (rating - BAND_START) // BAND_WIDTH
    if 0 <= idx < NUM_BANDS:
        return idx
    return None


def read_candidates(reader: csv.reader) -> list[list[Candidate]]:
    """Jedan prolaz kroz CSV: filtrira i raspoređuje kandidate po opsegu rejtinga."""
    header = next(reader)
    if header != EXPECTED_HEADER:
        print(f"UPOZORENJE: neočekivan CSV header: {header}", file=sys.stderr)

    bands: list[list[Candidate]] = [[] for _ in range(NUM_BANDS)]
    total_rows = 0
    kept = 0

    for row in reader:
        total_rows += 1
        if len(row) < 8:
            continue
        puzzle_id, fen, moves, rating_s, rd_s, pop_s, nbplays_s, themes = row[:8]
        try:
            rating = int(rating_s)
            rating_deviation = int(rd_s)
            popularity = int(pop_s)
            nb_plays = int(nbplays_s)
        except ValueError:
            continue

        if not fen or not moves:
            continue

        if not passes_quality_filter(rating, rating_deviation, popularity, nb_plays):
            continue

        idx = band_index(rating)
        if idx is None:
            continue

        bands[idx].append(Candidate(puzzle_id, fen, moves, rating, themes))
        kept += 1

        if total_rows % 1_000_000 == 0:
            print(f"  ... {total_rows:,} redova pročitano, {kept:,} kandidata prošlo filter", file=sys.stderr)

    print(f"Ukupno redova: {total_rows:,}; kandidata posle filtera kvaliteta: {kept:,}", file=sys.stderr)
    return bands


def select_band(candidates: list[Candidate], target: int, rng: random.Random) -> list[Candidate]:
    """Bira do `target` kandidata iz jednog opsega, favorizujući ravnomernu
    zastupljenost tema preko lenjivog (lazy-update) min-heap-a.

    Svaki kandidat kreće sa prioritetom min(broj_već_odabranih za svaku
    njegovu temu) = 0. Kad se iz heap-a izvuče kandidat, njegov prioritet se
    ponovo izračuna sa TRENUTNIM brojačima tema; ako se poklapa sa onim s
    kojim je ubačen u heap — bira se; ako je porastao (neka njegova tema je u
    međuvremenu birana) — vraća se nazad u heap sa novim, tačnim prioritetom.
    Ovo je standardan trik za heap sa "lenjivim" ažuriranjem prioriteta i
    ekvivalentan je ponovnom sortiranju posle svakog izbora, ali bez O(n) po
    izboru.

    Sekundarni kriterijum (nakon min-broja-po-temi): manji broj tema po
    zadatku. Bez ovoga, zadaci sa VIŠE tema imaju statistički veću šansu da
    im BAR JEDNA tema trenutno bude najslabije zastupljena (više "pokušaja"),
    pa ih algoritam sistematski preferira — što naduva `puzzle_themes` tabelu
    (izmereno: prosek 5.64 teme/zadatak naspram tipičnih 3-4 u izvornom
    korpusu) i fajl preko budžeta veličine bez ikakve koristi za pokrivenost
    tema. Sortiranje po broju tema kao tie-break drži primarni cilj (svaka
    tema zastupljena) uz manji, ravnomerniji broj tema po zadatku.
    """
    n = len(candidates)
    if n <= target:
        return list(candidates)

    order = list(range(n))
    rng.shuffle(order)
    tie_break = [0] * n
    for pos, idx in enumerate(order):
        tie_break[idx] = pos

    theme_count: dict[str, int] = defaultdict(int)
    heap: list[tuple[int, int, int, int]] = []
    themes_split: list[list[str]] = []
    for idx, cand in enumerate(candidates):
        themes = cand.themes.split()
        themes_split.append(themes)
        heapq.heappush(heap, (0, len(themes), tie_break[idx], idx))

    selected_idx: list[int] = []
    while heap and len(selected_idx) < target:
        score, n_themes, tb, idx = heapq.heappop(heap)
        themes = themes_split[idx]
        real_score = min((theme_count[t] for t in themes), default=0)
        if real_score > score:
            heapq.heappush(heap, (real_score, n_themes, tb, idx))
            continue
        selected_idx.append(idx)
        for t in themes:
            theme_count[t] += 1

    return [candidates[i] for i in selected_idx]


def build_database(selected: list[Candidate], out_path: str) -> None:
    conn = sqlite3.connect(out_path)
    cur = conn.cursor()
    cur.executescript(
        """
        DROP TABLE IF EXISTS puzzles;
        DROP TABLE IF EXISTS puzzle_themes;

        CREATE TABLE puzzles (
          id       TEXT PRIMARY KEY,
          fen      TEXT NOT NULL,
          moves    TEXT NOT NULL,
          rating   INTEGER NOT NULL,
          themes   TEXT NOT NULL
        );
        CREATE INDEX idx_rating ON puzzles(rating);

        CREATE TABLE puzzle_themes (theme TEXT NOT NULL, puzzle_id TEXT NOT NULL);
        CREATE INDEX idx_theme ON puzzle_themes(theme, puzzle_id);
        """
    )

    puzzle_rows = [(c.puzzle_id, c.fen, c.moves, c.rating, c.themes) for c in selected]
    cur.executemany(
        "INSERT INTO puzzles (id, fen, moves, rating, themes) VALUES (?, ?, ?, ?, ?)",
        puzzle_rows,
    )

    theme_rows = []
    for c in selected:
        for theme in c.themes.split():
            theme_rows.append((theme, c.puzzle_id))
    cur.executemany(
        "INSERT INTO puzzle_themes (theme, puzzle_id) VALUES (?, ?)",
        theme_rows,
    )

    conn.commit()
    conn.isolation_level = None
    cur.execute("VACUUM;")
    conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--stdin", action="store_true", help="čitaj CSV sa standardnog ulaza (strimovano)")
    parser.add_argument("--csv", metavar="PATH", help="čitaj CSV sa lokalne putanje (za ponovna pokretanja)")
    parser.add_argument("--out", default="Chessko/puzzles.sqlite", help="putanja izlaznog sqlite fajla")
    parser.add_argument("--per-band", type=int, default=DEFAULT_PER_BAND, help="ciljni broj zadataka po opsegu rejtinga")
    parser.add_argument("--seed", type=int, default=SEED, help="seed za determinizam biranja")
    args = parser.parse_args()

    if not args.stdin and not args.csv:
        parser.error("mora se navesti --stdin ili --csv PATH")

    rng = random.Random(args.seed)

    if args.stdin:
        text_stream = sys.stdin
    else:
        text_stream = open(args.csv, "r", newline="", encoding="utf-8")

    try:
        reader = csv.reader(text_stream)
        bands = read_candidates(reader)
    finally:
        if not args.stdin:
            text_stream.close()

    selected: list[Candidate] = []
    for i, band_candidates in enumerate(bands):
        band_lo = BAND_START + i * BAND_WIDTH
        band_hi = band_lo + BAND_WIDTH - 1
        chosen = select_band(band_candidates, args.per_band, rng)
        print(
            f"Opseg {band_lo}-{band_hi}: {len(band_candidates):,} kandidata -> odabrano {len(chosen):,}",
            file=sys.stderr,
        )
        selected.extend(chosen)

    print(f"Ukupno odabrano zadataka: {len(selected):,}", file=sys.stderr)

    build_database(selected, args.out)
    print(f"Baza zapisana: {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
