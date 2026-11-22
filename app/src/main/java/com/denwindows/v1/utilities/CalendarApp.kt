package com.denwindows.v1.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denwindows.v1.data.CalendarEventEntity
import com.denwindows.v1.theme.LocalDenColors
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarApp(
    events: List<CalendarEventEntity>,
    onSaveEvent: (CalendarEventEntity) -> Unit,
    onDeleteEvent: (Long) -> Unit
) {
    val denColors = LocalDenColors.current
    val todayCal = remember { Calendar.getInstance() }

    var displayYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var displayMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) } // 0-based
    var selectedDateIso by remember {
        mutableStateOf(
            formatIsoDate(
                todayCal.get(Calendar.YEAR),
                todayCal.get(Calendar.MONTH),
                todayCal.get(Calendar.DAY_OF_MONTH)
            )
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var eventTitle by remember { mutableStateOf("") }
    var eventTime by remember { mutableStateOf("09:00") }

    val monthName = DateFormatSymbols.getInstance(Locale.getDefault()).months[displayMonth]

    val monthMatrix = remember(displayYear, displayMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0..6 (Sun..Sat)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = MutableList<Int?>(42) { null }
        for (day in 1..daysInMonth) {
            cells[firstDayOfWeek + day - 1] = day
        }
        cells.chunked(7)
    }

    val dayEvents = remember(events, selectedDateIso) {
        events.filter { it.dateIso == selectedDateIso }
    }
    val eventDatesSet = remember(events) { events.map { it.dateIso }.toSet() }

    Row(modifier = Modifier.fillMaxSize().background(denColors.windowSurface)) {
        // Monthly Grid
        Column(modifier = Modifier.weight(1.4f).fillMaxHeight().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$monthName $displayYear",
                    style = MaterialTheme.typography.titleMedium,
                    color = denColors.textPrimary
                )
                Row {
                    IconButton(onClick = {
                        if (displayMonth == 0) {
                            displayMonth = 11; displayYear--
                        } else displayMonth--
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Month", tint = denColors.textPrimary)
                    }
                    TextButton(onClick = {
                        displayYear = todayCal.get(Calendar.YEAR)
                        displayMonth = todayCal.get(Calendar.MONTH)
                        selectedDateIso = formatIsoDate(
                            displayYear,
                            displayMonth,
                            todayCal.get(Calendar.DAY_OF_MONTH)
                        )
                    }) { Text("Today", fontSize = 12.sp) }
                    IconButton(onClick = {
                        if (displayMonth == 11) {
                            displayMonth = 0; displayYear++
                        } else displayMonth++
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Month", tint = denColors.textPrimary)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        color = denColors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                monthMatrix.forEach { week ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { dayNum ->
                            if (dayNum == null) {
                                Spacer(Modifier.weight(1f).fillMaxHeight())
                            } else {
                                val iso = formatIsoDate(displayYear, displayMonth, dayNum)
                                val isSelected = iso == selectedDateIso
                                val isToday = displayYear == todayCal.get(Calendar.YEAR) &&
                                    displayMonth == todayCal.get(Calendar.MONTH) &&
                                    dayNum == todayCal.get(Calendar.DAY_OF_MONTH)
                                val hasEvent = eventDatesSet.contains(iso)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> denColors.accent
                                                isToday -> denColors.accentContainer
                                                else -> denColors.secondarySurface.copy(alpha = 0.4f)
                                            }
                                        )
                                        .then(
                                            if (isToday && !isSelected) Modifier.border(1.dp, denColors.accent, RoundedCornerShape(8.dp))
                                            else Modifier
                                        )
                                        .clickable { selectedDateIso = iso },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isSelected) Color.White else denColors.textPrimary
                                        )
                                        if (hasEvent) {
                                            Spacer(Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else denColors.accent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Agenda / Local Events Pane
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(denColors.secondarySurface.copy(alpha = 0.45f))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Events", style = MaterialTheme.typography.titleSmall, color = denColors.textPrimary)
                    Text(selectedDateIso, style = MaterialTheme.typography.bodySmall, color = denColors.textSecondary)
                }
                IconButton(onClick = {
                    eventTitle = ""
                    eventTime = "09:00"
                    showAddDialog = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Event", tint = denColors.accent)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (dayEvents.isEmpty()) {
                Text(
                    "No local events scheduled for $selectedDateIso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = denColors.textSecondary
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(dayEvents, key = { it.id }) { ev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(denColors.windowSurface)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ev.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = denColors.textPrimary)
                                Text(ev.timeLabel, style = MaterialTheme.typography.bodySmall, color = denColors.accent)
                            }
                            IconButton(onClick = { onDeleteEvent(ev.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Event", tint = denColors.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Local Event ($selectedDateIso)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("Event title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = eventTime,
                        onValueChange = { eventTime = it },
                        label = { Text("Time (e.g. 14:30)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eventTitle.isNotBlank()) {
                            onSaveEvent(
                                CalendarEventEntity(
                                    dateIso = selectedDateIso,
                                    title = eventTitle.trim(),
                                    timeLabel = eventTime.trim().ifBlank { "All day" }
                                )
                            )
                            showAddDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatIsoDate(year: Int, monthZeroBased: Int, day: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, day)
