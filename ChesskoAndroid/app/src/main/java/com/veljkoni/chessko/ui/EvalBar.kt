package com.veljkoni.chessko.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.ui.theme.DS
import kotlin.math.abs
import kotlin.math.pow

@Composable
fun EvalBar(
    evaluation: Double,
    mateIn: Int? = null,
    isFlipped: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Calculate white height fraction using sigmoid (0.0 to 1.0)
    val targetWhiteFraction: Float = when {
        mateIn != null -> {
            if (mateIn > 0) 0.98f else 0.02f
        }
        evaluation >= 100.0 -> 0.98f
        evaluation <= -100.0 -> 0.02f
        else -> {
            val winChance = 1.0 / (1.0 + 10.0.pow(-evaluation / 4.0))
            winChance.toFloat().coerceIn(0.04f, 0.96f)
        }
    }

    // Animate the fraction smoothly
    val animatedWhiteFraction by animateFloatAsState(
        targetValue = targetWhiteFraction,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "evalFraction"
    )

    // Formatted evaluation string
    val evalText: String = when {
        mateIn != null -> if (mateIn > 0) "M$mateIn" else "-M${abs(mateIn)}"
        abs(evaluation) < 0.05 -> "0.0"
        evaluation > 0 -> "+%.1f".format(evaluation)
        else -> "%.1f".format(evaluation)
    }

    // White is on the bottom in standard orientation (unless flipped)
    val topIsWhite = isFlipped
    val topFraction = if (topIsWhite) animatedWhiteFraction else (1f - animatedWhiteFraction)
    val bottomFraction = 1f - topFraction

    // Bela i crna strana su STRANE U IGRI, ne tema -- ostaju fiksne cak i kad
    // se okvir/pozadina oko njih pomere na tokene (vidi CLAUDE.md, "NACELO").
    val whiteColor = Color(0xFFF1F5F9)
    val blackColor = Color(0xFF1E293B)

    val topColor = if (topIsWhite) whiteColor else blackColor
    val bottomColor = if (topIsWhite) blackColor else whiteColor

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(DS.scrim)
            .border(1.dp, DS.line, RoundedCornerShape(3.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(topFraction.coerceAtLeast(0.01f))
                    .background(topColor)
            )

            // Bottom Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(bottomFraction.coerceAtLeast(0.01f))
                    .background(bottomColor)
            )
        }
    }
}
