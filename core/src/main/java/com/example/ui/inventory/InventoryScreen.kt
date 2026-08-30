package com.example.ui.inventory

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventory.InventoryItem
import com.example.inventory.InventoryStore
import com.example.telephony.WhatsAppSender
import com.example.ui.theme.Crm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The inventory grid: pick what to send, then send it to whoever just called.
 *
 * The send is the point, so the button carries the count and stays pinned to the bottom rather
 * than scrolling away with the grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    sendToNumber: String?,
    sendToName: String?,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val items = remember { mutableStateListOf<InventoryItem>() }
    val selected = remember { mutableStateListOf<String>() }
    var loaded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        items.addAll(InventoryStore.load(context))
        loaded = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Crm.Text
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text("Inventory", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Crm.Text)
                        Text(
                            sendToName?.let { "Sending to $it" } ?: "Pick what to send",
                            fontSize = 11.sp,
                            color = Crm.TextMuted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add an item", tint = Crm.Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Crm.Surface,
                    titleContentColor = Crm.Text
                )
            )
        },
        containerColor = Crm.Ink
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !loaded -> Placeholder("Reading the inventory…")

                items.isEmpty() -> Placeholder(
                    "Nothing in the inventory yet. Add a photo, a name and a price with + above, " +
                            "then it is two taps to send it to a caller."
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        InventoryCard(
                            item = item,
                            selected = selected.contains(item.id),
                            onToggle = {
                                if (selected.contains(item.id)) selected.remove(item.id)
                                else selected.add(item.id)
                            },
                            onDelete = {
                                selected.remove(item.id)
                                scope.launch {
                                    val left = InventoryStore.remove(context, item.id)
                                    items.clear()
                                    items.addAll(left)
                                }
                            }
                        )
                    }
                }
            }

            if (selected.isNotEmpty()) {
                SendBar(
                    count = selected.size,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val chosen = items.filter { selected.contains(it.id) }
                    val failure = sendSelection(context, sendToNumber, chosen)
                    if (failure != null) {
                        Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
                    } else {
                        selected.clear()
                    }
                }
            }
        }
    }

    if (adding) {
        AddItemDialog(
            onDismiss = { adding = false },
            onAdd = { name, price, uri ->
                adding = false
                scope.launch {
                    val updated = InventoryStore.add(context, name, price, uri)
                    items.clear()
                    items.addAll(updated)
                }
            }
        )
    }
}

/**
 * Photos go as attachments and the names and prices as the caption; an item with no photo would
 * otherwise be silently dropped from a media share, so a text-only selection takes the wa.me
 * route instead.
 */
private fun sendSelection(
    context: android.content.Context,
    number: String?,
    chosen: List<InventoryItem>
): String? {
    if (chosen.isEmpty()) return "Nothing selected"

    val caption = chosen.joinToString("\n") { it.toShareLine() }
    val uris = chosen.mapNotNull { InventoryStore.shareUri(context, it) }

    return if (uris.isEmpty()) {
        WhatsAppSender.sendText(context, number, caption)
    } else {
        WhatsAppSender.sendMedia(context, number, uris, caption)
    }
}

@Composable
private fun InventoryCard(
    item: InventoryItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Crm.Surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Crm.Accent else Crm.Line,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggle() }
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                val path = InventoryStore.imagePath(context, item)
                val thumbnail = rememberThumbnail(path)
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Crm.Surface3),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Crm.Line,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) Crm.Accent else Color(0x66000000))
                        .border(1.dp, Color(0xA6FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Crm.AccentInk,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { confirming = true },
                    modifier = Modifier.align(Alignment.TopStart).size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove ${item.name}",
                        tint = Color(0xCCFFFFFF),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
                Text(
                    item.name,
                    color = Crm.Text,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    item.displayPrice,
                    color = Crm.Accent2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Crm.Surface2,
            title = { Text("Remove ${item.name}?", color = Crm.Text, fontSize = 15.sp) },
            text = {
                Text("Its photo is deleted too.", color = Crm.TextMuted, fontSize = 12.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) { Text("Remove", color = Crm.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text("Keep", color = Crm.TextMuted)
                }
            }
        )
    }
}

@Composable
private fun SendBar(count: Int, modifier: Modifier = Modifier, onSend: () -> Unit) {
    Button(
        onClick = onSend,
        colors = ButtonDefaults.buttonColors(containerColor = Crm.WhatsApp),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(
            "Send $count ${if (count == 1) "item" else "items"} via WhatsApp",
            color = Crm.WhatsAppInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Uri?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }

    // The photo picker needs no storage permission at all - the user hands over one image and
    // nothing else is readable.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { photo = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Crm.Surface2,
        title = { Text("Add to inventory", color = Crm.Text, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = fieldColours()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = fieldColours()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Text(if (photo == null) "Choose a photo" else "Photo chosen", color = Crm.Accent)
                    }
                    if (photo != null) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Crm.WhatsApp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onAdd(name.trim(), price.trim(), photo) }
            ) {
                Text("Add", color = if (name.isNotBlank()) Crm.Accent else Crm.Line)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Crm.TextMuted) }
        }
    )
}

@Composable
private fun fieldColours() = TextFieldDefaults.colors(
    focusedContainerColor = Crm.Surface,
    unfocusedContainerColor = Crm.Surface,
    focusedIndicatorColor = Crm.Accent,
    unfocusedIndicatorColor = Crm.Line,
    focusedLabelColor = Crm.Accent2,
    unfocusedLabelColor = Crm.TextMuted,
    focusedTextColor = Crm.Text,
    unfocusedTextColor = Crm.Text
)

/**
 * Decoded once per file and downsampled to roughly card size: a grid of full-resolution phone
 * photos is how a list like this runs out of memory.
 */
@Composable
private fun rememberThumbnail(file: File?): ImageBitmap? {
    var bitmap by remember(file?.path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file?.path) {
        bitmap = file?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(it.path, bounds)

                    val options = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSizeFor(bounds.outWidth, THUMBNAIL_WIDTH_PX)
                    }
                    android.graphics.BitmapFactory.decodeFile(it.path, options)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    return bitmap
}

/** Halve until the image is no wider than the card needs; powers of two are what decoders want. */
internal fun sampleSizeFor(sourceWidth: Int, targetWidth: Int): Int {
    if (sourceWidth <= 0 || targetWidth <= 0) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}

private const val THUMBNAIL_WIDTH_PX = 480

@Composable
private fun Placeholder(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = Crm.Line,
                modifier = Modifier.size(34.dp)
            )
            Text(message, color = Crm.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}
