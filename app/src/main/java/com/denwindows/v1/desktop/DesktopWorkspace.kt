package com.denwindows.v1.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denwindows.v1.data.DesktopIconSize
import com.denwindows.v1.data.DesktopItemEntity
import com.denwindows.v1.data.DesktopItemKind
import com.denwindows.v1.theme.LocalDenColors
import com.denwindows.v1.windows.DesktopAppType
import com.denwindows.v1.windows.iconVector
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopWorkspace(
    desktopItems: List<DesktopItemEntity>,
    iconSize: DesktopIconSize,
    getAndroidAppIcon: (String) -> ImageBitmap?,
    onOpenItem: (DesktopItemEntity) -> Unit,
    onMoveItem: (String, Int, Int) -> Unit,
    onCreateFolder: (String, Int, Int) -> Unit,
    onPinToTaskbar: (DesktopItemEntity, Boolean) -> Unit,
    onRemoveDesktopItem: (DesktopItemEntity) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onRefreshDesktop: () -> Unit,
    onOpenSettingsSection: (String) -> Unit,
    onOpenTerminal: () -> Unit,
    onEmptyAreaTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val denColors = LocalDenColors.current
    val density = LocalDensity.current

    var selectedId by remember { mutableStateOf<String?>(null) }
    var showDesktopMenu by remember { mutableStateOf(false) }
    var desktopMenuOffset by remember { mutableStateOf(DpOffset(120.dp, 80.dp)) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("New Folder") }
    var itemMenuTarget by remember { mutableStateOf<DesktopItemEntity?>(null) }

    val placedItems = remember(desktopItems) {
        desktopItems.filter { it.gridCol >= 0 && it.gridRow >= 0 }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) {
                                desktopMenuOffset = with(density) {
                                    DpOffset(pos.x.toDp(), pos.y.toDp())
                                }
                                showDesktopMenu = true
                            }
                        }
                    }
                }
            }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = {
                    selectedId = null
                    onEmptyAreaTap()
                },
                onLongClick = {
                    desktopMenuOffset = DpOffset(140.dp, 100.dp)
                    showDesktopMenu = true
                }
            )
            .padding(12.dp)
    ) {
        val cellW = iconSize.cellWidthDp.toFloat()
        val cellH = iconSize.cellHeightDp.toFloat()
        val maxCols = (maxWidth.value / cellW).toInt().coerceAtLeast(1)
        val maxRows = (maxHeight.value / cellH).toInt().coerceAtLeast(1)

        placedItems.forEach { item ->
            val col = item.gridCol.coerceIn(0, maxCols - 1)
            val row = item.gridRow.coerceIn(0, maxRows - 1)
            var dragOffsetX by remember(item.id, col) { mutableFloatStateOf(0f) }
            var dragOffsetY by remember(item.id, row) { mutableFloatStateOf(0f) }

            val baseX = col * cellW
            val baseY = row * cellH
            val isSelected = selectedId == item.id

            Box(
                modifier = Modifier
                    .offset(x = (baseX + dragOffsetX).dp, y = (baseY + dragOffsetY).dp)
                    .size(width = cellW.dp, height = cellH.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) denColors.accent.copy(alpha = 0.24f)
                        else Color.Transparent
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                1.dp,
                                denColors.accent.copy(alpha = 0.7f),
                                RoundedCornerShape(10.dp)
                            )
                        } else Modifier
                    )
                    .pointerInput(item.id, maxCols, maxRows) {
                        detectDragGestures(
                            onDragStart = { selectedId = item.id },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += with(density) { dragAmount.x.toDp().value }
                                dragOffsetY += with(density) { dragAmount.y.toDp().value }
                            },
                            onDragEnd = {
                                val targetCol = ((baseX + dragOffsetX) / cellW).roundToInt().coerceIn(0, maxCols - 1)
                                val targetRow = ((baseY + dragOffsetY) / cellH).roundToInt().coerceIn(0, maxRows - 1)
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                onMoveItem(item.id, targetCol, targetRow)
                            },
                            onDragCancel = {
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            }
                        )
                    }
                    .combinedClickable(
                        onClick = {
                            selectedId = item.id
                            onOpenItem(item)
                        },
                        onLongClick = {
                            selectedId = item.id
                            itemMenuTarget = item
                        }
                    )
                    .testTag("desktop_icon_${item.target}")
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DesktopIconGraphic(
                        item = item,
                        iconDp = iconSize.iconDp,
                        getAndroidAppIcon = getAndroidAppIcon
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Item-level Context Menu
                DropdownMenu(
                    expanded = itemMenuTarget?.id == item.id,
                    onDismissRequest = { itemMenuTarget = null }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                        onClick = {
                            itemMenuTarget = null
                            onOpenItem(item)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.isPinnedToTaskbar) "Unpin from Taskbar" else "Pin to Taskbar") },
                        leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                        onClick = {
                            itemMenuTarget = null
                            onPinToTaskbar(item, !item.isPinnedToTaskbar)
                        }
                    )
                    if (item.kind == DesktopItemKind.ANDROID_APP.name) {
                        DropdownMenuItem(
                            text = { Text("Android App Info") },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = {
                                itemMenuTarget = null
                                onOpenAppInfo(item.target)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from Desktop") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = {
                            itemMenuTarget = null
                            onRemoveDesktopItem(item)
                        }
                    )
                }
            }
        }

        // Desktop Background Context Menu
        Box(modifier = Modifier.offset(x = desktopMenuOffset.x, y = desktopMenuOffset.y)) {
            DropdownMenu(
                expanded = showDesktopMenu,
                onDismissRequest = { showDesktopMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Refresh Desktop") },
                    leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                    onClick = {
                        showDesktopMenu = false
                        onRefreshDesktop()
                    }
                )
                DropdownMenuItem(
                    text = { Text("New Folder") },
                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                    onClick = {
                        showDesktopMenu = false
                        newFolderName = "New Folder"
                        showNewFolderDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Open Terminal") },
                    leadingIcon = { Icon(Icons.Filled.Terminal, null) },
                    onClick = {
                        showDesktopMenu = false
                        onOpenTerminal()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Display Settings") },
                    leadingIcon = { Icon(Icons.Filled.DesktopWindows, null) },
                    onClick = {
                        showDesktopMenu = false
                        onOpenSettingsSection("desktop")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Personalize") },
                    leadingIcon = { Icon(Icons.Filled.Palette, null) },
                    onClick = {
                        showDesktopMenu = false
                        onOpenSettingsSection("personalization")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Desktop Settings") },
                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                    onClick = {
                        showDesktopMenu = false
                        onOpenSettingsSection("system")
                    }
                )
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create Desktop Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showNewFolderDialog = false
                    val nextCol = (placedItems.size / 4).coerceAtMost(5)
                    val nextRow = placedItems.size % 4
                    onCreateFolder(newFolderName, nextCol, nextRow)
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DesktopIconGraphic(
    item: DesktopItemEntity,
    iconDp: Int,
    getAndroidAppIcon: (String) -> ImageBitmap?
) {
    val denColors = LocalDenColors.current
    when (item.kind) {
        DesktopItemKind.ANDROID_APP.name -> {
            val bmp = getAndroidAppIcon(item.target)
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(iconDp.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                IconBox(Icons.Filled.Apps, denColors.accent, iconDp)
            }
        }
        DesktopItemKind.FOLDER.name -> {
            IconBox(Icons.Filled.Folder, Color(0xFFF3C15A), iconDp)
        }
        else -> {
            val appType = runCatching { DesktopAppType.valueOf(item.target) }
                .getOrDefault(DesktopAppType.SETTINGS)
            val tint = when (appType) {
                DesktopAppType.FILE_EXPLORER -> Color(0xFFF3C15A)
                DesktopAppType.BROWSER -> Color(0xFF4C8DFF)
                DesktopAppType.TERMINAL -> Color(0xFF38C976)
                DesktopAppType.CODE_EDITOR -> Color(0xFF2196F3)
                DesktopAppType.NOTEPAD -> Color(0xFF7BB0FF)
                DesktopAppType.CALCULATOR -> Color(0xFF9C88FF)
                DesktopAppType.CLOCK -> Color(0xFFE69B3A)
                DesktopAppType.CALENDAR -> Color(0xFFE15B78)
                DesktopAppType.TASK_MANAGER -> Color(0xFF26A69A)
                DesktopAppType.SETTINGS -> Color(0xFF8CA0B8)
                DesktopAppType.APP_COMPATIBILITY -> Color(0xFF4C8DFF)
            }
            IconBox(appType.iconVector(), tint, iconDp)
        }
    }
}

@Composable
private fun IconBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    iconDp: Int
) {
    Box(
        modifier = Modifier
            .size(iconDp.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.22f))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((iconDp * 0.58f).dp)
        )
    }
}
