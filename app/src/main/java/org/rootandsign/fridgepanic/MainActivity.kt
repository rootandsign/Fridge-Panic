package org.rootandsign.fridgepanic

import android.os.Bundle
import android.content.Context
import android.app.DatePickerDialog
import java.time.format.DateTimeFormatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

data class Food(val id: Long, val name: String, val useBy: String)
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
            Food(obj.getLong("id"), obj.getString("name"), obj.getString("useBy"))
        }
    } catch (_: Exception) { emptyList() }
}
private fun saveFoods(context: Context, foods: List<Food>) {
    val array = JSONArray()
    foods.forEach { food ->
        array.put(JSONObject().put("id", food.id).put("name", food.name).put("useBy", food.useBy))
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
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("3") }
    var dateMode by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now().plusDays(3)) }
    var tab by remember { mutableIntStateOf(0) }
    var scannerMessage by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF5EEAD4), secondary = coral, background = Color(0xFF101827), surface = Color(0xFF24334A))
                 else lightColorScheme(primary = Color(0xFF087F73), secondary = coral, background = Color(0xFFF8FAFC), surface = Color.White)
    val accent = if (dark) Color(0xFF5EEAD4) else Color(0xFF087F73)
    val urgent = foods.count { daysLeft(it) <= 2L }

    MaterialTheme(colorScheme = scheme) {
        Surface(color = scheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Spacer(Modifier.height(20.dp))
                Text("FRIDGE PANIC!", color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (urgent > 0) "Your fridge is panicking! 😱" else "Your fridge is happy! 😎", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("$urgent food(s) need attention", color = coral)
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
                            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose date: ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} 📅")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val n = days.toLongOrNull()
                            if (name.isNotBlank() && (dateMode || n != null)) {
                                val useBy = if (dateMode) selectedDate else LocalDate.now().plusDays(n!!)
                                foods.add(Food(System.nanoTime(), name.trim(), useBy.toString()))
                                saveFoods(context, foods)
                                name = ""
                                days = "3"
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
                Spacer(Modifier.height(12.dp))
                val visible = if (tab == 0) foods.toList().sortedBy { daysLeft(it) } else foods.filter { daysLeft(it) <= 2L }.sortedBy { daysLeft(it) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(visible, key = { it.id }) { food ->
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(food.name, fontWeight = FontWeight.Bold)
                                    val remaining = daysLeft(food)
                                    Text(when {
                                        remaining < 0 -> "Past use-by • ${-remaining} day(s)"
                                        remaining <= 2 -> "Use soon • $remaining day(s)"
                                        else -> "$remaining day(s) remaining"
                                    }, color = if (remaining <= 2) coral else accent)
                                }
                                TextButton(onClick = { foods.remove(food); saveFoods(context, foods) }) { Text("Used ✓") }
                            }
                        }
                    }
                }
                Text("Saved on this phone • Dates are estimates", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}
