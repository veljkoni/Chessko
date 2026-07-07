#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_localizations.py — generates Chessko/Localizable.xcstrings (String Catalog).

Source language: Serbian (sr). The Serbian text used in code (Text("…"),
String(localized:"…"), LocalizedStringKey(…)) IS the catalog key. This script
emits translations for: en, fr, de, it, ru, zh-Hans, hi.

Run:  python3 build_localizations.py
Any UI string present in code but missing here simply falls back to Serbian.
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
add("Završio si zadatak za danas!", "You finished today’s puzzle!", "Vous avez terminé le problème du jour !", "Du hast die heutige Aufgabe gelöst!", "Hai completato il problema di oggi!", "Вы решили сегодняшнюю задачу!", "你完成了今天的谜题！", "आपने आज की पहेली पूरी कर ली!")
add("Pokušaj ponovo", "Try again", "Réessayer", "Erneut versuchen", "Riprova", "Попробовать снова", "重试", "फिर कोशिश करें")
add("Neispravan URL", "Invalid URL", "URL invalide", "Ungültige URL", "URL non valido", "Неверный URL", "无效的网址", "अमान्य URL")
add("Nema dostupnih zadataka", "No puzzles available", "Aucun problème disponible", "Keine Aufgaben verfügbar", "Nessun problema disponibile", "Нет доступных задач", "暂无可用谜题", "कोई पहेली उपलब्ध नहीं")
add("Greška mreže: %@", "Network error: %@", "Erreur réseau : %@", "Netzwerkfehler: %@", "Errore di rete: %@", "Сетевая ошибка: %@", "网络错误：%@", "नेटवर्क त्रुटि: %@")
add("Neispravan FEN", "Invalid FEN", "FEN invalide", "Ungültiges FEN", "FEN non valido", "Неверный FEN", "无效的 FEN", "अमान्य FEN")

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
add("Napad na dam", "Queenside attack", "Attaque à l’aile dame", "Damenflügelangriff", "Attacco sull’ala di donna", "Атака на ферзевом фланге", "后翼进攻", "वज़ीर-पक्ष हमला")
add("Napad na kral", "Kingside attack", "Attaque à l’aile roi", "Königsflügelangriff", "Attacco sull’ala di re", "Атака на королевском фланге", "王翼进攻", "राजा-पक्ष हमला")

# ── ChessPuzzle.difficultyLabel ─────────────────────────────────────────────
add("Lako", "Easy", "Facile", "Leicht", "Facile", "Легко", "简单", "आसान")
add("Srednje", "Medium", "Moyen", "Mittel", "Medio", "Средне", "中等", "मध्यम")
add("Teško", "Hard", "Difficile", "Schwer", "Difficile", "Сложно", "困难", "कठिन")

# ── Learn tab home ──────────────────────────────────────────────────────────
add("Nauči šah", "Learn chess", "Apprendre les échecs", "Schach lernen", "Impara gli scacchi", "Учитесь играть в шахматы", "学习国际象棋", "शतरंज सीखें")
add("4 lekcije od osnova do završnice", "4 lessons from basics to the endgame", "4 leçons des bases jusqu’à la finale", "4 Lektionen von den Grundlagen bis zum Endspiel", "4 lezioni dalle basi al finale", "4 урока: от основ до эндшпиля", "从基础到残局的 4 节课", "मूल बातों से अंत खेल तक 4 पाठ")

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

# ── Lesson titles / subtitles ───────────────────────────────────────────────
add("Tabla, figure i kretanje", "The board, pieces and movement", "L’échiquier, les pièces et les déplacements", "Brett, Figuren und Bewegung", "La scacchiera, i pezzi e il movimento", "Доска, фигуры и ходы", "棋盘、棋子与走法", "बोर्ड, मोहरे और चाल")
add("Osnove šaha za početnike", "Chess basics for beginners", "Les bases des échecs pour débutants", "Schach-Grundlagen für Anfänger", "Le basi degli scacchi per principianti", "Основы шахмат для начинающих", "面向初学者的国际象棋基础", "शुरुआती लोगों के लिए शतरंज की मूल बातें")
add("Početak igre (Otvaranja)", "The start of the game (Openings)", "Le début de la partie (Ouvertures)", "Spielbeginn (Eröffnungen)", "L’inizio della partita (Aperture)", "Начало партии (дебюты)", "对局开始（开局）", "खेल की शुरुआत (ओपनिंग)")
add("Zlatna pravila i poznata otvaranja", "Golden rules and famous openings", "Règles d’or et ouvertures célèbres", "Goldene Regeln und berühmte Eröffnungen", "Regole d’oro e aperture famose", "Золотые правила и известные дебюты", "黄金法则与著名开局", "स्वर्णिम नियम और प्रसिद्ध ओपनिंग")
add("Središnjica", "The middlegame", "Le milieu de partie", "Das Mittelspiel", "Il mediogioco", "Миттельшпиль", "中局", "मध्य खेल")
add("Taktika i srce bitke", "Tactics and the heart of the battle", "La tactique, cœur de la bataille", "Taktik und das Herz der Schlacht", "La tattica e il cuore della battaglia", "Тактика и сердце битвы", "战术与战斗的核心", "रणनीति और लड़ाई का दिल")
add("Šah-mat, pat i remi", "Checkmate, stalemate and draw", "Échec et mat, pat et nulle", "Schachmatt, Patt und Remis", "Scacco matto, stallo e patta", "Мат, пат и ничья", "将杀、逼和与平局", "शहमात, गतिरोध और ड्रॉ")

# ── Section headers & generic lesson UI ─────────────────────────────────────
add("Istraži figure interaktivno", "Explore the pieces interactively", "Explorez les pièces de façon interactive", "Figuren interaktiv erkunden", "Esplora i pezzi in modo interattivo", "Изучайте фигуры интерактивно", "互动探索棋子", "मोहरों को इंटरैक्टिव रूप से जानें")
add("Specijalna pravila", "Special rules", "Règles spéciales", "Spezielle Regeln", "Regole speciali", "Особые правила", "特殊规则", "विशेष नियम")
add("— izaberi i istraži na tabli", "— pick one and explore on the board", "— choisissez et explorez sur l’échiquier", "— wähle aus und erkunde auf dem Brett", "— scegli ed esplora sulla scacchiera", "— выберите и изучите на доске", "——选择并在棋盘上探索", "— चुनें और बोर्ड पर जानें")
add("Tapni figuru da je promeniš · Tapni polje da je premestiš", "Tap a piece to change it · Tap a square to move it", "Touchez une pièce pour la changer · Touchez une case pour la déplacer", "Tippe auf eine Figur, um sie zu wechseln · Tippe auf ein Feld, um sie zu versetzen", "Tocca un pezzo per cambiarlo · Tocca una casa per spostarlo", "Коснитесь фигуры, чтобы сменить · Коснитесь поля, чтобы переместить", "点按棋子可更换 · 点按格子可移动", "मोहरा बदलने के लिए टैप करें · खाने पर टैप कर इसे हिलाएँ")
add("Kako se svaka figura kreće", "How each piece moves", "Comment chaque pièce se déplace", "Wie sich jede Figur bewegt", "Come si muove ogni pezzo", "Как ходит каждая фигура", "每个棋子如何走", "हर मोहरा कैसे चलता है")
add("Relativna vrednost figura", "Relative value of the pieces", "Valeur relative des pièces", "Relativer Wert der Figuren", "Valore relativo dei pezzi", "Относительная ценность фигур", "棋子的相对价值", "मोहरों का सापेक्ष मूल्य")
add("Elementarni matovi", "Elementary checkmates", "Mats élémentaires", "Elementare Mattbilder", "Matti elementari", "Элементарные маты", "基本将杀", "प्रारंभिक शहमात")
add("Poseban potez: Rokada", "A special move: Castling", "Un coup spécial : le roque", "Ein besonderer Zug: Rochade", "Una mossa speciale: l’arrocco", "Особый ход: рокировка", "特殊走法：王车易位", "विशेष चाल: कैसलिंग")
add("Zlatna pravila otvaranja", "Golden rules of the opening", "Les règles d’or de l’ouverture", "Goldene Regeln der Eröffnung", "Le regole d’oro dell’apertura", "Золотые правила дебюта", "开局的黄金法则", "ओपनिंग के स्वर्णिम नियम")
add("Tipične greške u otvaranju", "Typical opening mistakes", "Erreurs typiques en ouverture", "Typische Eröffnungsfehler", "Errori tipici in apertura", "Типичные ошибки в дебюте", "常见的开局错误", "ओपनिंग की आम गलतियाँ")
add("Poznata otvaranja", "Famous openings", "Ouvertures célèbres", "Berühmte Eröffnungen", "Aperture famose", "Известные дебюты", "著名开局", "प्रसिद्ध ओपनिंग")
add("Inicijativa", "Initiative", "L’initiative", "Initiative", "Iniziativa", "Инициатива", "主动权", "पहल")
add("Vrednosti figura", "Piece values", "Valeur des pièces", "Figurenwerte", "Valore dei pezzi", "Ценность фигур", "棋子价值", "मोहरों का मूल्य")
add("Osnovna taktička motiva", "Basic tactical motifs", "Motifs tactiques de base", "Grundlegende taktische Motive", "Motivi tattici di base", "Базовые тактические мотивы", "基本战术手段", "बुनियादी रणनीतिक तरीके")
add("Koordinacija figura", "Piece coordination", "Coordination des pièces", "Figurenkoordination", "Coordinazione dei pezzi", "Координация фигур", "棋子的协调", "मोहरों का समन्वय")
add("Prednost od jednog piona", "A one-pawn advantage", "L’avantage d’un pion", "Ein Bauer Vorteil", "Il vantaggio di un pedone", "Преимущество в одну пешку", "一兵的优势", "एक प्यादे की बढ़त")
add("Kralj postaje napadač", "The king becomes an attacker", "Le roi devient attaquant", "Der König wird zum Angreifer", "Il re diventa attaccante", "Король становится атакующим", "国王成为进攻者", "राजा हमलावर बन जाता है")
add("Pravilo o promociji piona", "The rule of pawn promotion", "La règle de la promotion du pion", "Die Regel der Bauernumwandlung", "La regola della promozione del pedone", "Правило превращения пешки", "兵的升变规则", "प्यादे के प्रमोशन का नियम")
add("Kardinalno načelo", "A cardinal principle", "Un principe cardinal", "Ein Grundprinzip", "Un principio cardine", "Главный принцип", "核心原则", "मूल सिद्धांत")
add("Lovac vs. Skakač u završnici", "Bishop vs. Knight in the endgame", "Fou contre Cavalier en finale", "Läufer vs. Springer im Endspiel", "Alfiere contro Cavallo nel finale", "Слон против коня в эндшпиле", "残局中的象对马", "अंत खेल में ऊँट बनाम घोड़ा")
add("Šah-Mat i Remi", "Checkmate and draw", "Échec et mat et nulle", "Schachmatt und Remis", "Scacco matto e patta", "Мат и ничья", "将杀与平局", "शहमात और ड्रॉ")
add("Mini finalni test", "Mini final test", "Mini test final", "Mini-Abschlusstest", "Mini test finale", "Мини финальный тест", "迷你结业测试", "मिनी अंतिम परीक्षा")
add("O autoru", "About the author", "À propos de l’auteur", "Über den Autor", "Sull’autore", "Об авторе", "关于作者", "लेखक के बारे में")
add("Izvor: Project Gutenberg", "Source: Project Gutenberg", "Source : Project Gutenberg", "Quelle: Project Gutenberg", "Fonte: Project Gutenberg", "Источник: Project Gutenberg", "来源：古腾堡计划", "स्रोत: Project Gutenberg")
add("Kapablanka piše", "Capablanca writes", "Capablanca écrit", "Capablanca schreibt", "Capablanca scrive", "Капабланка пишет", "卡帕布兰卡写道", "कापाब्लांका लिखते हैं")

# ── Piece value table: point words ──────────────────────────────────────────
add("1 bod", "1 point", "1 point", "1 Punkt", "1 punto", "1 очко", "1 分", "1 अंक")
add("3 boda", "3 points", "3 points", "3 Punkte", "3 punti", "3 очка", "3 分", "3 अंक")
add("5 boda", "5 points", "5 points", "5 Punkte", "5 punti", "5 очков", "5 分", "5 अंक")
add("9 bodova", "9 points", "9 points", "9 Punkte", "9 punti", "9 очков", "9 分", "9 अंक")

# ── Lesson 1: piece rows ────────────────────────────────────────────────────
add("Pion (Pešak)", "Pawn", "Le Pion", "Der Bauer", "Il Pedone", "Пешка", "兵", "प्यादा")
add("Top (Kula)", "Rook", "La Tour", "Der Turm", "La Torre", "Ладья", "车", "हाथी")
add("Lovac (Iber)", "Bishop", "Le Fou", "Der Läufer", "L’Alfiere", "Слон", "象", "ऊँट")
add("Skakač (Konj)", "Knight", "Le Cavalier", "Der Springer", "Il Cavallo", "Конь", "马", "घोड़ा")
add("Kraljica (Dama)", "Queen", "La Dame", "Die Dame", "La Donna", "Ферзь", "皇后", "वज़ीर")

# ── Lesson 1: bullets / boxes / paras ───────────────────────────────────────
add("Kretanje", "Movement", "Déplacement", "Bewegung", "Movimento", "Ход", "走法", "चाल")
add("Napad", "Attack", "Attaque", "Angriff", "Attacco", "Атака", "进攻", "हमला")
add("Jedinstven!", "Unique!", "Unique !", "Einzigartig!", "Unico!", "Уникален!", "独一无二！", "अद्वितीय!")
add("Uslovi za rokadu", "Conditions for castling", "Conditions du roque", "Bedingungen für die Rochade", "Condizioni per l’arrocco", "Условия рокировки", "王车易位的条件", "कैसलिंग की शर्तें")
add("Dva lovca su gotovo uvek jača od dva skakača", "Two bishops are almost always stronger than two knights", "Deux fous sont presque toujours plus forts que deux cavaliers", "Zwei Läufer sind fast immer stärker als zwei Springer", "Due alfieri sono quasi sempre più forti di due cavalli", "Два слона почти всегда сильнее двух коней", "双象几乎总是强于双马", "दो ऊँट लगभग हमेशा दो घोड़ों से मजबूत होते हैं")
add("Top vredi kao skakač plus dva piona", "A rook is worth a knight plus two pawns", "Une tour vaut un cavalier plus deux pions", "Ein Turm ist einen Springer plus zwei Bauern wert", "Una torre vale un cavallo più due pedoni", "Ладья стоит коня плюс две пешки", "一车价值相当于一马加两兵", "एक हाथी एक घोड़े और दो प्यादों के बराबर है")
add("Kralj u završnici postaje napadačka figura", "In the endgame the king becomes an attacking piece", "En finale, le roi devient une pièce d’attaque", "Im Endspiel wird der König zur Angriffsfigur", "Nel finale il re diventa un pezzo d’attacco", "В эндшпиле король становится атакующей фигурой", "残局中国王成为进攻棋子", "अंत खेल में राजा एक हमलावर मोहरा बन जाता है")

# ── Lesson 1: quote + body prose ────────────────────────────────────────────
add('"Prva stvar koju učenik treba da uradi jeste da upozna snagu figura. Ovo se najlakše postiže učenjem kako se brzo postiže šah-mat."',
    '"The first thing a student must do is become acquainted with the power of the pieces. This is best done by learning how to deliver checkmate quickly."',
    '« La première chose qu’un élève doit faire est de se familiariser avec la force des pièces. Le mieux est d’apprendre à mater rapidement. »',
    '„Das Erste, was ein Schüler tun muss, ist die Kraft der Figuren kennenzulernen. Am besten lernt man dies, indem man schnell mattsetzt.“',
    '"La prima cosa che uno studente deve fare è conoscere la forza dei pezzi. Il modo migliore è imparare a dare scacco matto rapidamente."',
    '«Первое, что должен сделать ученик, — познакомиться с силой фигур. Лучше всего это даётся через умение быстро ставить мат.»',
    '“学习者首先要做的是了解棋子的力量。最好的方法就是学会快速将杀。”',
    '"छात्र को सबसे पहले मोहरों की ताकत से परिचित होना चाहिए। यह जल्दी शहमात देना सीखकर सबसे अच्छा होता है।"')
