package org.rootandsign.fridgepanic

import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.app.DatePickerDialog
import java.time.format.DateTimeFormatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Food(val id: Long, val name: String, val useBy: String, val location: String = "Fridge")
private val mint = Color(0xFF18BDAA)
private val coral = Color(0xFFE66D6D)
private fun daysLeft(food: Food): Long = try {
    ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(food.useBy))
} catch (_: Exception) { 0L }

private fun readFoods(context: Context): List<Food> {
    return try {
        val raw = context.getSharedPreferences("groceries", Context.MODE_PRIVATE).getString("foods", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            Food(obj.getLong("id"), obj.getString("name"), obj.getString("useBy"), obj.optString("location", "Fridge"))
        }
    } catch (_: Exception) { emptyList() }
}
private fun exportFoods(foods: List<Food>): String {
    val entries = JSONArray()
    foods.forEach { food ->
        entries.put(JSONObject().put("id", food.id).put("name", food.name)
            .put("useBy", food.useBy).put("location", food.location))
    }
    return JSONObject().put("format", "fridge-panic-backup")
        .put("version", 1).put("foods", entries).toString(2)
}

private fun importFoods(raw: String): List<Food> {
    val root = JSONObject(raw)
    require(root.optString("format") == "fridge-panic-backup" && root.optInt("version") == 1) {
        "Not a supported Fridge Panic backup"
    }
    val array = root.getJSONArray("foods")
    require(array.length() <= 10000) { "Backup contains too many entries" }
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        val name = obj.getString("name").trim()
        require(name.isNotEmpty() && name.length <= 500) { "Invalid food name" }
        val date = LocalDate.parse(obj.getString("useBy"))
        val place = obj.optString("location", "Fridge")
        require(place in listOf("Fridge", "Freezer", "Pantry")) { "Invalid storage location" }
        Food(obj.getLong("id"), name, date.toString(), place)
    }.also { entries ->
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate food IDs" }
    }
}

private fun saveFoods(context: Context, foods: List<Food>) {
    val array = JSONArray()
    foods.forEach { food ->
        array.put(JSONObject().put("id", food.id).put("name", food.name).put("useBy", food.useBy).put("location", food.location))
    }
    context.getSharedPreferences("groceries", Context.MODE_PRIVATE).edit().putString("foods", array.toString()).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FridgeApp() }
    }
}

