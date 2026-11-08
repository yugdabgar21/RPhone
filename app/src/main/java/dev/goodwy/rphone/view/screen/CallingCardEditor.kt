package dev.goodwy.rphone.view.screen

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallingCardFont
import dev.goodwy.rphone.controller.util.CallingCardStyle
import dev.goodwy.rphone.controller.util.CallingCardStyleCodec

/**
 * Draft produced by the two-step calling card editor.
 *
 * A null [style] means "remove this customization and fall back to the app
 * default". The caller decides when that draft is persisted.
 */
data class CallingCardEditorResult(
    val backgroundUri: String?,
    val style: CallingCardStyle?
)

/**
 * Full-screen, two-step editor.
 *
 * Step 1 picks the portrait card photo. Step 2 previews the real call screen
 * while the user changes the font and name colour. Nothing is written to
 * storage here: [onDone] returns a draft.
 */
@Composable
fun CallingCardEditorDialog(
    contactName: String,
    phoneNumber: String,
    contactPhotoUri: String?,
    initialBackgroundUri: String?,
    inheritedBackgroundUri: String? = null,
    initialStyle: CallingCardStyle,
    fallbackStyle: CallingCardStyle,
    onDismiss: () -> Unit,
    onDone: (CallingCardEditorResult) -> Unit
) {
    val context = LocalContext.current
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var backgroundUri by rememberSaveable(initialBackgroundUri) {
        mutableStateOf(initialBackgroundUri)
    }
    var style by rememberSaveable(stateSaver = CallingCardStyleSaver) {
        mutableStateOf(initialStyle)
    }
    var usingDefault by rememberSaveable { mutableStateOf(false) }

    // The inherited global background is shown for context, but it is not a
    // draft value: Done still returns only what the user picked here.
    val previewBackgroundUri = backgroundUri ?: inheritedBackgroundUri

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            backgroundUri = uri.toString()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            backgroundUri = uri.toString()
        }
    }

    BackHandler {
        if (stage == 1) stage = 0 else onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            when (stage) {
                0 -> CallingCardPhotoStep(
                    contactName = contactName,
                    photoUri = contactPhotoUri,
                    backgroundUri = previewBackgroundUri,
                    canRemovePhoto = backgroundUri != null,
                    onBack = onDismiss,
                    onNext = { stage = 1 },
                    onChoosePhoto = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onBrowseFiles = { filePicker.launch(arrayOf("image/*")) },
                    onRemovePhoto = { backgroundUri = null }
                )

                else -> CallingCardCustomizeStep(
                    contactName = contactName,
                    phoneNumber = phoneNumber,
                    photoUri = contactPhotoUri,
                    backgroundUri = previewBackgroundUri,
                    style = style,
                    onBack = { stage = 0 },
                    onStyleChange = {
                        style = it
                        usingDefault = false
                    },
                    onUseDefault = {
                        style = fallbackStyle
                        usingDefault = true
                    },
                    usingDefault = usingDefault,
                    onDone = {
                        onDone(
                            CallingCardEditorResult(
                                backgroundUri = backgroundUri,
                                style = if (usingDefault) null else style
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = backLabel
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun CallingCardPhotoStep(
    contactName: String,
    photoUri: String?,
    backgroundUri: String?,
    canRemovePhoto: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onChoosePhoto: () -> Unit,
    onBrowseFiles: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        EditorHeader(
            title = stringResource(R.string.calling_card_photo_title),
            onBack = onBack,
            backLabel = stringResource(R.string.calling_card_back),
            actionLabel = stringResource(R.string.calling_card_next),
            onAction = onNext
        )

        PortraitPreviewFrame(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            if (backgroundUri != null) {
                AsyncImage(
                    model = backgroundUri,
                    contentDescription = stringResource(R.string.calling_card_photo_preview),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                NoPhotoPlaceholder(contactName = contactName, photoUri = photoUri)
            }
        }

        Text(
            text = stringResource(R.string.calling_card_photo_supporting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onChoosePhoto,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (backgroundUri == null) {
                        stringResource(R.string.calling_card_choose_photo)
                    } else {
                        stringResource(R.string.calling_card_change_photo)
                    }
                )
            }
            OutlinedButton(onClick = onBrowseFiles) {
                Text(stringResource(R.string.calling_card_browse_files))
            }
        }

        if (canRemovePhoto) {
            TextButton(
                onClick = onRemovePhoto,
                modifier = Modifier.padding(start = 24.dp)
            ) {
                Text(stringResource(R.string.calling_card_remove_photo))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NoPhotoPlaceholder(contactName: String, photoUri: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Icon(
                imageVector = Icons.Rounded.Wallpaper,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
        if (contactName.isNotBlank()) {
            Text(
                text = contactName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = stringResource(R.string.calling_card_no_photo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CallingCardCustomizeStep(
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    backgroundUri: String?,
    style: CallingCardStyle,
    onBack: () -> Unit,
    onStyleChange: (CallingCardStyle) -> Unit,
    onUseDefault: () -> Unit,
    usingDefault: Boolean,
    onDone: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        EditorHeader(
            title = stringResource(R.string.calling_card_customize_title),
            onBack = onBack,
            backLabel = stringResource(R.string.calling_card_back),
            actionLabel = stringResource(R.string.calling_card_done),
            onAction = onDone
        )

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PortraitPreviewFrame(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    LivePreview(
                        contactName = contactName,
                        phoneNumber = phoneNumber,
                        photoUri = photoUri,
                        backgroundUri = backgroundUri,
                        style = style
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    AppearanceControls(
                        style = style,
                        usingDefault = usingDefault,
                        onStyleChange = onStyleChange,
                        onUseDefault = onUseDefault
                    )
                }
            }
        } else {
            PortraitPreviewFrame(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                LivePreview(
                    contactName = contactName,
                    phoneNumber = phoneNumber,
                    photoUri = photoUri,
                    backgroundUri = backgroundUri,
                    style = style
                )
            }
            AppearanceControls(
                style = style,
                usingDefault = usingDefault,
                onStyleChange = onStyleChange,
                onUseDefault = onUseDefault
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LivePreview(
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    backgroundUri: String?,
    style: CallingCardStyle
) {
    CallScreenPreview(
        contactName = contactName,
        phoneNumber = phoneNumber,
        photoUri = photoUri,
        backgroundUri = backgroundUri,
        style = CallScreenPreviewStyle.Default,
        callingCardStyle = style,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun AppearanceControls(
    style: CallingCardStyle,
    usingDefault: Boolean,
    onStyleChange: (CallingCardStyle) -> Unit,
    onUseDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = stringResource(R.string.calling_card_font_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(CallingCardFont.entries, key = { it.id }) { font ->
                FontPreset(
                    font = font,
                    selected = !usingDefault && style.font == font,
                    onClick = { onStyleChange(style.copy(font = font)) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.calling_card_text_color_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "theme") {
                ColorSwatch(
                    color = null,
                    label = stringResource(R.string.calling_card_color_default),
                    selected = !usingDefault && style.textColorArgb == null,
                    onClick = { onStyleChange(style.copy(textColorArgb = null)) }
                )
            }
            items(callingCardColors, key = { it.argb }) { preset ->
                ColorSwatch(
                    color = Color(preset.argb),
                    label = stringResource(preset.labelRes),
                    selected = !usingDefault && style.textColorArgb == preset.argb,
                    onClick = { onStyleChange(style.copy(textColorArgb = preset.argb)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onUseDefault,
            enabled = !usingDefault
        ) {
            Text(stringResource(R.string.calling_card_use_default))
        }
    }
}

@Composable
private fun FontPreset(
    font: CallingCardFont,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Aa",
            fontFamily = font.family,
            fontSize = 22.sp,
            color = content,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(font.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Text(
                text = "A",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = label,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PortraitPreviewFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameWidth: Dp = minOf(maxWidth, maxHeight * 9f / 16f)
        val frameHeight: Dp = frameWidth * 16f / 9f
        Surface(
            modifier = Modifier
                .size(frameWidth, frameHeight)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 2.dp
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

private data class ColorPreset(val argb: Int, val labelRes: Int)

private val callingCardColors = listOf(
    ColorPreset(0xFFFFFFFF.toInt(), R.string.calling_card_color_white),
    ColorPreset(0xFFFFF1CC.toInt(), R.string.calling_card_color_cream),
    ColorPreset(0xFFFFC857.toInt(), R.string.calling_card_color_gold),
    ColorPreset(0xFFFF8A80.toInt(), R.string.calling_card_color_coral),
    ColorPreset(0xFF8ECAE6.toInt(), R.string.calling_card_color_sky),
    ColorPreset(0xFFA8E6CF.toInt(), R.string.calling_card_color_mint),
    ColorPreset(0xFFCDB4DB.toInt(), R.string.calling_card_color_lavender),
    ColorPreset(0xFF101418.toInt(), R.string.calling_card_color_ink)
)

private val CallingCardStyleSaver = Saver<CallingCardStyle, String>(
    save = { CallingCardStyleCodec.encode(it) },
    restore = { CallingCardStyleCodec.decode(it) ?: CallingCardStyle.ThemeDefault }
)
