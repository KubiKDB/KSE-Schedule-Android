package com.daniel.kseschedule

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daniel.kseschedule.Group
import com.daniel.kseschedule.ScheduleViewModel
import com.daniel.kseschedule.SearchView
import com.daniel.kseschedule.ui.theme.KSEScheduleTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KSEScheduleTheme {
                ScheduleView(viewModel = ScheduleViewModel(this))
            }
        }
    }
}
///Місце відображення пар
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleView(viewModel: ScheduleViewModel) {
    val context = LocalContext.current
    var showSelection by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf(emptyList<Group>()) }

    ///Витягування груп
    LaunchedEffect(Unit) {
        val parsedGroups = viewModel.parseGroups() ?: emptyList()
        groups = parsedGroups.map { (id, name) -> Group(name, id) }
        viewModel.fetchSchedule()
    }


    ///Верхній рядок
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("KSE Schedule") }, actions = {
                Button(onClick = { showSelection = true }) {
                    Text("Groups", color = MaterialTheme.colorScheme.secondary)
                }
            })
        }
    ) { paddingValues ->
        Column(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            when {
                viewModel.groupIDs.size > 20 -> Text("Too many groups selected", modifier = Modifier.padding(16.dp))
                viewModel.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.scheduleEntries) { dayTuple ->
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(dayTuple.first, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            dayTuple.second.forEach { event ->
                                Card(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text("${formatDate(event.startDate)} - ${formatDate(event.endDate)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                                        Text(event.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                                        Text(event.location, style = MaterialTheme.typography.bodySmall)
                                        Text(event.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ///Місце вибору груп
    if (showSelection) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = { showSelection = false },
            confirmButton = {},
            text = {
                SearchView(viewModel = viewModel, groups = groups) { updatedGroups ->
                    viewModel.groupIDs = updatedGroups
                }
            }
        )
    }
}

fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(date)
}