@Composable
fun FridgeApp() {
    val context = LocalContext.current
    val foods = remember { mutableStateListOf<Food>().apply { addAll(readFoods(context)) } }
    var name by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("3") }
    var dateMode by rememberSaveable { mutableStateOf(false) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(3).toString()) }
    var selectedDate by remember(selectedDateText) { mutableStateOf(LocalDate.parse(selectedDateText)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var location by rememberSaveable { mutableStateOf("Fridge") }
    var filterLocation by rememberSaveable { mutableStateOf("All") }
    var editing by remember { mutableStateOf<Food?>(null) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editDate by remember { mutableStateOf(LocalDate.now()) }
    var editLocation by rememberSaveable { mutableStateOf("Fridge") }
    var scannerMessage by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<List<Food>?>(null) }
    var backupMessage by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(exportFoods(foods).toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open backup destination")
                backupMessage = "Backup saved. Keep the file somewhere safe."
            } catch (e: Exception) {
                backupMessage = "Backup failed: ${e.message ?: "Could not write file"}"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("Cannot read selected file")
                restorePreview = importFoods(raw)
                backupMessage = ""
            } catch (e: Exception) {
                backupMessage = "Restore failed: ${e.message ?: "Invalid backup"}"
            }
        }
    }
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF5EEAD4), secondary = coral, background = Color(0xFF101827), surface = Color(0xFF24334A))
                 else lightColorScheme(primary = Color(0xFF087F73), secondary = coral, background = Color(0xFFF8FAFC), surface = Color.White)
    val accent = if (dark) Color(0xFF5EEAD4) else Color(0xFF087F73)
    val urgent = foods.count { daysLeft(it) <= 2L }
    val expired = foods.count { daysLeft(it) < 0L }
    val warning = foods.count { daysLeft(it) in 3L..5L }

    MaterialTheme(colorScheme = scheme) {
        Surface(color = scheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
                Spacer(Modifier.height(20.dp))
                Text("FRIDGE PANIC!", color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = scheme.surface), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (expired > 0) "🧊😱" else if (urgent > 0) "🧊😰" else if (warning > 0) "🧊😬" else "🧊😎", fontSize = 42.sp)
                        Column {
                            Text(if (expired > 0) "Oh no! Food expired!" else if (urgent > 0) "Your fridge is panicking!" else if (warning > 0) "Keep an eye on your food!" else "Your fridge is happy!", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(if (urgent == 0) "Everything looks good!" else "$urgent food(s) need attention", color = if (urgent > 0) coral else accent)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { tab = 0 }, modifier = Modifier.weight(1f)) { Text("My Fridge") }
                    Button(onClick = { tab = 1 }, modifier = Modifier.weight(1f)) { Text("Eat Me First!") }
                }
                Spacer(Modifier.height(12.dp))
                if (tab == 0) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Food name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { dateMode = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (!dateMode) scheme.primary else scheme.surface, contentColor = if (!dateMode) scheme.onPrimary else scheme.onSurface)) { Text("Days left") }
                        Button(onClick = { dateMode = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (dateMode) scheme.primary else scheme.surface, contentColor = if (dateMode) scheme.onPrimary else scheme.onSurface)) { Text("Use-by date") }
                    }
                    if (!dateMode) {
                        OutlinedTextField(value = days, onValueChange = { days = it.filter(Char::isDigit).take(4) }, label = { Text("Days until use-by") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    } else {
                        OutlinedButton(onClick = {
                            DatePickerDialog(context, { _, year, month, day ->
                                selectedDate = LocalDate.of(year, month + 1, day)
                                selectedDateText = selectedDate.toString()
                            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose date: ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} 📅")
                        }
                    }
                    Text("Store in", color = accent, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Fridge", "Freezer", "Pantry").forEach { place ->
                            FilterChip(selected = location == place, onClick = { location = place }, label = { Text(place) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val n = days.toLongOrNull()
                            if (name.isNotBlank() && (dateMode || n != null)) {
                                val useBy = if (dateMode) selectedDate else LocalDate.now().plusDays(n!!)
                                foods.add(Food(System.nanoTime(), name.trim(), useBy.toString(), location))
                                scannerMessage = "Added ✓"
                                saveFoods(context, foods)
                                name = ""
                                days = "3"
                            } else {
                                scannerMessage = "Enter a food name and valid days, or choose a use-by date."
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Add Food +") }
                        OutlinedButton(enabled = !scanning, onClick = {
                            scanning = true
                            scannerMessage = ""
                            GmsBarcodeScanning.getClient(context).startScan()
                                .addOnSuccessListener { barcode ->
                                    val code = barcode.rawValue
                                    if (code.isNullOrBlank()) {
                                        scannerMessage = "Could not read barcode. Try again."
                                        scanning = false
                                    } else {
                                        scannerMessage = "Looking up barcode $code..."
                                        Thread {
                                            val productName = try {
                                                val conn = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands").openConnection() as HttpURLConnection
                                                conn.connectTimeout = 7000
                                                conn.readTimeout = 7000
                                                conn.setRequestProperty("User-Agent", "FridgePanic/0.2 (https://github.com/rootandsign/Fridge-Panic)")
                                                try {
                                                    if (conn.responseCode == 200) {
                                                        val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                                                        obj.optJSONObject("product")?.optString("product_name")?.takeIf { it.isNotBlank() }
                                                    } else null
                                                } finally { conn.disconnect() }
                                            } catch (_: Exception) { null }
                                            (context as? android.app.Activity)?.runOnUiThread {
                                                if (productName != null) {
                                                    name = productName
                                                    scannerMessage = "Found $productName! Check name and days, then tap Add Food."
                                                } else {
                                                    scannerMessage = "Barcode $code scanned, but no product name found. Enter its name manually."
                                                }
                                                scanning = false
                                            }
                                        }.start()
                                    }
                                }
                                .addOnCanceledListener { scanning = false }
                                .addOnFailureListener {
                                    scannerMessage = "Scanner unavailable. Enter food manually."
                                    scanning = false
                                }
                        }) { Text(if (scanning) "Wait..." else "Scan 📷") }
                    }
                    if (scannerMessage.isNotBlank()) Text(scannerMessage, fontSize = 12.sp, color = accent)
                } else {
                    Text("Use these ingredients soon!", color = accent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("All", "Fridge", "Freezer", "Pantry").forEach { place ->
                        FilterChip(selected = filterLocation == place, onClick = { filterLocation = place }, label = { Text(place, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                val visible = foods.filter { filterLocation == "All" || it.location == filterLocation }.sortedWith(compareBy<Food> { daysLeft(it) }.thenBy { it.name })
                if (tab == 1) {
                    Text("Soonest use-by date first", color = accent, fontWeight = FontWeight.SemiBold)
                    if (visible.isNotEmpty() && visible.none { daysLeft(it) <= 2L }) Text("🎉 Nothing urgent yet! Here is what to eat next.", color = accent)
                }
                if (visible.isEmpty()) {
                    Text("🧊✨ It's empty here! Add something tasty in My Fridge.", color = accent)
                    Spacer(Modifier.height(10.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visible.forEach { food ->
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(food.name, fontWeight = FontWeight.Bold)
                                    Text(food.location, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                                    val remaining = daysLeft(food)
                                    Text(when {
                                        remaining < 0 -> "Past use-by • ${-remaining} day(s)"
                                        remaining <= 2 -> "Use soon • $remaining day(s)"
                                        else -> "$remaining day(s) remaining"
                                    }, color = when { remaining <= 2 -> coral; remaining <= 5 -> Color(0xFFD89B22); else -> accent })
                                }
                                Column {
                                    TextButton(onClick = {
                                        editing = food
                                        editName = food.name
                                        editDate = try { LocalDate.parse(food.useBy) } catch (_: Exception) { LocalDate.now() }
                                        editLocation = food.location
                                    }) { Text("Edit") }
                                    TextButton(onClick = { foods.remove(food); saveFoods(context, foods) }) { Text("Used ✓") }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Your food backup", color = accent, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        exportLauncher.launch("Fridge-Panic-backup-${LocalDate.now()}.json")
                    }, modifier = Modifier.weight(1f)) { Text("Backup") }
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }, modifier = Modifier.weight(1f)) { Text("Restore") }
                }
                Text("Save a backup before uninstalling or changing phones. Restoring replaces your current food list.", fontSize = 11.sp)
                if (backupMessage.isNotBlank()) Text(backupMessage, color = accent, fontSize = 12.sp)
                Text("Offline • Saved on this phone • Dates are estimates", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
        if (restorePreview != null) {
            AlertDialog(
                onDismissRequest = { restorePreview = null },
                title = { Text("Restore food backup?") },
                text = { Text("This will replace your current ${foods.size} food(s) with ${restorePreview?.size ?: 0} food(s) from the selected backup. This cannot be undone unless you saved a separate backup.") },
                confirmButton = {
                    TextButton(onClick = {
                        val restored = restorePreview
                        if (restored != null) {
                            foods.clear()
                            foods.addAll(restored)
                            saveFoods(context, foods)
                            backupMessage = "Restored ${restored.size} food(s) successfully."
                        }
                        restorePreview = null
                    }) { Text("Replace list") }
                },
                dismissButton = { TextButton(onClick = { restorePreview = null }) { Text("Cancel") } }
            )
        }
        if (editing != null) {
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("Edit grocery") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Food name") }, singleLine = true)
                        OutlinedButton(onClick = {
                            DatePickerDialog(context, { _, y, m, d -> editDate = LocalDate.of(y, m + 1, d) }, editDate.year, editDate.monthValue - 1, editDate.dayOfMonth).show()
                        }) { Text("Use by: ${editDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} 📅") }
                        listOf("Fridge", "Freezer", "Pantry").forEach { place ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = editLocation == place, onClick = { editLocation = place })
                                Text(place)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val current = editing
                        if (current != null && editName.isNotBlank()) {
                            val index = foods.indexOfFirst { it.id == current.id }
                            if (index >= 0) {
                                foods[index] = current.copy(name = editName.trim(), useBy = editDate.toString(), location = editLocation)
                                saveFoods(context, foods)
                            }
                            editing = null
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
            )
        }
    }
}
