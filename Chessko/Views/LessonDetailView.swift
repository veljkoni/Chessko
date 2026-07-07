import SwiftUI

// MARK: - Lesson Detail View

struct LessonDetailView: View {

    let lesson: LessonInfo
    var pieceExplorer: LearnViewModel   // used only in Lesson 1

    var body: some View {
        GeometryReader { geo in
            ZStack {
                AppBackgroundView()

                ScrollView(.vertical, showsIndicators: true) {
                    VStack(alignment: .leading, spacing: 0) {
                        lessonHeader

                        switch lesson.id {
                        case 1: lesson1
                        case 2: lesson2
                        case 3: lesson3
                        case 4: lesson4
                        default: EmptyView()
                        }
                    }
                    .frame(width: geo.size.width)
                    .padding(.bottom, 40)
                }
            }
        }
        .navigationTitle("Lekcija \(lesson.id)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.appBackground, for: .navigationBar)
    }

    // MARK: - Lesson Header

    private var lessonHeader: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(lesson.accentColor.opacity(0.18))
                    .frame(width: 60, height: 60)
                Image(systemName: lesson.systemIcon)
                    .font(.title2.weight(.medium))
                    .foregroundStyle(lesson.accentColor)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey(lesson.title))
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.primary)
                Text(LocalizedStringKey(lesson.subtitle))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 20)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Lesson 1: Tabla, figure i kretanje
    // ─────────────────────────────────────────────────────────────────────────

    @ViewBuilder private var lesson1: some View {

        L_Box(icon: "quote.opening", color: lesson.accentColor,
              title: "Kapablanka piše",
              text: "\"Prva stvar koju učenik treba da uradi jeste da upozna snagu figura. Ovo se najlakše postiže učenjem kako se brzo postiže šah-mat.\"")
            .padding(.top, 4)

        L_Para("Šah se igra na tabli od **64 polja** naizmenično svetle i tamne boje. Uvek zapamti: **donje desno polje mora biti svetlo**. Svaki igrač počinje sa **16 figura**.")
            .padding(.horizontal, 20).padding(.bottom, 16)

        // Interactive piece explorer
        L_SectionHeader(icon: "hand.point.up.left.fill", title: "Istraži figure interaktivno", color: lesson.accentColor)

        VStack(spacing: 10) {
            piecePicker
            BoardView(
                board:            pieceExplorer.board,
                isFlipped:        false,
                selectedPosition: pieceExplorer.selectedPosition,
                legalMoves:       pieceExplorer.legalMoves,
                lastMove:         pieceExplorer.lastMove,
                animatingPiece:   pieceExplorer.animatingPiece,
                flyingCapture:    pieceExplorer.flyingCapture,
                playerColor:      .white,
                isPlayerTurn:     true,
                onTap:            { pieceExplorer.tap(position: $0) }
            )
            .padding(.horizontal, 4)

            let scenarios = pieceExplorer.availableScenarios
            if !scenarios.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 6) {
                        Image(systemName: "star.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.yellow)
                        Text("Specijalna pravila")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                        Text("— izaberi i istraži na tabli")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.5))
                    }
                    HStack(spacing: 10) {
                        ForEach(scenarios, id: \.self) { s in
                            let active = pieceExplorer.activeScenario == s
                            Button {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    pieceExplorer.toggleScenario(s)
                                }
                            } label: {
                                HStack(spacing: 5) {
                                    Image(systemName: active ? "checkmark.circle.fill" : "circle")
                                        .font(.caption.weight(.semibold))
                                    Text(s.label)
                                        .font(.subheadline.weight(.semibold))
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.8)
                                }
                                .foregroundStyle(active ? .black : .white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 9)
                                .background(
                                    active ? Color.yellow : Color.white.opacity(0.15),
                                    in: RoundedRectangle(cornerRadius: 10)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .strokeBorder(
                                            active ? Color.yellow : Color.white.opacity(0.2),
                                            lineWidth: 1
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(14)
                .background(.yellow.opacity(0.07), in: RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(.yellow.opacity(0.25), lineWidth: 1)
                )
            }

            HStack(spacing: 5) {
                Image(systemName: "hand.point.up.left").font(.caption2)
                Text("Tapni figuru da je promeniš · Tapni polje da je premestiš").font(.caption)
            }
            .foregroundStyle(.white.opacity(0.35))
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16).padding(.bottom, 24)

        // Individual pieces
        L_SectionHeader(icon: "square.grid.2x2.fill", title: "Kako se svaka figura kreće", color: lesson.accentColor)

        lesson1Pieces
        lesson1Rokada

        // Piece values (Capablanca)
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "scalemass.fill", title: "Relativna vrednost figura", color: lesson.accentColor)

        L_Para("Kapablanka kaže: vrednost nije fiksna — menja se zavisno od pozicije. Ipak, ove brojke služe kao vodič u razmeni figura.")
            .padding(.horizontal, 20).padding(.bottom, 8)

        L_PieceValueTable()

        L_Bullet(icon: "info.circle.fill", color: lesson.accentColor, title: "Dva lovca su gotovo uvek jača od dva skakača",
                 text: "Lovac je naročito snažan kada postoje pioni na obe strane table i kada su linije otvorene.")
        L_Bullet(icon: "info.circle.fill", color: lesson.accentColor, title: "Top vredi kao skakač plus dva piona",
                 text: "Ili lovac plus dva piona. Zato se razmena figure za topa bez kompenzacije naziva \"gubljenje kvaliteta\".")
        L_Bullet(icon: "crown.fill", color: lesson.accentColor, title: "Kralj u završnici postaje napadačka figura",
                 text: "U otvaranju i središnjici Kralj je isključivo odbrambena figura. U završnici, kada nestane većina figura, mora aktivno da učestvuje u borbi.")

        // Elementary mates
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "checkmark.seal.fill", title: "Elementarni matovi", color: lesson.accentColor)

        L_Para("Pre nego što naučiš otvaranja i strategiju, nauči ove tri osnovne mat pozicije. Za svaki od njih potrebna je saradnja Kralja!")
            .padding(.horizontal, 20).padding(.bottom, 8)

        MateExerciseCard(
            fen:   "8/8/4k3/8/4K3/8/8/R7 w - - 0 1",
            title: "Vežba 1 — Kralj + Top",
            hint:  "Oteraj crnog Kralja na ivicu table. Top i Kralj moraju da sarađuju!",
            icon:  "rectangle.portrait.fill",
            color: .blue
        )
        .padding(.horizontal, 16).padding(.bottom, 16)

        MateExerciseCard(
            fen:   "4k3/8/8/8/8/8/8/2B1KB2 w - - 0 1",
            title: "Vežba 2 — Kralj + dva Lovca",
            hint:  "Oteraj Kralja ne samo na ivicu već i u ugao iste boje kao tvoji lovci.",
            icon:  "rhombus.fill",
            color: .purple
        )
        .padding(.horizontal, 16).padding(.bottom, 16)

        MateExerciseCard(
            fen:   "4k3/8/8/8/8/8/8/3QK3 w - - 0 1",
            title: "Vežba 3 — Kralj + Dama",
            hint:  "Najlakše! Dama odmah sužava prostor. Pazi na pat!",
            icon:  "crown.fill",
            color: .yellow
        )
        .padding(.horizontal, 16).padding(.bottom, 8)
    }

    private var piecePicker: some View {
        let pieces: [PieceType] = [.pawn, .knight, .bishop, .rook, .queen, .king]
        let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 3)
        return LazyVGrid(columns: columns, spacing: 6) {
            ForEach(pieces, id: \.self) { piece in
                let sel = pieceExplorer.selectedPieceType == piece
                Button {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        pieceExplorer.select(piece: piece)
                    }
                } label: {
                    VStack(spacing: 3) {
                        PieceImageView(piece: ChessPiece(type: piece, color: .white))
                            .frame(width: 28, height: 28)
                        Text(piece.srbName)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(sel ? .white : .white.opacity(0.6))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(sel ? Color.white.opacity(0.18) : Color.white.opacity(0.04),
                                in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 14))
    }

    @ViewBuilder private var lesson1Pieces: some View {
        L_PieceRow(type: .pawn, name: "Pion (Pešak)", count: "× 8")
        L_Para("Na početku imaš 8 piona — oni su tvoja \"pešadija\". Kapablanka napominje: **dobitak jednog piona je najmanji materijalni dobitak i često je dovoljan za pobedu**.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "arrow.up", color: .blue, title: "Kretanje",
                 text: "Ide isključivo napred, po jedno polje. Na prvom potezu može da preskoči dva polja. **Pioni ne mogu da idu unazad.**")
        L_Bullet(icon: "arrow.up.left.and.arrow.up.right", color: .blue, title: "Napad",
                 text: "Jede protivničke figure isključivo po dijagonali jedno polje unapred.")
        L_Box(icon: "crown.fill", color: .yellow,
              title: "Promocija",
              text: "Ako pion stigne do poslednjeg reda — pretvara se u bilo koju figuru, najčešće Damu. Ovo je moćno oružje u završnici!")
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .rook, name: "Top (Kula)", count: "× 2")
        L_Para("Stoji u uglovima table na početku. Efikasan je tek na otvorenim linijama — koliko god radi sa pioima koji blokiraju put, toliko je ograničen.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "arrow.up.and.down.and.arrow.left.and.right", color: .blue, title: "Kretanje",
                 text: "Kreće se po pravim linijama (napred-nazad, levo-desno) koliko god polja želi. Zajedno, dva Topa su neznatno jača od Dame.")
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .bishop, name: "Lovac (Iber)", count: "× 2")
        L_Para("Jedan lovac uvek ostaje na belim, drugi na crnim poljima. Kapablanka smatra da je **u većini pozicija Lovac vredniji od Skakača**.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "arrow.up.right.and.arrow.up.left", color: .blue, title: "Kretanje",
                 text: "Kreće se isključivo po dijagonalama. Slabost: \"Topov pion koji promovira na polju suprotne boje od Lovca\" najčešće vodi remiju umesto pobede.")
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .knight, name: "Skakač (Konj)", count: "× 2")
        L_Para("Jedina figura koja preskače ostale. Snažan je u **zatvorenim pozicijama** — kada su linije blokirane pionima. Na ivici table gubi na snazi.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "l.joystick.fill", color: .blue, title: "Kretanje",
                 text: "Kreće se u obliku slova \"L\": dva polja pravo pa jedno u stranu.")
        L_Box(icon: "star.fill", color: .yellow,
              title: "Jedinstven!",
              text: "Jedina figura koja može da preskače druge figure — i svoje i protivničke!")
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .queen, name: "Kraljica (Dama)", count: "× 1")
        L_Para("Stoji na polju **svoje boje** — bela Dama na belom polju, crna na crnom. Najmoćnija figura, ali ne treba je odmah izvoditi u otvaranju.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "arrow.up.and.down.and.arrow.left.and.right", color: .blue, title: "Kretanje",
                 text: "Kombinuje kretanje Topa i Lovca — kreće se u svim pravcima, koliko god polja želi.")
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .king, name: "Kralj", count: "× 1")
        L_Para("Najvažnija figura — njen gubitak znači kraj igre. U otvaranju je **pasivna odbrambena figura**, ali u završnici postaje moćan napadač.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "dot.square.fill", color: .blue, title: "Kretanje",
                 text: "Kreće se samo jedno polje u bilo kom pravcu. **Ne sme da stane na napadnuto polje!**")
    }

    @ViewBuilder private var lesson1Rokada: some View {
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "arrow.left.arrow.right", title: "Poseban potez: Rokada", color: lesson.accentColor)
        L_Para("Jednom u partiji možeš pomeriti **dve figure istovremeno** — Kralja i Topa. Kralj skoči dva polja ka Topu, a Top preskače Kralja i staje pored njega. Ovo služi da skloniš Kralja na sigurno i ubaciš Top u igru.")
            .padding(.horizontal, 20).padding(.bottom, 8)
        L_Box(icon: "exclamationmark.triangle.fill", color: .yellow,
              title: "Uslovi za rokadu",
              text: "Ni Kralj ni Top se do tada **nisu pomerali** · Između njih **nema nijedne figure** · Kralj se ne nalazi u šahu i ne prolazi kroz napadnuto polje")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Lesson 2: Početak igre (Otvaranja)
    // ─────────────────────────────────────────────────────────────────────────

    @ViewBuilder private var lesson2: some View {

        L_Box(icon: "quote.opening", color: lesson.accentColor,
              title: "Kapablanka piše",
              text: "\"Najvažnija stvar u otvaranju je brzo razviti figure. Nijedno parče ne treba pomeriti više od jednom pre nego što je razvoj završen, osim ako je to apsolutno neophodno.\"")
            .padding(.top, 4)

        L_Para("U šahu **Beli uvek igra prvi** i zbog toga ima blagu inicijalnu prednost. Zadatak oba igrača u otvaranju je isti: što brže dovesti figure u igru i zauzeti kontrolu nad centrom.")
            .padding(.horizontal, 20).padding(.bottom, 16)

        L_SectionHeader(icon: "checkmark.seal.fill", title: "Zlatna pravila otvaranja", color: lesson.accentColor)

        L_NumberedRule(number: 1, color: lesson.accentColor,
                       title: "Razvijaj figure brzo",
                       text: "Kapablanka savetuje: skakače razvijaj pre lovaca. Ne pomeraj istu figuru dva puta u otvaranju ako nisi primoran. Svaki potez treba da razvija novu figuru ili kontroliše centar.")
        L_NumberedRule(number: 2, color: lesson.accentColor,
                       title: "Kontroliši centar",
                       text: "Četiri centralna polja (e4, d4, e5, d5) su najvažnija na tabli. Ko vlada centrom ima više prostora za manevar. Kapablanka: \"Nijedan žestok napad ne može uspeti bez kontrole bar dva centralna polja.\"")
        L_NumberedRule(number: 3, color: lesson.accentColor,
                       title: "Zaštiti kralja — uradi rokadu!",
                       text: "Rokadu odigraj što pre je moguće. Kralj na otvorenom je laka meta. Kapablanka sam uvek rokira rano i preporučuje isto svim igračima, posebno početnicima.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "exclamationmark.triangle.fill", title: "Tipične greške u otvaranju", color: lesson.accentColor)

        L_Bullet(icon: "xmark.circle.fill", color: .red, title: "Prerano izvođenje Dame",
                 text: "Dama je snažna, ali ako je izvedeš rano, protivnik je napada pešacima i figurama — a svaki napad na Damu znači izgubljeni tempo jer mora da beži.")
        L_Bullet(icon: "xmark.circle.fill", color: .red, title: "Pasivna odbrana pionima",
                 text: "\"Filipidorski\" stil — odmah igrati P-d6 kao odgovor na e4 — daje protivniku slobodan razvoj i prostranstvo. Kapablanka pokazuje kako beli tada lako gradi superiornu poziciju.")
        L_Bullet(icon: "xmark.circle.fill", color: .red, title: "Zakasnela rokada",
                 text: "Svaki potez bez rokade kada su linije otvorene je rizik. Protivnik može otvoriti igru i napasti tvog kralja pre nego što se skloni.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 16)
        L_SectionHeader(icon: "book.fill", title: "Poznata otvaranja", color: lesson.accentColor)

        lesson2Openings
    }

    @ViewBuilder private var lesson2Openings: some View {
        L_Para("Odigraj svaki potez belih na tabli — crni odgovara automatski po teorijskoj liniji.")
            .padding(.horizontal, 20).padding(.bottom, 12)

        OpeningExerciseCard(line: OpeningLine(
            name: "Španska partija (Ruy Lopez)",
            uciMoves: ["e2e4", "e7e5", "g1f3", "b8c6", "f1b5"],
            hint: "1.e4 e5 2.Sf3 Sc6 3.Lb5 — Kapablankova omiljena",
            icon: "crown.fill",
            accentColor: lesson.accentColor
        ))
        .padding(.horizontal, 16).padding(.bottom, 16)

        OpeningExerciseCard(line: OpeningLine(
            name: "Italijanska partija",
            uciMoves: ["e2e4", "e7e5", "g1f3", "b8c6", "f1c4"],
            hint: "1.e4 e5 2.Sf3 Sc6 3.Lc4 — lovac nišani tačku f7",
            icon: "flame.fill",
            accentColor: .orange
        ))
        .padding(.horizontal, 16).padding(.bottom, 16)

        OpeningExerciseCard(line: OpeningLine(
            name: "Sicilijanska odbrana",
            uciMoves: ["e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4"],
            hint: "1.e4 c5 2.Sf3 d6 3.d4 cxd4 4.Sxd4 — asimetrična borba",
            icon: "shield.fill",
            accentColor: .purple
        ))
        .padding(.horizontal, 16).padding(.bottom, 8)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Lesson 3: Središnjica
    // ─────────────────────────────────────────────────────────────────────────

    @ViewBuilder private var lesson3: some View {

        L_Box(icon: "quote.opening", color: lesson.accentColor,
              title: "Kapablanka piše",
              text: "\"Idealna središnjica: sve figure su bačene u napad kao masa, koordinirajući se sa mašinskom preciznošću. Cilj svakog majstora je da postigne upravo takvu harmoniju.\"")
            .padding(.top, 4)

        L_Para("Kada su figure izvedene i kraljevi na sigurnom, počinje **središnjica** — najkreativniji i najkompleksniji deo šaha.")
            .padding(.horizontal, 20).padding(.bottom, 16)

        L_SectionHeader(icon: "flag.fill", title: "Inicijativa", color: lesson.accentColor)

        L_Para("Kapablanka objašnjava: Beli ima inicijalnu prednost zbog prvog poteza. Ovu prednost treba **čuvati što duže** — predaj je samo ako za uzvrat dobijaš materijal ili bolju poziciju.")
            .padding(.horizontal, 20).padding(.bottom, 8)

        L_Bullet(icon: "arrow.forward.circle.fill", color: lesson.accentColor, title: "Ko napadá, dikta tempo",
                 text: "Igrač sa inicijativom bira gde i kako da napadne. Protivnik mora da reaguje umesto da sprovodi sopstveni plan.")
        L_Bullet(icon: "exclamationmark.circle.fill", color: lesson.accentColor, title: "Ne napadaj bez sigurnosti",
                 text: "Kapablanka upozorava: direktan napad na Kralja nikada ne treba voditi do krajnosti ako nema apsolutne sigurnosti da će uspeti. Neuspeo napad znači katastrofu.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "scalemass.fill", title: "Vrednosti figura", color: lesson.accentColor)
        L_Para("U središnjici, vrednost figure zavisi od pozicije. Uvek pazi šta razmenjuješ!")
            .padding(.horizontal, 20).padding(.bottom, 8)
        L_PieceValueTable()

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "bolt.fill", title: "Osnovna taktička motiva", color: lesson.accentColor)

        L_Box(icon: "tuningfork", color: lesson.accentColor,
              title: "Viljuška (Rašlje)",
              text: "Jedna figura napadne **dve protivničke figure istovremeno**. Protivnik može da spasi samo jednu. Skakači su posebno opasni za viljuške — skaču na polje odakle napadaju Damu i Topa u isto vreme.")
        L_Box(icon: "link", color: lesson.accentColor,
              title: "Vezivanje (Pin)",
              text: "Napadneš figuru koja **ne sme da se pomeri** jer bi time otkrila vrednu figuru iza nje (Kralja ili Damu). Vezana figura je praktično izolovana iz igre — iskoristi to!")
        L_Box(icon: "arrow.triangle.2.circlepath", color: lesson.accentColor,
              title: "Otkriveni napad",
              text: "Pomeriš jednu figuru i time otkriješ napad druge figure iza nje na protivnikovu vrednu figuru. Posebno opasan kada je i sama figura koja se pomera napadačka.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "person.2.fill", title: "Koordinacija figura", color: lesson.accentColor)

        L_Para("Kapablanka stalno naglašava: figure moraju da rade zajedno kao tim.")
            .padding(.horizontal, 20).padding(.bottom, 8)

        L_Bullet(icon: "arrow.up.and.down", color: lesson.accentColor, title: "Topovi traže otvorene linije",
                 text: "Postavi ih na otvorenu kolonu ili sedmi red. Top zatvoren iza sopstvenih piona je pasivna figura.")
        L_Bullet(icon: "circle.fill", color: lesson.accentColor, title: "Skakači najjači u centru",
                 text: "\"Skakač na ivici table je loš skakač\" — kaže Kapablanka. U centru kontroliše čak 8 polja, na ivici samo 2-4.")
        L_Bullet(icon: "arrow.up.right", color: lesson.accentColor, title: "Lovci vole otvorene dijagonale",
                 text: "Lovac koji blokira sopstveni pion je ograničen. Pione postavljaj na polja **suprotne boje** od svog lovca.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "chart.line.uptrend.xyaxis", title: "Prednost od jednog piona", color: lesson.accentColor)

        L_Box(icon: "info.circle.fill", color: lesson.accentColor,
              title: "Kapablankovo zlatno pravilo",
              text: "\"Dobitak jednog piona između jednako jakih igrača najčešće znači pobedu.\" Ne potcenjuj pion — u završnici je on često odlučujući. Svaka sitna prednost se akumulira!")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK: - Lesson 4: Završnica
    // ─────────────────────────────────────────────────────────────────────────

    @ViewBuilder private var lesson4: some View {

        L_Box(icon: "quote.opening", color: lesson.accentColor,
              title: "Kapablanka piše",
              text: "\"Pre nego što se boriš za pobedu u otvaranju ili središnjici, moraš savladati završnicu. Onaj ko ne poznaje završnicu ne može biti jak šahista.\"")
            .padding(.top, 4)

        L_Para("Završnica počinje kada su sa table nestale najvažnije figure i ostanu Kraljevi sa pešacima i možda jednom-dve lake figure.")
            .padding(.horizontal, 20).padding(.bottom, 16)

        L_SectionHeader(icon: "crown.fill", title: "Kralj postaje napadač", color: lesson.accentColor)

        L_Para("Ovo je **najveća promena** u završnici. Kralj koji je celu partiju bežao sada mora aktivno da napadá.")
            .padding(.horizontal, 20).padding(.bottom, 8)

        L_Bullet(icon: "crown.fill", color: lesson.accentColor, title: "Dovedi Kralja u centar odmah",
                 text: "Čim oseti da je završnica blizu, počni da pomičeš Kralja ka centru table. Centralni Kralj dominira nad marginalnim.")
        L_Bullet(icon: "arrow.up.circle.fill", color: lesson.accentColor, title: "Pioni su budući Kraljevi",
                 text: "Svaki pion koji stigne do poslednjeg reda postaje Dama (ili druga figura). Ovo je glavni cilj u pešačkim završnicama.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "arrow.up.circle.fill", title: "Pravilo o promociji piona", color: lesson.accentColor)

        L_Para("Kapablanka objašnjava ovo pravilo jasno i precizno:")
            .padding(.horizontal, 20).padding(.bottom, 8)

        L_Box(icon: "checkmark.circle.fill", color: lesson.accentColor,
              title: "Ključno pravilo",
              text: "Da bi pešačka završnica bila pobednička, **Kralj mora biti ispred svog piona** sa barem jednim praznim poljem između njih. Ako je protivnički Kralj direktno ispred piona — igra je remi!")
        L_Bullet(icon: "arrow.up", color: lesson.accentColor, title: "Napreduj Kralja, ne piona",
                 text: "Kapablanka savetuje: napreduj Kralja koliko je moguće a da ne ugrožavaš piona. Piona pomiči tek kada je neophodno za njegovu zaštitu.")
        L_Bullet(icon: "ruler.fill", color: lesson.accentColor, title: "Tajno oružje — \"Opozicija\"",
                 text: "Kada su dva Kralja međusobno licem u lice sa neparnim brojem polja između, igrač koji je **prethodno poterao** ima prednost. Zove se opozicija — i ključna je za sve pešačke završnice.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "bolt.fill", title: "Kardinalno načelo", color: lesson.accentColor)

        L_Box(icon: "star.fill", color: .yellow,
              title: "Jedno drži dvoje — Kapablankovo načelo",
              text: "\"Pion koji drži dva protivnička piona je jedno od glavnih oruđa majstora.\" Ako tvoj pion blokira dva protivnička, ti si faktički figuru ispred — iskoristi tu prednost na drugoj strani table!")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "scalemass.fill", title: "Lovac vs. Skakač u završnici", color: lesson.accentColor)

        L_Bullet(icon: "arrow.up.right", color: lesson.accentColor, title: "Lovac je jači kada su pioni na obe strane",
                 text: "Lovac može istovremeno da napada pione na oba krila zahvaljujući dometu. Skakač je spor i ne može da stigne svuda.")
        L_Bullet(icon: "l.joystick.fill", color: lesson.accentColor, title: "Skakač je jači u zatvorenim pozicijama",
                 text: "Kada su pioni blokirani i pozicija zatvorena, skakač je bolji jer može da preskoče pione i stigne do idealnog polja.")
        L_Box(icon: "exclamationmark.triangle.fill", color: lesson.accentColor,
              title: "Slabost lovca — Topov pion",
              text: "Ako tvoj pion ide do h8 (ili a8) i to polje je suprotne boje od tvog lovca, protivnik drži ugao i igra je remi! Kapablanka ovo posebno ističe kao izvor mnogih propuštenih pobeda.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "trophy.fill", title: "Šah-Mat i Remi", color: lesson.accentColor)

        L_Box(icon: "exclamationmark.triangle.fill", color: .yellow,
              title: "Šah",
              text: "Situacija kada je Kralj napadnut. Igrač **mora** da se odbrani — pomeri kralja, pojede napadača, ili postavi štit između.")
        L_Box(icon: "xmark.shield.fill", color: .red,
              title: "Šah-Mat — Kraj igre",
              text: "Kralj je napadnut, a nema nijedan legalan način odbrane. Partija se završava ovde — Kralj se nikada zapravo ne jede.")
        L_Box(icon: "exclamationmark.2", color: lesson.accentColor,
              title: "Pat — Noćna mora pobednika!",
              text: "Igrač na potezu **nije u šahu**, ali nema nijedan legalan potez. Odmah je remi! Ovo je najopasnija greška u završnici — pretvoriti pobedničku poziciju u remi jednim lošim potezom.")
        L_Bullet(icon: "arrow.clockwise", color: lesson.accentColor, title: "Ponavljanje pozicije",
                 text: "Ako se ista pozicija ponovi **tri puta**, može se tražiti remi.")
        L_Bullet(icon: "minus.circle.fill", color: lesson.accentColor, title: "Nedovoljno materijala",
                 text: "Samo Kraljevi, ili Kralj + Lovac/Skakač protiv Kralja — nije moguće dati mat. Automatski remi.")

        // ─── Mini finalni test ───
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "trophy.fill", title: "Mini finalni test", color: lesson.accentColor)

        L_Para("Primeni sve što si naučio! Reši 5 zadataka — mat u najmanji broj poteza. Svaki koristi drugu kombinaciju figura.")
            .padding(.horizontal, 20).padding(.bottom, 12)

        // Zadatak 1 — Dama daje mat na zadnjoj liniji (mat u 1)
        MatePuzzleCard(
            fen:         "6k1/5ppp/8/8/8/8/3Q4/4R1K1 w - - 0 1",
            moves:       ["d2d8"],
            title:       "Zadatak 1 — Dama na zadnjoj liniji",
            hint:        "Crni Kralj je zarobljen. Dama ima slobodan put...",
            icon:        "crown.fill",
            accentColor: .yellow,
            mateIn:      1
        )
        .padding(.horizontal, 16)

        // Zadatak 2 — Top daje mat na zadnjoj liniji (mat u 1)
        MatePuzzleCard(
            fen:         "6k1/5ppp/8/1R6/8/8/8/6K1 w - - 0 1",
            moves:       ["b5b8"],
            title:       "Zadatak 2 — Top na 8. liniji",
            hint:        "Pešaci blokiraju sopstvenog Kralja. Top pronalazi put...",
            icon:        "rectangle.portrait.fill",
            accentColor: .blue,
            mateIn:      1
        )
        .padding(.horizontal, 16)

        // Zadatak 3 — Žrtva Topa, Dama daje mat (mat u 2)
        MatePuzzleCard(
            fen:         "2r3k1/5ppp/8/8/Q7/8/8/4R1K1 w - - 0 1",
            moves:       ["e1e8", "c8e8", "a4e8"],
            title:       "Zadatak 3 — Žrtva Topa!",
            hint:        "Top ide na e8 i daje šah. Crni Top mora da uzme — a onda Dama?",
            icon:        "rectangle.portrait.fill",
            accentColor: .orange,
            mateIn:      2
        )
        .padding(.horizontal, 16)

        // Zadatak 4 — Lovac tera Kralja, Top daje mat (mat u 2)
        MatePuzzleCard(
            fen:         "5k2/5ppp/8/4B3/8/8/8/4R1K1 w - - 0 1",
            moves:       ["e5d6", "f8g8", "e1e8"],
            title:       "Zadatak 4 — Lovac + Top",
            hint:        "Lovac daje šah i tera Kralja na g8. Zašto je to pogubno?",
            icon:        "rhombus.fill",
            accentColor: .green,
            mateIn:      2
        )
        .padding(.horizontal, 16)

        // Zadatak 5 — SparkChess: Žrtva Dame, Lovac daje mat (mat u 2) — iz prave partije
        MatePuzzleCard(
            fen:         "r1bq2r1/b4pk1/p1pp1p2/1p2pP2/1P2P1PB/3P4/1PPQ2P1/R3K2R w KQ - 0 1",
            moves:       ["d2h6", "g7h6", "h4f6"],
            title:       "Zadatak 5 (težak) — Žrtva Dame, Lovac mat",
            hint:        "Greet – Hanley, Liverpool 2008. Dama se žrtvuje na h6. Zašto Kralj mora da uzme?",
            icon:        "crown.fill",
            accentColor: .purple,
            mateIn:      2
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 8)

        // ─── O autoru ───
        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "person.fill", title: "O autoru", color: lesson.accentColor)

        L_Box(icon: "person.fill", color: lesson.accentColor,
              title: "Hoze Raul Kapablanka (1888–1942)",
              text: "Kubanski šahista, treći zvanični svetski prvak u šahu. Važi za jednog od najvećih šahiskih genija svih vremena — poznat po kristalno čistom stilu igre i intuitivnom razumevanju pozicije.")

        L_Para("Kapablanka je naučio šah sa svega **četiri godine** gledajući svog oca. Nikada nije pohađao šahovsku školu — sve je naučio sam, igrajući. Već sa 13 godina pobedio je kubanslog prvaka Juana Corzo-a i postao nacionalna senzacija.")
            .padding(.horizontal, 20).padding(.bottom, 12)

        L_Para("U periodu **1916–1924. godine** nije izgubio nijednu partiju. Svetsku šampionsku titulu osvojio je 1921. pobedivši legendarnog Emanuela Laskera, koji je bio prvak čitavih 27 godina.")
            .padding(.horizontal, 20).padding(.bottom, 12)

        L_Bullet(icon: "eye.fill", color: lesson.accentColor, title: "Fotografska preciznost",
                 text: "Pobedio je jednostavnošću i savršenom tehnikom — ne agresijom.")
        L_Bullet(icon: "person.2.fill", color: lesson.accentColor, title: "Popularizator šaha",
                 text: "\"Chess Fundamentals\" (1921) je pisao upravo za početnike i amatere.")

        Divider().background(.white.opacity(0.1)).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "text.book.closed.fill", title: "Izvor: Project Gutenberg", color: lesson.accentColor)

        L_Para("Sav sadržaj lekcija preuzet je iz digitalne verzije knjige dostupne na **Project Gutenberg** — neprofitnoj biblioteci knjiga u javnom domenu.")
            .padding(.horizontal, 20).padding(.bottom, 12)

        L_Box(icon: "globe", color: lesson.accentColor,
              title: "gutenberg.org/ebooks/33870",
              text: "Možeš je pročitati u celosti besplatno, bez registracije.")

        L_Box(icon: "heart.fill", color: lesson.accentColor,
              title: "Zahvalnost",
              text: "Chessko duguje zahvalnost Kapablanki na bezvremenim principima i Project Gutenberg zajednici volontera koji su digitalizovali ovu i hiljade drugih knjiga.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Reusable Lesson Components
// ─────────────────────────────────────────────────────────────────────────────

private struct L_SectionHeader: View {
    let icon: String
    let title: String
    let color: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(color)
            Text(LocalizedStringKey(title))
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 10)
    }
}

private struct L_Para: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(LocalizedStringKey(text))
            .font(.subheadline)
            .foregroundStyle(.primary.opacity(0.85))
            .fixedSize(horizontal: false, vertical: true)
    }
}

private struct L_Bullet: View {
    let icon: String
    let color: Color
    let title: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(color)
                .frame(width: 18)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                Text(LocalizedStringKey(title))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(LocalizedStringKey(text))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }
}

private struct L_Box: View {
    let icon: String
    let color: Color
    let title: String
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(color)
                Text(LocalizedStringKey(title))
                    .font(.caption.weight(.bold))
                    .foregroundStyle(color)
            }
            Text(LocalizedStringKey(text))
                .font(.subheadline)
                .foregroundStyle(.primary.opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(color.opacity(0.3), lineWidth: 1))
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }
}

