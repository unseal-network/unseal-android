/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

/** Small grey uppercase-ish section title above a [FormCard] (iOS `Section` header). */
@Composable
fun FormSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(start = 32.dp, end = 16.dp, bottom = 6.dp),
        text = title,
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textSecondary,
    )
}

/** Optional small grey footer under a [FormCard] (iOS `Section` footer). */
@Composable
fun FormSectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 6.dp),
        text = text,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

/** Grouped rounded card holding a set of borderless rows (iOS inset-grouped `Section`). */
@Composable
fun FormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(ElementTheme.colors.bgCanvasDefault, RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp),
        content = content,
    )
}

/** Inset divider between rows inside a [FormCard]. */
@Composable
fun FormDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

/**
 * Borderless editable row (iOS `TextField(placeholder)` inside a `Form`): just text + placeholder,
 * no outline. The surrounding [FormCard] provides the grouped background; [FormDivider] separates rows.
 */
@Composable
fun FormTextRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    BasicTextField(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = ElementTheme.colors.textPrimary),
        cursorBrush = SolidColor(ElementTheme.colors.iconAccentPrimary),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            inner()
        },
    )
}

/**
 * Row with a leading icon + title on the left and an arbitrary trailing slot on the right
 * (iOS `LabeledContent` / right-aligned `TextField`). Used for model/baseUrl/apiKey/read-only id.
 */
@Composable
fun FormLabeledRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
        Box(modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A picker row: leading icon + title, trailing current value + chevron; tap opens a menu/sheet. */
@Composable
fun FormPickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shapeAwareClickable(RoundedCornerShape(12.dp), enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Icon(
            imageVector = CompoundIcons.ChevronRight(),
            contentDescription = null,
            tint = ElementTheme.colors.iconTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Toggle row with a leading icon, a title + subtitle, and a trailing switch (iOS `Toggle` with
 * a `Label`). When [enabled] is false the label/icon are greyed out (e.g. auto-join when not public).
 */
@Composable
fun FormToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) ElementTheme.colors.textPrimary else ElementTheme.colors.textDisabled
    val iconTint = if (enabled) ElementTheme.colors.iconSecondary else ElementTheme.colors.iconDisabled
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ElementTheme.typography.fontBodyLgRegular, color = titleColor)
            Text(text = subtitle, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Avatar header row: avatar thumbnail + a "set avatar" label (iOS avatar `Section`). */
@Composable
fun FormAvatarRow(
    avatar: @Composable () -> Unit,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shapeAwareClickable(RoundedCornerShape(12.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        avatar()
        Text(
            text = actionLabel,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textActionPrimary,
        )
    }
}

/** A password field row (leading key icon + masked field), mirrors iOS `SecureField` in `LabeledContent`. */
@Composable
fun FormSecureRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FormLabeledRow(label = label, icon = icon, modifier = modifier) {
        BasicTextField(
            modifier = Modifier.weight(1f, fill = false),
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = ElementTheme.colors.textPrimary),
            cursorBrush = SolidColor(ElementTheme.colors.iconAccentPrimary),
            visualTransformation = remember { PasswordVisualTransformation() },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textSecondary)
                }
                inner()
            },
        )
    }
}
