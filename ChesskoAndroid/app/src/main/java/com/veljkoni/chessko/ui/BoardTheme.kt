package com.veljkoni.chessko.ui

import androidx.compose.ui.graphics.Color

enum class BoardTheme(
    val rawValue: String,
    val label: String,
    val lightColor: Color,
    val darkColor: Color
) {
    CLASSIC("classic", "Klasična", Color(0xFFE9EBDE), Color(0xFF8592AF)),
    FOREST("forest", "Šumska", Color(0xFFECECD7), Color(0xFF739552)),
    WOOD("wood", "Drvo", Color(0xFFF0D9B5), Color(0xFFB58863)),
    CHARCOAL("charcoal", "Ugalj", Color(0xFFE8E8E8), Color(0xFF646464)),
    MIDNIGHT_AURORA("midnight_aurora", "Polarna", Color(0xFFE4E9F2), Color(0xFF1E2530)),
    SAGE_EMERALD("sage_emerald", "Smaragd", Color(0xFFF2F3EC), Color(0xFF3D4F41)),
    WARM_TERRACOTTA("warm_terracotta", "Pesak", Color(0xFFF5EFEB), Color(0xFF9E4A35)),
    CYBER_LAVENDER("cyber_lavender", "Sajber", Color(0xFFECE9FC), Color(0xFF2B1D4F))
}