add("Šah se igra na tabli od **64 polja** naizmenično svetle i tamne boje. Uvek zapamti: **donje desno polje mora biti svetlo**. Svaki igrač počinje sa **16 figura**.",
    "Chess is played on a board of **64 squares** that alternate light and dark. Always remember: **the bottom-right square must be light**. Each player starts with **16 pieces**.",
    "Les échecs se jouent sur un échiquier de **64 cases** claires et foncées en alternance. Retenez toujours : **la case en bas à droite doit être claire**. Chaque joueur commence avec **16 pièces**.",
    "Schach wird auf einem Brett mit **64 Feldern** in abwechselnd hellen und dunklen Farben gespielt. Merke dir immer: **das Feld unten rechts muss hell sein**. Jeder Spieler beginnt mit **16 Figuren**.",
    "Gli scacchi si giocano su una scacchiera di **64 caselle** chiare e scure alternate. Ricorda sempre: **la casella in basso a destra deve essere chiara**. Ogni giocatore inizia con **16 pezzi**.",
    "Шахматы играются на доске из **64 полей** попеременно светлого и тёмного цвета. Всегда помните: **нижнее правое поле должно быть светлым**. Каждый игрок начинает с **16 фигурами**.",
    "国际象棋在 **64 格** 的棋盘上进行，深浅格交替。永远记住：**右下角必须是浅色格**。每方以 **16 个棋子** 开始。",
    "शतरंज **64 खानों** के बोर्ड पर खेला जाता है जो हल्के और गहरे रंग के होते हैं। हमेशा याद रखें: **नीचे-दाईं ओर का खाना हल्का होना चाहिए**। हर खिलाड़ी **16 मोहरों** से शुरू करता है।")
add("Kapablanka kaže: vrednost nije fiksna — menja se zavisno od pozicije. Ipak, ove brojke služe kao vodič u razmeni figura.",
    "Capablanca says: value is not fixed — it changes with the position. Still, these numbers serve as a guide when exchanging pieces.",
    "Capablanca dit : la valeur n’est pas fixe — elle change selon la position. Ces chiffres servent toutefois de guide pour les échanges.",
    "Capablanca sagt: Der Wert ist nicht fest — er ändert sich je nach Stellung. Dennoch dienen diese Zahlen als Leitfaden beim Abtausch.",
    "Capablanca dice: il valore non è fisso — cambia con la posizione. Tuttavia questi numeri servono da guida negli scambi.",
    "Капабланка говорит: ценность не постоянна — она меняется в зависимости от позиции. И всё же эти числа служат ориентиром при разменах.",
    "卡帕布兰卡说：价值并非固定——它随局面变化。不过这些数字在兑子时可作参考。",
    "कापाब्लांका कहते हैं: मूल्य स्थिर नहीं है — यह स्थिति के अनुसार बदलता है। फिर भी ये अंक मोहरों की अदला-बदली में मार्गदर्शक हैं।")
add("Lovac je naročito snažan kada postoje pioni na obe strane table i kada su linije otvorene.",
    "The bishop is especially strong when there are pawns on both sides of the board and the lines are open.",
    "Le fou est particulièrement fort lorsqu’il y a des pions des deux côtés et que les lignes sont ouvertes.",
    "Der Läufer ist besonders stark, wenn auf beiden Seiten Bauern stehen und die Linien offen sind.",
    "L’alfiere è particolarmente forte quando ci sono pedoni su entrambi i lati e le linee sono aperte.",
    "Слон особенно силён, когда пешки есть на обоих флангах и линии открыты.",
    "当棋盘两侧都有兵且线路开放时，象尤为强大。",
    "ऊँट तब विशेष रूप से मजबूत होता है जब बोर्ड के दोनों ओर प्यादे हों और लाइनें खुली हों।")
add('Ili lovac plus dva piona. Zato se razmena figure za topa bez kompenzacije naziva "gubljenje kvaliteta".',
    'Or a bishop plus two pawns. That is why giving up a minor piece for a rook without compensation is called "losing the exchange".',
    'Ou un fou plus deux pions. C’est pourquoi céder une pièce mineure contre une tour sans compensation s’appelle « perdre la qualité ».',
    'Oder ein Läufer plus zwei Bauern. Deshalb nennt man das Hergeben einer Leichtfigur für einen Turm ohne Kompensation „Qualitätsverlust“.',
    'Oppure un alfiere più due pedoni. Per questo cedere un pezzo minore per una torre senza compenso si chiama "perdere la qualità".',
    'Или слон плюс две пешки. Поэтому отдачу лёгкой фигуры за ладью без компенсации называют «потерей качества».',
    '或一象加两兵。因此无补偿地用轻子换车被称为“亏交换”。',
    'या एक ऊँट और दो प्यादे। इसीलिए बिना मुआवजे के हाथी के बदले छोटा मोहरा देना "क्वालिटी गँवाना" कहलाता है।')
add("U otvaranju i središnjici Kralj je isključivo odbrambena figura. U završnici, kada nestane većina figura, mora aktivno da učestvuje u borbi.",
    "In the opening and middlegame the king is purely a defensive piece. In the endgame, once most pieces are gone, it must take an active part in the fight.",
    "En ouverture et milieu de partie, le roi est purement défensif. En finale, quand la plupart des pièces ont disparu, il doit participer activement au combat.",
    "In Eröffnung und Mittelspiel ist der König rein defensiv. Im Endspiel, wenn die meisten Figuren weg sind, muss er aktiv am Kampf teilnehmen.",
    "In apertura e mediogioco il re è un pezzo puramente difensivo. Nel finale, quando la maggior parte dei pezzi è sparita, deve partecipare attivamente alla lotta.",
    "В дебюте и миттельшпиле король — чисто оборонительная фигура. В эндшпиле, когда большинства фигур не стало, он должен активно участвовать в борьбе.",
    "在开局和中局，国王纯属防守棋子。到了残局，大部分棋子消失后，它必须积极投入战斗。",
    "ओपनिंग और मध्य खेल में राजा केवल रक्षात्मक मोहरा है। अंत खेल में, जब अधिकांश मोहरे चले जाते हैं, उसे सक्रिय रूप से लड़ाई में भाग लेना चाहिए।")
add("Pre nego što naučiš otvaranja i strategiju, nauči ove tri osnovne mat pozicije. Za svaki od njih potrebna je saradnja Kralja!",
    "Before learning openings and strategy, master these three basic mating positions. Each of them needs the king’s cooperation!",
    "Avant d’apprendre les ouvertures et la stratégie, maîtrisez ces trois mats de base. Chacun nécessite la coopération du roi !",
    "Bevor du Eröffnungen und Strategie lernst, beherrsche diese drei grundlegenden Mattbilder. Jedes davon braucht die Mithilfe des Königs!",
    "Prima di imparare aperture e strategia, padroneggia queste tre posizioni di matto di base. Ognuna richiede la collaborazione del re!",
    "Прежде чем учить дебюты и стратегию, освойте эти три базовых матовых позиции. В каждой нужна помощь короля!",
    "在学习开局和策略之前，先掌握这三种基本将杀。每一种都需要国王的配合！",
    "ओपनिंग और रणनीति सीखने से पहले, इन तीन बुनियादी मात स्थितियों में महारत हासिल करें। हर एक में राजा का सहयोग आवश्यक है!")
add("Vežba 1 — Kralj + Top", "Exercise 1 — King + Rook", "Exercice 1 — Roi + Tour", "Übung 1 — König + Turm", "Esercizio 1 — Re + Torre", "Упражнение 1 — король + ладья", "练习 1——王 + 车", "अभ्यास 1 — राजा + हाथी")
add("Oteraj crnog Kralja na ivicu table. Top i Kralj moraju da sarađuju!", "Drive the black king to the edge of the board. Rook and king must cooperate!", "Repoussez le roi noir au bord de l’échiquier. La tour et le roi doivent coopérer !", "Treibe den schwarzen König an den Brettrand. Turm und König müssen zusammenarbeiten!", "Spingi il re nero sul bordo della scacchiera. Torre e re devono collaborare!", "Оттесните чёрного короля к краю доски. Ладья и король должны действовать вместе!", "把黑王逼到棋盘边缘。车和王必须配合！", "काले राजा को बोर्ड के किनारे की ओर धकेलें। हाथी और राजा को मिलकर काम करना होगा!")
add("Vežba 2 — Kralj + dva Lovca", "Exercise 2 — King + two Bishops", "Exercice 2 — Roi + deux Fous", "Übung 2 — König + zwei Läufer", "Esercizio 2 — Re + due Alfieri", "Упражнение 2 — король + два слона", "练习 2——王 + 双象", "अभ्यास 2 — राजा + दो ऊँट")
add("Oteraj Kralja ne samo na ivicu već i u ugao iste boje kao tvoji lovci.", "Drive the king not only to the edge but into a corner of the same colour as your bishops.", "Repoussez le roi non seulement au bord mais dans un coin de la même couleur que vos fous.", "Treibe den König nicht nur an den Rand, sondern in eine Ecke der gleichen Farbe wie deine Läufer.", "Spingi il re non solo sul bordo ma in un angolo dello stesso colore dei tuoi alfieri.", "Оттесните короля не только к краю, но и в угол того же цвета, что и ваши слоны.", "不仅要把王逼到边缘，还要逼进与你象同色的角落。", "राजा को केवल किनारे ही नहीं, बल्कि अपने ऊँटों के रंग के कोने में धकेलें।")
add("Vežba 3 — Kralj + Dama", "Exercise 3 — King + Queen", "Exercice 3 — Roi + Dame", "Übung 3 — König + Dame", "Esercizio 3 — Re + Donna", "Упражнение 3 — король + ферзь", "练习 3——王 + 后", "अभ्यास 3 — राजा + वज़ीर")
add("Najlakše! Dama odmah sužava prostor. Pazi na pat!", "The easiest! The queen immediately limits the space. Watch out for stalemate!", "Le plus facile ! La dame réduit tout de suite l’espace. Attention au pat !", "Am einfachsten! Die Dame schränkt den Raum sofort ein. Achte auf Patt!", "Il più facile! La donna limita subito lo spazio. Attento allo stallo!", "Самое лёгкое! Ферзь сразу сужает пространство. Берегитесь пата!", "最简单！后立刻压缩空间。小心逼和！", "सबसे आसान! वज़ीर तुरंत जगह सीमित कर देता है। गतिरोध से सावधान!")
add('Na početku imaš 8 piona — oni su tvoja "pešadija". Kapablanka napominje: **dobitak jednog piona je najmanji materijalni dobitak i često je dovoljan za pobedu**.',
    'At the start you have 8 pawns — they are your "infantry". Capablanca notes: **winning a single pawn is the smallest material gain and is often enough to win**.',
    'Au début, vous avez 8 pions — c’est votre « infanterie ». Capablanca note : **gagner un seul pion est le plus petit gain matériel et suffit souvent à gagner**.',
    'Zu Beginn hast du 8 Bauern — sie sind deine „Infanterie“. Capablanca merkt an: **ein einzelner Mehrbauer ist der kleinste materielle Gewinn und reicht oft zum Sieg**.',
    'All’inizio hai 8 pedoni — sono la tua "fanteria". Capablanca osserva: **guadagnare un solo pedone è il più piccolo vantaggio materiale e spesso basta per vincere**.',
    'В начале у вас 8 пешек — это ваша «пехота». Капабланка отмечает: **выигрыш одной пешки — наименьший материальный перевес, и его часто достаточно для победы**.',
    '开局时你有 8 个兵——它们是你的“步兵”。卡帕布兰卡指出：**多赢一个兵是最小的物质优势，却常常足以取胜**。',
    'शुरुआत में आपके पास 8 प्यादे होते हैं — ये आपकी "पैदल सेना" हैं। कापाब्लांका कहते हैं: **एक प्यादा जीतना सबसे छोटा भौतिक लाभ है और अक्सर जीत के लिए पर्याप्त होता है**।')
add("Ide isključivo napred, po jedno polje. Na prvom potezu može da preskoči dva polja. **Pioni ne mogu da idu unazad.**",
    "It moves only forward, one square at a time. On its first move it may advance two squares. **Pawns cannot move backward.**",
    "Il avance uniquement, d’une case à la fois. À son premier coup, il peut avancer de deux cases. **Les pions ne peuvent pas reculer.**",
    "Er zieht nur vorwärts, ein Feld nach dem anderen. Beim ersten Zug darf er zwei Felder vorrücken. **Bauern können nicht rückwärts ziehen.**",
    "Avanza solo in avanti, una casa alla volta. Alla prima mossa può avanzare di due case. **I pedoni non possono andare indietro.**",
    "Ходит только вперёд, на одно поле. Первым ходом может пойти на два поля. **Пешки не ходят назад.**",
    "只能向前走，每次一格。首步可走两格。**兵不能后退。**",
    "यह केवल आगे, एक खाना चलता है। पहली चाल में दो खाने बढ़ सकता है। **प्यादे पीछे नहीं जा सकते।**")
add("Jede protivničke figure isključivo po dijagonali jedno polje unapred.",
    "It captures enemy pieces only diagonally, one square ahead.",
    "Il capture les pièces adverses uniquement en diagonale, une case en avant.",
    "Er schlägt gegnerische Figuren nur diagonal, ein Feld nach vorn.",
    "Cattura i pezzi avversari solo in diagonale, una casa in avanti.",
    "Бьёт фигуры противника только по диагонали на одно поле вперёд.",
    "只能斜向前一格吃子。",
    "यह विरोधी मोहरों को केवल तिरछे, एक खाना आगे मारता है।")
add("Ako pion stigne do poslednjeg reda — pretvara se u bilo koju figuru, najčešće Damu. Ovo je moćno oružje u završnici!",
    "If a pawn reaches the last rank it turns into any piece, most often a queen. This is a powerful weapon in the endgame!",
    "Si un pion atteint la dernière rangée, il se transforme en n’importe quelle pièce, le plus souvent en dame. C’est une arme puissante en finale !",
    "Erreicht ein Bauer die letzte Reihe, verwandelt er sich in eine beliebige Figur, meist in eine Dame. Im Endspiel ist das eine mächtige Waffe!",
    "Se un pedone raggiunge l’ultima traversa si trasforma in qualsiasi pezzo, di solito una donna. È un’arma potente nel finale!",
    "Если пешка доходит до последней горизонтали, она превращается в любую фигуру, чаще всего в ферзя. Это мощное оружие в эндшпиле!",
    "如果兵到达底线，它会升变为任意棋子，通常是后。这是残局中的强力武器！",
    "यदि कोई प्यादा अंतिम रैंक तक पहुँचता है, तो वह किसी भी मोहरे में, अक्सर वज़ीर में बदल जाता है। यह अंत खेल में शक्तिशाली हथियार है!")
add("Stoji u uglovima table na početku. Efikasan je tek na otvorenim linijama — koliko god radi sa pioima koji blokiraju put, toliko je ograničen.",
    "It starts in the corners of the board. It becomes effective only on open lines — the more pawns block its path, the more limited it is.",
    "Elle commence dans les coins de l’échiquier. Elle ne devient efficace que sur les lignes ouvertes — plus des pions bloquent son chemin, plus elle est limitée.",
    "Er steht zu Beginn in den Brettecken. Erst auf offenen Linien wird er wirksam — je mehr Bauern den Weg versperren, desto eingeschränkter ist er.",
    "Parte negli angoli della scacchiera. Diventa efficace solo sulle linee aperte — più pedoni bloccano il cammino, più è limitata.",
    "В начале стоит по углам доски. Эффективна лишь на открытых линиях — чем больше пешек преграждает путь, тем она ограниченнее.",
    "开局时位于棋盘的角落。只有在开放线上才有效——越多兵挡路，它就越受限。",
    "यह शुरुआत में बोर्ड के कोनों में रहता है। यह केवल खुली लाइनों पर प्रभावी होता है — जितने अधिक प्यादे रास्ता रोकते हैं, यह उतना ही सीमित होता है।")
