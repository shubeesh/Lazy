package com.example.statesandlistsapp

import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

// 1) Data Model
@Parcelize
data class ShoppingItem(
    val id: Int,
    val name: String,
    val quantity: Int,
    val category: String,
    val purchased: Boolean = false,
    val favorite: Boolean = false
) : Parcelable



private val categoryColors = mapOf(
    "Produce" to Color(0xFF81C784),
    "Dairy" to Color(0xFFFFF59D),
    "Meat" to Color(0xFFFFAB91),
    "Bakery" to Color(0xFFB39DDB),
    "Other" to Color(0xFF90CAF9)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    ShoppingListScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen() {

    val items = remember { mutableStateListOf<ShoppingItem>() }

    // Inputs (saved across configuration changes)
    var inputName by rememberSaveable { mutableStateOf("") }
    var inputQuantity by rememberSaveable { mutableStateOf("1") }
    var selectedCategory by rememberSaveable { mutableStateOf("Produce") }
    var nextId by rememberSaveable { mutableStateOf(1) }

    // Filtering/searching/sort
    var filterMode by rememberSaveable { mutableStateOf("all") } // "all", "purchased", "to_buy"
    var sortMode by rememberSaveable { mutableStateOf("default") } // "default", "name", "category", "quantity"
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val scaffoldState = rememberScaffoldState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var recentlyDeleted: ShoppingItem? by remember { mutableStateOf(null) }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Shopping List") })
        },
        floatingActionButton = {
            SortingAndActionsFab(
                onClearPurchased = {
                    val removed = items.filter { it.purchased }
                    if (removed.isNotEmpty()) {
                        items.removeAll(removed)
                    }
                },
                onReset = {
                    items.clear()
                    nextId = 1
                },
                onSortBy = { mode ->
                    sortMode = mode
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 4) Input Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Item name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputQuantity,
                        onValueChange = { new ->
                            // allow only numbers
                            if (new.all { it.isDigit() }) {
                                inputQuantity = new
                            }
                        },
                        label = { Text("Quantity") },
                        modifier = Modifier.width(120.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    CategorySelector(
                        selected = selectedCategory,
                        onSelectionChange = { selectedCategory = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = {
                            val name = inputName.trim()
                            val quantity = inputQuantity.toIntOrNull() ?: 1
                            if (name.isNotEmpty()) {
                                val newItem = ShoppingItem(
                                    id = nextId,
                                    name = name,
                                    quantity = quantity,
                                    category = selectedCategory
                                )
                                items.add(newItem)
                                nextId++
                                inputName = ""
                                inputQuantity = "1"
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please enter an item name")
                                }
                            }
                        }) {
                            Text("Add item")
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChipGroup(
                    filterMode = filterMode,
                    onFilterChange = { filterMode = it }
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            StatisticsCard(items = items)

            Spacer(Modifier.height(12.dp))

            val filtered = remember(items, filterMode, searchQuery) {
                items.filter { item ->
                    val passesFilter = when (filterMode) {
                        "purchased" -> item.purchased
                        "to_buy" -> !item.purchased
                        else -> true
                    }
                    val matchesSearch = item.name.contains(searchQuery, ignoreCase = true)
                    passesFilter && matchesSearch
                }
            }

            val grouped = remember(filtered, sortMode) {
                val groupedRaw = filtered.groupBy { it.category }
                val categories = groupedRaw.keys.sorted()
                val sortedMap = categories.associateWith { catItems ->
                    when (sortMode) {
                        "name" -> groupedRaw[catItems]!!.sortedBy { it.name.lowercase() }
                        "quantity" -> groupedRaw[catItems]!!.sortedByDescending { it.quantity }
                        "category" -> groupedRaw[catItems]!!.sortedBy { it.category }
                        else -> groupedRaw[catItems]!!.sortedBy { it.id }
                    }
                }
                sortedMap
            }


            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No items yet — add your first item!")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .animateContentSize()
                ) {
                    grouped.forEach { (category, catItems) ->
                        stickyHeader {
                            Surface(
                                tonalElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(categoryColors[category] ?: Color.Gray)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(category, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.weight(1f))
                                    Text("${catItems.size}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        items(catItems, key = { it.id }) { item ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                ShoppingItemCard(
                                    item = item,
                                    onTogglePurchased = {
                                        val idx = items.indexOfFirst { it.id == item.id }
                                        if (idx >= 0) {
                                            items[idx] = items[idx].copy(purchased = !items[idx].purchased)
                                        }
                                    },
                                    onDelete = {
                                        // remove and show undo snackbar
                                        recentlyDeleted = item
                                        items.remove(item)
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Deleted ${item.name}",
                                                actionLabel = "Undo"
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                recentlyDeleted?.let { deleted ->
                                                    items.add(deleted)
                                                    recentlyDeleted = null
                                                }
                                            } else {
                                                recentlyDeleted = null
                                            }
                                        }
                                    },
                                    onToggleFavorite = {
                                        val idx = items.indexOfFirst { it.id == item.id }
                                        if (idx >= 0) {
                                            items[idx] = items[idx].copy(favorite = !items[idx].favorite)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Filter Chip Row
@Composable
fun FilterChipGroup(
    filterMode: String,
    onFilterChange: (String) -> Unit
) {
    Row {
        FilterChip(
            selected = filterMode == "all",
            onClick = { onFilterChange("all") },
            label = { Text("All") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = filterMode == "to_buy",
            onClick = { onFilterChange("to_buy") },
            label = { Text("To Buy") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = filterMode == "purchased",
            onClick = { onFilterChange("purchased") },
            label = { Text("Purchased") }
        )
    }
}

// 5) Statistics Card
@Composable
fun StatisticsCard(items: List<ShoppingItem>) {
    val total = items.size
    val purchased = items.count { it.purchased }
    val remaining = total - purchased
    val categories = items.map { it.category }.distinct().size
    val progress = if (total == 0) 0f else (purchased.toFloat() / total.toFloat())

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total items: $total", style = MaterialTheme.typography.bodyLarge)
                    Text("Purchased: $purchased • Remaining: $remaining", style = MaterialTheme.typography.bodyMedium)
                    Text("Categories: $categories", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.weight(1f))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

// 8) Item Card Component
@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    onTogglePurchased: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .animateContentSize(),
        totalElevation = 2.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Checkbox(
                checked = item.purchased,
                onCheckedChange = { onTogglePurchased() }
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.purchased) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(Modifier.height(2.dp))
                Text("Qty: ${item.quantity} • ${item.category}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = if (item.favorite) Color.Red else Color.Gray
                )
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

// 9) Category Helper
@Composable
fun CategorySelector(
    selected: String,
    onSelectionChange: (String) -> Unit
) {
    val categories = listOf("Produce", "Dairy", "Meat", "Bakery", "Other")
    Row {
        categories.forEach { cat ->
            AssistChip(
                onClick = { onSelectionChange(cat) },
                label = { Text(cat) },
                selected = selected == cat,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

// Floating action button with sorting and actions
@Composable
fun SortingAndActionsFab(
    onClearPurchased: () -> Unit,
    onReset: () -> Unit,
    onSortBy: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            text = { Text("Actions") },
            icon = { Icon(Icons.Default.MoreVert, contentDescription = "Actions") },
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Clear purchased") },
                onClick = {
                    onClearPurchased()
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Reset list") },
                onClick = {
                    onReset()
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by name") },
                onClick = {
                    onSortBy("name")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by quantity") },
                onClick = {
                    onSortBy("quantity")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Sort by category") },
                onClick = {
                    onSortBy("category")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Default sort") },
                onClick = {
                    onSortBy("default")
                    expanded = false
                }
            )
        }
    }
}}