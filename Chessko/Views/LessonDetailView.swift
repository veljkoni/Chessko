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
                .safeAreaPadding(.bottom, 24)
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
                    .font(.dsTitle.weight(.medium))
                    .foregroundStyle(lesson.accentColor)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(Loc(lesson.title))
                    .font(.dsHeading.weight(.bold))
                    .foregroundStyle(.primary)
                Text(Loc(lesson.subtitle))
                    .font(.dsBody)
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

        LessonPieceExplorer(viewModel: pieceExplorer)

        // Individual pieces
        L_SectionHeader(icon: "square.grid.2x2.fill", title: "Kako se svaka figura kreće", color: lesson.accentColor)

        lesson1Pieces
        lesson1Rokada

        // Piece values (Capablanca)
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "scalemass.fill", title: "Relativna vrednost figura", color: lesson.accentColor)

        L_Para("Kapablanka kaže: vrednost nije fiksna — menja se zavisno od pozicije. Ipak, ove brojke služe kao vodič u razmeni figura.")
            .padding(.horizontal, 20).padding(.bottom, 8)

        L_PieceValueTable(rows: lessonPieceValueRows)

        L_Bullet(icon: "info.circle.fill", color: lesson.accentColor, title: "Dva lovca su gotovo uvek jača od dva skakača",
                 text: "Lovac je naročito snažan kada postoje pioni na obe strane table i kada su linije otvorene.")
        L_Bullet(icon: "info.circle.fill", color: lesson.accentColor, title: "Top vredi kao skakač plus dva piona",
                 text: "Ili lovac plus dva piona. Zato se razmena figure za topa bez kompenzacije naziva \"gubljenje kvaliteta\".")
        L_Bullet(icon: "crown.fill", color: lesson.accentColor, title: "Kralj u završnici postaje napadačka figura",
                 text: "U otvaranju i središnjici Kralj je isključivo odbrambena figura. U završnici, kada nestane većina figura, mora aktivno da učestvuje u borbi.")

        // Elementary mates
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "checkmark.seal.fill", title: "Elementarni matovi", color: lesson.accentColor)

        L_Para("Pre nego što naučiš otvaranja i strategiju, nauči ove tri osnovne mat pozicije. Za svaki od njih potrebna je saradnja Kralja!")
            .padding(.horizontal, 20).padding(.bottom, 8)

        MateExerciseCard(
            fen:   "8/8/4k3/8/4K3/8/8/R7 w - - 0 1",
            title: "Vežba 1 — Kralj + Top",
            hint:  "Oteraj crnog Kralja na ivicu table. Top i Kralj moraju da sarađuju!",
            icon:  "rectangle.portrait.fill",
            color: DS.accent
        )
        .padding(.horizontal, 16).padding(.bottom, 16)

        MateExerciseCard(
            fen:   "4k3/8/8/8/8/8/8/2B1KB2 w - - 0 1",
            title: "Vežba 2 — Kralj + dva Lovca",
            hint:  "Oteraj Kralja ne samo na ivicu već i u ugao iste boje kao tvoji lovci.",
            icon:  "rhombus.fill",
            color: DS.accent
        )
        .padding(.horizontal, 16).padding(.bottom, 16)

        MateExerciseCard(
            fen:   "4k3/8/8/8/8/8/8/3QK3 w - - 0 1",
            title: "Vežba 3 — Kralj + Dama",
            hint:  "Najlakše! Dama odmah sužava prostor. Pazi na pat!",
            icon:  "crown.fill",
            color: DS.accent
        )
        .padding(.horizontal, 16).padding(.bottom, 8)
    }

    @ViewBuilder private var lesson1Pieces: some View {
        L_PieceRow(type: .pawn, name: "Pion (Pešak)", count: "× 8")
        L_Para("Na početku imaš 8 piona — oni su tvoja \"pešadija\". Kapablanka napominje: **dobitak jednog piona je najmanji materijalni dobitak i često je dovoljan za pobedu**.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "arrow.up", color: DS.accent, title: "Kretanje",
                 text: "Ide isključivo napred, po jedno polje. Na prvom potezu može da preskoči dva polja. **Pioni ne mogu da idu unazad.**")
        L_Bullet(icon: "arrow.up.left.and.arrow.up.right", color: DS.accent, title: "Napad",
                 text: "Jede protivničke figure isključivo po dijagonali jedno polje unapred.")
        L_Box(icon: "crown.fill", color: DS.warning,
              title: "Promocija",
              text: "Ako pion stigne do poslednjeg reda — pretvara se u bilo koju figuru, najčešće Damu. Ovo je moćno oružje u završnici!")
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .rook, name: "Top (Kula)", count: "× 2")
        L_Para("Stoji u uglovima table na početku. Efikasan je tek na otvorenim linijama — koliko god radi sa pioima koji blokiraju put, toliko je ograničen.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "arrow.up.and.down.and.arrow.left.and.right", color: DS.accent, title: "Kretanje",
                 text: "Kreće se po pravim linijama (napred-nazad, levo-desno) koliko god polja želi. Zajedno, dva Topa su neznatno jača od Dame.")
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .bishop, name: "Lovac (Iber)", count: "× 2")
        L_Para("Jedan lovac uvek ostaje na belim, drugi na crnim poljima. Kapablanka smatra da je **u većini pozicija Lovac vredniji od Skakača**.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "arrow.up.right.and.arrow.up.left", color: DS.accent, title: "Kretanje",
                 text: "Kreće se isključivo po dijagonalama. Slabost: \"Topov pion koji promovira na polju suprotne boje od Lovca\" najčešće vodi remiju umesto pobede.")
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .knight, name: "Skakač (Konj)", count: "× 2")
        L_Para("Jedina figura koja preskače ostale. Snažan je u **zatvorenim pozicijama** — kada su linije blokirane pionima. Na ivici table gubi na snazi.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "l.joystick.fill", color: DS.accent, title: "Kretanje",
                 text: "Kreće se u obliku slova \"L\": dva polja pravo pa jedno u stranu.")
        L_Box(icon: "star.fill", color: DS.warning,
              title: "Jedinstven!",
              text: "Jedina figura koja može da preskače druge figure — i svoje i protivničke!")
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .queen, name: "Kraljica (Dama)", count: "× 1")
        L_Para("Stoji na polju **svoje boje** — bela Dama na belom polju, crna na crnom. Najmoćnija figura, ali ne treba je odmah izvoditi u otvaranju.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "arrow.up.and.down.and.arrow.left.and.right", color: DS.accent, title: "Kretanje",
                 text: "Kombinuje kretanje Topa i Lovca — kreće se u svim pravcima, koliko god polja želi.")
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)

        L_PieceRow(type: .king, name: "Kralj", count: "× 1")
        L_Para("Najvažnija figura — njen gubitak znači kraj igre. U otvaranju je **pasivna odbrambena figura**, ali u završnici postaje moćan napadač.")
            .padding(.horizontal, 20).padding(.bottom, 4)
        L_Bullet(icon: "dot.square.fill", color: DS.accent, title: "Kretanje",
                 text: "Kreće se samo jedno polje u bilo kom pravcu. **Ne sme da stane na napadnuto polje!**")
    }

    @ViewBuilder private var lesson1Rokada: some View {
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "arrow.left.arrow.right", title: "Poseban potez: Rokada", color: lesson.accentColor)
        L_Para("Jednom u partiji možeš pomeriti **dve figure istovremeno** — Kralja i Topa. Kralj skoči dva polja ka Topu, a Top preskače Kralja i staje pored njega. Ovo služi da skloniš Kralja na sigurno i ubaciš Top u igru.")
            .padding(.horizontal, 20).padding(.bottom, 8)
        L_Box(icon: "exclamationmark.triangle.fill", color: DS.warning,
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

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "exclamationmark.triangle.fill", title: "Tipične greške u otvaranju", color: lesson.accentColor)

        L_Bullet(icon: "xmark.circle.fill", color: DS.danger, title: "Prerano izvođenje Dame",
                 text: "Dama je snažna, ali ako je izvedeš rano, protivnik je napada pešacima i figurama — a svaki napad na Damu znači izgubljeni tempo jer mora da beži.")
        L_Bullet(icon: "xmark.circle.fill", color: DS.danger, title: "Pasivna odbrana pionima",
                 text: "\"Filipidorski\" stil — odmah igrati P-d6 kao odgovor na e4 — daje protivniku slobodan razvoj i prostranstvo. Kapablanka pokazuje kako beli tada lako gradi superiornu poziciju.")
        L_Bullet(icon: "xmark.circle.fill", color: DS.danger, title: "Zakasnela rokada",
                 text: "Svaki potez bez rokade kada su linije otvorene je rizik. Protivnik može otvoriti igru i napasti tvog kralja pre nego što se skloni.")

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 16)
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
            accentColor: DS.accent
        ))
        .padding(.horizontal, 16).padding(.bottom, 16)

        OpeningExerciseCard(line: OpeningLine(
            name: "Sicilijanska odbrana",
            uciMoves: ["e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4"],
            hint: "1.e4 c5 2.Sf3 d6 3.d4 cxd4 4.Sxd4 — asimetrična borba",
            icon: "shield.fill",
            accentColor: DS.accent
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

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "scalemass.fill", title: "Vrednosti figura", color: lesson.accentColor)
        L_Para("U središnjici, vrednost figure zavisi od pozicije. Uvek pazi šta razmenjuješ!")
            .padding(.horizontal, 20).padding(.bottom, 8)
        L_PieceValueTable(rows: lessonPieceValueRows)

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
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

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "person.2.fill", title: "Koordinacija figura", color: lesson.accentColor)

        L_Para("Kapablanka stalno naglašava: figure moraju da rade zajedno kao tim.")
            .padding(.horizontal, 20).padding(.bottom, 8)

        L_Bullet(icon: "arrow.up.and.down", color: lesson.accentColor, title: "Topovi traže otvorene linije",
                 text: "Postavi ih na otvorenu kolonu ili sedmi red. Top zatvoren iza sopstvenih piona je pasivna figura.")
        L_Bullet(icon: "circle.fill", color: lesson.accentColor, title: "Skakači najjači u centru",
                 text: "\"Skakač na ivici table je loš skakač\" — kaže Kapablanka. U centru kontroliše čak 8 polja, na ivici samo 2-4.")
        L_Bullet(icon: "arrow.up.right", color: lesson.accentColor, title: "Lovci vole otvorene dijagonale",
                 text: "Lovac koji blokira sopstveni pion je ograničen. Pione postavljaj na polja **suprotne boje** od svog lovca.")

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
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

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
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

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "bolt.fill", title: "Kardinalno načelo", color: lesson.accentColor)

        L_Box(icon: "star.fill", color: DS.warning,
              title: "Jedno drži dvoje — Kapablankovo načelo",
              text: "\"Pion koji drži dva protivnička piona je jedno od glavnih oruđa majstora.\" Ako tvoj pion blokira dva protivnička, ti si faktički figuru ispred — iskoristi tu prednost na drugoj strani table!")

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "scalemass.fill", title: "Lovac vs. Skakač u završnici", color: lesson.accentColor)

        L_Bullet(icon: "arrow.up.right", color: lesson.accentColor, title: "Lovac je jači kada su pioni na obe strane",
                 text: "Lovac može istovremeno da napada pione na oba krila zahvaljujući dometu. Skakač je spor i ne može da stigne svuda.")
        L_Bullet(icon: "l.joystick.fill", color: lesson.accentColor, title: "Skakač je jači u zatvorenim pozicijama",
                 text: "Kada su pioni blokirani i pozicija zatvorena, skakač je bolji jer može da preskoče pione i stigne do idealnog polja.")
        L_Box(icon: "exclamationmark.triangle.fill", color: lesson.accentColor,
              title: "Slabost lovca — Topov pion",
              text: "Ako tvoj pion ide do h8 (ili a8) i to polje je suprotne boje od tvog lovca, protivnik drži ugao i igra je remi! Kapablanka ovo posebno ističe kao izvor mnogih propuštenih pobeda.")

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
        L_SectionHeader(icon: "trophy.fill", title: "Šah-Mat i Remi", color: lesson.accentColor)

        L_Box(icon: "exclamationmark.triangle.fill", color: DS.warning,
              title: "Šah",
              text: "Situacija kada je Kralj napadnut. Igrač **mora** da se odbrani — pomeri kralja, pojede napadača, ili postavi štit između.")
        L_Box(icon: "xmark.shield.fill", color: DS.danger,
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
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
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
            accentColor: DS.accent,
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
            accentColor: DS.accent,
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
            accentColor: DS.accent,
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
            accentColor: DS.accent,
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
            accentColor: DS.accent,
            mateIn:      2
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 8)

        // ─── O autoru ───
        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
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

        Divider().background(DS.line).padding(.horizontal, 20).padding(.vertical, 12)
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

// Privremeno: redovi tabele vrednosti dok lekcije 1 i 3 još žive u Swift-u.
// Task 5 ih briše zajedno sa telima lekcija — u JSON-u su već zapisani.
private let lessonPieceValueRows: [PieceValueRow] = [
    PieceValueRow(piece: "pawn",   name: "Pešak",  value: "1"),
    PieceValueRow(piece: "knight", name: "Skakač", value: "3"),
    PieceValueRow(piece: "bishop", name: "Lovac",  value: "3"),
    PieceValueRow(piece: "rook",   name: "Top",    value: "5"),
    PieceValueRow(piece: "queen",  name: "Dama",   value: "9"),
    PieceValueRow(piece: "king",   name: "Kralj",  value: "∞"),
]

// MARK: - Preview

#Preview {
    NavigationStack {
        LessonDetailView(lesson: LessonInfo.all[0], pieceExplorer: LearnViewModel())
    }
}