add("Kreće se po pravim linijama (napred-nazad, levo-desno) koliko god polja želi. Zajedno, dva Topa su neznatno jača od Dame.",
    "It moves in straight lines (up-down, left-right) as many squares as it likes. Together, two rooks are slightly stronger than a queen.",
    "Elle se déplace en lignes droites (haut-bas, gauche-droite) d’autant de cases qu’elle veut. Ensemble, deux tours sont un peu plus fortes qu’une dame.",
    "Er zieht in geraden Linien (auf-ab, links-rechts) beliebig weit. Zusammen sind zwei Türme etwas stärker als eine Dame.",
    "Si muove in linea retta (su-giù, sinistra-destra) per quante case vuole. Insieme, due torri sono leggermente più forti di una donna.",
    "Ходит по прямым линиям (вверх-вниз, влево-вправо) на любое число полей. Вместе две ладьи чуть сильнее ферзя.",
    "沿直线（上下、左右）走任意格数。两车合力略强于一后。",
    "यह सीधी रेखाओं में (ऊपर-नीचे, बाएँ-दाएँ) जितने चाहे खाने चलता है। मिलकर, दो हाथी एक वज़ीर से थोड़े मजबूत होते हैं।")
add("Jedan lovac uvek ostaje na belim, drugi na crnim poljima. Kapablanka smatra da je **u većini pozicija Lovac vredniji od Skakača**.",
    "One bishop always stays on the light squares, the other on the dark. Capablanca holds that **in most positions the bishop is worth more than the knight**.",
    "Un fou reste toujours sur les cases claires, l’autre sur les foncées. Capablanca estime que **dans la plupart des positions, le fou vaut plus que le cavalier**.",
    "Ein Läufer bleibt immer auf den hellen, der andere auf den dunklen Feldern. Capablanca meint, dass **der Läufer in den meisten Stellungen mehr wert ist als der Springer**.",
    "Un alfiere resta sempre sulle case chiare, l’altro sulle scure. Capablanca ritiene che **nella maggior parte delle posizioni l’alfiere valga più del cavallo**.",
    "Один слон всегда остаётся на белых полях, другой — на чёрных. Капабланка считает, что **в большинстве позиций слон ценнее коня**.",
    "一象始终在白格，另一象始终在黑格。卡帕布兰卡认为：**在多数局面中，象比马更有价值**。",
    "एक ऊँट हमेशा हल्के खानों पर, दूसरा गहरे खानों पर रहता है। कापाब्लांका मानते हैं कि **अधिकांश स्थितियों में ऊँट घोड़े से अधिक मूल्यवान है**।")
add('Kreće se isključivo po dijagonalama. Slabost: "Topov pion koji promovira na polju suprotne boje od Lovca" najčešće vodi remiju umesto pobede.',
    'It moves only along diagonals. Weakness: "a rook pawn that promotes on a square of the opposite colour to the bishop" most often leads to a draw instead of a win.',
    'Il se déplace uniquement en diagonale. Faiblesse : « un pion de tour qui promeut sur une case de couleur opposée au fou » mène le plus souvent à la nulle plutôt qu’au gain.',
    'Er zieht nur auf Diagonalen. Schwäche: „ein Turmbauer, der auf einem Feld der Gegenfarbe des Läufers umwandelt“ führt meist zum Remis statt zum Sieg.',
    'Si muove solo lungo le diagonali. Debolezza: "un pedone di torre che promuove su una casa di colore opposto all’alfiere" porta spesso alla patta invece che alla vittoria.',
    'Ходит только по диагоналям. Слабость: «ладейная пешка, превращающаяся на поле, противоположном по цвету слону», чаще всего ведёт к ничьей, а не к победе.',
    '只能沿对角线走。弱点：“在与象异色的格子上升变的车兵”往往导致和棋而非胜利。',
    'यह केवल विकर्णों पर चलता है। कमजोरी: "ऐसा हाथी-प्यादा जो ऊँट के विपरीत रंग के खाने पर प्रमोट होता है" अक्सर जीत के बजाय ड्रॉ की ओर ले जाता है।')
add("Jedina figura koja preskače ostale. Snažan je u **zatvorenim pozicijama** — kada su linije blokirane pionima. Na ivici table gubi na snazi.",
    "The only piece that jumps over others. It is strong in **closed positions** — when the lines are blocked by pawns. On the edge of the board it loses strength.",
    "La seule pièce qui saute par-dessus les autres. Il est fort dans les **positions fermées** — quand les lignes sont bloquées par des pions. Au bord de l’échiquier, il perd de sa force.",
    "Die einzige Figur, die über andere springt. Er ist stark in **geschlossenen Stellungen** — wenn die Linien von Bauern blockiert sind. Am Brettrand verliert er an Stärke.",
    "L’unico pezzo che salta gli altri. È forte nelle **posizioni chiuse** — quando le linee sono bloccate dai pedoni. Sul bordo della scacchiera perde forza.",
    "Единственная фигура, перепрыгивающая через другие. Силён в **закрытых позициях** — когда линии перекрыты пешками. На краю доски теряет силу.",
    "唯一能跳过其他棋子的棋子。它在**封闭局面**中很强——当线路被兵堵住时。在棋盘边缘则会减弱。",
    "एकमात्र मोहरा जो दूसरों के ऊपर से कूदता है। यह **बंद स्थितियों** में मजबूत है — जब लाइनें प्यादों से अवरुद्ध हों। बोर्ड के किनारे पर यह कमजोर हो जाता है।")
add('Kreće se u obliku slova "L": dva polja pravo pa jedno u stranu.',
    'It moves in an "L" shape: two squares straight, then one to the side.',
    'Il se déplace en forme de « L » : deux cases tout droit puis une sur le côté.',
    'Er zieht in L-Form: zwei Felder gerade, dann eins zur Seite.',
    'Si muove a forma di "L": due case dritto poi una di lato.',
    'Ходит буквой «Г»: два поля прямо, затем одно в сторону.',
    '走“L”形：直走两格，再横走一格。',
    'यह "L" आकार में चलता है: दो खाने सीधे फिर एक बगल में।')
add("Jedina figura koja može da preskače druge figure — i svoje i protivničke!",
    "The only piece that can jump over other pieces — both its own and the opponent’s!",
    "La seule pièce qui peut sauter par-dessus d’autres pièces — les siennes comme celles de l’adversaire !",
    "Die einzige Figur, die über andere Figuren springen kann — eigene wie gegnerische!",
    "L’unico pezzo che può saltare altri pezzi — sia i propri sia quelli avversari!",
    "Единственная фигура, которая может перепрыгивать через другие — и свои, и чужие!",
    "唯一能跳过其他棋子的棋子——无论己方还是对方！",
    "एकमात्र मोहरा जो अन्य मोहरों के ऊपर से कूद सकता है — अपने और विरोधी दोनों के!")
add("Stoji na polju **svoje boje** — bela Dama na belom polju, crna na crnom. Najmoćnija figura, ali ne treba je odmah izvoditi u otvaranju.",
    "It stands on a square of **its own colour** — the white queen on a light square, the black on a dark one. The most powerful piece, but you shouldn’t develop it too early in the opening.",
    "Elle se place sur une case de **sa couleur** — la dame blanche sur une case claire, la noire sur une foncée. La pièce la plus puissante, mais à ne pas sortir trop tôt en ouverture.",
    "Sie steht auf einem Feld **ihrer eigenen Farbe** — die weiße Dame auf hellem, die schwarze auf dunklem Feld. Die stärkste Figur, doch man sollte sie in der Eröffnung nicht zu früh herausbringen.",
    "Sta su una casa del **proprio colore** — la donna bianca su casa chiara, la nera su scura. Il pezzo più potente, ma non va sviluppato troppo presto in apertura.",
    "Стоит на поле **своего цвета** — белый ферзь на светлом поле, чёрный на тёмном. Самая мощная фигура, но не стоит выводить её слишком рано в дебюте.",
    "它位于**自己颜色**的格子上——白后在白格，黑后在黑格。最强大的棋子，但开局不宜过早出动。",
    "यह **अपने रंग** के खाने पर रहता है — सफ़ेद वज़ीर हल्के खाने पर, काला गहरे पर। सबसे शक्तिशाली मोहरा, पर ओपनिंग में इसे जल्दी बाहर नहीं लाना चाहिए।")
add("Kombinuje kretanje Topa i Lovca — kreće se u svim pravcima, koliko god polja želi.",
    "It combines the rook’s and bishop’s movement — moving in all directions, as many squares as it likes.",
    "Elle combine les déplacements de la tour et du fou — dans toutes les directions, autant de cases qu’elle veut.",
    "Sie verbindet die Bewegung von Turm und Läufer — in alle Richtungen, beliebig weit.",
    "Combina il movimento di torre e alfiere — in tutte le direzioni, per quante case vuole.",
    "Сочетает ход ладьи и слона — двигается во всех направлениях на любое число полей.",
    "它兼具车和象的走法——可向任意方向走任意格数。",
    "यह हाथी और ऊँट की चाल को मिलाता है — सभी दिशाओं में, जितने चाहे खाने।")
add("Najvažnija figura — njen gubitak znači kraj igre. U otvaranju je **pasivna odbrambena figura**, ali u završnici postaje moćan napadač.",
    "The most important piece — its loss means the end of the game. In the opening it is a **passive defensive piece**, but in the endgame it becomes a powerful attacker.",
    "La pièce la plus importante — sa perte signifie la fin de la partie. En ouverture, c’est une **pièce défensive passive**, mais en finale, il devient un puissant attaquant.",
    "Die wichtigste Figur — ihr Verlust bedeutet das Ende der Partie. In der Eröffnung ist er eine **passive Verteidigungsfigur**, im Endspiel wird er zum mächtigen Angreifer.",
    "Il pezzo più importante — la sua perdita significa la fine della partita. In apertura è un **pezzo difensivo passivo**, ma nel finale diventa un potente attaccante.",
    "Самая важная фигура — её потеря означает конец игры. В дебюте это **пассивная оборонительная фигура**, но в эндшпиле он становится мощным атакующим.",
    "最重要的棋子——失去它就意味着对局结束。开局中它是**被动的防守棋子**，但在残局中它会成为强大的进攻者。",
    "सबसे महत्वपूर्ण मोहरा — इसका खोना खेल का अंत है। ओपनिंग में यह एक **निष्क्रिय रक्षात्मक मोहरा** है, पर अंत खेल में यह शक्तिशाली हमलावर बन जाता है।")
add("Kreće se samo jedno polje u bilo kom pravcu. **Ne sme da stane na napadnuto polje!**",
    "It moves only one square in any direction. **It must not step onto an attacked square!**",
    "Il ne se déplace que d’une case dans n’importe quelle direction. **Il ne doit pas aller sur une case attaquée !**",
    "Er zieht nur ein Feld in beliebige Richtung. **Er darf kein angegriffenes Feld betreten!**",
    "Si muove di una sola casa in qualsiasi direzione. **Non può andare su una casa attaccata!**",
    "Ходит только на одно поле в любом направлении. **Нельзя становиться на атакованное поле!**",
    "它只能向任意方向走一格。**不能走到被攻击的格子上！**",
    "यह किसी भी दिशा में केवल एक खाना चलता है। **यह हमलाग्रस्त खाने पर नहीं जा सकता!**")
add("Jednom u partiji možeš pomeriti **dve figure istovremeno** — Kralja i Topa. Kralj skoči dva polja ka Topu, a Top preskače Kralja i staje pored njega. Ovo služi da skloniš Kralja na sigurno i ubaciš Top u igru.",
    "Once per game you may move **two pieces at the same time** — the king and a rook. The king jumps two squares toward the rook, and the rook hops over the king to its side. This is used to put the king to safety and bring the rook into play.",
    "Une fois par partie, vous pouvez déplacer **deux pièces en même temps** — le roi et une tour. Le roi saute de deux cases vers la tour, et la tour franchit le roi pour se placer à côté. Cela met le roi à l’abri et active la tour.",
    "Einmal pro Partie darfst du **zwei Figuren gleichzeitig** ziehen — König und Turm. Der König springt zwei Felder zum Turm, der Turm überspringt den König und stellt sich daneben. So bringst du den König in Sicherheit und den Turm ins Spiel.",
    "Una volta a partita puoi muovere **due pezzi contemporaneamente** — il re e una torre. Il re salta di due case verso la torre e la torre scavalca il re mettendosi accanto. Serve a mettere il re al sicuro e a portare la torre in gioco.",
    "Один раз за партию вы можете сделать ход **двумя фигурами сразу** — королём и ладьёй. Король прыгает на два поля к ладье, а ладья перескакивает через короля и встаёт рядом. Это уводит короля в безопасность и вводит ладью в игру.",
    "每盘棋有一次可以**同时移动两个棋子**——王和车。王朝车方向跳两格，车越过王落到其旁边。这能让王安全并使车投入战斗。",
    "एक खेल में एक बार आप **दो मोहरों को एक साथ** चला सकते हैं — राजा और हाथी। राजा हाथी की ओर दो खाने कूदता है, और हाथी राजा के ऊपर से कूदकर उसके बगल में आ जाता है। यह राजा को सुरक्षित करने और हाथी को खेल में लाने के लिए है।")
add("Ni Kralj ni Top se do tada **nisu pomerali** · Između njih **nema nijedne figure** · Kralj se ne nalazi u šahu i ne prolazi kroz napadnuto polje",
    "Neither king nor rook has **moved before** · There are **no pieces between them** · The king is not in check and does not pass through an attacked square",
    "Ni le roi ni la tour n’ont **bougé auparavant** · Il n’y a **aucune pièce entre eux** · Le roi n’est pas en échec et ne traverse pas de case attaquée",
    "Weder König noch Turm haben sich **zuvor bewegt** · Zwischen ihnen steht **keine Figur** · Der König steht nicht im Schach und zieht nicht über ein angegriffenes Feld",
    "Né il re né la torre si sono **mossi prima** · Tra loro **non c’è alcun pezzo** · Il re non è sotto scacco e non attraversa una casa attaccata",
    "Ни король, ни ладья **ранее не ходили** · Между ними **нет фигур** · Король не под шахом и не проходит через атакованное поле",
    "王和车此前**都未移动过** · 它们之间**没有任何棋子** · 王不在被将状态，且不经过被攻击的格子",
    "न राजा न हाथी **पहले हिले हों** · उनके बीच **कोई मोहरा न हो** · राजा शह में न हो और किसी हमलाग्रस्त खाने से होकर न गुजरे")

# ── Lesson 2 ────────────────────────────────────────────────────────────────
add('"Najvažnija stvar u otvaranju je brzo razviti figure. Nijedno parče ne treba pomeriti više od jednom pre nego što je razvoj završen, osim ako je to apsolutno neophodno."',
    '"The most important thing in the opening is to develop the pieces quickly. No piece should be moved more than once before development is complete, unless it is absolutely necessary."',
    '« Le plus important dans l’ouverture est de développer rapidement les pièces. Aucune pièce ne doit être déplacée plus d’une fois avant la fin du développement, sauf nécessité absolue. »',
    '„Das Wichtigste in der Eröffnung ist die schnelle Entwicklung der Figuren. Keine Figur sollte vor Abschluss der Entwicklung mehr als einmal gezogen werden, außer es ist absolut notwendig.“',
    '"La cosa più importante in apertura è sviluppare i pezzi rapidamente. Nessun pezzo va mosso più di una volta prima che lo sviluppo sia completo, a meno che non sia assolutamente necessario."',
    '«Самое важное в дебюте — быстро развить фигуры. Ни одну фигуру не следует двигать более одного раза до завершения развития, если это не абсолютно необходимо.»',
    '“开局最重要的是快速出动棋子。在完成出子之前，除非绝对必要，任何棋子都不应移动超过一次。”',
    '"ओपनिंग में सबसे महत्वपूर्ण है मोहरों को तेज़ी से विकसित करना। विकास पूरा होने से पहले किसी मोहरे को एक बार से अधिक नहीं हिलाना चाहिए, जब तक नितांत आवश्यक न हो।"')
add("U šahu **Beli uvek igra prvi** i zbog toga ima blagu inicijalnu prednost. Zadatak oba igrača u otvaranju je isti: što brže dovesti figure u igru i zauzeti kontrolu nad centrom.",
    "In chess **White always moves first** and therefore has a slight initial advantage. Both players’ task in the opening is the same: bring the pieces into play as fast as possible and take control of the centre.",
    "Aux échecs, **les Blancs jouent toujours en premier** et ont donc un léger avantage initial. La tâche des deux joueurs en ouverture est identique : développer les pièces le plus vite possible et prendre le contrôle du centre.",
    "Im Schach **zieht Weiß immer zuerst** und hat dadurch einen kleinen Anfangsvorteil. Die Aufgabe beider Spieler in der Eröffnung ist dieselbe: die Figuren so schnell wie möglich ins Spiel bringen und die Kontrolle über das Zentrum übernehmen.",
    "Negli scacchi **il Bianco muove sempre per primo** e ha quindi un leggero vantaggio iniziale. Il compito di entrambi in apertura è lo stesso: portare i pezzi in gioco il più in fretta possibile e prendere il controllo del centro.",
    "В шахматах **белые всегда ходят первыми** и поэтому имеют небольшое начальное преимущество. Задача обоих игроков в дебюте одна: как можно быстрее ввести фигуры в игру и захватить контроль над центром.",
    "在国际象棋中，**白方总是先走**，因此拥有微小的先手优势。双方在开局的任务相同：尽快出动棋子并控制中心。",
    "शतरंज में **सफ़ेद हमेशा पहले चलता है** और इसलिए उसे थोड़ी शुरुआती बढ़त मिलती है। ओपनिंग में दोनों खिलाड़ियों का काम एक ही है: मोहरों को जल्द से जल्द खेल में लाना और केंद्र पर नियंत्रण पाना।")