private struct L_PieceRow: View {
    let type: PieceType
    let name: String
    let count: String

    var body: some View {
        HStack(spacing: 12) {
            PieceImageView(piece: ChessPiece(type: type, color: .white))
                .frame(width: 32, height: 32)
            Text(LocalizedStringKey(name))
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            Spacer()
            Text(count)
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(Color.primary.opacity(0.06), in: Capsule())
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 6)
    }
}

private struct L_NumberedRule: View {
    let number: Int
    let color: Color
    let title: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Circle().fill(color.opacity(0.2)).frame(width: 32, height: 32)
                Text("\(number)")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(color)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey(title))
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                Text(LocalizedStringKey(text))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 14)
    }
}

private struct L_OpeningCard: View {
    let name: String
    let accentColor: Color
    let items: [(String, String)]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(name)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                HStack(alignment: .top, spacing: 8) {
                    Text(item.0 + ":")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(accentColor)
                        .fixedSize()
                    Text(item.1)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(accentColor.opacity(0.2), lineWidth: 1))
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }
}

private struct L_PieceValueTable: View {
    private let rows: [(PieceType, String, String)] = [
        (.pawn,   "Pešak",    "1"),
        (.knight, "Skakač",   "3"),
        (.bishop, "Lovac",    "3"),
        (.rook,   "Top",      "5"),
        (.queen,  "Dama",     "9"),
        (.king,   "Kralj",    "∞"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { idx, row in
                HStack(spacing: 12) {
                    PieceImageView(piece: ChessPiece(type: row.0, color: .white))
                        .frame(width: 28, height: 28)
                    Text(LocalizedStringKey(row.1))
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                    Spacer()
                    Text(LocalizedStringKey(row.2 == "∞" ? "∞" : "\(row.2) bod\(row.2 == "1" ? "" : row.2 == "9" ? "ova" : "a")"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(row.2 == "∞" ? Color.yellow : .primary)
                }
                .padding(.vertical, 9)
                .padding(.horizontal, 14)
                .background(idx % 2 == 0 ? Color.primary.opacity(0.03) : Color.clear)
            }
        }
        .frame(maxWidth: .infinity)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.primary.opacity(0.08), lineWidth: 1))
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
    }
}

