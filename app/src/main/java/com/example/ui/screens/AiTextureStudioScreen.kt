package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlossyStatusPill
import com.example.ui.components.GlossyTranslucentCard
import com.example.ui.components.TactileFilterChip
import com.example.ui.components.TactileIconOrbButton
import com.example.ui.theme.AlertRedSolid
import com.example.ui.theme.AlertRedSurface
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PorcelainWhite
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.WarmYellowBorder
import com.example.ui.viewmodel.AiTextureUiState
import com.example.util.TactilePulse

@Composable
fun AiTextureStudioScreen(
    aiTextureState: AiTextureUiState,
    currentAlbedoBitmap: Bitmap,
    currentNoiseBitmap: Bitmap,
    onGenerateNewTexture: (String, Boolean) -> Unit,
    onEditExistingTexture: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var promptText by remember {
        mutableStateOf("Seamless hand-painted ceramic glaze texture with cobalt blue geometric inlays and subtle gold wear for a 3D game asset")
    }
    var targetNoiseChannel by remember { mutableStateOf(false) }

    val quickPrompts = listOf(
        "Brushed titanium sci-fi hull plating with yellow hazard stripes",
        "Weathered Nordic basalt rock surface with fine moss crevices",
        "Seamless high-frequency cellular perlin micro-roughness noise map",
        "Polished porcelain ceramic glaze with royal blue artisan trim"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PorcelainWhite)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Editorial Google Gemini / Microsoft Copilot Studio Header
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlossyStatusPill(
                    text = "GEMINI-NANO-BANANA-2.1",
                    icon = Icons.Rounded.AutoFixHigh
                )
                GlossyStatusPill(
                    text = if (targetNoiseChannel) "CHANNEL: NOISE MAP" else "CHANNEL: ALBEDO MAP",
                    isYellowHighlight = true,
                    icon = if (targetNoiseChannel) Icons.Rounded.Grain else Icons.Rounded.Palette
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "AI PBR Texture & Noise Studio",
                style = MaterialTheme.typography.headlineMedium,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Synthesize seamless PBR maps or refine your scanned _albedo.png and _noise.png textures.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlueMuted
            )
        }

        // 2. Live Side-by-Side Texture Canvas Showcase
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(165.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(PureWhite)
            ) {
                Image(
                    bitmap = currentAlbedoBitmap.asImageBitmap(),
                    contentDescription = "Current Albedo Texture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = CircleShape,
                    color = if (!targetNoiseChannel) SolarYellowAccent else PureWhite.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = DeepNavyInk,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Albedo (_albedo.png)",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(165.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(PureWhite)
            ) {
                Image(
                    bitmap = currentNoiseBitmap.asImageBitmap(),
                    contentDescription = "Current Noise Texture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = CircleShape,
                    color = if (targetNoiseChannel) SolarYellowAccent else PureWhite.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Grain,
                            contentDescription = null,
                            tint = DeepNavyInk,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Noise (_noise.png)",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. AI Generation / Editing Status Banner
        when (val state = aiTextureState) {
            is AiTextureUiState.Loading -> {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = BrightYellowSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = CobaltBluePrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = state.modeLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = DeepNavyInk,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Baking directly onto active 3D mesh UV coordinates...",
                                style = MaterialTheme.typography.bodySmall,
                                color = DeepAmberInk
                            )
                        }
                    }
                }
            }

            is AiTextureUiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = AlertRedSurface,
                    border = BorderStroke(1.dp, AlertRedSolid),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "AI Error",
                            tint = AlertRedSolid,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertRedSolid,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("ai_texture_error_text")
                        )
                    }
                }
            }

            is AiTextureUiState.Success -> {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = BrightYellowSurface,
                    border = BorderStroke(1.dp, WarmYellowBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "Generated AI Texture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Texture Applied",
                                    tint = CobaltBluePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Applied to ${state.appliedToSlot}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeepNavyInk,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.prompt,
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepAmberInk,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            is AiTextureUiState.Idle -> Unit
        }

        // 4. Icon-First Google Gemini / Microsoft Copilot Prompt Composer Surface
        GlossyTranslucentCard {
            // Channel Target Pills with Material Extended Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TactileFilterChip(
                    label = "Target: Albedo Map",
                    selected = !targetNoiseChannel,
                    leadingIcon = Icons.Rounded.Palette,
                    onClick = { targetNoiseChannel = false },
                    modifier = Modifier.weight(1f),
                    testTagName = "select_albedo_channel_chip"
                )
                TactileFilterChip(
                    label = "Target: Noise Map",
                    selected = targetNoiseChannel,
                    leadingIcon = Icons.Rounded.Grain,
                    onClick = { targetNoiseChannel = true },
                    modifier = Modifier.weight(1f),
                    testTagName = "select_noise_channel_chip"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Suggestion Pills with Sparkle Icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEachIndexed { idx, presetPrompt ->
                    TactileFilterChip(
                        label = "Preset #${idx + 1}",
                        selected = promptText == presetPrompt,
                        leadingIcon = Icons.Rounded.AutoAwesome,
                        onClick = { promptText = presetPrompt },
                        testTagName = "ai_preset_prompt_$idx"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Composer Field + Icon-First Action Orbs
            val isBusy = aiTextureState is AiTextureUiState.Loading
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text("Describe the PBR material or edit instruction...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_texture_prompt_input"),
                    shape = RoundedCornerShape(22.dp),
                    minLines = 3,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SkyBlueSoft.copy(alpha = 0.35f),
                        unfocusedContainerColor = SkyBlueSoft.copy(alpha = 0.25f),
                        focusedBorderColor = CobaltBluePrimary,
                        unfocusedBorderColor = CrispBlueBorder.copy(alpha = 0.5f),
                        focusedTextColor = DeepNavyInk,
                        unfocusedTextColor = DeepNavyInk
                    )
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TactileIconOrbButton(
                        icon = Icons.Rounded.AutoFixHigh,
                        contentDesc = "Create Texture",
                        badgeText = "CREATE",
                        orbSize = 52.dp,
                        iconSize = 22.dp,
                        enabled = !isBusy && promptText.isNotBlank(),
                        pulseType = TactilePulse.TAP_LIGHT,
                        onClick = { onGenerateNewTexture(promptText, targetNoiseChannel) },
                        testTagName = "create_ai_texture_btn"
                    )

                    TactileIconOrbButton(
                        icon = Icons.Rounded.Brush,
                        contentDesc = "Edit Active Map",
                        badgeText = "EDIT",
                        orbSize = 52.dp,
                        iconSize = 22.dp,
                        isYellowAccent = true,
                        enabled = !isBusy && promptText.isNotBlank(),
                        pulseType = TactilePulse.TAP_LIGHT,
                        onClick = { onEditExistingTexture(promptText, targetNoiseChannel) },
                        testTagName = "edit_ai_texture_btn"
                    )
                }
            }
        }
    }
}