add("Razvijaj figure brzo", "Develop the pieces quickly", "Développez vite les pièces", "Entwickle die Figuren schnell", "Sviluppa i pezzi rapidamente", "Быстро развивайте фигуры", "快速出动棋子", "मोहरों को जल्दी विकसित करें")
add("Kapablanka savetuje: skakače razvijaj pre lovaca. Ne pomeraj istu figuru dva puta u otvaranju ako nisi primoran. Svaki potez treba da razvija novu figuru ili kontroliše centar.",
    "Capablanca advises: develop knights before bishops. Don’t move the same piece twice in the opening unless forced. Every move should develop a new piece or control the centre.",
    "Capablanca conseille : développez les cavaliers avant les fous. Ne déplacez pas deux fois la même pièce en ouverture sans y être contraint. Chaque coup doit développer une nouvelle pièce ou contrôler le centre.",
    "Capablanca rät: entwickle Springer vor Läufern. Ziehe dieselbe Figur in der Eröffnung nicht zweimal, wenn du nicht musst. Jeder Zug sollte eine neue Figur entwickeln oder das Zentrum kontrollieren.",
    "Capablanca consiglia: sviluppa i cavalli prima degli alfieri. Non muovere lo stesso pezzo due volte in apertura se non costretto. Ogni mossa deve sviluppare un nuovo pezzo o controllare il centro.",
    "Капабланка советует: развивайте коней раньше слонов. Не ходите одной фигурой дважды в дебюте без необходимости. Каждый ход должен развивать новую фигуру или контролировать центр.",
    "卡帕布兰卡建议：先出马后出象。开局中除非被迫，不要让同一棋子走两次。每一步都应出动新棋子或控制中心。",
    "कापाब्लांका सलाह देते हैं: ऊँट से पहले घोड़े विकसित करें। मजबूरी न हो तो ओपनिंग में एक ही मोहरे को दो बार न हिलाएँ। हर चाल को नया मोहरा विकसित करना चाहिए या केंद्र पर नियंत्रण रखना चाहिए।")
add("Kontroliši centar", "Control the centre", "Contrôlez le centre", "Kontrolliere das Zentrum", "Controlla il centro", "Контролируйте центр", "控制中心", "केंद्र पर नियंत्रण रखें")
add('Četiri centralna polja (e4, d4, e5, d5) su najvažnija na tabli. Ko vlada centrom ima više prostora za manevar. Kapablanka: "Nijedan žestok napad ne može uspeti bez kontrole bar dva centralna polja."',
    'The four central squares (e4, d4, e5, d5) are the most important on the board. Whoever rules the centre has more room to manoeuvre. Capablanca: "No vigorous attack can succeed without control of at least two central squares."',
    'Les quatre cases centrales (e4, d4, e5, d5) sont les plus importantes de l’échiquier. Qui domine le centre a plus d’espace pour manœuvrer. Capablanca : « Aucune attaque vigoureuse ne peut réussir sans le contrôle d’au moins deux cases centrales. »',
    'Die vier Zentralfelder (e4, d4, e5, d5) sind die wichtigsten auf dem Brett. Wer das Zentrum beherrscht, hat mehr Raum zum Manövrieren. Capablanca: „Kein energischer Angriff kann ohne Kontrolle über mindestens zwei Zentralfelder gelingen.“',
    'Le quattro case centrali (e4, d4, e5, d5) sono le più importanti della scacchiera. Chi domina il centro ha più spazio per manovrare. Capablanca: "Nessun attacco vigoroso può riuscire senza il controllo di almeno due case centrali."',
    'Четыре центральных поля (e4, d4, e5, d5) — важнейшие на доске. Кто владеет центром, имеет больше пространства для манёвра. Капабланка: «Ни одна энергичная атака не удастся без контроля хотя бы двух центральных полей.»',
    '四个中心格（e4、d4、e5、d5）是棋盘上最重要的。掌控中心者拥有更多回旋空间。卡帕布兰卡：“任何猛烈的进攻，若不控制至少两个中心格，都无法成功。”',
    'चार केंद्रीय खाने (e4, d4, e5, d5) बोर्ड पर सबसे महत्वपूर्ण हैं। जो केंद्र पर राज करता है उसके पास अधिक जगह होती है। कापाब्लांका: "कम से कम दो केंद्रीय खानों पर नियंत्रण के बिना कोई प्रबल हमला सफल नहीं हो सकता।"')
add("Zaštiti kralja — uradi rokadu!", "Protect the king — castle!", "Protégez le roi — roquez !", "Schütze den König — rochiere!", "Proteggi il re — arrocca!", "Защитите короля — рокируйте!", "保护国王——王车易位！", "राजा की रक्षा करें — कैसलिंग करें!")
add("Rokadu odigraj što pre je moguće. Kralj na otvorenom je laka meta. Kapablanka sam uvek rokira rano i preporučuje isto svim igračima, posebno početnicima.",
    "Castle as early as possible. A king in the open is an easy target. Capablanca himself always castled early and recommends the same to all players, especially beginners.",
    "Roquez le plus tôt possible. Un roi à découvert est une cible facile. Capablanca lui-même roquait toujours tôt et le recommande à tous, surtout aux débutants.",
    "Rochiere so früh wie möglich. Ein König im Freien ist ein leichtes Ziel. Capablanca selbst rochierte stets früh und empfiehlt dasselbe allen Spielern, besonders Anfängern.",
    "Arrocca il prima possibile. Un re allo scoperto è un bersaglio facile. Capablanca stesso arroccava sempre presto e consiglia lo stesso a tutti, soprattutto ai principianti.",
    "Рокируйте как можно раньше. Король на открытом месте — лёгкая мишень. Сам Капабланка всегда рокировал рано и советует то же всем игрокам, особенно начинающим.",
    "尽早王车易位。暴露的国王是容易的靶子。卡帕布兰卡本人总是早早易位，并建议所有人尤其是初学者也这样做。",
    "जितनी जल्दी हो सके कैसलिंग करें। खुले में राजा आसान निशाना है। कापाब्लांका स्वयं हमेशा जल्दी कैसलिंग करते थे और सभी को, खासकर शुरुआती लोगों को यही सलाह देते हैं।")
add("Prerano izvođenje Dame", "Bringing the queen out too early", "Sortir la dame trop tôt", "Die Dame zu früh herausbringen", "Sviluppare la donna troppo presto", "Слишком ранний вывод ферзя", "过早出动皇后", "वज़ीर को बहुत जल्दी बाहर लाना")
add("Dama je snažna, ali ako je izvedeš rano, protivnik je napada pešacima i figurama — a svaki napad na Damu znači izgubljeni tempo jer mora da beži.",
    "The queen is strong, but if you bring her out early the opponent attacks her with pawns and pieces — and every attack on the queen means a lost tempo because she must flee.",
    "La dame est puissante, mais sortie tôt, l’adversaire l’attaque avec pions et pièces — et chaque attaque sur la dame coûte un tempo car elle doit fuir.",
    "Die Dame ist stark, doch bringst du sie früh heraus, greift der Gegner sie mit Bauern und Figuren an — und jeder Angriff auf die Dame kostet ein Tempo, weil sie fliehen muss.",
    "La donna è forte, ma se la sviluppi presto l’avversario la attacca con pedoni e pezzi — e ogni attacco alla donna costa un tempo perché deve fuggire.",
    "Ферзь силён, но если вывести его рано, соперник атакует его пешками и фигурами — а каждое нападение на ферзя означает потерю темпа, ведь он вынужден отступать.",
    "皇后很强，但若过早出动，对手会用兵和子力攻击它——每次攻击皇后都意味着损失一步，因为它必须逃跑。",
    "वज़ीर मजबूत है, पर यदि जल्दी बाहर लाएँ तो विरोधी प्यादों और मोहरों से उस पर हमला करता है — और वज़ीर पर हर हमला एक टेम्पो की हानि है क्योंकि उसे भागना पड़ता है।")
add("Pasivna odbrana pionima", "Passive defence with pawns", "Défense passive avec les pions", "Passive Verteidigung mit Bauern", "Difesa passiva con i pedoni", "Пассивная защита пешками", "用兵被动防守", "प्यादों से निष्क्रिय बचाव")
add('"Filipidorski" stil — odmah igrati P-d6 kao odgovor na e4 — daje protivniku slobodan razvoj i prostranstvo. Kapablanka pokazuje kako beli tada lako gradi superiornu poziciju.',
    'The "Philidor" style — answering e4 immediately with P-d6 — gives the opponent free development and space. Capablanca shows how White then easily builds a superior position.',
    'Le style « Philidor » — répondre à e4 immédiatement par P-d6 — offre à l’adversaire développement et espace. Capablanca montre comment les Blancs construisent alors aisément une position supérieure.',
    'Der „Philidor“-Stil — auf e4 sofort mit P-d6 zu antworten — gibt dem Gegner freie Entwicklung und Raum. Capablanca zeigt, wie Weiß dann leicht eine überlegene Stellung aufbaut.',
    'Lo stile "Philidor" — rispondere a e4 subito con P-d6 — dà all’avversario sviluppo libero e spazio. Capablanca mostra come il Bianco costruisca poi facilmente una posizione superiore.',
    'Стиль «Филидора» — отвечать на e4 сразу P-d6 — даёт сопернику свободное развитие и пространство. Капабланка показывает, как белые затем легко строят превосходную позицию.',
    '“费利多尔”式——对 e4 立刻以 P-d6 应对——会让对手获得自由出子和空间。卡帕布兰卡展示了白方如何因此轻松建立优势局面。',
    '"फिलिडोर" शैली — e4 के जवाब में तुरंत P-d6 खेलना — विरोधी को मुक्त विकास और जगह देती है। कापाब्लांका दिखाते हैं कि सफ़ेद तब कैसे आसानी से बेहतर स्थिति बना लेता है।')
add("Zakasnela rokada", "Castling too late", "Roque tardif", "Zu spätes Rochieren", "Arrocco tardivo", "Запоздалая рокировка", "王车易位太晚", "देर से कैसलिंग")
add("Svaki potez bez rokade kada su linije otvorene je rizik. Protivnik može otvoriti igru i napasti tvog kralja pre nego što se skloni.",
    "Every move without castling while the lines are open is a risk. The opponent can open the position and attack your king before it gets to safety.",
    "Chaque coup sans roque alors que les lignes sont ouvertes est un risque. L’adversaire peut ouvrir le jeu et attaquer votre roi avant qu’il ne soit à l’abri.",
    "Jeder Zug ohne Rochade bei offenen Linien ist ein Risiko. Der Gegner kann das Spiel öffnen und deinen König angreifen, bevor er in Sicherheit ist.",
    "Ogni mossa senza arrocco con le linee aperte è un rischio. L’avversario può aprire il gioco e attaccare il tuo re prima che si metta al sicuro.",
    "Каждый ход без рокировки при открытых линиях — риск. Соперник может вскрыть позицию и атаковать вашего короля раньше, чем он укроется.",
    "在线路开放时每走一步而不易位都是冒险。对手可能打开局面，在你的国王到达安全之前发起攻击。",
    "जब लाइनें खुली हों तब बिना कैसलिंग की हर चाल जोखिम है। विरोधी खेल खोलकर आपके राजा के सुरक्षित होने से पहले हमला कर सकता है।")
add("Odigraj svaki potez belih na tabli — crni odgovara automatski po teorijskoj liniji.",
    "Play each of White’s moves on the board — Black replies automatically along the theoretical line.",
    "Jouez chaque coup des Blancs sur l’échiquier — les Noirs répondent automatiquement selon la ligne théorique.",
    "Spiele jeden Zug von Weiß auf dem Brett — Schwarz antwortet automatisch nach der theoretischen Linie.",
    "Gioca ogni mossa del Bianco sulla scacchiera — il Nero risponde automaticamente seguendo la linea teorica.",
    "Сыграйте каждый ход белых на доске — чёрные отвечают автоматически по теоретической линии.",
    "在棋盘上走出白方的每一步——黑方按理论线路自动应着。",
    "बोर्ड पर सफ़ेद की हर चाल खेलें — काला सैद्धांतिक लाइन के अनुसार स्वतः जवाब देता है।")
add("Španska partija (Ruy Lopez)", "Ruy Lopez (Spanish Game)", "Partie espagnole (Ruy Lopez)", "Spanische Partie (Ruy Lopez)", "Partita spagnola (Ruy Lopez)", "Испанская партия (Руи Лопес)", "西班牙开局（鲁伊·洛佩斯）", "रुय लोपेज़ (स्पैनिश खेल)")
add("1.e4 e5 2.Sf3 Sc6 3.Lb5 — Kapablankova omiljena", "1.e4 e5 2.Nf3 Nc6 3.Bb5 — Capablanca’s favourite", "1.e4 e5 2.Cf3 Cc6 3.Fb5 — la préférée de Capablanca", "1.e4 e5 2.Sf3 Sc6 3.Lb5 — Capablancas Liebling", "1.e4 e5 2.Cf3 Cc6 3.Ab5 — la preferita di Capablanca", "1.e4 e5 2.Кf3 Кc6 3.Сb5 — любимая Капабланки", "1.e4 e5 2.Nf3 Nc6 3.Bb5 — 卡帕布兰卡的最爱", "1.e4 e5 2.Nf3 Nc6 3.Bb5 — कापाब्लांका की पसंदीदा")
add("Italijanska partija", "Italian Game", "Partie italienne", "Italienische Partie", "Partita italiana", "Итальянская партия", "意大利开局", "इटैलियन खेल")
add("1.e4 e5 2.Sf3 Sc6 3.Lc4 — lovac nišani tačku f7", "1.e4 e5 2.Nf3 Nc6 3.Bc4 — the bishop targets f7", "1.e4 e5 2.Cf3 Cc6 3.Fc4 — le fou vise f7", "1.e4 e5 2.Sf3 Sc6 3.Lc4 — der Läufer zielt auf f7", "1.e4 e5 2.Cf3 Cc6 3.Ac4 — l’alfiere punta f7", "1.e4 e5 2.Кf3 Кc6 3.Сc4 — слон нацелен на f7", "1.e4 e5 2.Nf3 Nc6 3.Bc4 — 象瞄准 f7", "1.e4 e5 2.Nf3 Nc6 3.Bc4 — ऊँट f7 को निशाना बनाता है")
add("Sicilijanska odbrana", "Sicilian Defence", "Défense sicilienne", "Sizilianische Verteidigung", "Difesa siciliana", "Сицилианская защита", "西西里防御", "सिसिलियन रक्षा")
add("1.e4 c5 2.Sf3 d6 3.d4 cxd4 4.Sxd4 — asimetrična borba", "1.e4 c5 2.Nf3 d6 3.d4 cxd4 4.Nxd4 — an asymmetrical fight", "1.e4 c5 2.Cf3 d6 3.d4 cxd4 4.Cxd4 — un combat asymétrique", "1.e4 c5 2.Sf3 d6 3.d4 cxd4 4.Sxd4 — ein asymmetrischer Kampf", "1.e4 c5 2.Cf3 d6 3.d4 cxd4 4.Cxd4 — una lotta asimmetrica", "1.e4 c5 2.Кf3 d6 3.d4 cxd4 4.К:d4 — асимметричная борьба", "1.e4 c5 2.Nf3 d6 3.d4 cxd4 4.Nxd4 — 不对称的较量", "1.e4 c5 2.Nf3 d6 3.d4 cxd4 4.Nxd4 — असममित संघर्ष")

