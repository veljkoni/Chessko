import Foundation

// Ovaj fajl se kompajlira ISKLJUCIVO u sklopu SwiftPM testnog paketa
// (Package.swift definise CHESSKO_ENGINE_PACKAGE). U aplikaciji pravi
// Loc(_:) dolazi iz Logic/LocalizationManager.swift.
//
// ChessPiece.swift zove Loc(...) za srbName/srbAdjective. Testovima motora
// prevodi nisu bitni, pa je ovde Loc identitet.
#if CHESSKO_ENGINE_PACKAGE
func Loc(_ key: String) -> String { key }
#endif
