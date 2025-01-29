package com.daniel.kseschedule

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.mutableListOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ScheduleViewModel(context: Context) {
    var cont = context
    var scheduleEntries = mutableStateListOf<Pair<String, List<Event>>>()
    var isLoading by mutableStateOf(false)
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("UserGroups", Context.MODE_PRIVATE)

    var groupIDs by mutableStateOf(emptyList<Int>())

    private fun loadGroupIDs(): List<Int> {
        return sharedPreferences.getString("UserGroups", "")
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
    }

    private fun saveGroupIDs(groupIDs: List<Int>) {
        sharedPreferences.edit()
            .putString("UserGroups", groupIDs.joinToString(","))
            .apply()
    }

    fun toggleGroup(groupId: Int) {
        groupIDs = if (groupId in groupIDs) {
            groupIDs - groupId
        } else {
            groupIDs + groupId
        }
        saveGroupIDs(groupIDs)
    }

    fun fetchSchedule() {
        groupIDs = loadGroupIDs()
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            val content = downloadICS(groupIDs)
            isLoading = false
            if (content != null) {
                scheduleEntries.clear()
                scheduleEntries.addAll(parseICS(content))
            }
        }
    }

    fun parseGroups(): List<Pair<Int, String>>? {
        return try {
            val content = readRawFile(R.raw.groups, cont)?.readText(Charsets.UTF_8)
            content?.lines()?.mapNotNull { line ->
                val parts = line.split(" : ")
                if (parts.size == 2) parts[0].trim().toIntOrNull()?.let { id -> id to parts[1].trim() } else null
            }
        } catch (e: Exception) {
            println("Error reading file: ${e.localizedMessage}")
            null
        }
    }

    fun readRawFile(fileResId: Int, context: Context): File? {
        var tempFile: File? = null
        try {
            val inputStream = context.resources.openRawResource(fileResId)
            tempFile = File(context.cacheDir, "temp_groups.txt")
            val outputStream = FileOutputStream(tempFile)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                outputStream.write((line + "\n").toByteArray())
            }
            reader.close()
            outputStream.close()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating temporary file", e)
        }
        return tempFile
    }

    private suspend fun downloadICS(groupIDs: List<Int>): String? {
        return withContext(Dispatchers.IO) {
            try {
                val baseURL = "https://schedule.kse.ua/uk/index/ical"
                val idString = groupIDs.joinToString(",")
                val endDate = calculateEndDate()
                val urlString = "$baseURL?id_grp=$idString&date_end=$endDate"

                URL(urlString).readText()
            } catch (e: Exception) {
                Log.e("Download Error", "${e.message}")
                null
            }
        }
    }

    private fun calculateEndDate(): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val futureDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 30)
        }.time
        return formatter.format(futureDate)
    }

    private fun parseICS(content: String): List<Pair<String, List<Event>>> {
        val events = mutableMapOf<String, MutableList<Event>>()
        var currentEvent: Event? = null
        var isLoadingDesc: Boolean = true

        content.lines().forEach { line ->
            when {
                line.startsWith("BEGIN:VEVENT") -> currentEvent = Event()
                line.startsWith("END:VEVENT") -> currentEvent?.let {
                    val dateKey = formatDate(it.startDate)
                    events.getOrPut(dateKey) { mutableListOf() }.add(it)
                    currentEvent = null
                }
                line.startsWith("SUMMARY:") -> currentEvent?.title = line.removePrefix("SUMMARY:")
                line.startsWith("DTSTART;TZID=Europe/Kiev:") -> currentEvent?.startDate = parseDate(line.removePrefix("DTSTART;TZID=Europe/Kiev:"))
                line.startsWith("DTEND;TZID=Europe/Kiev:") -> currentEvent?.endDate = parseDate(line.removePrefix("DTEND;TZID=Europe/Kiev:"))
                line.startsWith("LOCATION:") -> currentEvent?.location = line.removePrefix("LOCATION:")
                line.startsWith("DESCRIPTION:") -> if (isLoadingDesc) { currentEvent?.desc = line.removePrefix("DESCRIPTION:").split("\\n\\n")[0].replace("\\n", " ")}
                line.startsWith("BEGIN:VALARM") -> isLoadingDesc = false
                line.startsWith("END:VALARM") -> isLoadingDesc = true
            }
        }
        return events.mapNotNull { (dateString, eventList) -> dateString to eventList }
            .sortedBy { reformatDate(it.first) }
            .map {(dateString, eventList) -> dateString to eventList }
    }

    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        return formatter.format(date)
    }
    private fun reformatDate(str: String): Date? {
        val formatter = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        return formatter.parse(str)
    }

    private fun parseDate(dateString: String): Date {
        val formatter = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())
        return formatter.parse(dateString) ?: Date()
    }
}

data class Event(
    var title: String = "",
    var startDate: Date = Date(),
    var endDate: Date = Date(),
    var location: String = "",
    var desc: String = ""
)


data class Group(val name: String, val id: Int) : Comparable<Group> {
    override fun compareTo(other: Group): Int = name.compareTo(other.name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchView(
    viewModel: ScheduleViewModel,
    groups: List<Group>,
    onGroupSelectionChange: (List<Int>) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val filteredGroups = remember(searchText, groups) {
        val filtered = if (searchText.isEmpty()) groups else groups.filter {
            it.name.contains(searchText, ignoreCase = true)
        }
        filtered.sortedBy { it.name }
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Search groups", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.background(MaterialTheme.colorScheme.background))},
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = MaterialTheme.colorScheme.background,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                textColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, shape = RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filteredGroups, key = { it.id }) { group ->
                GroupRow(
                    group = group,
                    isSelected = group.id in viewModel.groupIDs,
                    onClick = {
                        viewModel.toggleGroup(group.id)
                        viewModel.fetchSchedule()
                        onGroupSelectionChange(viewModel.groupIDs)
                    }
                )
            }
        }
    }
}

@Composable
fun GroupRow(group: Group, isSelected: Boolean, onClick: () -> Unit) {
    val icon = if (isSelected) Icons.Default.Clear else Icons.Default.Add
    val color = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Select Group",
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = group.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = color
        )
    }
}