# ── Lesson 3 ────────────────────────────────────────────────────────────────
add('"Idealna središnjica: sve figure su bačene u napad kao masa, koordinirajući se sa mašinskom preciznošću. Cilj svakog majstora je da postigne upravo takvu harmoniju."',
    '"The ideal middlegame: all the pieces thrown into the attack as a mass, coordinating with machine-like precision. Every master’s aim is to achieve exactly such harmony."',
    '« Le milieu de partie idéal : toutes les pièces lancées dans l’attaque en masse, coordonnées avec une précision mécanique. Le but de tout maître est d’atteindre une telle harmonie. »',
    '„Das ideale Mittelspiel: alle Figuren als Masse in den Angriff geworfen, mit maschineller Präzision koordiniert. Das Ziel jedes Meisters ist es, genau solche Harmonie zu erreichen.“',
    '"Il mediogioco ideale: tutti i pezzi lanciati all’attacco come una massa, coordinati con precisione meccanica. L’obiettivo di ogni maestro è raggiungere proprio tale armonia."',
    '«Идеальный миттельшпиль: все фигуры брошены в атаку как единая масса, согласованные с машинной точностью. Цель каждого мастера — достичь именно такой гармонии.»',
    '“理想的中局：所有棋子如同整体般投入进攻，以机器般的精度协调配合。每位大师的目标正是达到这样的和谐。”',
    '"आदर्श मध्य खेल: सभी मोहरे एक समूह की तरह हमले में झोंक दिए जाते हैं, मशीनी सटीकता से समन्वित। हर मास्टर का लक्ष्य ठीक ऐसी ही सामंजस्य पाना है।"')
add("Kada su figure izvedene i kraljevi na sigurnom, počinje **središnjica** — najkreativniji i najkompleksniji deo šaha.",
    "Once the pieces are developed and the kings are safe, the **middlegame** begins — the most creative and complex part of chess.",
    "Une fois les pièces développées et les rois en sécurité, le **milieu de partie** commence — la partie la plus créative et la plus complexe des échecs.",
    "Sind die Figuren entwickelt und die Könige in Sicherheit, beginnt das **Mittelspiel** — der kreativste und komplexeste Teil des Schachs.",
    "Quando i pezzi sono sviluppati e i re al sicuro, inizia il **mediogioco** — la parte più creativa e complessa degli scacchi.",
    "Когда фигуры развиты, а короли в безопасности, начинается **миттельшпиль** — самая творческая и сложная часть шахмат.",
    "当棋子出动、双方国王安全后，**中局**便开始了——这是国际象棋中最富创造性、最复杂的阶段。",
    "जब मोहरे विकसित हो जाते हैं और राजा सुरक्षित होते हैं, तब **मध्य खेल** शुरू होता है — शतरंज का सबसे रचनात्मक और जटिल भाग।")
add("Kapablanka objašnjava: Beli ima inicijalnu prednost zbog prvog poteza. Ovu prednost treba **čuvati što duže** — predaj je samo ako za uzvrat dobijaš materijal ili bolju poziciju.",
    "Capablanca explains: White has an initial advantage thanks to the first move. This advantage should be **kept as long as possible** — give it up only if you get material or a better position in return.",
    "Capablanca explique : les Blancs ont un avantage initial grâce au premier coup. Cet avantage doit être **conservé le plus longtemps possible** — ne le cédez que contre du matériel ou une meilleure position.",
    "Capablanca erklärt: Weiß hat dank des ersten Zuges einen Anfangsvorteil. Diesen Vorteil sollte man **so lange wie möglich bewahren** — gib ihn nur auf, wenn du dafür Material oder eine bessere Stellung bekommst.",
    "Capablanca spiega: il Bianco ha un vantaggio iniziale grazie alla prima mossa. Questo vantaggio va **conservato il più a lungo possibile** — cedilo solo se in cambio ottieni materiale o una posizione migliore.",
    "Капабланка объясняет: у белых есть начальное преимущество благодаря первому ходу. Это преимущество следует **удерживать как можно дольше** — отдавайте его только в обмен на материал или лучшую позицию.",
    "卡帕布兰卡解释：白方因先手而拥有初始优势。这一优势应**尽可能长久地保持**——只有在换取子力或更好局面时才放弃它。",
    "कापाब्लांका समझाते हैं: पहली चाल के कारण सफ़ेद को शुरुआती बढ़त होती है। इस बढ़त को **जितना संभव हो उतना बनाए रखें** — इसे केवल तभी छोड़ें जब बदले में सामग्री या बेहतर स्थिति मिले।")
add("Ko napadá, dikta tempo", "Whoever attacks dictates the tempo", "Qui attaque dicte le tempo", "Wer angreift, bestimmt das Tempo", "Chi attacca detta il ritmo", "Кто атакует, тот диктует темп", "进攻者主导节奏", "जो हमला करता है वह गति तय करता है")
add("Igrač sa inicijativom bira gde i kako da napadne. Protivnik mora da reaguje umesto da sprovodi sopstveni plan.",
    "The player with the initiative chooses where and how to attack. The opponent must react instead of carrying out their own plan.",
    "Le joueur qui a l’initiative choisit où et comment attaquer. L’adversaire doit réagir au lieu de mener son propre plan.",
    "Der Spieler mit der Initiative wählt, wo und wie er angreift. Der Gegner muss reagieren, statt seinen eigenen Plan zu verfolgen.",
    "Il giocatore con l’iniziativa sceglie dove e come attaccare. L’avversario deve reagire invece di attuare il proprio piano.",
    "Игрок с инициативой выбирает, где и как атаковать. Сопернику приходится реагировать, а не проводить свой план.",
    "掌握主动权的一方决定在何处、以何种方式进攻。对手只能被动应对，而无法执行自己的计划。",
    "पहल रखने वाला खिलाड़ी तय करता है कि कहाँ और कैसे हमला करना है। विरोधी को अपनी योजना चलाने के बजाय प्रतिक्रिया करनी पड़ती है।")
add("Ne napadaj bez sigurnosti", "Don’t attack without certainty", "N’attaquez pas sans certitude", "Greife nicht ohne Sicherheit an", "Non attaccare senza certezza", "Не атакуйте без уверенности", "没有把握不要进攻", "बिना निश्चितता के हमला न करें")
add("Kapablanka upozorava: direktan napad na Kralja nikada ne treba voditi do krajnosti ako nema apsolutne sigurnosti da će uspeti. Neuspeo napad znači katastrofu.",
    "Capablanca warns: a direct attack on the king should never be pushed to the limit without absolute certainty that it will succeed. A failed attack means disaster.",
    "Capablanca avertit : une attaque directe sur le roi ne doit jamais être menée à l’extrême sans certitude absolue de réussite. Une attaque ratée est une catastrophe.",
    "Capablanca warnt: Einen direkten Angriff auf den König sollte man nie bis zum Äußersten treiben, ohne absolute Gewissheit des Erfolgs. Ein gescheiterter Angriff bedeutet eine Katastrophe.",
    "Capablanca avverte: un attacco diretto al re non va mai spinto all’estremo senza l’assoluta certezza che riesca. Un attacco fallito è un disastro.",
    "Капабланка предупреждает: прямую атаку на короля никогда не следует доводить до крайности без абсолютной уверенности в успехе. Неудачная атака означает катастрофу.",
    "卡帕布兰卡告诫：除非有绝对把握成功，否则对国王的直接进攻绝不应推向极端。进攻失败便是灾难。",
    "कापाब्लांका चेतावनी देते हैं: सफलता की पूर्ण निश्चितता के बिना राजा पर सीधे हमले को कभी चरम तक नहीं ले जाना चाहिए। असफल हमला आपदा है।")
add("U središnjici, vrednost figure zavisi od pozicije. Uvek pazi šta razmenjuješ!",
    "In the middlegame a piece’s value depends on the position. Always be careful what you exchange!",
    "Au milieu de partie, la valeur d’une pièce dépend de la position. Faites toujours attention à ce que vous échangez !",
    "Im Mittelspiel hängt der Wert einer Figur von der Stellung ab. Achte immer darauf, was du abtauschst!",
    "Nel mediogioco il valore di un pezzo dipende dalla posizione. Fai sempre attenzione a cosa scambi!",
    "В миттельшпиле ценность фигуры зависит от позиции. Всегда следите, что вы разменивате!",
    "在中局中，棋子的价值取决于局面。务必留意你在兑换什么！",
    "मध्य खेल में मोहरे का मूल्य स्थिति पर निर्भर करता है। हमेशा ध्यान दें कि आप क्या बदल रहे हैं!")
add("Viljuška (Rašlje)", "Fork", "La fourchette", "Die Gabel", "La forchetta", "Вилка", "双叫（叉攻）", "कांटा")
add("Jedna figura napadne **dve protivničke figure istovremeno**. Protivnik može da spasi samo jednu. Skakači su posebno opasni za viljuške — skaču na polje odakle napadaju Damu i Topa u isto vreme.",
    "One piece attacks **two enemy pieces at once**. The opponent can save only one. Knights are especially dangerous for forks — they jump to a square from which they attack the queen and rook at the same time.",
    "Une pièce attaque **deux pièces adverses à la fois**. L’adversaire ne peut en sauver qu’une. Les cavaliers sont particulièrement dangereux pour les fourchettes — ils sautent sur une case d’où ils attaquent dame et tour en même temps.",
    "Eine Figur greift **zwei gegnerische Figuren gleichzeitig** an. Der Gegner kann nur eine retten. Springer sind für Gabeln besonders gefährlich — sie springen auf ein Feld, von dem sie Dame und Turm zugleich angreifen.",
    "Un pezzo attacca **due pezzi avversari contemporaneamente**. L’avversario può salvarne solo uno. I cavalli sono particolarmente pericolosi per le forchette — saltano su una casa da cui attaccano donna e torre allo stesso tempo.",
    "Одна фигура нападает на **две фигуры противника сразу**. Соперник может спасти только одну. Кони особенно опасны для вилок — они прыгают на поле, откуда атакуют ферзя и ладью одновременно.",
    "一个棋子**同时攻击两个对方棋子**。对手只能救一个。马尤其擅长双叫——它跳到某格，同时攻击后和车。",
    "एक मोहरा **एक साथ दो विरोधी मोहरों** पर हमला करता है। विरोधी केवल एक को बचा सकता है। घोड़े कांटे के लिए विशेष रूप से खतरनाक हैं — वे ऐसे खाने पर कूदते हैं जहाँ से वे वज़ीर और हाथी पर एक साथ हमला करते हैं।")
add("Vezivanje (Pin)", "Pin", "Le clouage", "Die Fesselung", "L’inchiodatura", "Связка", "牵制", "पिन")
add("Napadneš figuru koja **ne sme da se pomeri** jer bi time otkrila vrednu figuru iza nje (Kralja ili Damu). Vezana figura je praktično izolovana iz igre — iskoristi to!",
    "You attack a piece that **cannot move** because doing so would expose a valuable piece behind it (the king or queen). A pinned piece is practically out of the game — use that!",
    "Vous attaquez une pièce qui **ne peut pas bouger** car cela exposerait une pièce de valeur derrière elle (le roi ou la dame). Une pièce clouée est pratiquement hors jeu — profitez-en !",
    "Du greifst eine Figur an, die **sich nicht bewegen darf**, weil sie sonst eine wertvolle Figur dahinter (König oder Dame) freilegen würde. Eine gefesselte Figur ist praktisch aus dem Spiel — nutze das!",
    "Attacchi un pezzo che **non può muoversi** perché esporrebbe un pezzo di valore dietro di sé (il re o la donna). Un pezzo inchiodato è praticamente fuori gioco — sfruttalo!",
    "Вы нападаете на фигуру, которая **не может двигаться**, потому что иначе откроет ценную фигуру позади (короля или ферзя). Связанная фигура практически выключена из игры — используйте это!",
    "你攻击一个**不能移动**的棋子，因为移动会暴露其身后宝贵的棋子（王或后）。被牵制的棋子几乎被排除在对局之外——好好利用！",
    "आप ऐसे मोहरे पर हमला करते हैं जो **हिल नहीं सकता** क्योंकि ऐसा करने से उसके पीछे का मूल्यवान मोहरा (राजा या वज़ीर) खुल जाएगा। पिन किया मोहरा व्यावहारिक रूप से खेल से बाहर है — इसका लाभ उठाएँ!")
add("Pomeriš jednu figuru i time otkriješ napad druge figure iza nje na protivnikovu vrednu figuru. Posebno opasan kada je i sama figura koja se pomera napadačka.",
    "You move one piece and thereby unleash the attack of another piece behind it on the opponent’s valuable piece. Especially dangerous when the moving piece itself also attacks something.",
    "Vous déplacez une pièce et révélez ainsi l’attaque d’une autre pièce derrière elle sur une pièce de valeur adverse. Particulièrement dangereux quand la pièce qui bouge attaque elle-même.",
    "Du ziehst eine Figur und entfesselst dadurch den Angriff einer Figur dahinter auf eine wertvolle gegnerische Figur. Besonders gefährlich, wenn die ziehende Figur selbst ebenfalls angreift.",
    "Muovi un pezzo e così scateni l’attacco di un altro pezzo dietro di esso su un pezzo avversario di valore. Particolarmente pericoloso quando anche il pezzo che si muove attacca.",
    "Вы передвигаете одну фигуру и тем самым открываете нападение другой фигуры позади неё на ценную фигуру соперника. Особенно опасно, когда и сама уходящая фигура атакует.",
    "你移动一个棋子，从而释放其身后另一棋子对对方贵重棋子的攻击。当移动的棋子本身也发起攻击时尤其危险。",
    "आप एक मोहरा हिलाते हैं और इस तरह उसके पीछे के दूसरे मोहरे का हमला विरोधी के मूल्यवान मोहरे पर खुल जाता है। जब हिलने वाला मोहरा स्वयं भी हमला करे तो यह विशेष रूप से खतरनाक होता है।")
add("Kapablanka stalno naglašava: figure moraju da rade zajedno kao tim.",
    "Capablanca constantly stresses: the pieces must work together as a team.",
    "Capablanca insiste sans cesse : les pièces doivent travailler ensemble comme une équipe.",
    "Capablanca betont immer wieder: Die Figuren müssen als Team zusammenarbeiten.",
    "Capablanca sottolinea sempre: i pezzi devono lavorare insieme come una squadra.",
    "Капабланка постоянно подчёркивает: фигуры должны работать вместе, как команда.",
    "卡帕布兰卡反复强调：棋子必须像团队一样协同作战。",
    "कापाब्लांका बार-बार ज़ोर देते हैं: मोहरों को एक टीम की तरह मिलकर काम करना चाहिए।")
add("Topovi traže otvorene linije", "Rooks seek open files", "Les tours cherchent les colonnes ouvertes", "Türme suchen offene Linien", "Le torri cercano colonne aperte", "Ладьи ищут открытые линии", "车寻找开放线", "हाथी खुली फाइलें ढूँढते हैं")
add("Postavi ih na otvorenu kolonu ili sedmi red. Top zatvoren iza sopstvenih piona je pasivna figura.",
    "Place them on an open file or the seventh rank. A rook shut in behind its own pawns is a passive piece.",
    "Placez-les sur une colonne ouverte ou la septième rangée. Une tour enfermée derrière ses propres pions est une pièce passive.",
    "Stelle sie auf eine offene Linie oder die siebte Reihe. Ein Turm, der hinter den eigenen Bauern eingesperrt ist, ist eine passive Figur.",
    "Mettile su una colonna aperta o sulla settima traversa. Una torre chiusa dietro i propri pedoni è un pezzo passivo.",
    "Ставьте их на открытую линию или седьмую горизонталь. Ладья, запертая за собственными пешками, — пассивная фигура.",
    "把它们放在开放线或第七横线上。被自己兵困住的车是被动棋子。",
    "उन्हें खुली फाइल या सातवीं रैंक पर रखें। अपने ही प्यादों के पीछे बंद हाथी एक निष्क्रिय मोहरा है।")