// MARK: - Mate Puzzle Card

struct MatePuzzleCard: View {

    let mateIn: Int         // 1 or 2 (for badge display)

    @State private var vm: OpeningExerciseViewModel

    init(fen: String, moves: [String], title: String, hint: String,
         icon: String, accentColor: Color, mateIn: Int) {
        self.mateIn = mateIn
        let line = OpeningLine(
            name: title,
            uciMoves: moves,
            hint: hint,
            icon: icon,
            accentColor: accentColor,
            solvedMessage: "Sjajno! Mat pronađen! 🏆",
            wrongMessage:  "Nije to — traži pravi ključni potez!",
            playingPrompt: mateIn == 1 ? "Pronađi mat u 1 potezu!" : "Pronađi ključni potez!",
            startFEN: fen
        )
        _vm = State(initialValue: OpeningExerciseViewModel(line: line))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            // Header
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(vm.line.accentColor.opacity(0.2))
                        .frame(width: 32, height: 32)
                    Image(systemName: vm.line.icon)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(vm.line.accentColor)
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(LocalizedStringKey(vm.line.name))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                        Text("Mat u \(mateIn)")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(vm.line.accentColor)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(vm.line.accentColor.opacity(0.18), in: Capsule())
                    }
                    Text(LocalizedStringKey(vm.line.hint))
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.55))
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
                if vm.phase == .solved {
                    Image(systemName: "trophy.fill")
                        .foregroundStyle(.yellow)
                        .font(.title3)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            // Board
            BoardView(
                board:            vm.gameState.board,
                isFlipped:        false,
                selectedPosition: vm.selectedPosition,
                legalMoves:       vm.legalMovesForSelected,
                lastMove:         vm.lastMove,
                animatingPiece:   vm.animatingPiece,
                flyingCapture:    nil,
                playerColor:      .white,
                isPlayerTurn:     vm.isPlayerTurn,
                onTap:            { vm.tap(position: $0) }
            )
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .padding(.horizontal, 10)

            // Status bar
            HStack(spacing: 6) {
                Text(vm.statusMessage)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(puzzleStatusColor)
                    .lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 0)
                Button {
                    withAnimation(.easeInOut(duration: 0.15)) { vm.reset() }
                } label: {
                    Label("Ponovo", systemImage: "arrow.counterclockwise")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.6))
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(.white.opacity(0.1), in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(puzzleBorderColor, lineWidth: 1.5)
        )
        .animation(.easeInOut(duration: 0.2), value: vm.phase)
    }

    private var puzzleStatusColor: Color {
        switch vm.phase {
        case .solved:    return .yellow
        case .wrongMove: return .red
        case .playing:   return .white.opacity(0.65)
        }
    }

    private var puzzleBorderColor: Color {
        switch vm.phase {
        case .solved:    return .yellow.opacity(0.6)
        case .wrongMove: return .red.opacity(0.5)
        case .playing:   return vm.line.accentColor.opacity(0.3)
        }
    }
}

