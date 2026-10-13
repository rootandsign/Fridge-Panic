package org.rootandsign.fridgepanic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Food(val name: String, val days: Int)
private val midnight = Color(0xFF101827)
private val mint = Color(0xFF5EEAD4)
private val coral = Color(0xFFFF8E89)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FridgeApp() }
    }
}

@Composable
fun FridgeApp() {
    val foods = remember { mutableStateListOf(Food("Spinach", 1), Food("Milk", 2), Food("Carrots", 6)) }
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("3") }
    var tab by remember { mutableIntStateOf(0) }
    val urgent = foods.count { it.days <= 2 }
    MaterialTheme(colorScheme = darkColorScheme(primary = mint, background = midnight, surface = Color(0xFF24334A))) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Spacer(Modifier.height(22.dp))
            Text("FRIDGE PANIC!", color = mint, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(if (urgent > 0) "Your fridge is panicking! 😱" else "Your fridge is happy! 😎", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("$urgent food(s) need attention", color = coral)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { tab = 0 }, modifier = Modifier.weight(1f)) { Text("My Fridge") }
                Button(onClick = { tab = 1 }, modifier = Modifier.weight(1f)) { Text("Eat Me First!") }
            }
            Spacer(Modifier.height(14.dp))
            if (tab == 0) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Food name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = days, onValueChange = { days = it.filter(Char::isDigit).take(3) }, label = { Text("Days until use-by (estimate)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val n = days.toIntOrNull()
                    if (name.isNotBlank() && n != null) { foods.add(Food(name.trim(), n)); name = ""; days = "3" }
                }, modifier = Modifier.fillMaxWidth()) { Text("Add Food +") }
            } else {
                Text("Use these ingredients soon!", color = mint, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            val visible = if (tab == 0) foods.toList() else foods.filter { it.days <= 2 }.sortedBy { it.days }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible) { food ->
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(food.name, fontWeight = FontWeight.Bold)
                                Text(if (food.days <= 2) "Use soon • ${food.days} day(s)" else "${food.days} day(s) remaining", color = if (food.days <= 2) coral else mint)
                            }
                            TextButton(onClick = { foods.remove(food) }) { Text("Used ✓") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Prototype • Dates are estimates • Data resets when app closes", color = Color.LightGray, fontSize = 11.sp)
        }
    }
}
