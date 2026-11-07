package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.TranslucentGlossWhite
import com.example.ui.theme.WarmYellowBorder
import com.example.util.HapticFeedbackController
import com.example.util.TactilePulse

/**
 * Smooth Glossy Translucent Surface Card — Non-gradient, Non-dark-themed,
 * featuring crisp 1dp border, subtle elevation, and generous internal whitespace.
 */
@Composable
fun GlossyTranslucentCard(
    modifier: Modifier = Modifier,
    containerColor: Color = TranslucentGlossWhite,
    borderColor: Color = CrispBlueBorder,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * High-contrast Blue or Yellow Glossy Pill Badge with clean typography.
 */
@Composable
fun GlossyStatusPill(
    text: String,
    isYellowHighlight: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isYellowHighlight) BrightYellowSurface else SkyBlueSoft
    val borderColor = if (isYellowHighlight) WarmYellowBorder else CrispBlueBorder
    val contentColor = if (isYellowHighlight) DeepAmberInk else CobaltBluePrimary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Tactile Haptic Button with Royal Blue or Solar Yellow solid styling (no gradients).
 */
@Composable
fun TactilePrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isYellowAccent: Boolean = false,
    enabled: Boolean = true,
    pulseType: TactilePulse = TactilePulse.TAP_LIGHT,
    testTagName: String = "tactile_primary_btn"
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val bgColor = if (isYellowAccent) SolarYellowAccent else CobaltBluePrimary
    val fgColor = if (isYellowAccent) DeepNavyInk else PureWhite

    Button(
        onClick = {
            HapticFeedbackController.perform(context, haptics, pulseType)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .testTag(testTagName),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = fgColor,
            disabledContainerColor = SkyBlueSoft,
            disabledContentColor = SlateBlueMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Compact Telemetry Metric Tile for FPS, Quads, Depth Confidence, and Lightrays.
 */
@Composable
fun TelemetryStatTile(
    label: String,
    value: String,
    isHighlightedYellow: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isHighlightedYellow) BrightYellowSurface else PureWhite,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlightedYellow) WarmYellowBorder else CrispBlueBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isHighlightedYellow) DeepAmberInk else SlateBlueMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Selectable Chip with built-in haptic feedback and Blue/Yellow/White active states.
 */
@Composable
fun TactileFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String = "filter_chip"
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                onClick()
            }
            .testTag(testTagName),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) SolarYellowAccent else PureWhite,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) CobaltBluePrimary else CrispBlueBorder
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = DeepNavyInk
            )
        }
    }
}