// MARK: - Opening Exercise Card

struct OpeningExerciseCard: View {

    let line: OpeningLine

    @State private var vm: OpeningExerciseViewModel

    init(line: OpeningLine) {
        self.line = line
        _vm = State(initialValue: OpeningExerciseViewModel(line: line))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            // Header
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(line.accentColor.opacity(0.2))
                        .frame(width: 32, height: 32)
                    Image(systemName: line.icon)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(line.accentColor)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(LocalizedStringKey(line.name))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(LocalizedStringKey(line.hint))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Spacer(minLength: 0)
                if vm.phase == .solved {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .font(.title3)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            // Board
            BoardView(
                board:           vm.gameState.board,
                isFlipped:       false,
                selectedPosition: vm.selectedPosition,
                legalMoves:      vm.legalMovesForSelected,
                lastMove:        vm.lastMove,
                animatingPiece:  vm.animatingPiece,
                flyingCapture:   nil,
                playerColor:     .white,
                isPlayerTurn:    vm.isPlayerTurn,
                onTap:           { vm.tap(position: $0) }
            )
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .padding(.horizontal, 10)

            // Status bar
            HStack(spacing: 6) {
                // Progress pills
                let whiteMoveCount = (line.uciMoves.count + 1) / 2
                HStack(spacing: 3) {
                    ForEach(0..<whiteMoveCount, id: \.self) { i in
                        let done = i * 2 < vm.movePointer
                        RoundedRectangle(cornerRadius: 2)
                            .fill(done ? line.accentColor : Color.primary.opacity(0.12))
                            .frame(width: 18, height: 4)
                    }
                }

                Text(vm.statusMessage)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(openingStatusColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                Spacer(minLength: 0)

                Button {
                    withAnimation(.easeInOut(duration: 0.15)) { vm.reset() }
                } label: {
                    Label("Ponovo", systemImage: "arrow.counterclockwise")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06), in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(openingBorderColor, lineWidth: 1)
        )
    }

    private var openingStatusColor: Color {
        switch vm.phase {
        case .solved:    return .green
        case .wrongMove: return .red
        case .playing:   return .secondary
        }
    }

    private var openingBorderColor: Color {
        switch vm.phase {
        case .solved:    return .green.opacity(0.5)
        case .wrongMove: return .red.opacity(0.4)
        case .playing:   return line.accentColor.opacity(0.3)
        }
    }
}

// MARK: - Mate Exercise Card

struct MateExerciseCard: View {

    let fen:   String
    let title: String
    let hint:  String
    let icon:  String
    let color: Color

    @State private var vm: MateExerciseViewModel

    init(fen: String, title: String, hint: String, icon: String, color: Color) {
        self.fen   = fen
        self.title = title
        self.hint  = hint
        self.icon  = icon
        self.color = color
        _vm = State(initialValue: MateExerciseViewModel(fen: fen, title: title, hint: hint))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            // Header
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(color.opacity(0.2))
                        .frame(width: 32, height: 32)
                    Image(systemName: icon)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(color)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(LocalizedStringKey(title))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(LocalizedStringKey(hint))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Spacer(minLength: 0)
                if vm.isSolved {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .font(.title3)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            // Board
            BoardView(
                board:          vm.gameState.board,
                isFlipped:      false,
                selectedPosition: vm.selectedPosition,
                legalMoves:     vm.legalMovesForSelected,
                lastMove:       vm.lastMove,
                animatingPiece: vm.animatingPiece,
                flyingCapture:  nil,
                playerColor:    .white,
                isPlayerTurn:   vm.isPlayerTurn,
                onTap:          { vm.tap(position: $0) }
            )
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .padding(.horizontal, 10)

            // Status bar
            HStack(spacing: 6) {
                if vm.isThinking {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .scaleEffect(0.7)
                        .tint(color)
                }
                Text(vm.statusMessage)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(statusColor)
                Spacer(minLength: 0)
                Button {
                    withAnimation(.easeInOut(duration: 0.15)) { vm.reset() }
                } label: {
                    Label("Ponovo", systemImage: "arrow.counterclockwise")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06), in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(borderColor, lineWidth: 1)
        )
    }

    private var statusColor: Color {
        if vm.isSolved { return .green }
        if case .draw = vm.gameState.status { return .orange }
        if case .checkmate(let c) = vm.gameState.status, c == .white { return .red }
        if case .check = vm.gameState.status { return .orange }
        return .secondary
    }

    private var borderColor: Color {
        if vm.isSolved { return .green.opacity(0.5) }
        return color.opacity(0.3)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        LessonDetailView(lesson: LessonInfo.all[0], pieceExplorer: LearnViewModel())
    }
}