add("Skakači najjači u centru", "Knights are strongest in the centre", "Les cavaliers sont au plus fort au centre", "Springer sind im Zentrum am stärksten", "I cavalli sono più forti al centro", "Кони сильнее всего в центре", "马在中心最强", "घोड़े केंद्र में सबसे मजबूत होते हैं")
add('"Skakač na ivici table je loš skakač" — kaže Kapablanka. U centru kontroliše čak 8 polja, na ivici samo 2-4.',
    '"A knight on the rim is dim," says Capablanca. In the centre it controls as many as 8 squares, on the edge only 2–4.',
    '« Un cavalier au bord est un mauvais cavalier », dit Capablanca. Au centre, il contrôle jusqu’à 8 cases, au bord seulement 2 à 4.',
    '„Ein Springer am Rand bringt Kummer und Schand“, sagt Capablanca. Im Zentrum kontrolliert er bis zu 8 Felder, am Rand nur 2–4.',
    '"Un cavallo sul bordo è un cavallo scarso", dice Capablanca. Al centro controlla fino a 8 case, sul bordo solo 2–4.',
    '«Конь на краю доски — плохой конь», — говорит Капабланка. В центре он контролирует целых 8 полей, на краю лишь 2–4.',
    '“边缘的马是糟糕的马”，卡帕布兰卡如是说。在中心它能控制多达 8 个格子，在边缘只有 2–4 个。',
    '"किनारे का घोड़ा कमजोर घोड़ा होता है" — कापाब्लांका कहते हैं। केंद्र में यह 8 खाने तक नियंत्रित करता है, किनारे पर केवल 2–4।')
add("Lovci vole otvorene dijagonale", "Bishops love open diagonals", "Les fous aiment les diagonales ouvertes", "Läufer lieben offene Diagonalen", "Gli alfieri amano le diagonali aperte", "Слоны любят открытые диагонали", "象喜欢开放的斜线", "ऊँट खुले विकर्ण पसंद करते हैं")
add("Lovac koji blokira sopstveni pion je ograničen. Pione postavljaj na polja **suprotne boje** od svog lovca.",
    "A bishop blocked by its own pawn is limited. Put your pawns on squares of the **opposite colour** to your bishop.",
    "Un fou bloqué par son propre pion est limité. Placez vos pions sur des cases de **couleur opposée** à votre fou.",
    "Ein vom eigenen Bauern blockierter Läufer ist eingeschränkt. Stelle deine Bauern auf Felder der **Gegenfarbe** deines Läufers.",
    "Un alfiere bloccato dal proprio pedone è limitato. Metti i pedoni su case di **colore opposto** al tuo alfiere.",
    "Слон, заблокированный собственной пешкой, ограничен. Ставьте пешки на поля **противоположного цвета** относительно вашего слона.",
    "被自己兵挡住的象会受限。把你的兵放在与你象**异色**的格子上。",
    "अपने ही प्यादे से अवरुद्ध ऊँट सीमित होता है। अपने प्यादों को अपने ऊँट के **विपरीत रंग** के खानों पर रखें।")
add("Kapablankovo zlatno pravilo", "Capablanca’s golden rule", "La règle d’or de Capablanca", "Capablancas goldene Regel", "La regola d’oro di Capablanca", "Золотое правило Капабланки", "卡帕布兰卡的黄金法则", "कापाब्लांका का स्वर्णिम नियम")
add('"Dobitak jednog piona između jednako jakih igrača najčešće znači pobedu." Ne potcenjuj pion — u završnici je on često odlučujući. Svaka sitna prednost se akumulira!',
    '"Winning a single pawn between equally strong players most often means victory." Don’t underestimate a pawn — in the endgame it is often decisive. Every small advantage accumulates!',
    '« Gagner un seul pion entre joueurs de force égale signifie le plus souvent la victoire. » Ne sous-estimez pas un pion — en finale, il est souvent décisif. Chaque petit avantage s’accumule !',
    '„Ein einzelner Mehrbauer zwischen gleich starken Spielern bedeutet meist den Sieg.“ Unterschätze keinen Bauern — im Endspiel ist er oft entscheidend. Jeder kleine Vorteil summiert sich!',
    '"Guadagnare un solo pedone tra giocatori di pari forza significa quasi sempre la vittoria." Non sottovalutare un pedone — nel finale è spesso decisivo. Ogni piccolo vantaggio si accumula!',
    '«Выигрыш одной пешки между равными по силе игроками чаще всего означает победу.» Не недооценивайте пешку — в эндшпиле она часто решает. Каждое маленькое преимущество накапливается!',
    '“在实力相当的对手之间，多赢一个兵往往就意味着胜利。”不要小看一个兵——在残局中它常常是决定性的。每一点微小优势都会积累！',
    '"समान शक्ति के खिलाड़ियों के बीच एक प्यादा जीतना अक्सर जीत का मतलब होता है।" प्यादे को कम न आँकें — अंत खेल में यह अक्सर निर्णायक होता है। हर छोटी बढ़त जुड़ती जाती है!')

# ── Lesson 4 ────────────────────────────────────────────────────────────────
add('"Pre nego što se boriš za pobedu u otvaranju ili središnjici, moraš savladati završnicu. Onaj ko ne poznaje završnicu ne može biti jak šahista."',
    '"Before you fight for victory in the opening or middlegame, you must master the endgame. One who does not know the endgame cannot be a strong player."',
    '« Avant de lutter pour la victoire en ouverture ou en milieu de partie, vous devez maîtriser la finale. Qui ne connaît pas la finale ne peut être un joueur fort. »',
    '„Bevor du in Eröffnung oder Mittelspiel um den Sieg kämpfst, musst du das Endspiel beherrschen. Wer das Endspiel nicht kennt, kann kein starker Spieler sein.“',
    '"Prima di lottare per la vittoria in apertura o mediogioco, devi padroneggiare il finale. Chi non conosce il finale non può essere un giocatore forte."',
    '«Прежде чем бороться за победу в дебюте или миттельшпиле, нужно овладеть эндшпилем. Кто не знает эндшпиля, не может быть сильным игроком.»',
    '“在为开局或中局的胜利而战之前，你必须精通残局。不懂残局的人成不了强手。”',
    '"ओपनिंग या मध्य खेल में जीत के लिए लड़ने से पहले, आपको अंत खेल में महारत हासिल करनी होगी। जो अंत खेल नहीं जानता वह मजबूत खिलाड़ी नहीं हो सकता।"')
add("Završnica počinje kada su sa table nestale najvažnije figure i ostanu Kraljevi sa pešacima i možda jednom-dve lake figure.",
    "The endgame begins when the most important pieces have left the board and only the kings remain with pawns and perhaps one or two minor pieces.",
    "La finale commence quand les pièces les plus importantes ont quitté l’échiquier et qu’il ne reste que les rois avec des pions et peut-être une ou deux pièces mineures.",
    "Das Endspiel beginnt, wenn die wichtigsten Figuren das Brett verlassen haben und nur die Könige mit Bauern und vielleicht ein oder zwei Leichtfiguren bleiben.",
    "Il finale inizia quando i pezzi più importanti hanno lasciato la scacchiera e restano solo i re con i pedoni e forse uno o due pezzi minori.",
    "Эндшпиль начинается, когда важнейшие фигуры покинули доску и остаются короли с пешками и, возможно, одной-двумя лёгкими фигурами.",
    "当最重要的棋子离开棋盘，只剩下双王、兵以及或许一两个轻子时，残局便开始了。",
    "अंत खेल तब शुरू होता है जब सबसे महत्वपूर्ण मोहरे बोर्ड से चले जाते हैं और केवल राजा, प्यादों और शायद एक-दो छोटे मोहरों के साथ बचते हैं।")
add("Ovo je **najveća promena** u završnici. Kralj koji je celu partiju bežao sada mora aktivno da napadá.",
    "This is the **biggest change** in the endgame. The king that fled all game must now actively attack.",
    "C’est le **plus grand changement** en finale. Le roi qui a fui toute la partie doit maintenant attaquer activement.",
    "Das ist die **größte Veränderung** im Endspiel. Der König, der die ganze Partie geflohen ist, muss jetzt aktiv angreifen.",
    "Questo è il **cambiamento più grande** nel finale. Il re che è fuggito per tutta la partita ora deve attaccare attivamente.",
    "Это **самая большая перемена** в эндшпиле. Король, бегавший всю партию, теперь должен активно атаковать.",
    "这是残局中**最大的变化**。整盘棋都在逃跑的国王现在必须主动进攻。",
    "यह अंत खेल का **सबसे बड़ा बदलाव** है। पूरे खेल भागता रहा राजा अब सक्रिय रूप से हमला करना चाहिए।")
add("Dovedi Kralja u centar odmah", "Bring the king to the centre right away", "Amenez le roi au centre tout de suite", "Bring den König sofort ins Zentrum", "Porta subito il re al centro", "Сразу ведите короля в центр", "立刻把国王带到中心", "राजा को तुरंत केंद्र में लाएँ")
add("Čim oseti da je završnica blizu, počni da pomičeš Kralja ka centru table. Centralni Kralj dominira nad marginalnim.",
    "As soon as you sense the endgame approaching, start moving the king toward the centre of the board. A central king dominates a marginal one.",
    "Dès que vous sentez la finale approcher, commencez à amener le roi vers le centre. Un roi central domine un roi en marge.",
    "Sobald du das Endspiel nahen spürst, beginne, den König zur Brettmitte zu führen. Ein zentraler König beherrscht einen randständigen.",
    "Appena senti avvicinarsi il finale, comincia a portare il re verso il centro. Un re centrale domina uno marginale.",
    "Как только почувствуете приближение эндшпиля, начинайте двигать короля к центру доски. Центральный король доминирует над крайним.",
    "一旦感到残局临近，就开始把国王移向棋盘中心。位居中心的国王压制处于边缘的国王。",
    "जैसे ही आपको अंत खेल निकट लगे, राजा को बोर्ड के केंद्र की ओर बढ़ाना शुरू करें। केंद्रीय राजा किनारे वाले पर हावी रहता है।")
add("Pioni su budući Kraljevi", "Pawns are future queens", "Les pions sont de futures dames", "Bauern sind künftige Damen", "I pedoni sono future donne", "Пешки — будущие ферзи", "兵是未来的皇后", "प्यादे भविष्य के वज़ीर हैं")
add("Svaki pion koji stigne do poslednjeg reda postaje Dama (ili druga figura). Ovo je glavni cilj u pešačkim završnicama.",
    "Every pawn that reaches the last rank becomes a queen (or another piece). This is the main goal in pawn endgames.",
    "Chaque pion qui atteint la dernière rangée devient une dame (ou une autre pièce). C’est le but principal des finales de pions.",
    "Jeder Bauer, der die letzte Reihe erreicht, wird zur Dame (oder einer anderen Figur). Das ist das Hauptziel in Bauernendspielen.",
    "Ogni pedone che raggiunge l’ultima traversa diventa una donna (o un altro pezzo). È l’obiettivo principale nei finali di pedoni.",
    "Каждая пешка, дошедшая до последней горизонтали, становится ферзём (или другой фигурой). Это главная цель в пешечных эндшпилях.",
    "每个到达底线的兵都会变成后（或其他棋子）。这是兵类残局的主要目标。",
    "अंतिम रैंक तक पहुँचने वाला हर प्यादा वज़ीर (या अन्य मोहरा) बन जाता है। यह प्यादा अंत खेल का मुख्य लक्ष्य है।")
add("Kapablanka objašnjava ovo pravilo jasno i precizno:", "Capablanca explains this rule clearly and precisely:", "Capablanca explique cette règle clairement et précisément :", "Capablanca erklärt diese Regel klar und präzise:", "Capablanca spiega questa regola in modo chiaro e preciso:", "Капабланка объясняет это правило ясно и точно:", "卡帕布兰卡清晰而精确地解释了这一规则：", "कापाब्लांका इस नियम को स्पष्ट और सटीक रूप से समझाते हैं:")
add("Ključno pravilo", "The key rule", "La règle clé", "Die Schlüsselregel", "La regola chiave", "Ключевое правило", "关键规则", "मुख्य नियम")
add("Da bi pešačka završnica bila pobednička, **Kralj mora biti ispred svog piona** sa barem jednim praznim poljem između njih. Ako je protivnički Kralj direktno ispred piona — igra je remi!",
    "For a pawn endgame to be winning, **the king must be ahead of its pawn** with at least one empty square between them. If the enemy king is directly in front of the pawn — it’s a draw!",
    "Pour qu’une finale de pions soit gagnante, **le roi doit précéder son pion** avec au moins une case vide entre eux. Si le roi adverse est juste devant le pion — c’est nulle !",
    "Damit ein Bauernendspiel gewonnen ist, **muss der König vor seinem Bauern stehen** mit mindestens einem leeren Feld dazwischen. Steht der gegnerische König direkt vor dem Bauern — ist es remis!",
    "Perché un finale di pedoni sia vincente, **il re deve stare davanti al proprio pedone** con almeno una casa vuota tra loro. Se il re avversario è direttamente davanti al pedone — è patta!",
    "Чтобы пешечный эндшпиль был выигрышным, **король должен находиться впереди своей пешки** с хотя бы одним пустым полем между ними. Если вражеский король прямо перед пешкой — ничья!",
    "要让兵类残局获胜，**国王必须走在自己兵的前面**，且两者之间至少隔一个空格。如果对方国王正好挡在兵的前面——就是和棋！",
    "प्यादा अंत खेल जीतने के लिए, **राजा को अपने प्यादे से आगे होना चाहिए** और उनके बीच कम से कम एक खाली खाना हो। यदि विरोधी राजा सीधे प्यादे के सामने है — तो ड्रॉ है!")
add("Napreduj Kralja, ne piona", "Advance the king, not the pawn", "Avancez le roi, pas le pion", "Rücke den König vor, nicht den Bauern", "Avanza il re, non il pedone", "Продвигайте короля, а не пешку", "推进国王，而非兵", "राजा को आगे बढ़ाएँ, प्यादे को नहीं")
add("Kapablanka savetuje: napreduj Kralja koliko je moguće a da ne ugrožavaš piona. Piona pomiči tek kada je neophodno za njegovu zaštitu.",
    "Capablanca advises: advance the king as far as possible without endangering the pawn. Move the pawn only when it is necessary for its protection.",
    "Capablanca conseille : avancez le roi autant que possible sans mettre le pion en danger. Ne déplacez le pion que lorsque c’est nécessaire pour le protéger.",
    "Capablanca rät: rücke den König so weit wie möglich vor, ohne den Bauern zu gefährden. Ziehe den Bauern erst, wenn es zu seinem Schutz nötig ist.",
    "Capablanca consiglia: avanza il re il più possibile senza mettere in pericolo il pedone. Muovi il pedone solo quando è necessario per proteggerlo.",
    "Капабланка советует: продвигайте короля как можно дальше, не подвергая пешку опасности. Пешку двигайте лишь тогда, когда это нужно для её защиты.",
    "卡帕布兰卡建议：在不危及兵的前提下尽量推进国王。只有在必须保护兵时才推进兵。",
    "कापाब्लांका सलाह देते हैं: प्यादे को खतरे में डाले बिना राजा को जितना संभव हो आगे बढ़ाएँ। प्यादे को केवल तभी हिलाएँ जब उसकी रक्षा के लिए आवश्यक हो।")
add('Tajno oružje — "Opozicija"', 'The secret weapon — "the Opposition"', 'L’arme secrète — « l’opposition »', 'Die Geheimwaffe — „die Opposition“', 'L’arma segreta — "l’opposizione"', 'Секретное оружие — «оппозиция»', '秘密武器——“对王”', 'गुप्त हथियार — "विरोध (Opposition)"')
add("Kada su dva Kralja međusobno licem u lice sa neparnim brojem polja između, igrač koji je **prethodno poterao** ima prednost. Zove se opozicija — i ključna je za sve pešačke završnice.",
    "When two kings face each other with an odd number of squares between them, the player who **moved last** has the advantage. It is called the opposition — and it is key to all pawn endgames.",
    "Quand deux rois se font face avec un nombre impair de cases entre eux, le joueur qui **vient de jouer** a l’avantage. C’est l’opposition — essentielle dans toutes les finales de pions.",
    "Stehen sich zwei Könige mit einer ungeraden Zahl von Feldern dazwischen gegenüber, hat der Spieler im Vorteil, der **zuletzt gezogen** hat. Das nennt man die Opposition — sie ist der Schlüssel zu allen Bauernendspielen.",
    "Quando due re si fronteggiano con un numero dispari di case tra loro, ha il vantaggio il giocatore che **ha appena mosso**. Si chiama opposizione — ed è la chiave di tutti i finali di pedoni.",
    "Когда два короля стоят лицом к лицу с нечётным числом полей между ними, преимущество у игрока, который **только что сходил**. Это называется оппозицией — и она ключ ко всем пешечным эндшпилям.",
    "当两个国王面对面、之间有奇数个空格时，**刚走完棋**的一方占有优势。这称为对王（opposition）——它是所有兵类残局的关键。",
    "जब दो राजा आमने-सामने हों और उनके बीच विषम संख्या में खाने हों, तो जिस खिलाड़ी ने **अभी-अभी चाल चली** उसे बढ़त होती है। इसे विरोध (opposition) कहते हैं — और यह सभी प्यादा अंत खेलों की कुंजी है।")
