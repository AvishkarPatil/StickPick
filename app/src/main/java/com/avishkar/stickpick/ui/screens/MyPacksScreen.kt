package com.avishkar.stickpick.ui.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.avishkar.stickpick.R
import com.avishkar.stickpick.data.local.PackStorage
import com.avishkar.stickpick.data.model.StickerPack
import com.avishkar.stickpick.ui.components.VideoThumbnail
import com.avishkar.stickpick.ui.navigation.Routes
import com.avishkar.stickpick.viewmodel.MainViewModel
import com.avishkar.stickpick.whatsapp.PackArchiveHelper
import com.avishkar.stickpick.whatsapp.WhatsAppIntentHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPacksScreen(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val packs by vm.savedPacks.collectAsState()
    val waBusiness by vm.whatsappBusiness.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val packStorage = remember { PackStorage(context) }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadSavedPacks() }
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackbarHostState.showSnackbar(it); snackMsg = null }
    }

    Scaffold(
        topBar = {},
        containerColor = Color.Transparent,
        bottomBar = {},
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                Spacer(Modifier.height(20.dp))
                // Logo centered — same as Home
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "StickPick",
                        modifier = Modifier.height(36.dp),
                        contentScale = ContentScale.FillHeight
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text("My Packs", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Manage and sync your custom sticker collections",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }

            // Quick action cards
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(2f).height(140.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        onClick = { onNavigate(Routes.HOME) }
                    ) {
                        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(Icons.Outlined.AddCircle, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                            Column {
                                Text("Create New Pack", fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Convert Telegram stickers", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f).height(140.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text("${packs.size}", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text("PACKS", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            if (packs.isEmpty()) {
                item {
                    Spacer(Modifier.height(32.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Style, contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No packs yet", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(packs, key = { it.identifier }) { pack ->
                PackCard(
                    pack = pack,
                    vm = vm,
                    packStorage = packStorage,
                    showBusiness = waBusiness,
                    onAddToWhatsApp = {
                        WhatsAppIntentHelper.addStickerPackToWhatsApp(activity, pack.identifier, pack.name)
                    },
                    onAddToBusiness = {
                        WhatsAppIntentHelper.addStickerPackToWhatsAppBusiness(activity, pack.identifier, pack.name)
                    }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
            }
            GlassBottomNav(currentRoute = Routes.MY_PACKS, onNavigate = onNavigate,
                modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PackCard(
    pack: StickerPack,
    vm: MainViewModel,
    packStorage: PackStorage,
    showBusiness: Boolean,
    onAddToWhatsApp: () -> Unit,
    onAddToBusiness: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var editName by remember(pack.name) { mutableStateOf(pack.name) }

    // Resolve tray image — try pack's own dir, then base dir
    val trayFile = remember(pack.identifier, pack.trayImageFile) {
        // All tray files are in the base pack dir
        val baseId = run {
            val last = pack.identifier.lastIndexOf('_')
            if (last > 0) pack.identifier.substring(0, last) else pack.identifier
        }
        val baseDir = packStorage.getConvertedDir(baseId)
        val f = File(baseDir, pack.trayImageFile)
        if (f.exists()) f
        else {
            // Try exact dir
            val exactDir = packStorage.getConvertedDir(pack.identifier)
            val ef = File(exactDir, pack.trayImageFile)
            if (ef.exists()) ef else null
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showDetailDialog = true }
            ) {
                // Tray image preview
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    if (trayFile != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(trayFile).crossfade(true).build(),
                            contentDescription = "Pack tray",
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Style, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${pack.stickers.size} Stickers • ${pack.publisher}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap to view stickers", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
                IconButton(onClick = { scope.launch { PackArchiveHelper.sharePack(context, pack, packStorage) } }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { vm.deletePack(pack.identifier) }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            if (showBusiness) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAddToWhatsApp,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WhatsApp", color = Color.White, fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onAddToBusiness,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008069))
                    ) {
                        Icon(Icons.Outlined.Business, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Business", color = Color.White, fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Button(
                    onClick = onAddToWhatsApp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add to WhatsApp", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Pack Name") },
            text = { TextField(value = editName, onValueChange = { editName = it }, singleLine = true, shape = RoundedCornerShape(12.dp)) },
            confirmButton = { TextButton(onClick = { vm.updatePackName(pack.identifier, editName); showEditDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDetailDialog) {
        PackDetailDialog(pack = pack, packStorage = packStorage, onDismiss = { showDetailDialog = false })
    }
}

@Composable
private fun PackDetailDialog(pack: StickerPack, packStorage: PackStorage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val convertedDir = remember(pack.identifier) {
        val exactDir = packStorage.getConvertedDir(pack.identifier)
        if (exactDir.exists() && exactDir.listFiles()?.isNotEmpty() == true) {
            exactDir
        } else {
            val last = pack.identifier.lastIndexOf('_')
            val baseId = if (last > 0) pack.identifier.substring(0, last) else pack.identifier
            packStorage.getConvertedDir(baseId)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pack.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${pack.stickers.size} stickers", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch { PackArchiveHelper.sharePack(context, pack, packStorage) } }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(pack.stickers) { index, sticker ->
                        val convertedFile = File(convertedDir, sticker.imageFileName)
                        val rawFile = if (sticker.rawFilePath.isNotEmpty()) File(sticker.rawFilePath) else null

                        Card(
                            modifier = Modifier.aspectRatio(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Box(Modifier.fillMaxSize().padding(6.dp)) {
                                when {
                                    convertedFile.exists() -> {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(convertedFile).crossfade(true).build(),
                                            contentDescription = "Sticker ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    rawFile != null && rawFile.exists() && rawFile.extension == "webm" -> {
                                        VideoThumbnail(rawFile, Modifier.fillMaxSize())
                                    }
                                    else -> {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.BrokenImage, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                                if (rawFile?.extension?.lowercase() == "webm") {
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f)
                                    ) {
                                        Icon(Icons.Default.Videocam, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.padding(2.dp).size(10.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


