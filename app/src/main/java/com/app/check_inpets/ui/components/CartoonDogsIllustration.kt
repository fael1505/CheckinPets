package com.app.check_inpets.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.check_inpets.ui.theme.CheckinPetsTheme
import com.app.check_inpets.ui.theme.CoralAlert

@Composable
fun CartoonDogsIllustration(
    healthScore: Int,
    modifier: Modifier = Modifier,
    hasUncaredAlert: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dogs_idle")
    val wagAngle by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tail_wag"
    )
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_float"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val scale = minOf(w / 360f, h / 170f).coerceAtLeast(0.6f)

            // Soft meadow base (reddish when nobody cared)
            drawRoundRect(
                color = if (hasUncaredAlert) Color(0xFFFAD8D8) else Color(0xFFE9F4EA),
                topLeft = Offset(cx - 145f * scale, h * 0.78f),
                size = Size(290f * scale, 26f * scale),
                cornerRadius = CornerRadius(24f * scale, 24f * scale)
            )

            // Ground shadows
            val shadowColor = if (hasUncaredAlert) Color(0x2EA02828) else Color(0x22000000)
            drawOval(
                color = shadowColor,
                topLeft = Offset(cx - 122f * scale, h * 0.82f),
                size = Size(118f * scale, 14f * scale)
            )
            drawOval(
                color = shadowColor,
                topLeft = Offset(cx + 26f * scale, h * 0.82f),
                size = Size(86f * scale, 14f * scale)
            )

            // 1) Black Dachshund ("Salsicha Preto") on the left
            drawBlackDachshund(
                origin = Offset(cx - 65f * scale, h * 0.57f),
                s = scale,
                wagAngle = wagAngle
            )

            // 2) Light-orange Yorkshire ("Yorkshire Laranja-Claro") on the right
            drawLightOrangeYorkshire(
                origin = Offset(cx + 72f * scale, h * 0.56f),
                s = scale,
                wagAngle = -wagAngle
            )

            if (hasUncaredAlert) {
                val badgeCenter = Offset(cx + 4f * scale, h * 0.24f + floatOffset * scale)
                drawCircle(
                    color = CoralAlert,
                    radius = 12f * scale,
                    center = badgeCenter
                )
                drawLine(
                    color = Color.White,
                    start = Offset(badgeCenter.x, badgeCenter.y - 5.5f * scale),
                    end = Offset(badgeCenter.x, badgeCenter.y + 1.5f * scale),
                    strokeWidth = 2.6f * scale,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5f * scale,
                    center = Offset(badgeCenter.x, badgeCenter.y + 5.2f * scale)
                )
            } else if (healthScore >= 60) {
                drawCuteHeart(
                    center = Offset(cx + 4f * scale, h * 0.24f + floatOffset * scale),
                    radius = 9f * scale,
                    color = Color(0xFFE86A7A)
                )
            }
        }
    }
}

