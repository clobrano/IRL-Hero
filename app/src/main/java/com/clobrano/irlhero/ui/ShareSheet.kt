package com.clobrano.irlhero.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.clobrano.irlhero.share.CardFormat
import com.clobrano.irlhero.share.CardTheme
import com.clobrano.irlhero.share.ShareCardRenderer
import com.clobrano.irlhero.share.ShareItem
import com.clobrano.irlhero.share.Sharing
import com.clobrano.irlhero.ui.theme.LocalDarkTheme

/** Share sheet (Flow 3): text or image, post or story, light or dark, with a preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(item: ShareItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var asImage by rememberSaveable { mutableStateOf(true) }
    var format by rememberSaveable { mutableStateOf(CardFormat.POST) }
    val appDark = LocalDarkTheme.current
    var theme by rememberSaveable { mutableStateOf(if (appDark) CardTheme.DARK else CardTheme.LIGHT) }
    val bitmap = remember(item, format, theme) { ShareCardRenderer.render(item.card, format, theme) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share", style = MaterialTheme.typography.titleLarge)
            Choice(listOf("Image", "Text"), if (asImage) 0 else 1) { asImage = it == 0 }
            if (asImage) {
                Choice(CardFormat.entries.map { it.label }, format.ordinal) { format = CardFormat.entries[it] }
                Choice(listOf("Light", "Dark"), theme.ordinal) { theme = CardTheme.entries[it] }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Preview of the share card",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Text(item.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            }
            Button(
                onClick = {
                    if (asImage) Sharing.shareImage(context, bitmap) else Sharing.shareText(context, item.text)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share") }
        }
    }
}

@Composable
private fun Choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(selected = selected == i, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) {
                Text(label)
            }
        }
    }
}