add("Jedno drži dvoje — Kapablankovo načelo", "One holds two — Capablanca’s principle", "Un en tient deux — le principe de Capablanca", "Einer hält zwei — Capablancas Prinzip", "Uno ne tiene due — il principio di Capablanca", "Один держит двоих — принцип Капабланки", "一子牵制二子——卡帕布兰卡的原则", "एक दो को रोके — कापाब्लांका का सिद्धांत")
add('"Pion koji drži dva protivnička piona je jedno od glavnih oruđa majstora." Ako tvoj pion blokira dva protivnička, ti si faktički figuru ispred — iskoristi tu prednost na drugoj strani table!',
    '"A pawn that holds two enemy pawns is one of the master’s main tools." If your pawn blocks two of the opponent’s, you are effectively a piece ahead — use that advantage on the other side of the board!',
    '« Un pion qui en tient deux est l’un des principaux outils du maître. » Si votre pion en bloque deux à l’adversaire, vous avez en fait une pièce d’avance — exploitez cet avantage de l’autre côté de l’échiquier !',
    '„Ein Bauer, der zwei gegnerische Bauern hält, ist eines der Hauptwerkzeuge des Meisters.“ Blockiert dein Bauer zwei gegnerische, bist du praktisch eine Figur voraus — nutze diesen Vorteil auf der anderen Brettseite!',
    '"Un pedone che ne tiene due avversari è uno degli strumenti principali del maestro." Se il tuo pedone ne blocca due dell’avversario, sei di fatto un pezzo avanti — sfrutta quel vantaggio sull’altro lato della scacchiera!',
    '«Пешка, удерживающая две пешки соперника, — одно из главных орудий мастера.» Если ваша пешка блокирует две чужие, вы фактически на фигуру впереди — используйте это преимущество на другом фланге!',
    '“一个能牵制对方两个兵的兵，是大师的主要武器之一。”如果你的兵挡住对方两个兵，你实际上就多了一个子——在棋盘另一侧利用这个优势！',
    '"दो विरोधी प्यादों को रोकने वाला प्यादा मास्टर के मुख्य औज़ारों में से एक है।" यदि आपका प्यादा विरोधी के दो प्यादों को रोकता है, तो आप वास्तव में एक मोहरा आगे हैं — बोर्ड के दूसरी ओर इस बढ़त का उपयोग करें!')
add("Lovac je jači kada su pioni na obe strane", "The bishop is stronger when pawns are on both sides", "Le fou est plus fort quand il y a des pions des deux côtés", "Der Läufer ist stärker, wenn auf beiden Seiten Bauern stehen", "L’alfiere è più forte quando ci sono pedoni su entrambi i lati", "Слон сильнее, когда пешки на обоих флангах", "兵在两侧时象更强", "जब प्यादे दोनों ओर हों तो ऊँट अधिक मजबूत होता है")
add("Lovac može istovremeno da napada pione na oba krila zahvaljujući dometu. Skakač je spor i ne može da stigne svuda.",
    "Thanks to its range the bishop can attack pawns on both wings at once. The knight is slow and cannot reach everywhere.",
    "Grâce à sa portée, le fou peut attaquer des pions sur les deux ailes à la fois. Le cavalier est lent et ne peut pas être partout.",
    "Dank seiner Reichweite kann der Läufer Bauern auf beiden Flügeln gleichzeitig angreifen. Der Springer ist langsam und kann nicht überallhin gelangen.",
    "Grazie alla sua gittata l’alfiere può attaccare pedoni su entrambe le ali contemporaneamente. Il cavallo è lento e non può arrivare ovunque.",
    "Благодаря дальнобойности слон может атаковать пешки на обоих флангах одновременно. Конь медлителен и не успевает повсюду.",
    "凭借射程，象能同时攻击两翼的兵。马很慢，无法兼顾各处。",
    "अपनी पहुँच के कारण ऊँट एक साथ दोनों किनारों के प्यादों पर हमला कर सकता है। घोड़ा धीमा है और हर जगह नहीं पहुँच सकता।")
add("Skakač je jači u zatvorenim pozicijama", "The knight is stronger in closed positions", "Le cavalier est plus fort dans les positions fermées", "Der Springer ist stärker in geschlossenen Stellungen", "Il cavallo è più forte nelle posizioni chiuse", "Конь сильнее в закрытых позициях", "马在封闭局面中更强", "घोड़ा बंद स्थितियों में अधिक मजबूत होता है")
add("Kada su pioni blokirani i pozicija zatvorena, skakač je bolji jer može da preskoče pione i stigne do idealnog polja.",
    "When the pawns are locked and the position is closed, the knight is better because it can jump over pawns and reach the ideal square.",
    "Quand les pions sont bloqués et la position fermée, le cavalier est meilleur car il peut sauter par-dessus les pions et atteindre la case idéale.",
    "Wenn die Bauern blockiert und die Stellung geschlossen ist, ist der Springer besser, weil er über Bauern springen und das ideale Feld erreichen kann.",
    "Quando i pedoni sono bloccati e la posizione è chiusa, il cavallo è migliore perché può saltare i pedoni e raggiungere la casa ideale.",
    "Когда пешки заблокированы и позиция закрыта, конь лучше, потому что он может перепрыгивать пешки и достигать идеального поля.",
    "当兵被锁住、局面封闭时，马更出色，因为它能跳过兵到达理想的格子。",
    "जब प्यादे अवरुद्ध हों और स्थिति बंद हो, तो घोड़ा बेहतर है क्योंकि वह प्यादों के ऊपर से कूदकर आदर्श खाने तक पहुँच सकता है।")
add("Slabost lovca — Topov pion", "The bishop’s weakness — the rook pawn", "La faiblesse du fou — le pion de tour", "Die Schwäche des Läufers — der Turmbauer", "La debolezza dell’alfiere — il pedone di torre", "Слабость слона — ладейная пешка", "象的弱点——车兵", "ऊँट की कमजोरी — हाथी-प्यादा")
add("Ako tvoj pion ide do h8 (ili a8) i to polje je suprotne boje od tvog lovca, protivnik drži ugao i igra je remi! Kapablanka ovo posebno ističe kao izvor mnogih propuštenih pobeda.",
    "If your pawn heads to h8 (or a8) and that square is the opposite colour to your bishop, the opponent holds the corner and it’s a draw! Capablanca especially highlights this as the source of many missed wins.",
    "Si votre pion va vers h8 (ou a8) et que cette case est de couleur opposée à votre fou, l’adversaire tient le coin et c’est nulle ! Capablanca le souligne comme la source de bien des gains manqués.",
    "Strebt dein Bauer nach h8 (oder a8) und ist dieses Feld die Gegenfarbe deines Läufers, hält der Gegner die Ecke und es ist remis! Capablanca hebt dies besonders als Quelle vieler verpasster Siege hervor.",
    "Se il tuo pedone va verso h8 (o a8) e quella casa è di colore opposto al tuo alfiere, l’avversario tiene l’angolo ed è patta! Capablanca lo sottolinea come fonte di molte vittorie mancate.",
    "Если ваша пешка идёт на h8 (или a8) и это поле противоположного цвета вашему слону, соперник держит угол — и это ничья! Капабланка особо отмечает это как источник многих упущенных побед.",
    "如果你的兵奔向 h8（或 a8），而该格与你的象异色，对手便能守住角落，结果是和棋！卡帕布兰卡特别指出这是许多错失胜局的根源。",
    "यदि आपका प्यादा h8 (या a8) की ओर जाता है और वह खाना आपके ऊँट के विपरीत रंग का है, तो विरोधी कोना थामे रहता है और ड्रॉ हो जाता है! कापाब्लांका इसे कई गँवाई गई जीतों का स्रोत बताते हैं।")
add("Situacija kada je Kralj napadnut. Igrač **mora** da se odbrani — pomeri kralja, pojede napadača, ili postavi štit između.",
    "A situation where the king is under attack. The player **must** defend — move the king, capture the attacker, or place a shield in between.",
    "Une situation où le roi est attaqué. Le joueur **doit** se défendre — déplacer le roi, capturer l’attaquant, ou interposer un bouclier.",
    "Eine Situation, in der der König angegriffen wird. Der Spieler **muss** sich verteidigen — den König ziehen, den Angreifer schlagen oder einen Schild dazwischenstellen.",
    "Una situazione in cui il re è sotto attacco. Il giocatore **deve** difendersi — muovere il re, catturare l’attaccante o frapporre uno scudo.",
    "Ситуация, когда король под атакой. Игрок **обязан** защититься — увести короля, побить нападающего или поставить заслон.",
    "国王受到攻击的局面。该方**必须**应对——移动国王、吃掉攻击者，或在中间放置屏障。",
    "ऐसी स्थिति जब राजा पर हमला हो। खिलाड़ी को **अवश्य** बचाव करना होगा — राजा हिलाएँ, हमलावर को मारें, या बीच में ढाल रखें।")
add("Šah-Mat — Kraj igre", "Checkmate — end of the game", "Échec et mat — fin de la partie", "Schachmatt — Ende der Partie", "Scacco matto — fine della partita", "Мат — конец игры", "将杀——对局结束", "शहमात — खेल का अंत")
add("Kralj je napadnut, a nema nijedan legalan način odbrane. Partija se završava ovde — Kralj se nikada zapravo ne jede.",
    "The king is under attack and there is no legal way to defend. The game ends here — the king is never actually captured.",
    "Le roi est attaqué et il n’y a aucun moyen légal de se défendre. La partie se termine ici — le roi n’est jamais réellement capturé.",
    "Der König wird angegriffen und es gibt keine legale Verteidigung. Die Partie endet hier — der König wird nie tatsächlich geschlagen.",
    "Il re è sotto attacco e non c’è alcun modo legale di difendersi. La partita finisce qui — il re non viene mai realmente catturato.",
    "Король под атакой, и нет ни одного законного способа защититься. Партия заканчивается — короля никогда фактически не бьют.",
    "国王被攻击，且没有任何合法的防御办法。对局到此结束——国王实际上永远不会被吃掉。",
    "राजा पर हमला है और बचाव का कोई वैध तरीका नहीं। खेल यहीं समाप्त होता है — राजा वास्तव में कभी नहीं मारा जाता।")
add("Pat — Noćna mora pobednika!", "Stalemate — the winner’s nightmare!", "Pat — le cauchemar du gagnant !", "Patt — der Albtraum des Siegers!", "Stallo — l’incubo del vincitore!", "Пат — кошмар победителя!", "逼和——胜者的噩梦！", "गतिरोध — विजेता का दुःस्वप्न!")
add("Igrač na potezu **nije u šahu**, ali nema nijedan legalan potez. Odmah je remi! Ovo je najopasnija greška u završnici — pretvoriti pobedničku poziciju u remi jednim lošim potezom.",
    "The player to move is **not in check**, but has no legal move. It’s an immediate draw! This is the most dangerous mistake in the endgame — turning a winning position into a draw with one bad move.",
    "Le joueur au trait **n’est pas en échec**, mais n’a aucun coup légal. C’est nulle immédiatement ! C’est l’erreur la plus dangereuse en finale — transformer une position gagnante en nulle par un mauvais coup.",
    "Der Spieler am Zug steht **nicht im Schach**, hat aber keinen legalen Zug. Sofort remis! Das ist der gefährlichste Fehler im Endspiel — eine Gewinnstellung mit einem schlechten Zug ins Remis zu verwandeln.",
    "Il giocatore al tratto **non è sotto scacco**, ma non ha alcuna mossa legale. È subito patta! È l’errore più pericoloso del finale — trasformare una posizione vincente in patta con una mossa sbagliata.",
    "Игрок, чей ход, **не под шахом**, но не имеет ни одного законного хода. Немедленная ничья! Это опаснейшая ошибка в эндшпиле — превратить выигранную позицию в ничью одним плохим ходом.",
    "轮到走棋的一方**没有被将**，却没有任何合法着法。立即和棋！这是残局中最危险的错误——一步坏棋把胜局变成和棋。",
    "चाल वाला खिलाड़ी **शह में नहीं है**, पर उसके पास कोई वैध चाल नहीं। तुरंत ड्रॉ! यह अंत खेल की सबसे खतरनाक गलती है — एक खराब चाल से जीती हुई स्थिति को ड्रॉ में बदल देना।")
add("Ako se ista pozicija ponovi **tri puta**, može se tražiti remi.",
    "If the same position occurs **three times**, a draw can be claimed.",
    "Si la même position se répète **trois fois**, la nulle peut être réclamée.",
    "Wiederholt sich dieselbe Stellung **dreimal**, kann Remis beansprucht werden.",
    "Se la stessa posizione si ripete **tre volte**, si può richiedere la patta.",
    "Если одна и та же позиция повторяется **три раза**, можно требовать ничью.",
    "如果同一局面出现**三次**，可以要求和棋。",
    "यदि वही स्थिति **तीन बार** आती है, तो ड्रॉ का दावा किया जा सकता है।")
add("Samo Kraljevi, ili Kralj + Lovac/Skakač protiv Kralja — nije moguće dati mat. Automatski remi.",
    "Only kings, or king + bishop/knight against king — checkmate is impossible. Automatic draw.",
    "Seuls les rois, ou roi + fou/cavalier contre roi — le mat est impossible. Nulle automatique.",
    "Nur Könige, oder König + Läufer/Springer gegen König — Matt ist unmöglich. Automatisches Remis.",
    "Solo i re, oppure re + alfiere/cavallo contro re — il matto è impossibile. Patta automatica.",
    "Только короли, или король + слон/конь против короля — мат невозможен. Автоматическая ничья.",
    "只剩双王，或王 + 象/马 对王——无法将杀。自动和棋。",
    "केवल राजा, या राजा + ऊँट/घोड़ा बनाम राजा — शहमात संभव नहीं। स्वतः ड्रॉ।")
add("Primeni sve što si naučio! Reši 5 zadataka — mat u najmanji broj poteza. Svaki koristi drugu kombinaciju figura.",
    "Apply everything you’ve learned! Solve 5 puzzles — mate in the fewest moves. Each uses a different combination of pieces.",
    "Appliquez tout ce que vous avez appris ! Résolvez 5 problèmes — mat en un minimum de coups. Chacun utilise une combinaison de pièces différente.",
    "Wende alles an, was du gelernt hast! Löse 5 Aufgaben — Matt in möglichst wenigen Zügen. Jede nutzt eine andere Figurenkombination.",
    "Applica tutto ciò che hai imparato! Risolvi 5 problemi — matto nel minor numero di mosse. Ognuno usa una diversa combinazione di pezzi.",
    "Примените всё, что узнали! Решите 5 задач — мат в наименьшее число ходов. В каждой своя комбинация фигур.",
    "运用你学到的一切！解开 5 道谜题——用最少的步数将杀。每道使用不同的棋子组合。",
    "जो कुछ सीखा है उसे लागू करें! 5 पहेलियाँ हल करें — सबसे कम चालों में मात। हर एक में मोहरों का अलग संयोजन है।")