private fun DrawScope.drawBlackDachshund(
    origin: Offset,
    s: Float,
    wagAngle: Float
) {
    val blackFur = Color(0xFF232227)
    val darkFurHighlight = Color(0xFF34323A)
    val tanAccent = Color(0xFFC98A55)
    val outline = Color(0xFF141317)

    // Wagging tail
    rotate(degrees = wagAngle, pivot = Offset(origin.x - 44f * s, origin.y + 2f * s)) {
        val tailPath = Path().apply {
            moveTo(origin.x - 42f * s, origin.y + 4f * s)
            quadraticTo(
                origin.x - 66f * s,
                origin.y - 16f * s,
                origin.x - 58f * s,
                origin.y - 30f * s
            )
        }
        drawPath(
            path = tailPath,
            color = blackFur,
            style = Stroke(width = 7f * s, cap = StrokeCap.Round)
        )
    }

    // Back legs
    drawRoundRect(
        color = Color(0xFF1A191D),
        topLeft = Offset(origin.x - 36f * s, origin.y + 22f * s),
        size = Size(11f * s, 22f * s),
        cornerRadius = CornerRadius(5f * s, 5f * s)
    )
    drawRoundRect(
        color = blackFur,
        topLeft = Offset(origin.x - 28f * s, origin.y + 20f * s),
        size = Size(12f * s, 24f * s),
        cornerRadius = CornerRadius(6f * s, 6f * s)
    )

    // Front legs
    drawRoundRect(
        color = Color(0xFF1A191D),
        topLeft = Offset(origin.x + 12f * s, origin.y + 22f * s),
        size = Size(11f * s, 22f * s),
        cornerRadius = CornerRadius(5f * s, 5f * s)
    )
    drawRoundRect(
        color = blackFur,
        topLeft = Offset(origin.x + 21f * s, origin.y + 20f * s),
        size = Size(12f * s, 24f * s),
        cornerRadius = CornerRadius(6f * s, 6f * s)
    )
    // Cute tan paws
    drawOval(
        color = tanAccent,
        topLeft = Offset(origin.x - 29f * s, origin.y + 39f * s),
        size = Size(14f * s, 7f * s)
    )
    drawOval(
        color = tanAccent,
        topLeft = Offset(origin.x + 21f * s, origin.y + 39f * s),
        size = Size(14f * s, 7f * s)
    )

    // Long sausage body
    drawRoundRect(
        color = blackFur,
        topLeft = Offset(origin.x - 46f * s, origin.y - 4f * s),
        size = Size(82f * s, 32f * s),
        cornerRadius = CornerRadius(16f * s, 16f * s)
    )
    // Subtle back highlight
    drawRoundRect(
        color = darkFurHighlight,
        topLeft = Offset(origin.x - 34f * s, origin.y + 1f * s),
        size = Size(48f * s, 7f * s),
        cornerRadius = CornerRadius(4f * s, 4f * s)
    )
    // Tan chest patch
    drawOval(
        color = tanAccent,
        topLeft = Offset(origin.x + 22f * s, origin.y + 6f * s),
        size = Size(14f * s, 18f * s)
    )

    // Cute terracotta collar
    drawLine(
        color = Color(0xFFD97742),
        start = Offset(origin.x + 18f * s, origin.y - 3f * s),
        end = Offset(origin.x + 34f * s, origin.y + 8f * s),
        strokeWidth = 5f * s,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = Color(0xFFF6C65B),
        radius = 3.5f * s,
        center = Offset(origin.x + 31f * s, origin.y + 10f * s)
    )

    // Head
    val headCenter = Offset(origin.x + 31f * s, origin.y - 18f * s)
    drawCircle(
        color = blackFur,
        radius = 20f * s,
        center = headCenter
    )
    // Long snout/muzzle
    drawRoundRect(
        color = blackFur,
        topLeft = Offset(headCenter.x + 6f * s, headCenter.y - 4f * s),
        size = Size(23f * s, 16f * s),
        cornerRadius = CornerRadius(8f * s, 8f * s)
    )
    // Tan muzzle underside
    drawOval(
        color = tanAccent,
        topLeft = Offset(headCenter.x + 8f * s, headCenter.y + 5f * s),
        size = Size(18f * s, 8f * s)
    )

    // Floppy Dachshund ear
    rotate(degrees = 12f, pivot = Offset(headCenter.x - 8f * s, headCenter.y - 6f * s)) {
        drawOval(
            color = Color(0xFF17161B),
            topLeft = Offset(headCenter.x - 18f * s, headCenter.y - 6f * s),
            size = Size(16f * s, 28f * s)
        )
        drawOval(
            color = darkFurHighlight,
            topLeft = Offset(headCenter.x - 15f * s, headCenter.y - 2f * s),
            size = Size(10f * s, 20f * s)
        )
    }

    // Tan eyebrow dots (signature black & tan dachshund look)
    drawOval(
        color = tanAccent,
        topLeft = Offset(headCenter.x + 3f * s, headCenter.y - 11f * s),
        size = Size(6f * s, 4f * s)
    )

    // Expressive cartoon eye
    drawCircle(
        color = Color.White,
        radius = 4.2f * s,
        center = Offset(headCenter.x + 8f * s, headCenter.y - 4f * s)
    )
    drawCircle(
        color = outline,
        radius = 2.7f * s,
        center = Offset(headCenter.x + 9f * s, headCenter.y - 4f * s)
    )
    drawCircle(
        color = Color.White,
        radius = 1.1f * s,
        center = Offset(headCenter.x + 9.8f * s, headCenter.y - 5f * s)
    )

    // Nose
    drawOval(
        color = Color(0xFF0E0D10),
        topLeft = Offset(headCenter.x + 24f * s, headCenter.y - 5f * s),
        size = Size(7f * s, 5.5f * s)
    )

    // Happy smile & tiny pink tongue
    val smile = Path().apply {
        moveTo(headCenter.x + 14f * s, headCenter.y + 7f * s)
        quadraticTo(
            headCenter.x + 20f * s,
            headCenter.y + 12f * s,
            headCenter.x + 25f * s,
            headCenter.y + 6f * s
        )
    }
    drawPath(smile, color = outline, style = Stroke(width = 2f * s, cap = StrokeCap.Round))
    drawOval(
        color = Color(0xFFF28B99),
        topLeft = Offset(headCenter.x + 17f * s, headCenter.y + 9f * s),
        size = Size(6f * s, 5f * s)
    )
}

