package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.example.ui.theme.TranslucentHudOverlay
import com.example.ui.theme.WarmYellowBorder
import com.example.util.HapticFeedbackController
import com.example.util.TactilePulse

/**
 * Microsoft Fluent 2 Mica / Google Material 3 Expressive Surface Section.
 */
@Composable
fun GlossyTranslucentCard(
    modifier: Modifier = Modifier,
    containerColor: Color = TranslucentGlossWhite,
    borderColor: Color = CrispBlueBorder.copy(alpha = 0.45f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(0.6.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Compact Icon + Badge Status Pill.
 */
@Composable
fun GlossyStatusPill(
    text: String,
    isYellowHighlight: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isYellowHighlight) BrightYellowSurface else SkyBlueSoft
    val contentColor = if (isYellowHighlight) DeepAmberInk else CobaltBluePrimary

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Icon-First Circular Action Button (replaces bulky text-heavy buttons across the app).
 * Supports a prominent primary Material Extended icon with an optional micro-badge below or inside.
 */
@Composable
fun TactileIconOrbButton(
    icon: ImageVector,
    contentDesc: String,
    badgeText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    isYellowAccent: Boolean = false,
    isSoftSurface: Boolean = false,
    enabled: Boolean = true,
    pulseType: TactilePulse = TactilePulse.TAP_LIGHT,
    testTagName: String = "tactile_orb_btn"
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val bgColor = when {
        !enabled -> SkyBlueSoft.copy(alpha = 0.5f)
        isYellowAccent -> SolarYellowAccent
        isSoftSurface -> SkyBlueSoft
        else -> CobaltBluePrimary
    }
    val fgColor = when {
        !enabled -> SlateBlueMuted
        isYellowAccent -> DeepNavyInk
        isSoftSurface -> CobaltBluePrimary
        else -> PureWhite
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = {
                if (enabled) {
                    HapticFeedbackController.perform(context, haptics, pulseType)
                    onClick()
                }
            },
            enabled = enabled,
            shape = CircleShape,
            color = bgColor,
            shadowElevation = if (isSoftSurface) 0.dp else 4.dp,
            border = if (isYellowAccent) {
                BorderStroke(2.dp, CobaltBluePrimary)
            } else if (isSoftSurface) {
                BorderStroke(1.dp, CrispBlueBorder)
            } else {
                BorderStroke(2.dp, SolarYellowAccent)
            },
            modifier = Modifier
                .size(orbSize.coerceAtLeast(48.dp))
                .semantics(mergeDescendants = true) {
                    contentDescription = contentDesc
                }
                .testTag(testTagName)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = fgColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Compact Icon-First Pill Action (kept for compatibility where a compact icon+short label pill is used).
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
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTagName),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = fgColor,
            disabledContainerColor = SkyBlueSoft,
            disabledContentColor = SlateBlueMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 1.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Icon-Driven Segment / Mode Chip using Material Icons Extended.
 */
@Composable
fun TactileFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    iconOnly: Boolean = false,
    testTagName: String = "filter_chip"
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) SolarYellowAccent else SkyBlueSoft.copy(alpha = 0.65f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chip_bg"
    )
    val textColor = if (selected) DeepNavyInk else CobaltBluePrimary

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable {
                HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
            }
            .testTag(testTagName),
        shape = CircleShape,
        color = bgColor,
        border = if (selected) BorderStroke(1.2.dp, CobaltBluePrimary) else null
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (iconOnly) 12.dp else 14.dp,
                vertical = 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = label,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
                if (!iconOnly && label.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            if (!iconOnly) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Pure Circular Icon Button for floating camera & 3D stage HUD rails (no wide text labels).
 */
@Composable
fun CircularHudIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeYellow: Boolean = false,
    onClick: () -> Unit,
    testTagName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val bgColor = when {
        active && activeYellow -> SolarYellowAccent
        active -> CobaltBluePrimary
        else -> TranslucentHudOverlay
    }
    val contentColor = when {
        active && activeYellow -> DeepNavyInk
        active -> PureWhite
        else -> CobaltBluePrimary
    }

    Surface(
        onClick = {
            HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
            onClick()
        },
        shape = CircleShape,
        color = bgColor,
        shadowElevation = 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (active && activeYellow) WarmYellowBorder else CrispBlueBorder.copy(alpha = 0.75f)
        ),
        modifier = modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
            }
            .testTag(testTagName)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Google Home / Microsoft Fluent 3-column inline metric strip with optional Material Icons Extended.
 */
@Composable
fun FluentMetricStrip(
    metrics: List<Triple<String, String, Boolean>>,
    icons: List<ImageVector>? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        metrics.forEachIndexed { index, (label, value, highlight) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val metricIcon = icons?.getOrNull(index)
                    if (metricIcon != null) {
                        Icon(
                            imageVector = metricIcon,
                            contentDescription = label,
                            tint = if (highlight) DeepAmberInk else CobaltBluePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (highlight) DeepAmberInk else CobaltBluePrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateBlueMuted,
                    maxLines = 1
                )
            }
            if (index < metrics.lastIndex) {
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight(0.65f)
                        .width(1.dp),
                    color = CrispBlueBorder
                )
            }
        }
    }
}

@Composable
fun TelemetryStatTile(
    label: String,
    value: String,
    isHighlightedYellow: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isHighlightedYellow) BrightYellowSurface else SkyBlueSoft.copy(alpha = 0.55f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isHighlightedYellow) DeepAmberInk else SlateBlueMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = if (isHighlightedYellow) DeepNavyInk else CobaltBluePrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FluentHairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 0.8.dp,
        color = CrispBlueBorder.copy(alpha = 0.55f)
    )
}
