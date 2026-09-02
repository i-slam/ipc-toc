package com.example.ui.inventory

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventory.InventorySnapshot
import com.example.inventory.PhotoCache
import com.example.inventory.SupabaseConfig
import com.example.inventory.Vehicle
import com.example.inventory.VehicleRepository
import com.example.telephony.WhatsAppSender
import com.example.ui.theme.Crm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The inventory, read from the database that owns it.
 *
 * There is no editing here on purpose: stock is maintained elsewhere and this app is a reader, so
 * the only actions are choosing vehicles and sending them to whoever just called.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    config: SupabaseConfig,
    sendToNumber: String?,
    sendToName: String?,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf(InventorySnapshot(emptyList(), fromCache = false)) }
    var sending by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(refreshKey) {
        loading = true
        snapshot = VehicleRepository.load(context, config)
        selected.retainAll(snapshot.vehicles.map { it.id }.toSet())
        loading = false
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
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Crm.Accent)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading && snapshot.isEmpty -> Placeholder("Reading the inventory…")

                snapshot.isEmpty -> Placeholder(
                    snapshot.problem ?: "No vehicles are marked available right now."
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = if (selected.isEmpty()) 16.dp else 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (snapshot.problem != null) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            StaleBanner(snapshot.problem!!)
                        }
                    }
                    items(snapshot.vehicles, key = { it.id }) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            selected = selected.contains(vehicle.id),
                            onToggle = {
                                if (selected.contains(vehicle.id)) selected.remove(vehicle.id)
                                else selected.add(vehicle.id)
                            }
                        )
                    }
                }
            }

            if (selected.isNotEmpty()) {
                SendBar(
                    count = selected.size,
                    busy = sending,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    if (sending) return@SendBar
                    sending = true
                    scope.launch {
                        val chosen = snapshot.vehicles.filter { selected.contains(it.id) }
                        val failure = sendSelection(context, sendToNumber, chosen)
                        sending = false
                        if (failure != null) {
                            Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
                        } else {
                            selected.clear()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Photos are URLs in the database and WhatsApp needs files, so the chosen ones are fetched to
 * cache before the share opens. Anything without a usable photo still goes as text rather than
 * being quietly dropped from the selection.
 *
 * One vehicle that has a catalogue link is the exception, and deliberately so: WhatsApp turns a
 * wa.me/p link into a product card with its own picture, price and an order button, which is a
 * better thing to receive than a photo with a caption. It only does that for a text message and
 * only for the first link, so attaching our own photo would replace the card with an image and
 * throw the rest away.
 */
private suspend fun sendSelection(
    context: Context,
    number: String?,
    chosen: List<Vehicle>
): String? {
    if (chosen.isEmpty()) return "Nothing selected"

    val caption = chosen.joinToString("\n\n") { it.toShareText() }

    val single = chosen.singleOrNull()
    if (single != null && single.hasProductLink) {
        return WhatsAppSender.sendText(context, number, caption)
    }

    val uris = withContext(Dispatchers.IO) {
        chosen.mapNotNull { vehicle ->
            PhotoCache.ensure(context, vehicle)?.let { PhotoCache.shareUri(context, it) }
        }
    }

    return if (uris.isEmpty()) {
        WhatsAppSender.sendText(context, number, caption)
    } else {
        WhatsAppSender.sendMedia(context, number, uris, caption)
    }
}

@Composable
private fun VehicleCard(vehicle: Vehicle, selected: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    var photo by remember(vehicle.id) { mutableStateOf<File?>(null) }

    LaunchedEffect(vehicle.id) { photo = PhotoCache.ensure(context, vehicle) }

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
            .semantics {
                contentDescription =
                    "${vehicle.title}, ${vehicle.displayPrice}${if (selected) ", selected" else ""}"
            }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
            ) {
                val thumbnail = rememberThumbnail(photo)
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Crm.Surface3),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Crm.Line,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                if (vehicle.hasProductLink) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Crm.WhatsAppInk)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "Catalogue",
                            color = Crm.WhatsAppText,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (vehicle.specialOffer) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Crm.Accent)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Crm.AccentInk,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            vehicle.offerNote?.takeIf { it.isNotBlank() } ?: "Offer",
                            color = Crm.AccentInk,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
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
                            contentDescription = null,
                            tint = Crm.AccentInk,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
                Text(
                    vehicle.title,
                    color = Crm.Text,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                if (vehicle.specLine.isNotBlank()) {
                    Text(vehicle.specLine, color = Crm.TextMuted, fontSize = 9.5.sp, maxLines = 1)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        vehicle.displayPrice,
                        color = Crm.Accent2,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    vehicle.wasPrice?.let {
                        Text(
                            it,
                            color = Crm.TextFaint,
                            fontSize = 9.sp,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }
            }
        }
    }
}

/** Says plainly that these are the last known prices, not the current ones. */
@Composable
private fun StaleBanner(problem: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Crm.WarnSurface)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = Crm.Accent2,
            modifier = Modifier.size(16.dp)
        )
        Text(
            "$problem — showing the last inventory this phone saw, so prices may have moved.",
            color = Crm.Accent2,
            fontSize = 10.5.sp
        )
    }
}

@Composable
private fun SendBar(count: Int, busy: Boolean, modifier: Modifier = Modifier, onSend: () -> Unit) {
    Button(
        onClick = onSend,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = Crm.WhatsApp,
            disabledContainerColor = Crm.WhatsApp.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = Crm.WhatsAppInk,
                strokeWidth = 2.dp,
                modifier = Modifier.size(15.dp)
            )
            Text(
                "  Fetching photos…",
                color = Crm.WhatsAppInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                "Send $count ${if (count == 1) "vehicle" else "vehicles"} via WhatsApp",
                color = Crm.WhatsAppInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Decoded once per file and downsampled to roughly card size: a grid of full-resolution photos is
 * how a screen like this runs out of memory.
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
