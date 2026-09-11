#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_localizations.py — generates Chessko/Localizable.xcstrings (String Catalog).

Source language: Serbian (sr). The Serbian text used in code (Text("…"),
String(localized:"…"), LocalizedStringKey(…)) IS the catalog key. This script
emits translations for: en, fr, de, it, ru, zh-Hans, hi.

Run:  python3 build_localizations.py
Any UI string present in code but missing here simply falls back to Serbian.

Od Faze 3 katalog pokriva SAMO interfejs. Tekst lekcija zivi u
Chessko/Content/lessons/<lekcija>.<jezik>.json i generise ga
build_lesson_json.py — ne dodavati sadrzaj lekcija ovde.
"""
import json, os

LANGS = ["en", "fr", "de", "it", "ru", "zh-Hans", "hi"]
OUT = os.path.join(os.path.dirname(__file__), "Chessko", "Localizable.xcstrings")

# key = Serbian source string ; value = {lang: translation}
T = {}

def add(sr, en, fr, de, it, ru, zh, hi):
    T[sr] = {"en": en, "fr": fr, "de": de, "it": it, "ru": ru, "zh-Hans": zh, "hi": hi}

# ── Piece names (PieceType.srbName / value table / piece rows) ───────────────
add("Kralj", "King", "Roi", "König", "Re", "Король", "国王", "राजा")
add("Dama", "Queen", "Dame", "Dame", "Donna", "Ферзь", "皇后", "वज़ीर")
add("Top", "Rook", "Tour", "Turm", "Torre", "Ладья", "车", "हाथी")
add("Lovac", "Bishop", "Fou", "Läufer", "Alfiere", "Слон", "象", "ऊँट")
add("Skakač", "Knight", "Cavalier", "Springer", "Cavallo", "Конь", "马", "घोड़ा")
add("Pešak", "Pawn", "Pion", "Bauer", "Pedone", "Пешка", "兵", "प्यादा")

# ── Colour adjectives (a11y, lowercase) ─────────────────────────────────────
add("beli", "white", "blanc", "weiß", "bianco", "белый", "白方", "सफ़ेद")
add("crni", "black", "noir", "schwarz", "nero", "чёрный", "黑方", "काला")

# (Colour-picker buttons reuse the localized adjective, capitalised in code,
#  so no separate "Beli"/"Crni" keys — avoids String Catalog symbol collisions.)

# ── Accessibility ───────────────────────────────────────────────────────────
add("mogući potez", "legal move", "coup possible", "möglicher Zug", "mossa possibile", "возможный ход", "可走位置", "संभावित चाल")
add("prazno", "empty", "vide", "leer", "vuoto", "пусто", "空", "खाली")
add("izabrano", "selected", "sélectionné", "ausgewählt", "selezionato", "выбрано", "已选中", "चयनित")
add("poslednji potez", "last move", "dernier coup", "letzter Zug", "ultima mossa", "последний ход", "上一步", "पिछली चाल")
add("Dupli dodir za potez", "Double-tap to move", "Toucher deux fois pour jouer", "Doppeltippen zum Ziehen", "Tocca due volte per muovere", "Двойное касание, чтобы сходить", "双击移动", "चलने के लिए दो बार टैप करें")
add("Dupli dodir za izbor", "Double-tap to select", "Toucher deux fois pour sélectionner", "Doppeltippen zum Auswählen", "Tocca due volte per selezionare", "Двойное касание для выбора", "双击选择", "चुनने के लिए दो बार टैप करें")

# ── Tabs ────────────────────────────────────────────────────────────────────
add("Igra", "Play", "Jouer", "Spielen", "Gioca", "Игра", "对弈", "खेल")
add("Zadaci", "Puzzles", "Problèmes", "Aufgaben", "Problemi", "Задачи", "谜题", "पहेलियाँ")
add("Učenje", "Learn", "Apprendre", "Lernen", "Impara", "Обучение", "学习", "सीखें")

# ── Game screen chrome ──────────────────────────────────────────────────────
add("Nova igra", "New game", "Nouvelle partie", "Neues Spiel", "Nuova partita", "Новая игра", "新对局", "नया खेल")
add("Napustiti partiju?", "Leave the game?", "Quitter la partie ?", "Spiel verlassen?", "Abbandonare la partita?", "Покинуть партию?", "离开对局？", "खेल छोड़ें?")
add("Napusti", "Leave", "Quitter", "Verlassen", "Abbandona", "Покинуть", "离开", "छोड़ें")
add("Nastavi", "Continue", "Continuer", "Fortsetzen", "Continua", "Продолжить", "继续", "जारी रखें")
add("Partija je u toku. Sigurno želiš da počneš iznova?",
    "A game is in progress. Are you sure you want to start over?",
    "Une partie est en cours. Voulez-vous vraiment recommencer ?",
    "Ein Spiel läuft. Möchtest du wirklich neu beginnen?",
    "Una partita è in corso. Vuoi davvero ricominciare?",
    "Партия в процессе. Точно начать заново?",
    "对局正在进行中。确定要重新开始吗？",
    "एक खेल चल रहा है। क्या आप वाकई फिर से शुरू करना चाहते हैं?")
add("Izaberi stranu", "Choose a side", "Choisir un camp", "Wähle eine Seite", "Scegli il colore", "Выберите сторону", "选择执棋方", "अपना पक्ष चुनें")
add("Izaberi figuru", "Choose a piece", "Choisir une pièce", "Wähle eine Figur", "Scegli un pezzo", "Выберите фигуру", "选择棋子", "मोहरा चुनें")
add("Stockfish zahteva NNUE fajlove", "Stockfish requires NNUE files", "Stockfish nécessite des fichiers NNUE", "Stockfish benötigt NNUE-Dateien", "Stockfish richiede file NNUE", "Stockfish требует файлы NNUE", "Stockfish 需要 NNUE 文件", "Stockfish के लिए NNUE फ़ाइलें आवश्यक हैं")
add("Računar", "Computer", "Ordinateur", "Computer", "Computer", "Компьютер", "电脑", "कंप्यूटर")
add("Ti", "You", "Vous", "Du", "Tu", "Вы", "你", "आप")

# ── Stringovi koji su ranije ostali bez kljuca (Faza 3.1) ────────────────────
# Nadjeni kontrolnom proverom "svaki Loc() ima kljuc". Bez njih `Loc(_:)` vraca
# sam kljuc, pa je korisnik na svih 7 stranih jezika video srpski tekst — i to
# na objavama koje se vide u SVAKOJ partiji (sah, mat).
# "Beli"/"Crni" NISU ovde: kljucevi "beli"/"crni" postoje, a generisanje simbola
# im pravi isti simbol pa build puca. GameView zato koristi `.capitalized`.
add("Beli igra", "White to move", "Aux blancs de jouer", "Weiß am Zug", "Muove il Bianco", "Ход белых", "白方走棋", "सफ़ेद की चाल")
add("Crni igra", "Black to move", "Aux noirs de jouer", "Schwarz am Zug", "Muove il Nero", "Ход чёрных", "黑方走棋", "काली की चाल")
add("Mat! Beli je pobedio! 🎉", "Checkmate! White wins! 🎉", "Échec et mat ! Les blancs gagnent ! 🎉", "Schachmatt! Weiß gewinnt! 🎉", "Scacco matto! Vince il Bianco! 🎉", "Мат! Белые победили! 🎉", "将死！白方获胜！🎉", "शहमात! सफ़ेद जीता! 🎉")
add("Mat! Crni je pobedio! 🎉", "Checkmate! Black wins! 🎉", "Échec et mat ! Les noirs gagnent ! 🎉", "Schachmatt! Schwarz gewinnt! 🎉", "Scacco matto! Vince il Nero! 🎉", "Мат! Чёрные победили! 🎉", "将死！黑方获胜！🎉", "शहमात! काला जीता! 🎉")
add("Šah! Beli kralj je napadnut.", "Check! The white king is under attack.", "Échec ! Le roi blanc est attaqué.", "Schach! Der weiße König wird angegriffen.", "Scacco! Il re bianco è sotto attacco.", "Шах! Белый король под боем.", "将军！白王受到攻击。", "शह! सफ़ेद राजा पर हमला है।")
add("Šah! Crni kralj je napadnut.", "Check! The black king is under attack.", "Échec ! Le roi noir est attaqué.", "Schach! Der schwarze König wird angegriffen.", "Scacco! Il re nero è sotto attacco.", "Шах! Чёрный король под боем.", "将军！黑王受到攻击。", "शह! काले राजा पर हमला है।")
add("Predati partiju?", "Resign the game?", "Abandonner la partie ?", "Partie aufgeben?", "Abbandonare la partita?", "Сдать партию?", "认输？", "बाज़ी छोड़ें?")
add("Da li ste sigurni da želite da predate trenutnu partiju?", "Are you sure you want to resign the current game?", "Voulez-vous vraiment abandonner la partie en cours ?", "Möchtest du die laufende Partie wirklich aufgeben?", "Vuoi davvero abbandonare la partita in corso?", "Вы уверены, что хотите сдать текущую партию?", "确定要认输当前对局吗？", "क्या आप वाकई मौजूदा बाज़ी छोड़ना चाहते हैं?")
add("Pregledaj partiju", "Review the game", "Revoir la partie", "Partie analysieren", "Rivedi la partita", "Разобрать партию", "复盘对局", "बाज़ी की समीक्षा करें")
add("Potvrda", "Confirm", "Confirmer", "Bestätigen", "Conferma", "Подтвердить", "确认", "पुष्टि करें")
add("Resetuj", "Reset", "Réinitialiser", "Zurücksetzen", "Reimposta", "Сбросить", "重置", "रीसेट करें")
add("Zatvori", "Close", "Fermer", "Schließen", "Chiudi", "Закрыть", "关闭", "बंद करें")
add("Klasični", "Classic", "Classiques", "Klassisch", "Classici", "Классические", "经典", "क्लासिक")
add("Neonski", "Neon", "Néon", "Neon", "Neon", "Неоновые", "霓虹", "नियॉन")
add("Drvene", "Wooden", "En bois", "Holz", "In legno", "Деревянные", "木质", "लकड़ी")
add("Metalne", "Metal", "Métal", "Metall", "Metallo", "Металлические", "金属", "धातु")
add("Ravne", "Flat", "Plates", "Flach", "Piatte", "Плоские", "扁平", "सपाट")

# ── Difficulty (GameDifficulty.label) ───────────────────────────────────────
add("Lak", "Easy", "Facile", "Leicht", "Facile", "Лёгкий", "简单", "आसान")
add("Srednji", "Medium", "Moyen", "Mittel", "Medio", "Средний", "中等", "मध्यम")
add("Težak", "Hard", "Difficile", "Schwer", "Difficile", "Сложный", "困难", "कठिन")

# ── Game status messages ────────────────────────────────────────────────────
add("Tvoj potez", "Your move", "À vous de jouer", "Du bist am Zug", "Tocca a te", "Ваш ход", "轮到你了", "आपकी चाल")
add("Računar razmišlja...", "Computer is thinking…", "L’ordinateur réfléchit…", "Computer denkt nach…", "Il computer sta pensando…", "Компьютер думает…", "电脑思考中…", "कंप्यूटर सोच रहा है…")
add("Šah! Tvoj kralj je napadnut.", "Check! Your king is under attack.", "Échec ! Votre roi est attaqué.", "Schach! Dein König wird angegriffen.", "Scacco! Il tuo re è sotto attacco.", "Шах! Ваш король под атакой.", "将军！你的国王被攻击。", "शह! आपके राजा पर हमला है।")
add("Šah! Napadaš kralja.", "Check! You attack the king.", "Échec ! Vous attaquez le roi.", "Schach! Du greifst den König an.", "Scacco! Attacchi il re.", "Шах! Вы атакуете короля.", "将军！你在攻击对方国王。", "शह! आप राजा पर हमला कर रहे हैं।")
add("Mat! Izgubio si.", "Checkmate! You lost.", "Échec et mat ! Vous avez perdu.", "Schachmatt! Du hast verloren.", "Scacco matto! Hai perso.", "Мат! Вы проиграли.", "将死！你输了。", "शहमात! आप हार गए।")
add("Mat! Pobedio si! 🎉", "Checkmate! You won! 🎉", "Échec et mat ! Vous avez gagné ! 🎉", "Schachmatt! Du hast gewonnen! 🎉", "Scacco matto! Hai vinto! 🎉", "Мат! Вы выиграли! 🎉", "将死！你赢了！🎉", "शहमात! आप जीत गए! 🎉")
add("Pat – remi!", "Stalemate – draw!", "Pat – nulle !", "Patt – remis!", "Stallo – patta!", "Пат – ничья!", "逼和——平局！", "गतिरोध – ड्रॉ!")
add("Remi – pravilo 50 poteza.", "Draw – fifty-move rule.", "Nulle – règle des 50 coups.", "Remis – 50-Züge-Regel.", "Patta – regola delle 50 mosse.", "Ничья – правило 50 ходов.", "平局——50回合规则。", "ड्रॉ – पचास-चाल नियम।")
add("Remi – ponavljanje pozicije.", "Draw – repetition.", "Nulle – répétition.", "Remis – Stellungswiederholung.", "Patta – ripetizione.", "Ничья – повторение позиции.", "平局——重复局面。", "ड्रॉ – पुनरावृत्ति।")
add("Remi – nedovoljan materijal.", "Draw – insufficient material.", "Nulle – matériel insuffisant.", "Remis – ungenügendes Material.", "Patta – materiale insufficiente.", "Ничья – недостаточно материала.", "平局——子力不足。", "ड्रॉ – अपर्याप्त सामग्री।")

# ── Puzzle screen ───────────────────────────────────────────────────────────
add("Izaberi dan", "Choose a day", "Choisir un jour", "Tag wählen", "Scegli un giorno", "Выберите день", "选择日期", "दिन चुनें")
add("Otkaži", "Cancel", "Annuler", "Abbrechen", "Annulla", "Отмена", "取消", "रद्द करें")
add("Otvori", "Open", "Ouvrir", "Öffnen", "Apri", "Открыть", "打开", "खोलें")
add("Rešeni zadaci", "Solved puzzles", "Problèmes résolus", "Gelöste Aufgaben", "Problemi risolti", "Решённые задачи", "已解决的谜题", "हल की गई पहेलियाँ")
add("Danas", "Today", "Aujourd’hui", "Heute", "Oggi", "Сегодня", "今天", "आज")
add("Juče", "Yesterday", "Hier", "Gestern", "Ieri", "Вчера", "昨天", "कल")
add("Učitavam zadatak...", "Loading puzzle…", "Chargement du problème…", "Aufgabe wird geladen…", "Caricamento problema…", "Загрузка задачи…", "正在加载谜题…", "पहेली लोड हो रही है…")
add("Greška pri učitavanju.", "Loading error.", "Erreur de chargement.", "Ladefehler.", "Errore di caricamento.", "Ошибка загрузки.", "加载出错。", "लोडिंग त्रुटि।")
add("Pronađi pravi potez za bele", "Find the right move for White", "Trouvez le bon coup pour les Blancs", "Finde den richtigen Zug für Weiß", "Trova la mossa giusta per il Bianco", "Найдите верный ход за белых", "为白方找到正确的一步", "सफ़ेद के लिए सही चाल खोजें")
add("Pronađi pravi potez za crne", "Find the right move for Black", "Trouvez le bon coup pour les Noirs", "Finde den richtigen Zug für Schwarz", "Trova la mossa giusta per il Nero", "Найдите верный ход за чёрных", "为黑方找到正确的一步", "काले के लिए सही चाल खोजें")
add("Pogrešno. Pokušaj ponovo.", "Wrong. Try again.", "Faux. Réessayez.", "Falsch. Versuch es nochmal.", "Sbagliato. Riprova.", "Неверно. Попробуйте снова.", "错误。再试一次。", "गलत। फिर कोशिश करें।")
add("Odlično! Zadatak rešen! 🎉", "Excellent! Puzzle solved! 🎉", "Excellent ! Problème résolu ! 🎉", "Ausgezeichnet! Aufgabe gelöst! 🎉", "Ottimo! Problema risolto! 🎉", "Отлично! Задача решена! 🎉", "太棒了！谜题已解决！🎉", "बढ़िया! पहेली हल हो गई! 🎉")
add("Rešenje...", "Solution…", "Solution…", "Lösung…", "Soluzione…", "Решение…", "解答…", "समाधान…")
add("Prikaži rešenje", "Show solution", "Voir la solution", "Lösung anzeigen", "Mostra soluzione", "Показать решение", "显示解答", "समाधान दिखाएँ")
add("Sledeći dan", "Next day", "Jour suivant", "Nächster Tag", "Giorno successivo", "Следующий день", "下一天", "अगला दिन")
add("Sledeći zadatak", "Next puzzle", "Problème suivant", "Nächste Aufgabe", "Prossimo problema", "Следующая задача", "下一题", "अगली पहेली")
add("Završio si zadatak za danas!", "You finished today’s puzzle!", "Vous avez terminé le problème du jour !", "Du hast die heutige Aufgabe gelöst!", "Hai completato il problema di oggi!", "Вы решили сегодняшнюю задачу!", "你完成了今天的谜题！", "आपने आज की पहेली पूरी कर ली!")
add("Pokušaj ponovo", "Try again", "Réessayer", "Erneut versuchen", "Riprova", "Попробовать снова", "重试", "फिर कोशिश करें")
add("Nema dostupnih zadataka", "No puzzles available", "Aucun problème disponible", "Keine Aufgaben verfügbar", "Nessun problema disponibile", "Нет доступных задач", "暂无可用谜题", "कोई पहेली उपलब्ध नहीं")
add("Zadatak je oštećen", "This puzzle is corrupted", "Ce problème est corrompu", "Diese Aufgabe ist beschädigt", "Questo problema è danneggiato", "Задача повреждена", "该题目已损坏", "यह पहेली क्षतिग्रस्त है")
add("Neispravan FEN", "Invalid FEN", "FEN invalide", "Ungültiges FEN", "FEN non valido", "Неверный FEN", "无效的 FEN", "अमान्य FEN")
add("Baza zadataka nije dostupna", "Puzzle database unavailable", "Base de problèmes indisponible", "Aufgabendatenbank nicht verfügbar", "Database dei problemi non disponibile", "База задач недоступна", "题目数据库不可用", "पहेली डेटाबेस उपलब्ध नहीं")

# ── Puzzle themes ───────────────────────────────────────────────────────────
add("Otvaranje", "Opening", "Ouverture", "Eröffnung", "Apertura", "Дебют", "开局", "ओपनिंग")
add("Srednja igra", "Middlegame", "Milieu de partie", "Mittelspiel", "Mediogioco", "Миттельшпиль", "中局", "मध्य खेल")
add("Završnica", "Endgame", "Finale", "Endspiel", "Finale", "Эндшпиль", "残局", "अंत खेल")
add("Mat", "Mate", "Mat", "Matt", "Matto", "Мат", "将杀", "मात")
add("Mat u 1", "Mate in 1", "Mat en 1", "Matt in 1", "Matto in 1", "Мат в 1", "一步杀", "1 में मात")
add("Mat u 2", "Mate in 2", "Mat en 2", "Matt in 2", "Matto in 2", "Мат в 2", "两步杀", "2 में मात")
add("Mat u 3", "Mate in 3", "Mat en 3", "Matt in 3", "Matto in 3", "Мат в 3", "三步杀", "3 में मात")
add("Vilica", "Fork", "Fourchette", "Gabel", "Forchetta", "Вилка", "双叫", "कांटा")
add("Vezivanje", "Pin", "Clouage", "Fesselung", "Inchiodatura", "Связка", "牵制", "पिन")
add("Nabijanje", "Skewer", "Enfilade", "Spieß", "Infilata", "Сквозной удар", "串击", "स्क्यूअर")
add("Žrtva", "Sacrifice", "Sacrifice", "Opfer", "Sacrificio", "Жертва", "弃子", "बलिदान")
add("Otkriveni napad", "Discovered attack", "Attaque à la découverte", "Abzugsangriff", "Attacco di scoperta", "Вскрытое нападение", "闪击", "खुला हमला")
add("Odvlačenje", "Deflection", "Déviation", "Ablenkung", "Deviazione", "Отвлечение", "引离", "विचलन")
add("Prednost", "Advantage", "Avantage", "Vorteil", "Vantaggio", "Преимущество", "优势", "बढ़त")
add("Odlučujuće", "Crushing", "Écrasant", "Entscheidend", "Schiacciante", "Решающее", "决定性", "निर्णायक")
add("Kratko", "Short", "Court", "Kurz", "Corto", "Короткая", "短", "छोटा")
add("Dugo", "Long", "Long", "Lang", "Lungo", "Длинная", "长", "लंबा")
add("Jedan potez", "One move", "Un coup", "Ein Zug", "Una mossa", "Один ход", "一步", "एक चाल")
add("Odbrana", "Defense", "Défense", "Verteidigung", "Difesa", "Защита", "防守", "बचाव")
add("Napad na damu", "Queenside attack", "Attaque à l’aile dame", "Damenflügelangriff", "Attacco sull’ala di donna", "Атака на ферзевом фланге", "后翼进攻", "वज़ीर-पक्ष हमला")
add("Napad na kralja", "Kingside attack", "Attaque à l’aile roi", "Königsflügelangriff", "Attacco sull’ala di re", "Атака на королевском фланге", "王翼进攻", "राजा-पक्ष हमला")

# ── ChessPuzzle.difficultyLabel ─────────────────────────────────────────────
add("Lako", "Easy", "Facile", "Leicht", "Facile", "Легко", "简单", "आसान")
add("Srednje", "Medium", "Moyen", "Mittel", "Medio", "Средне", "中等", "मध्यम")
add("Teško", "Hard", "Difficile", "Schwer", "Difficile", "Сложно", "困难", "कठिन")

# ── Learn tab home ──────────────────────────────────────────────────────────
add("Nauči šah", "Learn chess", "Apprendre les échecs", "Schach lernen", "Impara gli scacchi", "Учитесь играть в шахматы", "学习国际象棋", "शतरंज सीखें")
add("%lld lekcije od osnova do završnice", "%lld lessons from basics to the endgame", "%lld leçons des bases à la finale", "%lld Lektionen von den Grundlagen bis zum Endspiel", "%lld lezioni dalle basi al finale", "%lld уроков от основ до эндшпиля", "%lld 节课，从基础到残局", "%lld पाठ, मूल बातों से अंत तक")

# ── Learn scenarios / move count ────────────────────────────────────────────
add("Rokada", "Castling", "Roque", "Rochade", "Arrocco", "Рокировка", "王车易位", "कैसलिंग")
add("Promocija", "Promotion", "Promotion", "Umwandlung", "Promozione", "Превращение", "升变", "प्रमोशन")
add("Nema mogućih poteza", "No legal moves", "Aucun coup possible", "Keine möglichen Züge", "Nessuna mossa possibile", "Нет возможных ходов", "无可走的棋", "कोई संभव चाल नहीं")
add("1 mogući potez", "1 legal move", "1 coup possible", "1 möglicher Zug", "1 mossa possibile", "1 возможный ход", "1 个可走位置", "1 संभव चाल")
add("%lld mogućih poteza", "%lld legal moves", "%lld coups possibles", "%lld mögliche Züge", "%lld mosse possibili", "Возможных ходов: %lld", "%lld 个可走位置", "%lld संभव चालें")

# ── Mate-exercise (Lesson 1) status ─────────────────────────────────────────
add("Bravo! Mat! 🎉", "Bravo! Checkmate! 🎉", "Bravo ! Échec et mat ! 🎉", "Bravo! Schachmatt! 🎉", "Bravo! Scacco matto! 🎉", "Браво! Мат! 🎉", "太棒了！将死！🎉", "शाबाश! शहमात! 🎉")
add("Poraz — pokušaj ponovo.", "Defeat — try again.", "Défaite — réessayez.", "Niederlage — versuch es nochmal.", "Sconfitta — riprova.", "Поражение — попробуйте снова.", "失败——再试一次。", "हार — फिर कोशिश करें।")
add("Remi — pazi na pat! Pokušaj ponovo.", "Draw — watch for stalemate! Try again.", "Nulle — attention au pat ! Réessayez.", "Remis — Vorsicht vor Patt! Versuch es nochmal.", "Patta — attento allo stallo! Riprova.", "Ничья — берегитесь пата! Попробуйте снова.", "平局——小心逼和！再试一次。", "ड्रॉ — गतिरोध से बचें! फिर कोशिश करें।")
add("Šah! Nastavi...", "Check! Keep going…", "Échec ! Continuez…", "Schach! Mach weiter…", "Scacco! Continua…", "Шах! Продолжайте…", "将军！继续…", "शह! जारी रखें…")
add("Šah — mora da se braniš!", "Check — you must defend!", "Échec — vous devez défendre !", "Schach — du musst verteidigen!", "Scacco — devi difenderti!", "Шах — нужно защищаться!", "将军——你必须应将！", "शह — आपको बचाव करना होगा!")
add("Crni razmišlja...", "Black is thinking…", "Les Noirs réfléchissent…", "Schwarz denkt nach…", "Il Nero sta pensando…", "Чёрные думают…", "黑方思考中…", "काला सोच रहा है…")
add("Na potezu si!", "Your move!", "À vous de jouer !", "Du bist am Zug!", "Tocca a te!", "Ваш ход!", "轮到你了！", "आपकी चाल!")

# ── Opening / mate exercise (lesson cards) ──────────────────────────────────
add("%lld/%lld poteza", "%lld/%lld moves", "%lld/%lld coups", "%lld/%lld Züge", "%lld/%lld mosse", "%lld/%lld ходов", "%lld/%lld 步", "%lld/%lld चालें")
add("Potez %lld — pronađi pravi potez za bele!", "Move %lld — find the right move for White!", "Coup %lld — trouvez le bon coup pour les Blancs !", "Zug %lld — finde den richtigen Zug für Weiß!", "Mossa %lld — trova la mossa giusta per il Bianco!", "Ход %lld — найдите верный ход за белых!", "第 %lld 步——为白方找到正确的一步！", "चाल %lld — सफ़ेद के लिए सही चाल खोजें!")
add("Bravo! Otvaranje savladano! ✓", "Bravo! Opening mastered! ✓", "Bravo ! Ouverture maîtrisée ! ✓", "Bravo! Eröffnung gemeistert! ✓", "Bravo! Apertura padroneggiata! ✓", "Браво! Дебют освоен! ✓", "太棒了！开局已掌握！✓", "शाबाश! ओपनिंग में महारत! ✓")
add("Pogrešan potez — pokušaj ponovo.", "Wrong move — try again.", "Mauvais coup — réessayez.", "Falscher Zug — versuch es nochmal.", "Mossa sbagliata — riprova.", "Неверный ход — попробуйте снова.", "走错了——再试一次。", "गलत चाल — फिर कोशिश करें।")
add("Sjajno! Mat pronađen! 🏆", "Great! Mate found! 🏆", "Génial ! Mat trouvé ! 🏆", "Großartig! Matt gefunden! 🏆", "Ottimo! Matto trovato! 🏆", "Отлично! Мат найден! 🏆", "太棒了！找到将杀！🏆", "बढ़िया! मात मिल गई! 🏆")
add("Nije to — traži pravi ključni potez!", "Not quite — look for the key move!", "Pas tout à fait — cherchez le coup clé !", "Nicht ganz — suche den Schlüsselzug!", "Non proprio — cerca la mossa chiave!", "Не то — ищите ключевой ход!", "不对——寻找关键的一步！", "वह नहीं — मुख्य चाल खोजें!")
add("Pronađi mat u 1 potezu!", "Find mate in 1!", "Trouvez le mat en 1 !", "Finde Matt in 1!", "Trova il matto in 1!", "Найдите мат в 1 ход!", "找出一步杀！", "1 चाल में मात खोजें!")
add("Pronađi ključni potez!", "Find the key move!", "Trouvez le coup clé !", "Finde den Schlüsselzug!", "Trova la mossa chiave!", "Найдите ключевой ход!", "找出关键的一步！", "मुख्य चाल खोजें!")

# ── Generic lesson UI ───────────────────────────────────────────────────────
add("Ponovo", "Restart", "Recommencer", "Neu", "Ricomincia", "Заново", "重来", "फिर से")
add("Mat u %lld", "Mate in %lld", "Mat en %lld", "Matt in %lld", "Matto in %lld", "Мат в %lld", "%lld 步杀", "%lld में मात")
add("Lekcija %lld", "Lesson %lld", "Leçon %lld", "Lektion %lld", "Lezione %lld", "Урок %lld", "第 %lld 课", "पाठ %lld")
add("Lekcija nije dostupna", "Lesson unavailable", "Leçon indisponible", "Lektion nicht verfügbar", "Lezione non disponibile", "Урок недоступен", "课程不可用", "पाठ उपलब्ध नहीं")

# ── Piece explorer (Lekcija 1, `LessonPieceExplorer`) ───────────────────────
add("Specijalna pravila", "Special rules", "Règles spéciales", "Spezielle Regeln", "Regole speciali", "Особые правила", "特殊规则", "विशेष नियम")
add("— izaberi i istraži na tabli", "— pick one and explore on the board", "— choisissez et explorez sur l’échiquier", "— wähle aus und erkunde auf dem Brett", "— scegli ed esplora sulla scacchiera", "— выберите и изучите на доске", "——选择并在棋盘上探索", "— चुनें और बोर्ड पर जानें")
add("Tapni figuru da je promeniš · Tapni polje da je premestiš", "Tap a piece to change it · Tap a square to move it", "Touchez une pièce pour la changer · Touchez une case pour la déplacer", "Tippe auf eine Figur, um sie zu wechseln · Tippe auf ein Feld, um sie zu versetzen", "Tocca un pezzo per cambiarlo · Tocca una casa per spostarlo", "Коснитесь фигуры, чтобы сменить · Коснитесь поля, чтобы переместить", "点按棋子可更换 · 点按格子可移动", "मोहरा बदलने के लिए टैप करें · खाने पर टैप कर इसे हिलाएँ")

# ── Piece value table: point words ──────────────────────────────────────────
# NE brisati: `L_PieceValueTable` ih SASTAVLJA interpolacijom ("\(value) bod…"),
# pa ih pretraga literala po Swift izvoru ne vidi i prijavi kao nekorišćene.
add("1 bod", "1 point", "1 point", "1 Punkt", "1 punto", "1 очко", "1 分", "1 अंक")
add("3 boda", "3 points", "3 points", "3 Punkte", "3 punti", "3 очка", "3 分", "3 अंक")
add("5 boda", "5 points", "5 points", "5 Punkte", "5 punti", "5 очков", "5 分", "5 अंक")
add("9 bodova", "9 points", "9 points", "9 Punkte", "9 punti", "9 очков", "9 分", "9 अंक")

# ── Chess clock + statistics ────────────────────────────────────────────────
add("90 minuta za celu partiju uz dodavanje od 30 sekundi po potezu (format domaćih liga).", "90 minutes for the entire game with a 30-second increment per move (national league format).", "90 minutes pour toute la partie avec un incrément de 30 secondes par coup (format ligue nationale).", "90 Minuten für die gesamte Partie mit 30 Sekunden Inkrement pro Zug (Format der nationalen Liga).", "90 minuti per l'intera partita con incremento di 30 secondi per mossa (formato lega nazionale).", "90 минут на всю партию с добавлением 30 секунд на ход (формат национальной лиги).", "整场比赛 90 分钟，每步增加 30 秒（国家联赛格式）。", "पूरे खेल के लिए 90 मिनट और प्रति चाल 30 सेकंड की वृद्धि (राष्ट्रीय लीग प्रारूप)।")
add("Statistika igranja", "Game Statistics", "Statistiques de jeu", "Spielstatistiken", "Statistiche di gioco", "Статистика игры", "游戏统计", "खेल सांख्यिकी")
add("Odigrano", "Played", "Jouées", "Gespielt", "Giocate", "Сыграно", "已玩", "खेला गया")
add("Pobede", "Wins", "Victoires", "Siege", "Vittorie", "Победы", "获胜", "जीत")
add("Porazi", "Losses", "Défaites", "Niederlagen", "Sconfitte", "Поражения", "失利", "हार")
add("Remi", "Draws", "Nulles", "Remis", "Patte", "Ничьи", "平局", "ड्रॉ")
add("Uspešnost", "Win Rate", "Taux de victoire", "Siegesrate", "Percentuale di vittorie", "Процент побед", "胜率", "जीत दर")
add("Najbolji niz", "Best Streak", "Meilleure série", "Beste Serie", "Miglior serie", "Лучшая серия", "最佳连胜", "सर्वश्रेष्ठ सिलसिला")
add("Rešeno zadataka", "Puzzles Solved", "Problèmes résolus", "Gelöste Rätsel", "Problemi risolti", "Решено задач", "已解谜题", "हल की गई पहेलियाँ")
add("Rejting zadataka", "Puzzle rating", "Classement des problèmes", "Aufgaben-Wertung", "Punteggio problemi", "Рейтинг задач", "谜题评分", "पहेली रेटिंग")
add("Resetuj statistiku", "Reset Statistics", "Réinitialiser les statistiques", "Statistiken zurücksetzen", "Reimposta statistiche", "Сбросить статистику", "重置统计", "सांख्यिकी रीसेट करें")
add("Da li želite da resetujete sve statistike?", "Do you want to reset all statistics?", "Voulez-vous réinitialiser toutes les statistiques ?", "Möchten Sie alle Statistiken zurücksetzen?", "Vuoi reimpostare tutte le statistiche?", "Вы хотите сбросить всю статистику?", "您想重置所有统计数据吗？", "क्या आप सभी सांख्यिकी रीसेट करना चाहते हैं?")

# ── LearnViewModel.infoText (piece-explorer descriptions) ────────────────────
add("Kreće se jedno polje napred. Uzima figuru dijagonalno ispred sebe. Sa startne pozicije može da skoči i dva polja odjednom.",
    "It moves one square forward. It captures a piece diagonally in front of it. From its starting position it may jump two squares at once.",
    "Il avance d’une case. Il capture une pièce en diagonale devant lui. Depuis sa case de départ, il peut avancer de deux cases d’un coup.",
    "Er zieht ein Feld vorwärts. Er schlägt eine Figur diagonal vor sich. Von seinem Startfeld darf er zwei Felder auf einmal vorrücken.",
    "Avanza di una casa. Cattura un pezzo in diagonale davanti a sé. Dalla posizione iniziale può saltare due case in una volta.",
    "Ходит на одно поле вперёд. Бьёт фигуру по диагонали перед собой. С начальной позиции может пойти сразу на два поля.",
    "向前走一格。斜前方吃子。从起始位置可一次跳两格。",
    "यह एक खाना आगे चलता है। अपने सामने तिरछे मोहरा मारता है। शुरुआती स्थिति से एक बार में दो खाने कूद सकता है।")
add("Kreće se horizontalno ili vertikalno, koliko god polja želi. Ne može preskakati figure.",
    "It moves horizontally or vertically, as many squares as it likes. It cannot jump over pieces.",
    "Elle se déplace horizontalement ou verticalement, autant de cases qu’elle veut. Elle ne peut pas sauter par-dessus les pièces.",
    "Er zieht waagerecht oder senkrecht, beliebig weit. Er kann keine Figuren überspringen.",
    "Si muove in orizzontale o verticale, per quante case vuole. Non può saltare i pezzi.",
    "Ходит по горизонтали или вертикали на любое число полей. Не может перепрыгивать фигуры.",
    "横向或纵向走任意格数。不能跳过棋子。",
    "यह क्षैतिज या लंबवत, जितने चाहे खाने चलता है। यह मोहरों के ऊपर से नहीं कूद सकता।")
add("Kreće se u obliku slova L: dva polja u jednom pravcu pa jedno bočno. Jedina figura koja može preskočiti druge.",
    "It moves in an L shape: two squares in one direction, then one to the side. The only piece that can jump over others.",
    "Il se déplace en forme de L : deux cases dans une direction puis une sur le côté. La seule pièce qui peut sauter par-dessus les autres.",
    "Er zieht in L-Form: zwei Felder in eine Richtung, dann eins zur Seite. Die einzige Figur, die über andere springen kann.",
    "Si muove a forma di L: due case in una direzione poi una di lato. L’unico pezzo che può saltare gli altri.",
    "Ходит буквой «Г»: два поля в одну сторону, затем одно вбок. Единственная фигура, способная перепрыгивать через другие.",
    "走 L 形：朝一个方向走两格，再横走一格。唯一能跳过其他棋子的棋子。",
    "यह L आकार में चलता है: एक दिशा में दो खाने फिर एक बगल में। एकमात्र मोहरा जो दूसरों के ऊपर से कूद सकता है।")
add("Kreće se dijagonalno, koliko god polja želi. Uvek ostaje na istoj boji polja.",
    "It moves diagonally, as many squares as it likes. It always stays on the same colour of square.",
    "Il se déplace en diagonale, autant de cases qu’il veut. Il reste toujours sur la même couleur de case.",
    "Er zieht diagonal, beliebig weit. Er bleibt immer auf der gleichen Feldfarbe.",
    "Si muove in diagonale, per quante case vuole. Resta sempre sullo stesso colore di casa.",
    "Ходит по диагонали на любое число полей. Всегда остаётся на полях одного цвета.",
    "沿对角线走任意格数。始终停留在同色格上。",
    "यह तिरछे, जितने चाहे खाने चलता है। यह हमेशा एक ही रंग के खाने पर रहता है।")
add("Najjača figura na tabli. Kombinuje kretanje topa i lovca — horizontalno, vertikalno i dijagonalno.",
    "The strongest piece on the board. It combines the rook’s and bishop’s movement — horizontal, vertical and diagonal.",
    "La pièce la plus forte de l’échiquier. Elle combine les déplacements de la tour et du fou — horizontal, vertical et diagonal.",
    "Die stärkste Figur auf dem Brett. Sie verbindet die Bewegung von Turm und Läufer — waagerecht, senkrecht und diagonal.",
    "Il pezzo più forte della scacchiera. Combina il movimento di torre e alfiere — orizzontale, verticale e diagonale.",
    "Сильнейшая фигура на доске. Сочетает ход ладьи и слона — по горизонтали, вертикали и диагонали.",
    "棋盘上最强的棋子。它兼具车和象的走法——横、竖、斜皆可。",
    "बोर्ड का सबसे मजबूत मोहरा। यह हाथी और ऊँट की चाल को मिलाता है — क्षैतिज, लंबवत और तिरछा।")
add("Kreće se jedno polje u bilo kom smeru. Ne sme stati na polje koje napada protivnik. Zaštiti ga!",
    "It moves one square in any direction. It must not move to a square attacked by the opponent. Protect it!",
    "Il se déplace d’une case dans n’importe quelle direction. Il ne doit pas aller sur une case attaquée par l’adversaire. Protégez-le !",
    "Er zieht ein Feld in beliebige Richtung. Er darf nicht auf ein vom Gegner angegriffenes Feld ziehen. Beschütze ihn!",
    "Si muove di una casa in qualsiasi direzione. Non può andare su una casa attaccata dall’avversario. Proteggilo!",
    "Ходит на одно поле в любом направлении. Нельзя вставать на поле, атакованное соперником. Защищайте его!",
    "向任意方向走一格。不能走到对手攻击的格子上。保护好它！",
    "यह किसी भी दिशा में एक खाना चलता है। यह विरोधी द्वारा हमलाग्रस्त खाने पर नहीं जा सकता। इसकी रक्षा करें!")
add("Poseban potez: ako kralj i top nisu se još pomerali i između njih nema figura, kralj skoči dva polja ka topu, a top preskoči kralja. Tapni g1 (kratka rokada) ili c1 (duga rokada).",
    "A special move: if the king and rook have not yet moved and there are no pieces between them, the king jumps two squares toward the rook and the rook hops over the king. Tap g1 (kingside) or c1 (queenside).",
    "Un coup spécial : si le roi et la tour n’ont pas encore bougé et qu’aucune pièce ne les sépare, le roi saute de deux cases vers la tour et la tour franchit le roi. Touchez g1 (petit roque) ou c1 (grand roque).",
    "Ein besonderer Zug: Wenn König und Turm noch nicht gezogen haben und keine Figur dazwischen steht, springt der König zwei Felder zum Turm und der Turm überspringt den König. Tippe g1 (kurze Rochade) oder c1 (lange Rochade).",
    "Una mossa speciale: se re e torre non si sono ancora mossi e non ci sono pezzi tra loro, il re salta due case verso la torre e la torre scavalca il re. Tocca g1 (arrocco corto) o c1 (arrocco lungo).",
    "Особый ход: если король и ладья ещё не ходили и между ними нет фигур, король прыгает на два поля к ладье, а ладья перескакивает через короля. Коснитесь g1 (короткая рокировка) или c1 (длинная).",
    "特殊走法：如果王和车都还未移动且它们之间没有棋子，王朝车跳两格，车越过王。点按 g1（短易位）或 c1（长易位）。",
    "विशेष चाल: यदि राजा और हाथी अभी तक नहीं हिले हैं और उनके बीच कोई मोहरा नहीं है, तो राजा हाथी की ओर दो खाने कूदता है और हाथी राजा के ऊपर से कूदता है। g1 (छोटी कैसलिंग) या c1 (बड़ी कैसलिंग) पर टैप करें।")
add("Posebno uzimanje pešakom: ako protivnički pešak skoči dva polja i nađe se pored tvojeg pešaka, možeš ga uzeti 'u prolazu' — kao da se pomerio samo jedno polje. Tapni d6.",
    "A special pawn capture: if an enemy pawn jumps two squares and lands beside your pawn, you may take it 'en passant' — as if it had moved only one square. Tap d6.",
    "Une prise spéciale du pion : si un pion adverse avance de deux cases et se retrouve à côté de votre pion, vous pouvez le prendre « en passant » — comme s’il n’avait avancé que d’une case. Touchez d6.",
    "Ein besonderer Bauernschlag: Springt ein gegnerischer Bauer zwei Felder vor und landet neben deinem Bauern, darfst du ihn „en passant“ schlagen — als wäre er nur ein Feld gezogen. Tippe d6.",
    "Una cattura speciale del pedone: se un pedone avversario salta di due case e si trova accanto al tuo, puoi prenderlo 'en passant' — come se avesse mosso di una sola casa. Tocca d6.",
    "Особое взятие пешкой: если пешка соперника прыгает на два поля и оказывается рядом с вашей, вы можете взять её «на проходе» — как если бы она пошла на одно поле. Коснитесь d6.",
    "特殊的兵吃法：如果对方的兵跳两格并停在你的兵旁边，你可以「吃过路兵」——就像它只走了一格一样。点按 d6。",
    "प्यादे का विशेष मारना: यदि विरोधी प्यादा दो खाने कूदकर आपके प्यादे के बगल में आ जाए, तो आप उसे 'अन पासां' (चलते-चलते) मार सकते हैं — मानो वह केवल एक खाना चला हो। d6 पर टैप करें।")
add("Kad beli pešak stigne do osmog reda (redovi 8), može se pretvoriti u bilo koju figuru — gotovo uvek u damu. Tapni e8.",
    "When a white pawn reaches the eighth rank (row 8) it can turn into any piece — almost always a queen. Tap e8.",
    "Quand un pion blanc atteint la huitième rangée (rangée 8), il peut se transformer en n’importe quelle pièce — presque toujours en dame. Touchez e8.",
    "Erreicht ein weißer Bauer die achte Reihe (Reihe 8), kann er sich in eine beliebige Figur verwandeln — fast immer in eine Dame. Tippe e8.",
    "Quando un pedone bianco raggiunge l’ottava traversa (riga 8) può trasformarsi in qualsiasi pezzo — quasi sempre in donna. Tocca e8.",
    "Когда белая пешка достигает восьмой горизонтали (ряд 8), она может превратиться в любую фигуру — почти всегда в ферзя. Коснитесь e8.",
    "当白兵到达第八横线（第 8 行）时，它可升变为任意棋子——几乎总是后。点按 e8。",
    "जब सफ़ेद प्यादा आठवीं रैंक (पंक्ति 8) तक पहुँचता है, तो यह किसी भी मोहरे में बदल सकता है — लगभग हमेशा वज़ीर में। e8 पर टैप करें।")

# ── Settings sheet (hamburger menu) ─────────────────────────────────────────
add("Podešavanja", "Settings", "Réglages", "Einstellungen", "Impostazioni", "Настройки", "设置", "सेटिंग्स")
add("Težina", "Difficulty", "Difficulté", "Schwierigkeit", "Difficoltà", "Сложность", "难度", "कठिनाई")
add("Jezik", "Language", "Langue", "Sprache", "Lingua", "Язык", "语言", "भाषा")
add("Sistem", "System", "Système", "System", "Sistema", "Системный", "跟随系统", "सिस्टम")
add("Gotovo", "Done", "Terminé", "Fertig", "Fatto", "Готово", "完成", "हो गया")
add("Stockfish nivo", "Stockfish level", "Niveau de Stockfish", "Stockfish-Stufe", "Livello Stockfish", "Уровень Stockfish", "Stockfish 等级", "Stockfish स्तर")
add("Veći nivo = jača igra.", "Higher level = stronger play.", "Niveau plus élevé = jeu plus fort.", "Höhere Stufe = stärkeres Spiel.", "Livello più alto = gioco più forte.", "Выше уровень — сильнее игра.", "等级越高，棋力越强。", "उच्च स्तर = मजबूत खेल।")
# Stockfish level names ("Srednje" reused from the puzzle difficulty key)
add("Početnik", "Beginner", "Débutant", "Anfänger", "Principiante", "Новичок", "初学者", "शुरुआती")
add("Amater", "Amateur", "Amateur", "Amateur", "Amatore", "Любитель", "业余", "शौकिया")
add("Napredno", "Advanced", "Avancé", "Fortgeschritten", "Avanzato", "Продвинутый", "高级", "उन्नत")
add("Ekspert", "Expert", "Expert", "Experte", "Esperto", "Эксперт", "专家", "विशेषज्ञ")
add("Maksimalno", "Maximum", "Maximum", "Maximum", "Massimo", "Максимум", "最大", "अधिकतम")

# Settings translations
add("Klasična", "Classic", "Classique", "Klassisch", "Classico", "Классическая", "经典", "क्लासिक")
add("Šumska", "Forest", "Forêt", "Wald", "Foresta", "Лесная", "森林", "वन")
add("Drvo", "Wood", "Bois", "Holz", "Legno", "Дерево", "木质", "लकड़ी")
add("Ugalj", "Charcoal", "Charbon", "Kohle", "Carbonella", "Уголь", "木炭", "कोयला")
add("Polarna", "Polar", "Polaire", "Polar", "Polare", "Полярная", "极地", "ध्रुवीय")
add("Smaragd", "Emerald", "Émeraude", "Smaragd", "Smeraldo", "Изумруд", "祖母绿", "पन्ना")
add("Pesak", "Sand", "Sable", "Sand", "Sabbia", "Песок", "沙滩", "रेत")
add("Sajber", "Cyber", "Cyber", "Cyber", "Cyber", "Кибер", "赛博", "साइबर")
add("Izgled table", "Board Appearance", "Apparence de l'échiquier", "Brett-Aussehen", "Aspetto della scacchiera", "Внешний vuд доски", "棋盘外观", "बोर्ड का रूप")
add("Tema table", "Board Theme", "Thème de l'échiquier", "Brett-Thema", "Tema della scacchiera", "Тема доски", "棋盘主题", "बोर्ड थीम")
add("Prikaži koordinate", "Show Coordinates", "Afficher les coordonnées", "Koordinaten anzeigen", "Mostra coordinate", "Показывать координаты", "显示坐标", "निर्देशांक दिखाएं")
add("Prikaži poslednji potez", "Show Last Move", "Afficher le dernier coup", "Letzten Zug anzeigen", "Mostra l'ultima mossa", "Показывать последний ход", "显示最后一步", "अंतिम चाल दिखाएं")
add("Prikaži moguća polja", "Show Legal Moves", "Afficher les coups légaux", "Legale Züge anzeigen", "Mostra mosse legali", "Показывать возможные ходы", "显示合法走法", "वैध चालें दिखाएं")
add("Stil figura", "Piece Style", "Style des pièces", "Figurenstil", "Stile dei pezzi", "Стиль фигур", "棋子风格", "मोहरों की शैली")
add("Stil", "Style", "Style", "Stil", "Stile", "Стиль", "风格", "शैली")
add("Igra i pravila", "Game & Rules", "Jeu & Règles", "Spiel & Regeln", "Gioco & Regole", "Игра и правила", "游戏与规则", "खेल और नियम")
add("Automatska promocija u damu", "Auto Promote to Queen", "Promotion automatique en dame", "Automatische Damen-Umwandlung", "Promozione automatica a donna", "Автопревращение в ферзя", "自动升变为后", "वज़ीर में स्वचालित पदोन्नति")
add("Rotiraj tablu u lokalnoj igri", "Rotate Board in Local Play", "Tourner l'échiquier en jeu local", "Brett im lokalen Spiel drehen", "Ruota la scacchiera nel gioco locale", "Поворачивать доску в локальной игре", "在本地对战中旋转棋盘", "स्थानीय खेल में board घुमाएँ")
add("Izgled", "Appearance", "Apparence", "Aussehen", "Aspetto", "Оформление", "外观", "रूप")
add("Svetla", "Light", "Clair", "Hell", "Chiaro", "Светлая", "浅色", "हल्का")
add("Tamna", "Dark", "Sombre", "Dunkel", "Scuro", "Тёмная", "深色", "गहरा")
add("Ostalo", "Other", "Autre", "Sonstiges", "Altro", "Другое", "其他", "अन्य")
add("Tanke", "Thin", "Fin", "Dünn", "Sottile", "Тонкие", "细体", "पतली")
add("Igraonica", "Gameroom", "Gameroom", "Spielzimmer", "Gameroom", "Игровая комната", "游戏室", "गेमरूम")
add("Staklene", "Glass", "Verre", "Glas", "Vetro", "Стеклянные", "玻璃", "कांच")
add("Prevlačenje levo/desno", "Swipe left/right", "Glisser gauche/droite", "Wischen links/rechts", "Scorri sinistra/destra", "Проведите влево/вправо", "左右滑动", "बाएं/दाएं स्वाइप करें")
add("Prevlačenje gore/dole", "Swipe up/down", "Glisser haut/bas", "Wischen oben/unten", "Scorri su/giù", "Проведите вверх/вниз", "上下滑动", "ऊपर/नीचे स्वाइप करें")

# Chess AI and Stockfish Level Name Proposals
add("Lokalni AI: Početnik", "Local AI: Beginner", "IA locale: Débutant", "Lokale KI: Anfänger", "IA locale: Principiante", "Локальный ИИ: Новичок", "本地 AI：初学者", "स्थानीय एआई: शुरुआती")
add("Lokalni AI: Amater", "Local AI: Amateur", "IA locale: Amateur", "Lokale KI: Amateur", "IA locale: Dilettante", "Локальный ИИ: Любитель", "本地 AI：业余", "स्थानीय एआई: शौकिया")
add("Lokalni AI: Napredni", "Local AI: Advanced", "IA locale: Avancé", "Lokale KI: Fortgeschritten", "IA locale: Avanzato", "Локальный ИИ: Продвинутый", "本地 AI：高级", "स्थानीय एआई: उन्नत")
add("Stockfish: 1300 ELO", "Stockfish: 1300 ELO", "Stockfish: ELO 1300", "Stockfish: 1300 ELO", "Stockfish: 1300 ELO", "Stockfish: 1300 ELO", "Stockfish：1300 ELO", "स्टॉकफ़िश: 1300 ELO")
add("Stockfish: 1600 ELO", "Stockfish: 1600 ELO", "Stockfish: ELO 1600", "Stockfish: 1600 ELO", "Stockfish: 1600 ELO", "Stockfish: 1600 ELO", "Stockfish：1600 ELO", "स्टॉकफ़िश: 1600 ELO")
add("Stockfish: 1900 ELO", "Stockfish: 1900 ELO", "Stockfish: ELO 1900", "Stockfish: 1900 ELO", "Stockfish: 1900 ELO", "Stockfish: 1900 ELO", "Stockfish：1900 ELO", "स्टॉकफ़िश: 1900 ELO")
add("Stockfish: 2200 ELO", "Stockfish: 2200 ELO", "Stockfish: ELO 2200", "Stockfish: 2200 ELO", "Stockfish: 2200 ELO", "Stockfish: 2200 ELO", "Stockfish：2200 ELO", "स्टॉकफ़िश: 2200 ELO")
add("Stockfish: 2600 ELO", "Stockfish: 2600 ELO", "Stockfish: ELO 2600", "Stockfish: 2600 ELO", "Stockfish: 2600 ELO", "Stockfish: 2600 ELO", "Stockfish：2600 ELO", "स्टॉकफ़िश: 2600 ELO")
add("Stockfish: Maksimalno", "Stockfish: Maximum", "Stockfish: Maximum", "Stockfish: Maximum", "Stockfish: Massimo", "Stockfish: Максимум", "Stockfish：最大", "स्टॉकफ़िश: अधिकतम")

# Missing translations added on July 8, 2026
add("Zvuk", "Sound", "Son", "Ton", "Suono", "Звук", "声音", "ध्वनि")
add("Vibracija", "Haptics", "Vibrations", "Vibration", "Vibrazione", "Вибрация", "振动反馈", "कंपन")
add("Ostalo", "Other", "Autre", "Sonstiges", "Altro", "Другое", "其他", "अन्य")
add("O aplikaciji", "About", "À propos", "Über die App", "Informazioni", "О приложении", "关于应用", "ऐप के बारे में")
add("Verzija 1.0.0", "Version 1.0.0", "Version 1.0.0", "Version 1.0.0", "Versione 1.0.0", "Версия 1.0.0", "版本 1.0.0", "संस्करण 1.0.0")
add("Ova aplikacija je otvorenog koda, koristi Stockfish šahovski pokretač pod GPLv3 licencom i preuzima šahovske zadatke iz slobodne Lichess baze.", "This app is open-source, runs the Stockfish engine under GPLv3, and fetches puzzles from the free Lichess database.", "Cette application est open-source, utilise le moteur Stockfish sous GPLv3 et récupère les problèmes de la base libre Lichess.", "Diese App ist Open-Source, nutzt die Stockfish-Engine unter GPLv3 und lädt Aufgaben aus der freien Lichess-Datenbank.", "Questa app è open-source, utilizza il motore Stockfish sotto licenza GPLv3 e scarica i problemi dal database gratuito di Lichess.", "Это приложение с открытым исходным кодом, использует шахматный движок Stockfish под лицензией GPLv3 и загружает задачи из свободной базы Lichess.", "本应用为开源软件，在 GPLv3 许可下运行 Stockfish 引擎，并从免费的 Lichess 数据库获取谜题。", "यह ऐप ओपन-सोर्स है, GPLv3 के तहत स्टॉकफ़िश इंजन चलाता है, और मुफ़्त Lichess डेटाबेस से पहेलियाँ प्राप्त करता है।")
add("Igraj protiv računara", "Play vs Computer", "Jouer contre l'ordinateur", "Gegen den Computer spielen", "Gioca contro il computer", "Играть против компьютера", "人机对弈", "कंप्यूटर के खिलाफ खेलें")
add("Igraj sa prijateljem", "Play vs Friend", "Jouer contre un ami", "Gegen einen Freund spielen", "Gioca contro un amico", "Играть с другом", "双人对弈", "दोस्त के खिलाफ खेलें")
add("Šahovski sat", "Chess Clock", "Pendule d'échecs", "Schach-Uhr", "Orologio da scacchi", "Шахматные часы", "棋钟", "शतरंज की घड़ी")
add("Potezi", "Moves", "Coups", "Züge", "Mosse", "Ходы", "步数", "चालें")
add("Zvanični svetski blic", "Official world blitz", "Blitz mondial officiel", "Offizieller Welt-Blitz", "Blitz mondiale ufficiale", "Официальный мировой блиц", "官方世界超快棋", "आधिकारिक विश्व ब्लिट्ज़")
add("Stara škola blica", "Old school blitz", "Blitz à l'ancienne", "Klassischer Blitz", "Blitz vecchia scuola", "Старошкольный блиц", "传统超快棋", "पुराने ढर्रे का ब्लिट्ज़")
add("Turnirski blic", "Tournament blitz", "Blitz de tournoi", "Turnier-Blitz", "Blitz da torneo", "Турнирный блиц", "锦标赛超快棋", "टूर्नामेंट ब्लिट्ज़")
add("Zvanični svetski rapid", "Official world rapid", "Rapid mondial officiel", "Offizielles Welt-Rapid", "Rapid mondiale ufficiale", "Официальный мировой рапид", "官方世界快棋", "आधिकारिक विश्व रैपिड")
add("Popularni online rapid", "Popular online rapid", "Rapide en ligne populaire", "Popularer Online-Rapid", "Rapid online popolare", "Популярный онлайн-рапид", "热门线上快棋", "लोकप्रिय ऑनलाइन रैपिड")
add("Lokalni turniri", "Local tournaments", "Tournois locaux", "Lokale Turniere", "Tornei locali", "Местные турниры", "地方锦标赛", "स्थानीय टूर्नामेंट")
add("Turniri kandidata / FIDE", "Candidates / FIDE tournaments", "Tournois des Candidats / FIDE", "Kandidaten- / FIDE-Turniere", "Tornei dei Candidati / FIDE", "Турниры претендентов / FIDE", "候选人赛 / FIDE 锦标赛", "उम्मीदवार / FIDE टूर्नामेंट")
add("Domaća liga", "National league", "Ligue nationale", "Nationale Liga", "Lega nazionale", "Национальная лига", "国家联赛", "राष्ट्रीय लीग")
add("Vreme je isteklo!", "Time's up!", "Temps écoulé !", "Zeit abgelaufen!", "Tempo scaduto!", "Время истекло!", "时间到！", "समय समाप्त!")
add("Poteza: %d", "Moves: %d", "Coups : %d", "Züge: %d", "Mosse: %d", "Ходов: %d", "步数：%d", "चालें: %d")
add("Objašnjenje vremenskih kontrola", "Time Control Explanations", "Explications des contrôles du temps", "Erklärung der Zeitkontrollen", "Spiegazione dei controlli di tempo", "Объяснение контроля времени", "时间控制说明", "समय नियंत्रण स्पष्टीकरण")
add("3 minuta + 2 sekunde inkrementa. Zvanični format na Svetskim prvenstvima u blicu.", "3 minutes + 2 seconds increment. Official format for the World Blitz Championships.", "3 minutes + 2 secondes d'incrément. Format officiel des championnats du monde de blitz.", "3 Minuten + 2 Sekunden Inkrement. Offizielles Format bei den Blitz-Weltmeisterschaften.", "3 minuti + 2 secondi di incremento. Formato ufficiale nei Campionati mondiali blitz.", "3 минуты + 2 секунды добавления. Официальный формат чемпионатов мира по блицу.", "3 分钟 + 2 秒增量。世界超快棋锦标赛官方格式。", "3 मिनट + 2 सेकंड की वृद्धि। विश्व ब्लिट्ज़ चैंपियनशिप के लिए आधिकारिक प्रारूप।")
add("Čistih 5 minuta bez inkrementa. Stara škola blica.", "Straight 5 minutes with no increment. Old school blitz.", "5 minutes brutes sans incrément. Blitz à l'ancienne.", "Glatte 5 Minuten ohne Inkrement. Klassischer Blitz.", "5 minuti senza incremento. Blitz vecchia scuola.", "Чистые 5 минут без добавления. Старошкольный блиц.", "无增量的纯 5 分钟。传统超快棋。", "बिना किसी वृद्धि के सीधे 5 मिनट। पुराने ढर्रे का ब्लिट्ज़।")
add("5 minuta + 3 sekunde inkrementa. Koristi se na jačim online turnirima.", "5 minutes + 3 seconds increment. Used in competitive online tournaments.", "5 minutes + 3 secondes d'incrément. Utilisé dans les tournois en ligne compétitifs.", "5 Minuten + 3 Sekunden Inkrement. Wird in wettbewerbsfähigen Online-Turnieren verwendet.", "5 minuti + 3 secondi di incremento. Usato nei tornei online competitivi.", "5 минут + 3 секунды добавления. Используется в сильных онлайн-турнирах.", "5 分钟 + 3 秒增量。用于竞争性线上锦标赛。", "5 मिनट + 3 सेकंड की वृद्धि। प्रतिस्पर्धी ऑनलाइन टूर्नामेंट में उपयोग किया जाता है।")
add("15 minuta + 10 sekundi inkrementa. Zvanični FIDE format za Svetska prvenstva.", "15 minutes + 10 seconds increment. Official FIDE format for the World Rapid Championships.", "15 minutes + 10 secondes d'incrément. Format FIDE officiel pour les championnats du monde de rapide.", "15 Minuten + 10 Sekunden Inkrement. Offizielles FIDE-Format für die Rapid-Weltmeisterschaften.", "15 minuti + 10 secondi di incremento. Formato FIDE ufficiale per i Campionati mondiali rapid.", "15 минут + 10 секунд добавления. Официальный формат FIDE для чемпионатов мира по рапиду.", "15 分钟 + 10 秒增量。世界快棋锦标赛官方 FIDE 格式。", "15 मिनट + 10 सेकंड की वृद्धि। विश्व रैपिड चैंपियनशिप के लिए आधिकारिक FIDE प्रारूप।")
add("10 minuta + 5 sekundi inkrementa. Popularan online format, ozbiljniji od blica.", "10 minutes + 5 seconds increment. Popular online format, more serious than blitz.", "10 minutes + 5 secondes d'incrément. Format en ligne populaire, plus sérieux que le blitz.", "10 Minuten + 5 Sekunden Inkrement. Beliebtes Online-Format, ernsthafter als Blitz.", "10 minuti + 5 secondi di incremento. Popolare formato online, più serio del blitz.", "10 минут + 5 секунд добавления. Популярный онлайн-формат, более серьезный, чем блиц.", "10 分钟 + 5 秒增量。热门线上格式，比超快棋更正规。", "10 मिनट + 5 सेकंड की वृद्धि। लोकप्रिय ऑनलाइन प्रारूप, ब्लिट्ज़ से अधिक गंभीर।")
add("25 minuta + 10 sekundi inkrementa. Čest format na lokalnim turnirima.", "25 minutes + 10 seconds increment. Common format for local tournaments.", "25 minutes + 10 secondes d'incrément. Format courant pour los tournois locaux.", "25 Minuten + 10 Sekunden Inkrement. Häufiges Format bei lokalen Turnieren.", "25 minuti + 10 secondi di incremento. Formato comune nei tornei locali.", "25 минут + 10 секунд добавления. Распространенный формат местных турниров.", "25 分钟 + 10 秒增量。地方锦标赛的常见格式。", "25 मिनट + 10 सेकंड की वृद्धि। स्थानीय टूर्नामेंट के लिए सामान्य प्रारूप।")
add("90 minuta za 40 poteza, potom +30 minuta, uz 30s inkrementa. Format za svetske šampionate.", "90 minutes for 40 moves, then +30 minutes, with a 30s increment. Format for world championships.", "90 minutes pour 40 coups, puis +30 minutes, avec un incrément de 30s. Format des championnats du monde.", "90 Minuten für 40 Züge, dann +30 Minuten, bei 30s Inkrement. Format für Weltmeisterschaften.", "90 minuti per 40 mosse, poi +30 minuti, con incremento di 30s. Formato per campionati mondiali.", "90 минут на 40 ходов, затем +30 минут, с добавлением 30с. Формат чемпионатов мира.", "40 步棋 90 分钟，之后 +30 分钟，每步增加 30 秒。世界锦标赛格式。", "40 चालों के लिए 90 मिनट, फिर +30 मिनट, 30s की वृद्धि के साथ। विश्व चैंपियनशिप के लिए प्रारूप।")
add("90 minuta za celu partiju uz dodavanje od 30 sekundi po potezu (format domaćih liga).", "90 minutes for the entire game with a 30-second increment per move (national league format).", "90 minutes pour toute la partie avec un incrément de 30 secondes par coup (format ligue nationale).", "90 Minuten für die gesamte Partie mit 30 Sekunden Inkrement pro Zug (Format der nationalen Liga).", "90 minuti per l'intera partita con incremento di 30 secondi per mossa (formato lega nazionale).", "90 минут на всю партию с добавлением 30 секунд на ход (формат национальной лиги).", "整场比赛 90 分钟，每步增加 30 秒（国家联赛格式）。", "पूरे खेल के लिए 90 मिनट और प्रति चाल 30 सेकंड की वृद्धि (राष्ट्रीय लीग प्रारूप)।")
add("Početnik (~500 ELO)", "Beginner (~500 ELO)", "Débutant (~500 ELO)", "Anfänger (~500 ELO)", "Principiante (~500 ELO)", "Новичок (~500 ELO)", "初学者 (~500 ELO)", "शुरुआती (~500 ELO)")
add("Za one koji uče pravila i osnove", "For learning rules and basics", "Pour apprendre les règles et les bases", "Zum Lernen der Regeln und Grundlagen", "Per imparare le regole e le basi", "Для изучения правил и основ", "适合学习规则与基础", "नियम और बुनियादी बातें सीखने के लिए")
add("Lako (~900 ELO)", "Easy (~900 ELO)", "Facile (~900 ELO)", "Leicht (~900 ELO)", "Facile (~900 ELO)", "Легко (~900 ELO)", "简单 (~900 ELO)", "आसान (~900 ELO)")
add("Opuštena i prijatna partija", "Relaxed and casual game", "Partie détendue et décontractée", "Entspanntes Freizeitspiel", "Partita rilassata e informale", "Расслабленная игра для отдыха", "轻松休闲的对局", "आरामदायक और आकस्मिक खेल")
add("Srednje (~1300 ELO)", "Medium (~1300 ELO)", "Moyen (~1300 ELO)", "Mittel (~1300 ELO)", "Medio (~1300 ELO)", "Средне (~1300 ELO)", "中等 (~1300 ELO)", "मध्यम (~1300 ELO)")
add("Dobar balans za redovne igrače", "Good balance for regular players", "Bon équilibre pour les joueurs réguliers", "Gute Balance für regelmäßige Spieler", "Buon equilibrio per giocatori abituali", "Хороший баланс для регулярных игроков", "适合经常下棋的均衡难度", "नियमित खिलाड़ियों के लिए अच्छा संतुलन")
add("Teško (~1700 ELO)", "Hard (~1700 ELO)", "Difficile (~1700 ELO)", "Schwer (~1700 ELO)", "Difficile (~1700 ELO)", "Сложно (~1700 ELO)", "困难 (~1700 ELO)", "कठिन (~1700 ELO)")
add("Snažna igra bez previda", "Strong play with few mistakes", "Jeu solide sans erreurs", "Starkes Spiel ohne Fehler", "Gioco solido senza sviste", "Сильная игра без очевидных ошибок", "几乎不出错的强劲着法", "बिना किसी बड़ी गलती के मजबूत खेल")
add("Stockfish Majstor (2200+ ELO)", "Stockfish Master (2200+ ELO)", "Stockfish Maître (2200+ ELO)", "Stockfish Meister (2200+ ELO)", "Stockfish Maestro (2200+ ELO)", "Stockfish Мастер (2200+ ELO)", "Stockfish 大师 (2200+ ELO)", "Stockfish मास्टर (2200+ ELO)")
add("Maksimalna snaga šahovskog motora", "Maximum chess engine power", "Puissance maximale du moteur d'échecs", "Maximale Stärke der Schachengine", "Massima potenza del motore scacchistico", "Максимальная сила шахматного движка", "国际象棋引擎的最高水平", "शतरंज इंजन की अधिकतम ताकत")
add("Početnik", "Beginner", "Débutant", "Anfänger", "Principiante", "Новичок", "初学者", "शुरुआती")
add("Težina protivnika (AI)", "Opponent Difficulty (AI)", "Difficulté de l'adversaire (IA)", "Gegnerstärke (KI)", "Difficoltà avversario (IA)", "Сложность противника (ИИ)", "对手难度 (AI)", "प्रतिद्वंद्वी कठिनाई (AI)")
add("Težina protivnika", "Opponent Difficulty", "Difficulté de l'adversaire", "Gegnerstärke", "Difficoltà avversario", "Сложность противника", "对手难度", "प्रतिद्वंद्वी कठिनाई")
add("Predaja", "Resign", "Abandonner", "Aufgeben", "Abbandona", "Сдаться", "认输", "इस्तीफा")
add("Predaj", "Resign", "Abandonner", "Aufgeben", "Abbandona", "Сдаться", "认输", "इस्तीफा")
add("Predaja partije", "Resign Game", "Abandonner la partie", "Partie aufgeben", "Abbandona la partita", "Сдать партию", "认输棋局", "खेल से इस्तीफा दें")
add("Da li ste sigurni da želite da predate partiju?", "Are you sure you want to resign the game?", "Êtes-vous sûr de vouloir abandonner la partie ?", "Sind Sie sicher, dass Sie die Partie aufgeben möchten?", "Sei sicuro di voler abbandonare la partita?", "Вы уверены, что хотите сдать партию?", "您确定要认输吗？", "क्या आप सुनिश्चित हैं कि आप खेल छोड़ना चाहते हैं?")
add("Nastavi igru", "Continue Playing", "Continuer à jouer", "Weiterspielen", "Continua a giocare", "Продолжить игру", "继续对局", "खेलना जारी रखें")
add("Predaja! Crni je pobedio.", "Resignation! Black wins.", "Abandon ! Les Noirs gagnent.", "Aufgabe! Schwarz gewinnt.", "Abbandono! Il Nero vince.", "Сдача! Чёрные победили.", "认输！黑方获胜。", "इस्तीफा! काला जीता।")
add("Predaja! Beli je pobedio.", "Resignation! White wins.", "Abandon ! Les Blancs gagnent.", "Aufgabe! Weiß gewinnt.", "Abbandono! Il Bianco vince.", "Сдача! Белые победили.", "认输！白方获胜。", "इस्तीफा! सफेद जीता।")
add("Predaja! Izgubio si.", "Resignation! You lost.", "Abandon ! Vous avez perdu.", "Aufgabe! Du hast verloren.", "Abbandono! Hai perso.", "Сдача! Вы проиграли.", "认输！您输了。", "इस्तीफा! आप हार गए।")
add("Predaja! Pobedio si! 🎉", "Resignation! You won! 🎉", "Abandon ! Vous avez gagné ! 🎉", "Aufgabe! Du hast gewonnen! 🎉", "Abbandono! Hai vinto! 🎉", "Сдача! Вы победили! 🎉", "认输！您获胜了！🎉", "इस्तीफा! आप जीत गए! 🎉")
add("Pregled partije", "Game Review", "Analyse de la partie", "Partieanalyse", "Revisione partita", "Обзор партии", "复盘分析", "खेल समीक्षा")
add("Početna pozicija", "Initial position", "Position initiale", "Ausgangsposition", "Posizione iniziale", "Начальная позиция", "初始位置", "प्रारंभिक स्थिति")
add("Potez %d od %d", "Move %d of %d", "Coup %d sur %d", "Zug %d von %d", "Mossa %d di %d", "Ход %d из %d", "第 %d 步（共 %d 步）", "चाल %d / %d")
add("Evaluaciona traka (Eval Bar)", "Evaluation Bar (Eval Bar)", "Barre d'évaluation (Eval Bar)", "Bewertungsleiste (Eval Bar)", "Barra di valutazione (Eval Bar)", "Шкала оценки (Eval Bar)", "局面评估条（Eval Bar）", "मूल्यांकन पट्टी (Eval Bar)")
add("Prikazuje ocenu pozicije u realnom vremenu pored table", "Shows real-time position evaluation next to the board", "Affiche l'évaluation en temps réel à côté de l'échiquier", "Zeigt die Stellungsbewertung in Echtzeit neben dem Brett", "Mostra la valutazione della posizione in tempo reale accanto alla scacchiera", "Показывает оценку позиции в реальном времени рядом с доской", "在棋盘旁实时显示局面优劣评估", "बोर्ड के बगल में वास्तविक समय में स्थिति का मूल्यांकन दिखाता है")

# ── Put (Faza 4a) ───────────────────────────────────────────────────────────
# "Nastavi" vec postoji gore (dijalog "Napustiti partiju?") i namerno se
# ne duplira — isti smisao, isti kljuc.
add("Put", "Path", "Parcours", "Pfad", "Percorso", "Путь", "路径", "पथ")
add("Korak %lld", "Step %lld", "Étape %lld", "Schritt %lld", "Passo %lld", "Шаг %lld", "第 %lld 步", "चरण %lld")
add("Vežba", "Practice", "Entraînement", "Übung", "Esercizio", "Практика", "练习", "अभ्यास")
add("Partija", "Game", "Partie", "Partie", "Partita", "Партия", "对局", "बाज़ी")
add("Test", "Test", "Test", "Test", "Test", "Тест", "测验", "परीक्षा")
add("Vežba · %lld", "Practice · %lld", "Entraînement · %lld", "Übung · %lld", "Esercizio · %lld", "Практика · %lld", "练习 · %lld", "अभ्यास · %lld")
add("Test · %lld", "Test · %lld", "Test · %lld", "Test · %lld", "Test · %lld", "Тест · %lld", "测验 · %lld", "परीक्षा · %lld")
add("Dana zaredom", "Day streak", "Jours d'affilée", "Tage in Folge", "Giorni di fila", "Дней подряд", "连续天数", "लगातार दिन")
add("Cilj za danas ispunjen", "Today's goal met", "Objectif du jour atteint", "Tagesziel erreicht", "Obiettivo di oggi raggiunto", "Цель на сегодня выполнена", "今日目标已完成", "आज का लक्ष्य पूरा")
add("Cilj za danas nije ispunjen", "Today's goal not met yet", "Objectif du jour non atteint", "Tagesziel noch nicht erreicht", "Obiettivo di oggi non ancora raggiunto", "Цель на сегодня ещё не выполнена", "今日目标尚未完成", "आज का लक्ष्य अभी पूरा नहीं हुआ")
add("Prešao si ceo put!", "You finished the whole path!", "Tu as terminé tout le parcours !", "Du hast den ganzen Pfad geschafft!", "Hai completato tutto il percorso!", "Вы прошли весь путь!", "你走完了整条路径！", "आपने पूरा पथ पूरा किया!")
add("Put nije dostupan", "Path unavailable", "Parcours indisponible", "Pfad nicht verfügbar", "Percorso non disponibile", "Путь недоступен", "路径不可用", "पथ उपलब्ध नहीं")
add("Uskoro", "Coming soon", "Bientôt", "Demnächst", "Prossimamente", "Скоро", "即将推出", "जल्द आ रहा है")
add("Nastavi, korak %lld, %@", "Continue, step %lld, %@", "Continuer, étape %lld, %@", "Weiter, Schritt %lld, %@", "Continua, passo %lld, %@", "Продолжить, шаг %lld, %@", "继续，第 %lld 步，%@", "जारी रखें, चरण %lld, %@")
add("Korak %lld, %@, uskoro dostupno", "Step %lld, %@, coming soon", "Étape %lld, %@, bientôt disponible", "Schritt %lld, %@, demnächst verfügbar", "Passo %lld, %@, prossimamente", "Шаг %lld, %@, скоро", "第 %lld 步，%@，即将推出", "चरण %lld, %@, जल्द उपलब्ध")
add("Zaključano", "Locked", "Verrouillé", "Gesperrt", "Bloccato", "Заблокировано", "已锁定", "बंद")
add("Dostupno", "Available", "Disponible", "Verfügbar", "Disponibile", "Доступно", "可用", "उपलब्ध")
add("Završeno", "Completed", "Terminé", "Abgeschlossen", "Completato", "Завершено", "已完成", "पूर्ण")
add("Zaključano — završi prethodne korake", "Locked — finish the earlier steps", "Verrouillé — termine les étapes précédentes", "Gesperrt — schließe die vorherigen Schritte ab", "Bloccato — completa i passi precedenti", "Заблокировано — завершите предыдущие шаги", "已锁定 — 请先完成前面的步骤", "बंद — पहले के चरण पूरे करें")
add("Završi korak", "Finish step", "Terminer l'étape", "Schritt abschließen", "Completa il passo", "Завершить шаг", "完成此步", "चरण पूरा करें")
add("Korak je završen", "Step completed", "Étape terminée", "Schritt abgeschlossen", "Passo completato", "Шаг завершён", "此步已完成", "चरण पूरा हो गया")

# ── Pokretac koraka `practice` / `test` (Faza 4a, Task 4) ───────────────────
add("Zadatak %lld od %lld", "Puzzle %lld of %lld", "Problème %lld sur %lld", "Aufgabe %lld von %lld", "Problema %lld di %lld", "Задача %lld из %lld", "第 %lld 题，共 %lld 题", "पहेली %lld / %lld")
add("Test mora biti rešen bez greške", "The test must be solved without mistakes", "Le test doit être réussi sans erreur", "Der Test muss fehlerfrei gelöst werden", "Il test va superato senza errori", "Тест нужно пройти без ошибок", "测验必须零失误通过", "परीक्षा बिना गलती के पूरी करनी होगी")
add("Greška — test kreće ispočetka", "Mistake — the test restarts", "Erreur — le test recommence", "Fehler — der Test beginnt von vorn", "Errore — il test ricomincia", "Ошибка — тест начинается заново", "失误 — 测验重新开始", "गलती — परीक्षा फिर से शुरू")
add("Nazad na Put", "Back to the path", "Retour au parcours", "Zurück zum Pfad", "Torna al percorso", "Назад к пути", "返回路径", "पथ पर वापस")
add("Korak nije dostupan", "Step unavailable", "Étape indisponible", "Schritt nicht verfügbar", "Passo non disponibile", "Шаг недоступен", "此步不可用", "चरण उपलब्ध नहीं")

# ── Pokretac koraka `game` (Faza 4a, Task 5) ───────────────────────────────
add("Korak se završava kad partija dođe do kraja.",
    "The step is completed once the game reaches its end.",
    "L'étape se termine lorsque la partie arrive à son terme.",
    "Der Schritt gilt als abgeschlossen, sobald die Partie zu Ende ist.",
    "Il passo si completa quando la partita arriva alla fine.",
    "Шаг завершается, когда партия доходит до конца.",
    "对局下到结束时，此步即完成。",
    "बाज़ी के अंत तक पहुँचने पर यह चरण पूरा हो जाता है।")


def build():
    entries = {}
    for sr, trans in T.items():
        locs = {"sr": {"stringUnit": {"state": "translated", "value": sr}}}
        for lang, val in trans.items():
            locs[lang] = {"stringUnit": {"state": "translated", "value": val}}
        entries[sr] = {"extractionState": "manual", "localizations": locs}
    catalog = {"sourceLanguage": "sr", "strings": entries, "version": "1.0"}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)
    print(f"Wrote {OUT} with {len(entries)} keys × {len(LANGS)+1} languages.")

if __name__ == "__main__":
    build()