private fun DrawScope.drawLightOrangeYorkshire(
    origin: Offset,
    s: Float,
    wagAngle: Float
) {
    val lightOrange = Color(0xFFF4B166)
    val warmGold = Color(0xFFE39344)
    val creamTuft = Color(0xFFFFE3BC)
    val innerEar = Color(0xFFF7C69C)
    val ink = Color(0xFF3B281B)

    // Fluffy tail on right
    rotate(degrees = wagAngle, pivot = Offset(origin.x + 26f * s, origin.y + 4f * s)) {
        drawOval(
            color = warmGold,
            topLeft = Offset(origin.x + 22f * s, origin.y - 12f * s),
            size = Size(14f * s, 22f * s)
        )
    }

    // Short fluffy legs
    drawRoundRect(
        color = warmGold,
        topLeft = Offset(origin.x - 20f * s, origin.y + 20f * s),
        size = Size(12f * s, 23f * s),
        cornerRadius = CornerRadius(6f * s, 6f * s)
    )
    drawRoundRect(
        color = lightOrange,
        topLeft = Offset(origin.x + 8f * s, origin.y + 20f * s),
        size = Size(12f * s, 23f * s),
        cornerRadius = CornerRadius(6f * s, 6f * s)
    )
    drawOval(
        color = creamTuft,
        topLeft = Offset(origin.x - 21f * s, origin.y + 37f * s),
        size = Size(14f * s, 7f * s)
    )
    drawOval(
        color = creamTuft,
        topLeft = Offset(origin.x + 7f * s, origin.y + 37f * s),
        size = Size(14f * s, 7f * s)
    )

    // Compact fluffy body
    drawRoundRect(
        color = lightOrange,
        topLeft = Offset(origin.x - 27f * s, origin.y - 4f * s),
        size = Size(54f * s, 32f * s),
        cornerRadius = CornerRadius(16f * s, 16f * s)
    )
    // Cream chest fluff
    drawOval(
        color = creamTuft,
        topLeft = Offset(origin.x - 16f * s, origin.y + 2f * s),
        size = Size(26f * s, 20f * s)
    )

    // Perky triangular Yorkshire ears
    val headCenter = Offset(origin.x - 2f * s, origin.y - 20f * s)

    val leftEar = Path().apply {
        moveTo(headCenter.x - 20f * s, headCenter.y - 4f * s)
        lineTo(headCenter.x - 24f * s, headCenter.y - 28f * s)
        lineTo(headCenter.x - 6f * s, headCenter.y - 16f * s)
        close()
    }
    val rightEar = Path().apply {
        moveTo(headCenter.x + 20f * s, headCenter.y - 4f * s)
        lineTo(headCenter.x + 24f * s, headCenter.y - 28f * s)
        lineTo(headCenter.x + 6f * s, headCenter.y - 16f * s)
        close()
    }
    drawPath(leftEar, color = warmGold)
    drawPath(rightEar, color = warmGold)

    // Inner pink/peach ears
    val leftInner = Path().apply {
        moveTo(headCenter.x - 18f * s, headCenter.y - 7f * s)
        lineTo(headCenter.x - 20f * s, headCenter.y - 22f * s)
        lineTo(headCenter.x - 9f * s, headCenter.y - 14f * s)
        close()
    }
    val rightInner = Path().apply {
        moveTo(headCenter.x + 18f * s, headCenter.y - 7f * s)
        lineTo(headCenter.x + 20f * s, headCenter.y - 22f * s)
        lineTo(headCenter.x + 9f * s, headCenter.y - 14f * s)
        close()
    }
    drawPath(leftInner, color = innerEar)
    drawPath(rightInner, color = innerEar)

    // Fluffy round face (layered circles for soft fur silhouette)
    drawCircle(color = lightOrange, radius = 22f * s, center = headCenter)
    drawCircle(color = lightOrange, radius = 10f * s, center = Offset(headCenter.x - 16f * s, headCenter.y + 4f * s))
    drawCircle(color = lightOrange, radius = 10f * s, center = Offset(headCenter.x + 16f * s, headCenter.y + 4f * s))

    // Cute Yorkshire topknot & tiny sky-blue bow
    drawOval(
        color = creamTuft,
        topLeft = Offset(headCenter.x - 9f * s, headCenter.y - 30f * s),
        size = Size(18f * s, 14f * s)
    )
    drawCircle(
        color = Color(0xFF4F86C6),
        radius = 3.2f * s,
        center = Offset(headCenter.x, headCenter.y - 22f * s)
    )
    drawOval(
        color = Color(0xFF6BA0DC),
        topLeft = Offset(headCenter.x - 8f * s, headCenter.y - 25f * s),
        size = Size(7f * s, 5.5f * s)
    )
    drawOval(
        color = Color(0xFF6BA0DC),
        topLeft = Offset(headCenter.x + 1f * s, headCenter.y - 25f * s),
        size = Size(7f * s, 5.5f * s)
    )

    // Cream muzzle tuft
    drawOval(
        color = creamTuft,
        topLeft = Offset(headCenter.x - 13f * s, headCenter.y - 1f * s),
        size = Size(26f * s, 16f * s)
    )

    // Bright cartoon eyes
    listOf(-8.5f, 8.5f).forEach { dx ->
        drawCircle(
            color = Color.White,
            radius = 4.3f * s,
            center = Offset(headCenter.x + dx * s, headCenter.y - 5f * s)
        )
        drawCircle(
            color = ink,
            radius = 2.8f * s,
            center = Offset(headCenter.x + dx * s, headCenter.y - 5f * s)
        )
        drawCircle(
            color = Color.White,
            radius = 1.1f * s,
            center = Offset(headCenter.x + (dx - 0.8f) * s, headCenter.y - 6f * s)
        )
    }

    // Little button nose
    drawOval(
        color = ink,
        topLeft = Offset(headCenter.x - 3.5f * s, headCenter.y - 1f * s),
        size = Size(7f * s, 5f * s)
    )

    // Cheerful smile & pink tongue
    val leftMouth = Path().apply {
        moveTo(headCenter.x - 6f * s, headCenter.y + 6f * s)
        quadraticTo(headCenter.x - 3f * s, headCenter.y + 9.5f * s, headCenter.x, headCenter.y + 5f * s)
        quadraticTo(headCenter.x + 3f * s, headCenter.y + 9.5f * s, headCenter.x + 6f * s, headCenter.y + 6f * s)
    }
    drawPath(
        path = leftMouth,
        color = ink,
        style = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawOval(
        color = Color(0xFFF28B99),
        topLeft = Offset(headCenter.x - 3f * s, headCenter.y + 8f * s),
        size = Size(6f * s, 5.5f * s)
    )
}

private fun DrawScope.drawCuteHeart(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.7f)
        cubicTo(
            center.x - radius * 1.4f, center.y - radius * 0.2f,
            center.x - radius * 0.7f, center.y - radius * 1.2f,
            center.x, center.y - radius * 0.4f
        )
        cubicTo(
            center.x + radius * 0.7f, center.y - radius * 1.2f,
            center.x + radius * 1.4f, center.y - radius * 0.2f,
            center.x, center.y + radius * 0.7f
        )
        close()
    }
    drawPath(path, color = color)
}

@Preview(showBackground = true)
@Composable
private fun CartoonDogsPreview() {
    CheckinPetsTheme {
        CartoonDogsIllustration(healthScore = 90)
    }
}