add("Zadatak 1 — Dama na zadnjoj liniji", "Puzzle 1 — Queen on the back rank", "Problème 1 — Dame sur la dernière rangée", "Aufgabe 1 — Dame auf der Grundreihe", "Problema 1 — Donna sull’ultima traversa", "Задача 1 — ферзь на последней горизонтали", "谜题 1——后在底线", "पहेली 1 — आख़िरी रैंक पर वज़ीर")
add("Crni Kralj je zarobljen. Dama ima slobodan put...", "The black king is trapped. The queen has a clear path…", "Le roi noir est piégé. La dame a la voie libre…", "Der schwarze König ist gefangen. Die Dame hat freie Bahn…", "Il re nero è intrappolato. La donna ha la via libera…", "Чёрный король в ловушке. У ферзя свободный путь…", "黑王被困。后有畅通的路线……", "काला राजा फँसा है। वज़ीर का रास्ता साफ़ है…")
add("Zadatak 2 — Top na 8. liniji", "Puzzle 2 — Rook on the 8th rank", "Problème 2 — Tour sur la 8e rangée", "Aufgabe 2 — Turm auf der 8. Reihe", "Problema 2 — Torre sull’8ª traversa", "Задача 2 — ладья на 8-й горизонтали", "谜题 2——车到第 8 横线", "पहेली 2 — 8वीं रैंक पर हाथी")
add("Pešaci blokiraju sopstvenog Kralja. Top pronalazi put...", "The pawns block their own king. The rook finds the way…", "Les pions bloquent leur propre roi. La tour trouve la voie…", "Die Bauern blockieren ihren eigenen König. Der Turm findet den Weg…", "I pedoni bloccano il proprio re. La torre trova la via…", "兵挡住了自己的国王。车找到了路…", "अपने ही राजा को प्यादे रोक रहे हैं। हाथी रास्ता खोज लेता है…", "अपने ही प्यादे राजा को रोक रहे हैं। हाथी रास्ता ढूँढ लेता है…")
add("Zadatak 3 — Žrtva Topa!", "Puzzle 3 — Rook sacrifice!", "Problème 3 — Sacrifice de tour !", "Aufgabe 3 — Turmopfer!", "Problema 3 — Sacrificio di torre!", "Задача 3 — жертва ладьи!", "谜题 3——弃车！", "पहेली 3 — हाथी का बलिदान!")
add("Top ide na e8 i daje šah. Crni Top mora da uzme — a onda Dama?", "The rook goes to e8 with check. The black rook must take — and then the queen?", "La tour va en e8 avec échec. La tour noire doit prendre — et ensuite la dame ?", "Der Turm geht mit Schach nach e8. Der schwarze Turm muss schlagen — und dann die Dame?", "La torre va in e8 con scacco. La torre nera deve prendere — e poi la donna?", "Ладья идёт на e8 с шахом. Чёрная ладья обязана взять — а затем ферзь?", "车到 e8 将军。黑车必须吃——然后是后？", "हाथी शह देते हुए e8 पर जाता है। काले हाथी को लेना ही होगा — और फिर वज़ीर?")
add("Zadatak 4 — Lovac + Top", "Puzzle 4 — Bishop + Rook", "Problème 4 — Fou + Tour", "Aufgabe 4 — Läufer + Turm", "Problema 4 — Alfiere + Torre", "Задача 4 — слон + ладья", "谜题 4——象 + 车", "पहेली 4 — ऊँट + हाथी")
add("Lovac daje šah i tera Kralja na g8. Zašto je to pogubno?", "The bishop gives check and drives the king to g8. Why is that fatal?", "Le fou fait échec et pousse le roi en g8. Pourquoi est-ce fatal ?", "Der Läufer gibt Schach und treibt den König nach g8. Warum ist das verhängnisvoll?", "L’alfiere dà scacco e spinge il re in g8. Perché è fatale?", "Слон даёт шах и гонит короля на g8. Почему это губительно?", "象将军，把国王逼到 g8。为什么这是致命的？", "ऊँट शह देकर राजा को g8 पर धकेलता है। यह घातक क्यों है?")
add("Zadatak 5 (težak) — Žrtva Dame, Lovac mat", "Puzzle 5 (hard) — Queen sacrifice, bishop mate", "Problème 5 (difficile) — Sacrifice de dame, mat du fou", "Aufgabe 5 (schwer) — Damenopfer, Läufermatt", "Problema 5 (difficile) — Sacrificio di donna, matto d’alfiere", "Задача 5 (сложная) — жертва ферзя, мат слоном", "谜题 5（困难）——弃后，象将杀", "पहेली 5 (कठिन) — वज़ीर का बलिदान, ऊँट से मात")
add("Greet – Hanley, Liverpool 2008. Dama se žrtvuje na h6. Zašto Kralj mora da uzme?", "Greet – Hanley, Liverpool 2008. The queen is sacrificed on h6. Why must the king take?", "Greet – Hanley, Liverpool 2008. La dame se sacrifie en h6. Pourquoi le roi doit-il prendre ?", "Greet – Hanley, Liverpool 2008. Die Dame opfert sich auf h6. Warum muss der König schlagen?", "Greet – Hanley, Liverpool 2008. La donna si sacrifica in h6. Perché il re deve prendere?", "Грит – Хэнли, Ливерпуль 2008. Ферзь жертвуется на h6. Почему король обязан взять?", "Greet – Hanley，利物浦 2008。后在 h6 弃身。为什么国王必须吃？", "ग्रीट – हैनली, लिवरपूल 2008। वज़ीर h6 पर बलिदान होता है। राजा को लेना क्यों पड़ता है?")
add("Hoze Raul Kapablanka (1888–1942)", "José Raúl Capablanca (1888–1942)", "José Raúl Capablanca (1888–1942)", "José Raúl Capablanca (1888–1942)", "José Raúl Capablanca (1888–1942)", "Хосе Рауль Капабланка (1888–1942)", "何塞·劳尔·卡帕布兰卡 (1888–1942)", "होज़े राउल कापाब्लांका (1888–1942)")
add("Kubanski šahista, treći zvanični svetski prvak u šahu. Važi za jednog od najvećih šahiskih genija svih vremena — poznat po kristalno čistom stilu igre i intuitivnom razumevanju pozicije.",
    "A Cuban chess player, the third official World Chess Champion. Regarded as one of the greatest chess geniuses of all time — known for a crystal-clear playing style and intuitive understanding of position.",
    "Joueur d’échecs cubain, troisième champion du monde officiel. Considéré comme l’un des plus grands génies des échecs de tous les temps — connu pour son style limpide et sa compréhension intuitive de la position.",
    "Ein kubanischer Schachspieler, der dritte offizielle Schachweltmeister. Gilt als eines der größten Schachgenies aller Zeiten — bekannt für seinen kristallklaren Stil und sein intuitives Stellungsverständnis.",
    "Scacchista cubano, terzo campione del mondo ufficiale. Considerato uno dei più grandi geni scacchistici di tutti i tempi — noto per uno stile cristallino e una comprensione intuitiva della posizione.",
    "Кубинский шахматист, третий официальный чемпион мира. Считается одним из величайших шахматных гениев всех времён — известен кристально ясным стилем игры и интуитивным пониманием позиции.",
    "古巴棋手，第三位正式国际象棋世界冠军。被视为有史以来最伟大的国际象棋天才之一——以清澈如水的棋风和对局面的直觉理解著称。",
    "क्यूबाई शतरंज खिलाड़ी, तीसरे आधिकारिक विश्व शतरंज चैंपियन। हर समय के सबसे महान शतरंज प्रतिभाओं में गिने जाते हैं — अपनी स्पष्ट शैली और स्थिति की सहज समझ के लिए प्रसिद्ध।")
add("Kapablanka je naučio šah sa svega **četiri godine** gledajući svog oca. Nikada nije pohađao šahovsku školu — sve je naučio sam, igrajući. Već sa 13 godina pobedio je kubanslog prvaka Juana Corzo-a i postao nacionalna senzacija.",
    "Capablanca learned chess at just **four years old** by watching his father. He never attended a chess school — he learned it all himself, by playing. At only 13 he beat the Cuban champion Juan Corzo and became a national sensation.",
    "Capablanca a appris les échecs à seulement **quatre ans** en regardant son père. Il n’a jamais fréquenté d’école d’échecs — il a tout appris seul, en jouant. À seulement 13 ans, il a battu le champion cubain Juan Corzo et est devenu une sensation nationale.",
    "Capablanca lernte Schach mit nur **vier Jahren**, indem er seinem Vater zusah. Er besuchte nie eine Schachschule — er lernte alles selbst durchs Spielen. Mit nur 13 Jahren schlug er den kubanischen Meister Juan Corzo und wurde eine nationale Sensation.",
    "Capablanca imparò gli scacchi a soli **quattro anni** guardando suo padre. Non frequentò mai una scuola di scacchi — imparò tutto da solo, giocando. A soli 13 anni batté il campione cubano Juan Corzo e divenne una sensazione nazionale.",
    "Капабланка научился шахматам всего в **четыре года**, наблюдая за отцом. Он никогда не посещал шахматную школу — всему научился сам, играя. Уже в 13 лет он обыграл кубинского чемпиона Хуана Корсо и стал национальной сенсацией.",
    "卡帕布兰卡年仅**四岁**便通过观看父亲下棋学会了国际象棋。他从未上过棋校——全靠自己在对弈中学会。年仅 13 岁，他便击败古巴冠军胡安·科尔索，成为全国轰动人物。",
    "कापाब्लांका ने महज़ **चार साल** की उम्र में अपने पिता को देखकर शतरंज सीखी। उन्होंने कभी शतरंज स्कूल नहीं गए — सब कुछ खुद खेलकर सीखा। केवल 13 वर्ष की उम्र में उन्होंने क्यूबाई चैंपियन हुआन कोर्ज़ो को हराकर राष्ट्रीय सनसनी बन गए।")
add("U periodu **1916–1924. godine** nije izgubio nijednu partiju. Svetsku šampionsku titulu osvojio je 1921. pobedivši legendarnog Emanuela Laskera, koji je bio prvak čitavih 27 godina.",
    "In the period **1916–1924** he did not lose a single game. He won the world championship title in 1921, defeating the legendary Emanuel Lasker, who had been champion for a full 27 years.",
    "De **1916 à 1924**, il n’a perdu aucune partie. Il a remporté le titre de champion du monde en 1921 en battant le légendaire Emanuel Lasker, champion pendant 27 ans.",
    "Im Zeitraum **1916–1924** verlor er keine einzige Partie. Den Weltmeistertitel gewann er 1921, indem er den legendären Emanuel Lasker besiegte, der ganze 27 Jahre Weltmeister gewesen war.",
    "Nel periodo **1916–1924** non perse nemmeno una partita. Conquistò il titolo mondiale nel 1921 battendo il leggendario Emanuel Lasker, campione per ben 27 anni.",
    "В период **1916–1924 годов** он не проиграл ни одной партии. Титул чемпиона мира он завоевал в 1921 году, победив легендарного Эмануэля Ласкера, который был чемпионом целых 27 лет.",
    "在 **1916–1924 年** 间，他没有输掉一盘棋。1921 年，他击败统治棋坛长达 27 年的传奇人物埃马努埃尔·拉斯克，夺得世界冠军头衔。",
    "**1916–1924** की अवधि में उन्होंने एक भी बाज़ी नहीं हारी। 1921 में उन्होंने पूरे 27 वर्षों तक चैंपियन रहे महान एमानुएल लास्कर को हराकर विश्व चैंपियन का खिताब जीता।")
add("Fotografska preciznost", "Photographic precision", "Une précision photographique", "Fotografische Präzision", "Precisione fotografica", "Фотографическая точность", "照相般的精确", "तस्वीर जैसी सटीकता")
add("Pobedio je jednostavnošću i savršenom tehnikom — ne agresijom.", "He won through simplicity and perfect technique — not aggression.", "Il gagnait par la simplicité et une technique parfaite — non par l’agressivité.", "Er gewann durch Einfachheit und perfekte Technik — nicht durch Aggression.", "Vinceva con la semplicità e una tecnica perfetta — non con l’aggressività.", "Он побеждал простотой и безупречной техникой — не агрессией.", "他靠简洁和完美的技术取胜——而非凶猛进攻。", "उन्होंने सरलता और उत्तम तकनीक से जीत हासिल की — आक्रामकता से नहीं।")
add("Popularizator šaha", "A populariser of chess", "Un vulgarisateur des échecs", "Ein Popularisierer des Schachs", "Un divulgatore degli scacchi", "Популяризатор шахмат", "国际象棋的普及者", "शतरंज के प्रचारक")
add('"Chess Fundamentals" (1921) je pisao upravo za početnike i amatere.',
    'He wrote "Chess Fundamentals" (1921) precisely for beginners and amateurs.',
    'Il a écrit « Chess Fundamentals » (1921) précisément pour les débutants et les amateurs.',
    'Er schrieb „Chess Fundamentals“ (1921) genau für Anfänger und Amateure.',
    'Scrisse "Chess Fundamentals" (1921) proprio per principianti e dilettanti.',
    'Он написал «Chess Fundamentals» (1921) именно для начинающих и любителей.',
    '他写《国际象棋基础》(1921) 正是为了初学者和业余爱好者。',
    'उन्होंने "Chess Fundamentals" (1921) ठीक शुरुआती और शौकिया खिलाड़ियों के लिए लिखी।')
add("Sav sadržaj lekcija preuzet je iz digitalne verzije knjige dostupne na **Project Gutenberg** — neprofitnoj biblioteci knjiga u javnom domenu.",
    "All lesson content is taken from the digital version of the book available on **Project Gutenberg** — a non-profit library of public-domain books.",
    "Tout le contenu des leçons provient de la version numérique du livre disponible sur **Project Gutenberg** — une bibliothèque à but non lucratif d’ouvrages du domaine public.",
    "Alle Lektionsinhalte stammen aus der digitalen Version des Buches, verfügbar bei **Project Gutenberg** — einer gemeinnützigen Bibliothek gemeinfreier Bücher.",
    "Tutti i contenuti delle lezioni provengono dalla versione digitale del libro disponibile su **Project Gutenberg** — una biblioteca senza scopo di lucro di libri di pubblico dominio.",
    "Весь материал уроков взят из цифровой версии книги, доступной на **Project Gutenberg** — некоммерческой библиотеке книг в общественном достоянии.",
    "所有课程内容均取自 **古腾堡计划** 上提供的该书数字版——一个收录公共领域图书的非营利图书馆。",
    "सभी पाठ सामग्री **Project Gutenberg** पर उपलब्ध पुस्तक के डिजिटल संस्करण से ली गई है — सार्वजनिक डोमेन की पुस्तकों का एक गैर-लाभकारी पुस्तकालय।")
add("Možeš je pročitati u celosti besplatno, bez registracije.",
    "You can read it in full for free, without registration.",
    "Vous pouvez le lire intégralement gratuitement, sans inscription.",
    "Du kannst es vollständig kostenlos lesen, ohne Registrierung.",
    "Puoi leggerlo per intero gratuitamente, senza registrazione.",
    "Вы можете прочитать её полностью бесплатно, без регистрации.",
    "你可以免费、无需注册地完整阅读它。",
    "आप इसे बिना पंजीकरण के पूरी तरह मुफ़्त पढ़ सकते हैं।")
add("Zahvalnost", "Acknowledgement", "Remerciements", "Danksagung", "Ringraziamenti", "Благодарность", "致谢", "आभार")
add("Chessko duguje zahvalnost Kapablanki na bezvremenim principima i Project Gutenberg zajednici volontera koji su digitalizovali ovu i hiljade drugih knjiga.",
    "Chessko owes thanks to Capablanca for his timeless principles and to the Project Gutenberg community of volunteers who digitised this and thousands of other books.",
    "Chessko remercie Capablanca pour ses principes intemporels et la communauté de bénévoles de Project Gutenberg qui ont numérisé ce livre et des milliers d’autres.",
    "Chessko schuldet Capablanca Dank für seine zeitlosen Prinzipien und der Project-Gutenberg-Gemeinschaft von Freiwilligen, die dieses und Tausende anderer Bücher digitalisiert haben.",
    "Chessko deve gratitudine a Capablanca per i suoi principi senza tempo e alla comunità di volontari di Project Gutenberg che ha digitalizzato questo e migliaia di altri libri.",
    "Chessko благодарит Капабланку за его вечные принципы и сообщество волонтёров Project Gutenberg, оцифровавших эту и тысячи других книг.",
    "Chessko 感谢卡帕布兰卡留下的永恒原则，也感谢古腾堡计划的志愿者社区，他们数字化了这本书及成千上万本其他书籍。",
    "Chessko, कापाब्लांका के कालजयी सिद्धांतों और Project Gutenberg के स्वयंसेवकों के समुदाय का आभारी है जिन्होंने इस और हज़ारों अन्य पुस्तकों को डिजिटल किया।")

# ── Lesson 4: remaining bullet/box titles ───────────────────────────────────
add("Šah", "Check", "Échec", "Schach", "Scacco", "Шах", "将军", "शह")
add("Ponavljanje pozicije", "Repetition of position", "Répétition de position", "Stellungswiederholung", "Ripetizione di posizione", "Повторение позиции", "重复局面", "स्थिति की पुनरावृत्ति")
add("Nedovoljno materijala", "Insufficient material", "Matériel insuffisant", "Ungenügendes Material", "Materiale insufficiente", "Недостаточно материала", "子力不足", "अपर्याप्त सामग्री")

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
