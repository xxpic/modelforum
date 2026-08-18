package com.yanparker.modelforum.data.key

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun ApiKeyField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var hidden by remember { mutableStateOf(true) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { androidx.compose.material3.Text(label) },
        singleLine = true,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { hidden = !hidden }) {
                Icon(
                    if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (hidden) "Показать ключ" else "Скрыть ключ",
                )
            }
        },
        modifier = modifier,
    )
}