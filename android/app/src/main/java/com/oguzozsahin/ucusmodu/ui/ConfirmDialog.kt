package com.oguzozsahin.ucusmodu.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle

/**
 * Geri donusu olmayan is icin onay: ucusu bitirmek ya da yol alinmis ucus
 * surerken baska rotaya gecmek. Vazgec, disina dokunma ve geri tusu ayni sey.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    colors: FlightColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        shape = RoundedCornerShape(4.dp),
        title = {
            Text(title, style = labelStyle(colors.ink).copy(fontSize = 15.sp, letterSpacing = 1.2.sp))
        },
        text = { Text(body, style = monoStyle(colors.ink2, 12)) },
        confirmButton = {
            FlightButton(text = confirmText, colors = colors, primary = true, onClick = onConfirm)
        },
        dismissButton = {
            FlightButton(text = "Vazgeç", colors = colors, onClick = onDismiss)
        },
    )
}
