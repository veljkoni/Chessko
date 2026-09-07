#!/usr/bin/env python3
"""Generise Chessko/Content/lessons/<id>.<lang>.json iz strukture zapisane
ovde + prevoda koji vec postoje u Chessko/Localizable.xcstrings.

Struktura (redosled i tip blokova, ikone, FEN-ovi, UCI potezi) je verno
prenesena iz Chessko/Views/LessonDetailView.swift — lekcija 1 (linije 66-313),
2 (314-387), 3 (388-451), 4 (452-627). Tekst se NE prekucava: svaki string je
T("<srpski kljuc>") i vadi se iz kataloga za svih 8 jezika.

Pokretanje:  python3 build_lesson_json.py
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).parent
CATALOG = ROOT / "Chessko" / "Localizable.xcstrings"
OUT_DIR = ROOT / "Chessko" / "Content" / "lessons"
LANGS = ["sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi"]

_catalog = json.loads(CATALOG.read_text())["strings"]


def T(key):
    """Marker za tekst koji se prevodi. Vraca sam kljuc; `render` ga zameni."""
    return ("__T__", key)


class Raw(str):
    """Tekst koji NIJE u katalogu i ne prevodi se (npr. URL). Prolazi doslovno."""


def _t(text):
    """T(...) za obican string, doslovno za Raw."""
    return text if isinstance(text, Raw) else T(text)


def render(value, lang):
    """Rekurzivno zamenjuje T(...) markere prevodom na `lang`."""
    if isinstance(value, tuple) and len(value) == 2 and value[0] == "__T__":
        key = value[1]
        entry = _catalog.get(key)
        if entry is None:
            sys.exit(f"GRESKA: kljuc nije u katalogu: {key!r}")
        loc = entry.get("localizations", {}).get(lang)
        if loc is None:
            sys.exit(f"GRESKA: kljuc {key!r} nema jezik {lang}")
        return loc["stringUnit"]["value"]
    if isinstance(value, dict):
        return {k: render(v, lang) for k, v in value.items()}
    if isinstance(value, list):
        return [render(v, lang) for v in value]
    return value


# ─────────────────────────────────────────────────────────────────────────────
# Pomocni konstruktori blokova — cuvaju redosled kljuceva i skracuju zapis.
# ─────────────────────────────────────────────────────────────────────────────

def heading(icon, title):
    return {"type": "heading", "text": _t(title), "icon": icon}


def para(text):
    return {"type": "paragraph", "text": _t(text)}


def bullet(icon, title, text, style=None):
    return {"icon": icon, "title": _t(title), "text": _t(text), "style": style}


def bullets(*items):
    return {"type": "bullets", "items": list(items)}


def box(style, icon, title, text):
    return {"type": "box", "style": style, "icon": icon,
            "title": _t(title), "text": _t(text)}


def piece_row(piece, name, count):
    return {"type": "pieceRow", "piece": piece, "name": _t(name), "count": count}


def numbered_rule(number, title, text):
    return {"type": "numberedRule", "number": number,
            "title": _t(title), "text": _t(text)}


# `L_PieceValueTable` drzi redove u samoj komponenti; prepisani su ovde.
KING_INFINITE_VALUE = "∞"

def piece_value_table():
    return {"type": "pieceValueTable", "rows": [
        {"piece": "pawn",   "name": T("Pešak"),  "value": "1"},
        {"piece": "knight", "name": T("Skakač"), "value": "3"},
        {"piece": "bishop", "name": T("Lovac"),  "value": "3"},
        {"piece": "rook",   "name": T("Top"),    "value": "5"},
        {"piece": "queen",  "name": T("Dama"),   "value": "9"},
        {"piece": "king",   "name": T("Kralj"),  "value": KING_INFINITE_VALUE},
    ]}


def exercise(kind, title, hint, icon, uci=None, fen=None,
             solved=None, wrong=None, prompt=None, mate_in=None):
    return {"type": "exercise", "kind": kind,
            "title": _t(title), "hint": _t(hint), "icon": icon,
            "uciMoves": uci,
            "startFEN": fen,
            "solvedMessage": T(solved) if solved else None,
            "wrongMessage": T(wrong) if wrong else None,
            "playingPrompt": T(prompt) if prompt else None,
            "mateIn": mate_in}


EXPLORER = {"type": "explorer"}

# `Divider().background(DS.line)` iz izvora. Prenosi se eksplicitno jer ne prati
# nijedno pravilo koje bi renderer mogao da pogodi — vidi komentar uz
# `LessonBlock.divider`.
DIVIDER = {"type": "divider"}

# Podrazumevane poruke `OpeningLine` (OpeningExerciseViewModel.swift:11-12).
# Otvaranja ih u Swift-u ne navode eksplicitno; ovde se upisuju da JSON bude
# samodovoljan. `playingPrompt` ostaje null jer ga VM racuna po broju poteza.
OPENING_SOLVED = "Bravo! Otvaranje savladano! ✓"
OPENING_WRONG = "Pogrešan potez — pokušaj ponovo."

# Poruke koje `MatePuzzleCard` (LessonDetailView.swift:869-873) postavlja sam.
MATE_SOLVED = "Sjajno! Mat pronađen! 🏆"
MATE_WRONG = "Nije to — traži pravi ključni potez!"
MATE_PROMPT_1 = "Pronađi mat u 1 potezu!"
MATE_PROMPT_N = "Pronađi ključni potez!"


# ─────────────────────────────────────────────────────────────────────────────
# LEKCIJA 1 — Tabla, figure i kretanje   (LessonDetailView.swift:66-313)
# ─────────────────────────────────────────────────────────────────────────────
LESSON_1 = {
    "id": "board-and-pieces",
    "title": T("Tabla, figure i kretanje"),
    "subtitle": T("Osnove šaha za početnike"),
    "icon": "square.grid.3x3.fill",
    "blocks": [
        box("info", "quote.opening", "Kapablanka piše",
            '"Prva stvar koju učenik treba da uradi jeste da upozna snagu figura. Ovo se najlakše postiže učenjem kako se brzo postiže šah-mat."'),
        para("Šah se igra na tabli od **64 polja** naizmenično svetle i tamne boje. Uvek zapamti: **donje desno polje mora biti svetlo**. Svaki igrač počinje sa **16 figura**."),

        heading("hand.point.up.left.fill", "Istraži figure interaktivno"),
        EXPLORER,

        heading("square.grid.2x2.fill", "Kako se svaka figura kreće"),

        # ── lesson1Pieces ──
        piece_row("pawn", "Pion (Pešak)", "× 8"),
        para('Na početku imaš 8 piona — oni su tvoja "pešadija". Kapablanka napominje: **dobitak jednog piona je najmanji materijalni dobitak i često je dovoljan za pobedu**.'),
        bullets(
            bullet("arrow.up", "Kretanje",
                   "Ide isključivo napred, po jedno polje. Na prvom potezu može da preskoči dva polja. **Pioni ne mogu da idu unazad.**"),
            bullet("arrow.up.left.and.arrow.up.right", "Napad",
                   "Jede protivničke figure isključivo po dijagonali jedno polje unapred."),
        ),
        box("rule", "crown.fill", "Promocija",
            "Ako pion stigne do poslednjeg reda — pretvara se u bilo koju figuru, najčešće Damu. Ovo je moćno oružje u završnici!"),

        DIVIDER,
        piece_row("rook", "Top (Kula)", "× 2"),
        para("Stoji u uglovima table na početku. Efikasan je tek na otvorenim linijama — koliko god radi sa pioima koji blokiraju put, toliko je ograničen."),
        bullets(
            bullet("arrow.up.and.down.and.arrow.left.and.right", "Kretanje",
                   "Kreće se po pravim linijama (napred-nazad, levo-desno) koliko god polja želi. Zajedno, dva Topa su neznatno jača od Dame."),
        ),

        DIVIDER,
        piece_row("bishop", "Lovac (Iber)", "× 2"),
        para("Jedan lovac uvek ostaje na belim, drugi na crnim poljima. Kapablanka smatra da je **u većini pozicija Lovac vredniji od Skakača**."),
        bullets(
            bullet("arrow.up.right.and.arrow.up.left", "Kretanje",
                   'Kreće se isključivo po dijagonalama. Slabost: "Topov pion koji promovira na polju suprotne boje od Lovca" najčešće vodi remiju umesto pobede.'),
        ),

        DIVIDER,
        piece_row("knight", "Skakač (Konj)", "× 2"),
        para("Jedina figura koja preskače ostale. Snažan je u **zatvorenim pozicijama** — kada su linije blokirane pionima. Na ivici table gubi na snazi."),
        bullets(
            bullet("l.joystick.fill", "Kretanje",
                   'Kreće se u obliku slova "L": dva polja pravo pa jedno u stranu.'),
        ),
        box("rule", "star.fill", "Jedinstven!",
            "Jedina figura koja može da preskače druge figure — i svoje i protivničke!"),

        DIVIDER,
        piece_row("queen", "Kraljica (Dama)", "× 1"),
        para("Stoji na polju **svoje boje** — bela Dama na belom polju, crna na crnom. Najmoćnija figura, ali ne treba je odmah izvoditi u otvaranju."),
        bullets(
            bullet("arrow.up.and.down.and.arrow.left.and.right", "Kretanje",
                   "Kombinuje kretanje Topa i Lovca — kreće se u svim pravcima, koliko god polja želi."),
        ),

        DIVIDER,
        piece_row("king", "Kralj", "× 1"),
        para("Najvažnija figura — njen gubitak znači kraj igre. U otvaranju je **pasivna odbrambena figura**, ali u završnici postaje moćan napadač."),
        bullets(
            bullet("dot.square.fill", "Kretanje",
                   "Kreće se samo jedno polje u bilo kom pravcu. **Ne sme da stane na napadnuto polje!**"),
        ),

        # ── lesson1Rokada ──
        DIVIDER,
        heading("arrow.left.arrow.right", "Poseban potez: Rokada"),
        para("Jednom u partiji možeš pomeriti **dve figure istovremeno** — Kralja i Topa. Kralj skoči dva polja ka Topu, a Top preskače Kralja i staje pored njega. Ovo služi da skloniš Kralja na sigurno i ubaciš Top u igru."),
        box("rule", "exclamationmark.triangle.fill", "Uslovi za rokadu",
            "Ni Kralj ni Top se do tada **nisu pomerali** · Između njih **nema nijedne figure** · Kralj se ne nalazi u šahu i ne prolazi kroz napadnuto polje"),

        # ── vrednosti figura ──
        DIVIDER,
        heading("scalemass.fill", "Relativna vrednost figura"),
        para("Kapablanka kaže: vrednost nije fiksna — menja se zavisno od pozicije. Ipak, ove brojke služe kao vodič u razmeni figura."),
        piece_value_table(),
        bullets(
            bullet("info.circle.fill", "Dva lovca su gotovo uvek jača od dva skakača",
                   "Lovac je naročito snažan kada postoje pioni na obe strane table i kada su linije otvorene."),
            bullet("info.circle.fill", "Top vredi kao skakač plus dva piona",
                   'Ili lovac plus dva piona. Zato se razmena figure za topa bez kompenzacije naziva "gubljenje kvaliteta".'),
            bullet("crown.fill", "Kralj u završnici postaje napadačka figura",
                   "U otvaranju i središnjici Kralj je isključivo odbrambena figura. U završnici, kada nestane većina figura, mora aktivno da učestvuje u borbi."),
        ),

        # ── elementarni matovi ──
        DIVIDER,
        heading("checkmark.seal.fill", "Elementarni matovi"),
        para("Pre nego što naučiš otvaranja i strategiju, nauči ove tri osnovne mat pozicije. Za svaki od njih potrebna je saradnja Kralja!"),
        exercise("vsEngine", "Vežba 1 — Kralj + Top",
                 "Oteraj crnog Kralja na ivicu table. Top i Kralj moraju da sarađuju!",
                 "rectangle.portrait.fill",
                 fen="8/8/4k3/8/4K3/8/8/R7 w - - 0 1"),
        exercise("vsEngine", "Vežba 2 — Kralj + dva Lovca",
                 "Oteraj Kralja ne samo na ivicu već i u ugao iste boje kao tvoji lovci.",
                 "rhombus.fill",
                 fen="4k3/8/8/8/8/8/8/2B1KB2 w - - 0 1"),
        exercise("vsEngine", "Vežba 3 — Kralj + Dama",
                 "Najlakše! Dama odmah sužava prostor. Pazi na pat!",
                 "crown.fill",
                 fen="4k3/8/8/8/8/8/8/3QK3 w - - 0 1"),
    ],
}


# ─────────────────────────────────────────────────────────────────────────────
# LEKCIJA 2 — Početak igre (Otvaranja)   (LessonDetailView.swift:314-387)
# ─────────────────────────────────────────────────────────────────────────────
LESSON_2 = {
    "id": "openings",
    "title": T("Početak igre (Otvaranja)"),
    "subtitle": T("Zlatna pravila i poznata otvaranja"),
    "icon": "flag.fill",
    "blocks": [
        box("info", "quote.opening", "Kapablanka piše",
            '"Najvažnija stvar u otvaranju je brzo razviti figure. Nijedno parče ne treba pomeriti više od jednom pre nego što je razvoj završen, osim ako je to apsolutno neophodno."'),
        para("U šahu **Beli uvek igra prvi** i zbog toga ima blagu inicijalnu prednost. Zadatak oba igrača u otvaranju je isti: što brže dovesti figure u igru i zauzeti kontrolu nad centrom."),

        heading("checkmark.seal.fill", "Zlatna pravila otvaranja"),
        numbered_rule(1, "Razvijaj figure brzo",
                      "Kapablanka savetuje: skakače razvijaj pre lovaca. Ne pomeraj istu figuru dva puta u otvaranju ako nisi primoran. Svaki potez treba da razvija novu figuru ili kontroliše centar."),
        numbered_rule(2, "Kontroliši centar",
                      'Četiri centralna polja (e4, d4, e5, d5) su najvažnija na tabli. Ko vlada centrom ima više prostora za manevar. Kapablanka: "Nijedan žestok napad ne može uspeti bez kontrole bar dva centralna polja."'),
        numbered_rule(3, "Zaštiti kralja — uradi rokadu!",
                      "Rokadu odigraj što pre je moguće. Kralj na otvorenom je laka meta. Kapablanka sam uvek rokira rano i preporučuje isto svim igračima, posebno početnicima."),

        DIVIDER,
        heading("exclamationmark.triangle.fill", "Tipične greške u otvaranju"),
        # Ove tri stavke su u Swift-u DS.danger (crvene), ne u akcentu lekcije.
        bullets(
            bullet("xmark.circle.fill", "Prerano izvođenje Dame",
                   "Dama je snažna, ali ako je izvedeš rano, protivnik je napada pešacima i figurama — a svaki napad na Damu znači izgubljeni tempo jer mora da beži.",
                   style="warning"),
            bullet("xmark.circle.fill", "Pasivna odbrana pionima",
                   '"Filipidorski" stil — odmah igrati P-d6 kao odgovor na e4 — daje protivniku slobodan razvoj i prostranstvo. Kapablanka pokazuje kako beli tada lako gradi superiornu poziciju.',
                   style="warning"),
            bullet("xmark.circle.fill", "Zakasnela rokada",
                   "Svaki potez bez rokade kada su linije otvorene je rizik. Protivnik može otvoriti igru i napasti tvog kralja pre nego što se skloni.",
                   style="warning"),
        ),

        DIVIDER,
        heading("book.fill", "Poznata otvaranja"),
        para("Odigraj svaki potez belih na tabli — crni odgovara automatski po teorijskoj liniji."),
        exercise("scripted", "Španska partija (Ruy Lopez)",
                 "1.e4 e5 2.Sf3 Sc6 3.Lb5 — Kapablankova omiljena", "crown.fill",
                 uci=["e2e4", "e7e5", "g1f3", "b8c6", "f1b5"],
                 solved=OPENING_SOLVED, wrong=OPENING_WRONG),
        exercise("scripted", "Italijanska partija",
                 "1.e4 e5 2.Sf3 Sc6 3.Lc4 — lovac nišani tačku f7", "flame.fill",
                 uci=["e2e4", "e7e5", "g1f3", "b8c6", "f1c4"],
                 solved=OPENING_SOLVED, wrong=OPENING_WRONG),
        exercise("scripted", "Sicilijanska odbrana",
                 "1.e4 c5 2.Sf3 d6 3.d4 cxd4 4.Sxd4 — asimetrična borba", "shield.fill",
                 uci=["e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4"],
                 solved=OPENING_SOLVED, wrong=OPENING_WRONG),
    ],
}


# ─────────────────────────────────────────────────────────────────────────────
# LEKCIJA 3 — Središnjica   (LessonDetailView.swift:388-451)
# ─────────────────────────────────────────────────────────────────────────────
LESSON_3 = {
    "id": "middlegame",
    "title": T("Središnjica"),
    "subtitle": T("Taktika i srce bitke"),
    "icon": "bolt.fill",
    "blocks": [
        box("info", "quote.opening", "Kapablanka piše",
            '"Idealna središnjica: sve figure su bačene u napad kao masa, koordinirajući se sa mašinskom preciznošću. Cilj svakog majstora je da postigne upravo takvu harmoniju."'),
        para("Kada su figure izvedene i kraljevi na sigurnom, počinje **središnjica** — najkreativniji i najkompleksniji deo šaha."),

        heading("flag.fill", "Inicijativa"),
        para("Kapablanka objašnjava: Beli ima inicijalnu prednost zbog prvog poteza. Ovu prednost treba **čuvati što duže** — predaj je samo ako za uzvrat dobijaš materijal ili bolju poziciju."),
        bullets(
            bullet("arrow.forward.circle.fill", "Ko napadá, dikta tempo",
                   "Igrač sa inicijativom bira gde i kako da napadne. Protivnik mora da reaguje umesto da sprovodi sopstveni plan."),
            bullet("exclamationmark.circle.fill", "Ne napadaj bez sigurnosti",
                   "Kapablanka upozorava: direktan napad na Kralja nikada ne treba voditi do krajnosti ako nema apsolutne sigurnosti da će uspeti. Neuspeo napad znači katastrofu."),
        ),

        DIVIDER,
        heading("scalemass.fill", "Vrednosti figura"),
        para("U središnjici, vrednost figure zavisi od pozicije. Uvek pazi šta razmenjuješ!"),
        piece_value_table(),

        DIVIDER,
        heading("bolt.fill", "Osnovna taktička motiva"),
        box("info", "tuningfork", "Viljuška (Rašlje)",
            "Jedna figura napadne **dve protivničke figure istovremeno**. Protivnik može da spasi samo jednu. Skakači su posebno opasni za viljuške — skaču na polje odakle napadaju Damu i Topa u isto vreme."),
        box("info", "link", "Vezivanje (Pin)",
            "Napadneš figuru koja **ne sme da se pomeri** jer bi time otkrila vrednu figuru iza nje (Kralja ili Damu). Vezana figura je praktično izolovana iz igre — iskoristi to!"),
        box("info", "arrow.triangle.2.circlepath", "Otkriveni napad",
            "Pomeriš jednu figuru i time otkriješ napad druge figure iza nje na protivnikovu vrednu figuru. Posebno opasan kada je i sama figura koja se pomera napadačka."),

        DIVIDER,
        heading("person.2.fill", "Koordinacija figura"),
        para("Kapablanka stalno naglašava: figure moraju da rade zajedno kao tim."),
        bullets(
            bullet("arrow.up.and.down", "Topovi traže otvorene linije",
                   "Postavi ih na otvorenu kolonu ili sedmi red. Top zatvoren iza sopstvenih piona je pasivna figura."),
            bullet("circle.fill", "Skakači najjači u centru",
                   '"Skakač na ivici table je loš skakač" — kaže Kapablanka. U centru kontroliše čak 8 polja, na ivici samo 2-4.'),
            bullet("arrow.up.right", "Lovci vole otvorene dijagonale",
                   "Lovac koji blokira sopstveni pion je ograničen. Pione postavljaj na polja **suprotne boje** od svog lovca."),
        ),

        DIVIDER,
        heading("chart.line.uptrend.xyaxis", "Prednost od jednog piona"),
        box("info", "info.circle.fill", "Kapablankovo zlatno pravilo",
            '"Dobitak jednog piona između jednako jakih igrača najčešće znači pobedu." Ne potcenjuj pion — u završnici je on često odlučujući. Svaka sitna prednost se akumulira!'),
    ],
}


# ─────────────────────────────────────────────────────────────────────────────
# LEKCIJA 4 — Završnica   (LessonDetailView.swift:452-627)
# ─────────────────────────────────────────────────────────────────────────────
LESSON_4 = {
    "id": "endgame",
    "title": T("Završnica"),
    "subtitle": T("Šah-mat, pat i remi"),
    "icon": "flag.checkered",
    "blocks": [
        box("info", "quote.opening", "Kapablanka piše",
            '"Pre nego što se boriš za pobedu u otvaranju ili središnjici, moraš savladati završnicu. Onaj ko ne poznaje završnicu ne može biti jak šahista."'),
        para("Završnica počinje kada su sa table nestale najvažnije figure i ostanu Kraljevi sa pešacima i možda jednom-dve lake figure."),

        heading("crown.fill", "Kralj postaje napadač"),
        para("Ovo je **najveća promena** u završnici. Kralj koji je celu partiju bežao sada mora aktivno da napadá."),
        bullets(
            bullet("crown.fill", "Dovedi Kralja u centar odmah",
                   "Čim oseti da je završnica blizu, počni da pomičeš Kralja ka centru table. Centralni Kralj dominira nad marginalnim."),
            bullet("arrow.up.circle.fill", "Pioni su budući Kraljevi",
                   "Svaki pion koji stigne do poslednjeg reda postaje Dama (ili druga figura). Ovo je glavni cilj u pešačkim završnicama."),
        ),

        DIVIDER,
        heading("arrow.up.circle.fill", "Pravilo o promociji piona"),
        para("Kapablanka objašnjava ovo pravilo jasno i precizno:"),
        box("info", "checkmark.circle.fill", "Ključno pravilo",
            "Da bi pešačka završnica bila pobednička, **Kralj mora biti ispred svog piona** sa barem jednim praznim poljem između njih. Ako je protivnički Kralj direktno ispred piona — igra je remi!"),
        bullets(
            bullet("arrow.up", "Napreduj Kralja, ne piona",
                   "Kapablanka savetuje: napreduj Kralja koliko je moguće a da ne ugrožavaš piona. Piona pomiči tek kada je neophodno za njegovu zaštitu."),
            bullet("ruler.fill", 'Tajno oružje — "Opozicija"',
                   "Kada su dva Kralja međusobno licem u lice sa neparnim brojem polja između, igrač koji je **prethodno poterao** ima prednost. Zove se opozicija — i ključna je za sve pešačke završnice."),
        ),

        DIVIDER,
        heading("bolt.fill", "Kardinalno načelo"),
        box("rule", "star.fill", "Jedno drži dvoje — Kapablankovo načelo",
            '"Pion koji drži dva protivnička piona je jedno od glavnih oruđa majstora." Ako tvoj pion blokira dva protivnička, ti si faktički figuru ispred — iskoristi tu prednost na drugoj strani table!'),

        DIVIDER,
        heading("scalemass.fill", "Lovac vs. Skakač u završnici"),
        bullets(
            bullet("arrow.up.right", "Lovac je jači kada su pioni na obe strane",
                   "Lovac može istovremeno da napada pione na oba krila zahvaljujući dometu. Skakač je spor i ne može da stigne svuda."),
            bullet("l.joystick.fill", "Skakač je jači u zatvorenim pozicijama",
                   "Kada su pioni blokirani i pozicija zatvorena, skakač je bolji jer može da preskoče pione i stigne do idealnog polja."),
        ),
        box("info", "exclamationmark.triangle.fill", "Slabost lovca — Topov pion",
            "Ako tvoj pion ide do h8 (ili a8) i to polje je suprotne boje od tvog lovca, protivnik drži ugao i igra je remi! Kapablanka ovo posebno ističe kao izvor mnogih propuštenih pobeda."),

        DIVIDER,
        heading("trophy.fill", "Šah-Mat i Remi"),
        box("rule", "exclamationmark.triangle.fill", "Šah",
            "Situacija kada je Kralj napadnut. Igrač **mora** da se odbrani — pomeri kralja, pojede napadača, ili postavi štit između."),
        box("warning", "xmark.shield.fill", "Šah-Mat — Kraj igre",
            "Kralj je napadnut, a nema nijedan legalan način odbrane. Partija se završava ovde — Kralj se nikada zapravo ne jede."),
        box("info", "exclamationmark.2", "Pat — Noćna mora pobednika!",
            "Igrač na potezu **nije u šahu**, ali nema nijedan legalan potez. Odmah je remi! Ovo je najopasnija greška u završnici — pretvoriti pobedničku poziciju u remi jednim lošim potezom."),
        bullets(
            bullet("arrow.clockwise", "Ponavljanje pozicije",
                   "Ako se ista pozicija ponovi **tri puta**, može se tražiti remi."),
            bullet("minus.circle.fill", "Nedovoljno materijala",
                   "Samo Kraljevi, ili Kralj + Lovac/Skakač protiv Kralja — nije moguće dati mat. Automatski remi."),
        ),

        # ── Mini finalni test ──
        DIVIDER,
        heading("trophy.fill", "Mini finalni test"),
        para("Primeni sve što si naučio! Reši 5 zadataka — mat u najmanji broj poteza. Svaki koristi drugu kombinaciju figura."),
        exercise("scripted", "Zadatak 1 — Dama na zadnjoj liniji",
                 "Crni Kralj je zarobljen. Dama ima slobodan put...", "crown.fill",
                 uci=["d2d8"], fen="6k1/5ppp/8/8/8/8/3Q4/4R1K1 w - - 0 1",
                 solved=MATE_SOLVED, wrong=MATE_WRONG,
                 prompt=MATE_PROMPT_1, mate_in=1),
        exercise("scripted", "Zadatak 2 — Top na 8. liniji",
                 "Pešaci blokiraju sopstvenog Kralja. Top pronalazi put...",
                 "rectangle.portrait.fill",
                 uci=["b5b8"], fen="6k1/5ppp/8/1R6/8/8/8/6K1 w - - 0 1",
                 solved=MATE_SOLVED, wrong=MATE_WRONG,
                 prompt=MATE_PROMPT_1, mate_in=1),
        exercise("scripted", "Zadatak 3 — Žrtva Topa!",
                 "Top ide na e8 i daje šah. Crni Top mora da uzme — a onda Dama?",
                 "rectangle.portrait.fill",
                 uci=["e1e8", "c8e8", "a4e8"],
                 fen="2r3k1/5ppp/8/8/Q7/8/8/4R1K1 w - - 0 1",
                 solved=MATE_SOLVED, wrong=MATE_WRONG,
                 prompt=MATE_PROMPT_N, mate_in=2),
        exercise("scripted", "Zadatak 4 — Lovac + Top",
                 "Lovac daje šah i tera Kralja na g8. Zašto je to pogubno?",
                 "rhombus.fill",
                 uci=["e5d6", "f8g8", "e1e8"],
                 fen="5k2/5ppp/8/4B3/8/8/8/4R1K1 w - - 0 1",
                 solved=MATE_SOLVED, wrong=MATE_WRONG,
                 prompt=MATE_PROMPT_N, mate_in=2),
        exercise("scripted", "Zadatak 5 (težak) — Žrtva Dame, Lovac mat",
                 "Greet – Hanley, Liverpool 2008. Dama se žrtvuje na h6. Zašto Kralj mora da uzme?",
                 "crown.fill",
                 uci=["d2h6", "g7h6", "h4f6"],
                 fen="r1bq2r1/b4pk1/p1pp1p2/1p2pP2/1P2P1PB/3P4/1PPQ2P1/R3K2R w KQ - 0 1",
                 solved=MATE_SOLVED, wrong=MATE_WRONG,
                 prompt=MATE_PROMPT_N, mate_in=2),

        # ── O autoru ──
        DIVIDER,
        heading("person.fill", "O autoru"),
        box("info", "person.fill", "Hoze Raul Kapablanka (1888–1942)",
            "Kubanski šahista, treći zvanični svetski prvak u šahu. Važi za jednog od najvećih šahiskih genija svih vremena — poznat po kristalno čistom stilu igre i intuitivnom razumevanju pozicije."),
        para("Kapablanka je naučio šah sa svega **četiri godine** gledajući svog oca. Nikada nije pohađao šahovsku školu — sve je naučio sam, igrajući. Već sa 13 godina pobedio je kubanslog prvaka Juana Corzo-a i postao nacionalna senzacija."),
        para("U periodu **1916–1924. godine** nije izgubio nijednu partiju. Svetsku šampionsku titulu osvojio je 1921. pobedivši legendarnog Emanuela Laskera, koji je bio prvak čitavih 27 godina."),
        bullets(
            bullet("eye.fill", "Fotografska preciznost",
                   "Pobedio je jednostavnošću i savršenom tehnikom — ne agresijom."),
            bullet("person.2.fill", "Popularizator šaha",
                   '"Chess Fundamentals" (1921) je pisao upravo za početnike i amatere.'),
        ),

        DIVIDER,
        heading("text.book.closed.fill", "Izvor: Project Gutenberg"),
        para("Sav sadržaj lekcija preuzet je iz digitalne verzije knjige dostupne na **Project Gutenberg** — neprofitnoj biblioteci knjiga u javnom domenu."),
        # Naslov je URL — nije u katalogu i namerno se ne prevodi.
        box("info", "globe", Raw("gutenberg.org/ebooks/33870"),
            "Možeš je pročitati u celosti besplatno, bez registracije."),
        box("info", "heart.fill", "Zahvalnost",
            "Chessko duguje zahvalnost Kapablanki na bezvremenim principima i Project Gutenberg zajednici volontera koji su digitalizovali ovu i hiljade drugih knjiga."),
    ],
}


LESSONS = [LESSON_1, LESSON_2, LESSON_3, LESSON_4]


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    written = 0
    for lesson in LESSONS:
        for lang in LANGS:
            doc = render(lesson, lang)
            doc["language"] = lang
            path = OUT_DIR / f"{lesson['id']}.{lang}.json"
            path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
            written += 1
    print(f"Zapisano {written} fajlova u {OUT_DIR}")


if __name__ == "__main__":
    main()
