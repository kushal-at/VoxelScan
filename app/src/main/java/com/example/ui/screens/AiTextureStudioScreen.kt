package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlossyStatusPill
import com.example.ui.components.GlossyTranslucentCard
import com.example.ui.components.TactileFilterChip
import com.example.ui.components.TactilePrimaryButton
import com.example.ui.theme.AlertRedSolid
import com.example.ui.theme.AlertRedSurface
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PureWhite
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Header Card
        GlossyTranslucentCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlossyStatusPill(
                    text = "GEMINI-NANO-BANANA-2.1",
                    icon = Icons.Default.AutoFixHigh
                )
                GlossyStatusPill(
                    text = if (targetNoiseChannel) "TARGET: NOISE MAP" else "TARGET: ALBEDO MAP",
                    isYellowHighlight = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AI PBR Texture & Noise Map Studio",
                style = MaterialTheme.typography.headlineMedium,
                color = DeepNavyInk
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Create brand-new seamless textures or edit your 3D model's captured Image Texture (_albedo.png) and Noise Texture (_noise.png) using text prompts.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlueMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Channel Selector (Image Texture vs Noise Texture)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TactileFilterChip(
                    label = "Image Texture (_albedo.png)",
                    selected = !targetNoiseChannel,
                    onClick = { targetNoiseChannel = false },
                    modifier = Modifier.weight(1f),
                    testTagName = "select_albedo_channel_chip"
                )
                TactileFilterChip(
                    label = "Noise Texture (_noise.png)",
                    selected = targetNoiseChannel,
                    onClick = { targetNoiseChannel = true },
                    modifier = Modifier.weight(1f),
                    testTagName = "select_noise_channel_chip"
                )
            }
        }

        // 2. Prompt Input & Preset Chips
        GlossyTranslucentCard {
            Text(
                text = "Texture Generation or Edit Prompt",
                style = MaterialTheme.typography.titleMedium,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("Describe the texture to create or how to edit the current map") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_texture_prompt_input"),
                shape = RoundedCornerShape(16.dp),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = PureWhite,
                    focusedBorderColor = CobaltBluePrimary,
                    unfocusedBorderColor = CrispBlueBorder,
                    focusedTextColor = DeepNavyInk,
                    unfocusedTextColor = DeepNavyInk
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                        onClick = { promptText = presetPrompt },
                        testTagName = "ai_preset_prompt_$idx"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isBusy = aiTextureState is AiTextureUiState.Loading
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TactilePrimaryButton(
                    text = "Create Texture",
                    icon = Icons.Default.AutoFixHigh,
                    enabled = !isBusy && promptText.isNotBlank(),
                    onClick = { onGenerateNewTexture(promptText, targetNoiseChannel) },
                    pulseType = TactilePulse.TAP_LIGHT,
                    modifier = Modifier.weight(1f),
                    testTagName = "create_ai_texture_btn"
                )

                TactilePrimaryButton(
                    text = "Edit Active Map",
                    icon = Icons.Default.Brush,
                    isYellowAccent = true,
                    enabled = !isBusy && promptText.isNotBlank(),
                    onClick = { onEditExistingTexture(promptText, targetNoiseChannel) },
                    pulseType = TactilePulse.TAP_LIGHT,
                    modifier = Modifier.weight(1f),
                    testTagName = "edit_ai_texture_btn"
                )
            }
        }

        // 3. AI Generation / Editing Status & Output Preview
        when (val state = aiTextureState) {
            is AiTextureUiState.Loading -> {
                GlossyTranslucentCard(
                    containerColor = BrightYellowSurface,
                    borderColor = WarmYellowBorder
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            color = CobaltBluePrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = state.modeLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = DeepNavyInk,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Applying directly to active 3D model UV channels upon completion.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DeepAmberInk
                            )
                        }
                    }
                }
            }

            is AiTextureUiState.Error -> {
                GlossyTranslucentCard(
                    containerColor = AlertRedSurface,
                    borderColor = AlertRedSolid
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "AI Error",
                            tint = AlertRedSolid,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlertRedSolid,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("ai_texture_error_text")
                        )
                    }
                }
            }

            is AiTextureUiState.Success -> {
                GlossyTranslucentCard(
                    containerColor = BrightYellowSurface,
                    borderColor = WarmYellowBorder
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Texture Applied",
                            tint = CobaltBluePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Applied to ${state.appliedToSlot}",
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(2.dp, CobaltBluePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "Generated AI Texture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            is AiTextureUiState.Idle -> Unit
        }

        // 4. Current Active 3D Asset Textures (Live Comparison)
        GlossyTranslucentCard {
            Text(
                text = "Active 3D Model Texture Maps",
                style = MaterialTheme.typography.titleMedium,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, CobaltBluePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Image(
                            bitmap = currentAlbedoBitmap.asImageBitmap(),
                            contentDescription = "Current Albedo Texture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Image Texture (_albedo.png)",
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepNavyInk
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, SolarYellowAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Image(
                            bitmap = currentNoiseBitmap.asImageBitmap(),
                            contentDescription = "Current Noise Texture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Noise Texture (_noise.png)",
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepNavyInk
                    )
                }
            }
        }
    }
}
