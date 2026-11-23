package com.rampyare.trader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            val colorScheme = lightColorScheme(
                primary = Color(0xFF1E3A8A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDBEAFE),
                onPrimaryContainer = Color(0xFF1E3A8A),
                secondary = Color(0xFFD97706),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFEF3C7),
                onSecondaryContainer = Color(0xFF92400E),
                surface = Color(0xFFF8FAFC),
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF334155),
                outline = Color(0xFFCBD5E1)
            )
            MaterialTheme(colorScheme = colorScheme) {
                App()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = viewModel()) {
    val ctx = LocalContext.current
    var typed by remember { mutableStateOf("") }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.handle(it) }
    }

    LaunchedEffect(vm.exportReq) {
        vm.exportReq?.let { k ->
            val rows = if (k == "bill") vm.lastBillRows else vm.reportRows()
            if (rows.isEmpty()) vm.msg = "Export ke liye data nahi mila"
            else if (k == "csv") Export.share(ctx, Export.csv(ctx, rows), "text/csv")
            else Export.share(ctx, Export.pdf(ctx, rows), "application/pdf")
            vm.exportReq = null
        }
    }

    LaunchedEffect(vm.waReq) {
        vm.waReq?.let { (mob, text) ->
            val num = mob.filter { it.isDigit() }.let { if (it.length == 10) "91$it" else it }
            val uri = Uri.parse("https://wa.me/$num?text=" + Uri.encode(text))
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
            } catch (e: Exception) {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e2: Exception) {
                    vm.msg = "⚠️ WhatsApp nahi khul saka"
                }
            }
            vm.waReq = null
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.primary) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "रामप्यारे ट्रेडर्स (RamPyare Trader)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                            Text(
                                "सीमेंट एवं भवन निर्माण सामग्री स्टोर",
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2563EB)
                        ) {
                            Text(
                                SimpleDateFormat("dd MMM", Locale.US).format(Date()),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                val tabs = listOf(
                    Triple(0, "🧾 बिल", "Bill"),
                    Triple(1, "📦 स्टॉक", "Stock"),
                    Triple(2, "👥 खाता", "Khata"),
                    Triple(3, "📊 हिसाब", "Ledger"),
                    Triple(4, "📈 मुनाफा", "Profit")
                )
                tabs.forEach { (index, label, sub) ->
                    NavigationBarItem(
                        selected = vm.tab == index,
                        onClick = { vm.tab = index },
                        icon = {
                            Text(label.take(2), fontSize = 20.sp)
                        },
                        label = {
                            Text(
                                label.drop(3),
                                fontWeight = if (vm.tab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US"))
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलिए: जैसे '1 tractor balu 6000', 'cement acc 200 bag'...")
                    }
                    mic.launch(intent)
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Text("🎤", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("BOLO / बोलें", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            // Live Message Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💡", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        vm.msg,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Type & Send Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("कमांड टाइप करें या माइक दबाएं...", fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = {
                            if (typed.isNotBlank()) {
                                vm.handle(typed)
                                typed = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Quick Voice Suggestion Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    SuggestionChip(
                        onClick = { vm.handle("add new product") },
                        label = { Text("+ Naya Product", fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFEFF6FF))
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { vm.handle("add new customer") },
                        label = { Text("+ Naya Grahak", fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFFEF3C7))
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { vm.handle("1 tractor balu 6000") },
                        label = { Text("1 tractor balu 6000", fontSize = 12.sp) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { vm.handle("cement acc 200 bag") },
                        label = { Text("cement acc 200 bag", fontSize = 12.sp) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { vm.handle("churi 2kg") },
                        label = { Text("churi 2kg", fontSize = 12.sp) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { vm.handle("labour 300") },
                        label = { Text("labour 300", fontSize = 12.sp) }
                    )
                }
            }

            // Tab Content
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                when (vm.tab) {
                    0 -> BillTab(vm)
                    1 -> StockTab(vm)
                    2 -> CustTab(vm)
                    3 -> HisabTab(vm)
                    else -> ProfitTab(vm)
                }
            }
        }
    }

    // Modal Dialogs
    if (vm.showAddProductDialog) {
        AddProductDialog(vm)
    }
    if (vm.showEditProductDialog && vm.editingProduct != null) {
        EditProductDialog(vm, vm.editingProduct!!)
    }

    if (vm.showAddCustDialog) {
        AddCustomerDialog(vm)
    }
    if (vm.showEditCustDialog && vm.editingCustomer != null) {
        EditCustomerDialog(vm, vm.editingCustomer!!)
    }

    if (vm.showAddExpenseDialog) {
        AddExpenseDialog(vm)
    }
    if (vm.showAddStockDialog && vm.selectedStockItem != null) {
        AddStockDialog(vm, vm.selectedStockItem!!)
    }
}

/** TAB 0: BILLING SCREEN */
@Composable
fun BillTab(vm: VM) {
    var labourAmt by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Customer Selector Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDBEAFE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                vm.cust?.name?.take(1) ?: "👤",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    vm.cust?.name ?: "कोई ग्राहक नहीं चुना",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A)
                                )
                                if (vm.cust != null) {
                                    IconButton(
                                        onClick = {
                                            vm.editingCustomer = vm.cust
                                            vm.showEditCustDialog = true
                                        },
                                        modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Mobile", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            if (vm.cust != null) {
                                Text("📱 ${vm.cust!!.mobile}", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    "पिछला बाकी: ${m(vm.cust!!.due)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (vm.cust!!.due > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                                )
                            } else {
                                Text("ग्राहक चुनें या बोलें: 'customer ramesh'", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = { vm.tab = 2 },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (vm.cust == null) "चुनें" else "बदलें", fontSize = 12.sp)
                    }
                }
            }
        }

        // Bill Line Items
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("बिल में जुड़े आइटम (${vm.lines.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (vm.lines.isNotEmpty()) {
                    TextButton(onClick = { vm.lines.clear() }) {
                        Text("बिल खाली करें", color = Color(0xFFDC2626), fontSize = 12.sp)
                    }
                }
            }
        }

        if (vm.lines.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🧾", fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("बिल अभी खाली है", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "माइक दबाकर बोलें: '1 tractor balu 6000', 'cement acc 200 bag', या 'churi 2kg'",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(vm.lines) { i, l ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(l.item, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "${fmt(l.qty)} ${l.unit}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Text(
                            m(l.amount),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { vm.lines.removeAt(i) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }

        // Add Labour charge row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = labourAmt,
                        onValueChange = { labourAmt = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("लेबर / पल्लेदारी चार्ज ₹", fontSize = 12.sp) },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val a = labourAmt.toDoubleOrNull() ?: 0.0
                            if (a > 0) {
                                vm.lines.add(Line("Labour (लेबर)", 1.0, "charge", a))
                                labourAmt = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ जोड़ें", fontSize = 12.sp)
                    }
                }
            }
        }

        // Bill Summary & Calculation Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("इस बिल का कुल (Total):", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(m(vm.total), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = if (vm.paid == 0.0) "" else fmt(vm.paid),
                        onValueChange = { vm.paid = it.toDoubleOrNull() ?: 0.0 },
                        label = { Text("जमा रकम (Cash / Online Paid ₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Text("₹", fontWeight = FontWeight.Bold) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("इस बिल का बाकी:", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(m(vm.total - vm.paid), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val oldDue = vm.cust?.due ?: 0.0
                    val finalDue = oldDue + vm.total - vm.paid
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("नया कुल बाकी (पुराना + नया):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(m(finalDue), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (finalDue > 0) Color(0xFFDC2626) else Color(0xFF16A34A))
                    }

                    Spacer(Modifier.height(14.dp))

                    // Action Buttons
                    Button(
                        onClick = { vm.save() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("💾 बिल सेव करें (Save Bill)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.whatsapp() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            enabled = vm.lastBillText.isNotEmpty()
                        ) {
                            Text("📲 WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { vm.exportReq = "bill" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            enabled = vm.lastBillRows.isNotEmpty()
                        ) {
                            Text("📄 PDF बिल", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** TAB 1: STOCK & INVENTORY SCREEN */
@Composable
fun StockTab(vm: VM) {
    val itemsList = vm.items.value
    val totalStockVal = itemsList.sumOf { it.stock * it.avgCost }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Stock Header Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("दुकान में कुल स्टॉक वैल्यू", color = Color(0xFF93C5FD), fontSize = 12.sp)
                    Text(m(totalStockVal), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("कुल प्रोडक्ट्स: ${itemsList.size}", color = Color.White, fontSize = 13.sp)
                        Button(
                            onClick = {
                                vm.initialDialogName = ""
                                vm.showAddProductDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("+ नया प्रोडक्ट जोड़ें", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        items(itemsList) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "यूनिट: ${item.unit}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                item.stock <= 0 -> Color(0xFFFEE2E2)
                                item.stock < 10 -> Color(0xFFFEF3C7)
                                else -> Color(0xFFDCFCE7)
                            }
                        ) {
                            Text(
                                "${fmt(item.stock)} ${item.unit}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = when {
                                    item.stock <= 0 -> Color(0xFFDC2626)
                                    item.stock < 10 -> Color(0xFFB45309)
                                    else -> Color(0xFF15803D)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("खरीद औसत: ${m(item.avgCost)}/${item.unit}", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("बिक्री रेट: ${m(item.sellRate)}/${item.unit}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("कुल माल: ${m(item.stock * item.avgCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                vm.selectedStockItem = item
                                vm.showAddStockDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("+ स्टॉक बढ़ाएं", fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                vm.editingProduct = item
                                vm.showEditProductDialog = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { vm.deleteItem(item) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}

/** TAB 2: CUSTOMERS & KHATA SCREEN */
@Composable
fun CustTab(vm: VM) {
    var searchQuery by remember { mutableStateOf("") }
    val custList = vm.customers.value
    val filtered = if (searchQuery.isBlank()) {
        vm.matches.ifEmpty { custList }
    } else {
        custList.filter { it.name.contains(searchQuery, true) || it.mobile.contains(searchQuery) }
    }
    val totalDue = custList.sumOf { it.due }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Khata Header Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("बाजार में कुल उधार (Market Due)", color = Color(0xFFFECACA), fontSize = 12.sp)
                    Text(m(totalDue), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("कुल ग्राहक: ${custList.size}", color = Color.White, fontSize = 13.sp)
                        Button(
                            onClick = {
                                vm.initialDialogName = ""
                                vm.showAddCustDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("+ नया ग्राहक जोड़ें", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("नाम या 10-digit मोबाइल से खोजें...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }

        items(filtered) { c ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(c.name.take(1), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                IconButton(
                                    onClick = {
                                        vm.editingCustomer = c
                                        vm.showEditCustDialog = true
                                    },
                                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text("📱 ${c.mobile}", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            m(c.due),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (c.due > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                        )
                        Text(
                            if (c.due > 0) "बाकी है" else "चुकता",
                            fontSize = 11.sp,
                            color = if (c.due > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = {
                                    vm.pick(c)
                                    vm.tab = 0
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("बिल बनाएं", fontSize = 11.sp)
                            }
                            if (c.due > 0) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        vm.pick(c)
                                        vm.remind()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("📲", fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** TAB 3: LEDGER & HISAB SCREEN */
@Composable
fun HisabTab(vm: VM) {
    val df = SimpleDateFormat("dd-MM-yy hh:mm a", Locale.US)
    val totalSales = vm.bills.value.sumOf { it.total }
    val totalProfit = vm.bills.value.sumOf { it.total - it.cost }
    val marketDue = vm.customers.value.sumOf { it.due }
    val stockVal = vm.items.value.sumOf { it.stock * it.avgCost }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text("दुकान का संपूर्ण हिसाब-किताब", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // 4 KPI Cards Grid
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard(title = "कुल बिक्री (Sales)", value = m(totalSales), icon = "💰", color = Color(0xFF2563EB), modifier = Modifier.weight(1f))
                KpiCard(title = "कुल मुनाफा (Profit)", value = m(totalProfit), icon = "📈", color = Color(0xFF16A34A), modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard(title = "उधार बाकी (Due)", value = m(marketDue), icon = "👥", color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                KpiCard(title = "स्टॉक वैल्यू", value = m(stockVal), icon = "📦", color = Color(0xFFD97706), modifier = Modifier.weight(1f))
            }
        }

        // Export Report Buttons
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("रिपोर्ट डाउनलोड / शेयर करें", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.exportReq = "pdf" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("📄 PDF रिपोर्ट")
                        }
                        OutlinedButton(
                            onClick = { vm.exportReq = "csv" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("📊 Excel (CSV)")
                        }
                    }
                }
            }
        }

        // Selected Customer Transaction Ledger
        item {
            if (vm.cust != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${vm.cust!!.name} का खाता", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    IconButton(
                                        onClick = {
                                            vm.editingCustomer = vm.cust
                                            vm.showEditCustDialog = true
                                        },
                                        modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text("मोबाइल: ${vm.cust!!.mobile} | बाकी: ${m(vm.cust!!.due)}", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                            Button(
                                onClick = { vm.remind() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("📲 याद दिलाएं", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (vm.ledger.isNotEmpty()) {
            item {
                Text("लेनदेन इतिहास (Transaction History)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            items(vm.ledger) { txn ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (txn.type == "BILL") "🧾 बिल कटा" else "💵 रकम जमा (Payment)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (txn.type == "BILL") Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                            Text(df.format(Date(txn.date)), fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                        Text(
                            m(txn.amount),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (txn.type == "BILL") Color(0xFFDC2626) else Color(0xFF16A34A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KpiCard(title: String, value: String, icon: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 11.sp, color = Color(0xFF64748B))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/** TAB 4: MUNAFA & PROFIT ANALYTICS SCREEN */
@Composable
fun ProfitTab(vm: VM) {
    val L = vm.saleLines.value
    val P = vm.purchases.value
    val E = vm.expenses.value
    val now = System.currentTimeMillis()
    val ws = Analytics.weekStart(now)
    val ms = Analytics.monthStart(now)

    var sel by remember { mutableStateOf(1) }
    val names = listOf("इस हफ्ते", "इस महीने", "कुल")
    val froms = listOf(ws, ms, 0L)
    val per = Analytics.period(names[sel], froms[sel], Long.MAX_VALUE, L, P, E)
    val prods = Analytics.products(L, froms[sel], Long.MAX_VALUE)
    val margin = if (per.revenue > 0) per.net / per.revenue * 100 else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Time filter selector
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n ->
                    FilterChip(
                        selected = sel == i,
                        onClick = { sel = i },
                        label = { Text(n, fontWeight = if (sel == i) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Net Profit Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (per.net >= 0) Color(0xFF14532D) else Color(0xFF7F1D1D)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("${names[sel]} का शुद्ध मुनाफा (Net Profit)", color = Color(0xFFBBF7D0), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(m(per.net), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("मार्जिन: ${"%.1f".format(margin)}%", color = Color(0xFF86EFAC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF22C55E).copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("कुल बिक्री (Revenue)", color = Color(0xFFBBF7D0), fontSize = 11.sp)
                            Text(m(per.revenue), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("माल लागत (Cost)", color = Color(0xFFBBF7D0), fontSize = 11.sp)
                            Text(m(per.revenue - per.gross), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("दुकान खर्च (Expense)", color = Color(0xFFBBF7D0), fontSize = 11.sp)
                            Text(m(per.expense), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Hero Product Card
        if (prods.isNotEmpty()) {
            val hero = prods.first()
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🏆", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("सबसे ज्यादा मुनाफा देने वाला प्रोडक्ट", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF92400E))
                            Text("${hero.name} — मुनाफा: ${m(hero.profit)}", fontSize = 13.sp, color = Color(0xFF78350F))
                        }
                    }
                }
            }
        }

        // Add Expense Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("दुकान का दैनिक खर्च", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("डीजल, चाय, बिजली, गाड़ी रिपेयरिंग", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Button(
                        onClick = { vm.showAddExpenseDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("+ खर्च जोड़ें", fontSize = 12.sp)
                    }
                }
            }
        }

        // Product-wise breakdown
        item {
            Text("प्रोडक्ट अनुसार मुनाफा (${names[sel]})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(prods) { p ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("बिका: ${fmt(p.qty)} | बिक्री: ${m(p.revenue)}", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "मुनाफा ${m(p.profit)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (p.profit >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                        val prodMargin = if (p.revenue > 0) p.profit / p.revenue * 100 else 0.0
                        Text("${"%.1f".format(prodMargin)}% मार्जिन", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

/** DIALOG: ADD PRODUCT */
@Composable
fun AddProductDialog(vm: VM) {
    var name by remember { mutableStateOf(vm.initialDialogName) }
    var unit by remember { mutableStateOf("bag") }
    var qty by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var sellRate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { vm.showAddProductDialog = false },
        title = { Text("नया प्रोडक्ट जोड़ें (Add Product)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("प्रोडक्ट का नाम (जैसे: Cement ACC)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("यूनिट चुनें:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val units = listOf("bag", "tractor", "kg", "pcs", "ton", "trolley")
                    items(units) { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u) }
                        )
                    }
                }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("शुरुआती स्टॉक क्वांटिटी ($unit)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("कुल खरीद मूल्य ₹ (Total Cost)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = sellRate,
                    onValueChange = { sellRate = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("बिक्री रेट प्रति $unit ₹") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qty.toDoubleOrNull() ?: 0.0
                    val cp = costPrice.toDoubleOrNull() ?: 0.0
                    val sr = sellRate.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        vm.addItem(name, unit, q, cp, sr)
                    }
                }
            ) {
                Text("सेव करें (Save)")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showAddProductDialog = false }) {
                Text("रद्द करें")
            }
        }
    )
}

/** DIALOG: EDIT PRODUCT */
@Composable
fun EditProductDialog(vm: VM, item: Item) {
    var name by remember { mutableStateOf(item.name) }
    var unit by remember { mutableStateOf(item.unit) }
    var stock by remember { mutableStateOf(fmt(item.stock)) }
    var avgCost by remember { mutableStateOf(fmt(item.avgCost)) }
    var sellRate by remember { mutableStateOf(fmt(item.sellRate)) }

    AlertDialog(
        onDismissRequest = { vm.showEditProductDialog = false },
        title = { Text("प्रोडक्ट एडिट करें", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("प्रोडक्ट का नाम") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("यूनिट:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val units = listOf("bag", "tractor", "kg", "pcs", "ton", "trolley")
                    items(units) { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u) }
                        )
                    }
                }
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("स्टॉक क्वांटिटी ($unit)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = avgCost,
                    onValueChange = { avgCost = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("खरीद औसत रेट प्रति $unit ₹") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = sellRate,
                    onValueChange = { sellRate = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("बिक्री रेट प्रति $unit ₹") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val st = stock.toDoubleOrNull() ?: item.stock
                    val ac = avgCost.toDoubleOrNull() ?: item.avgCost
                    val sr = sellRate.toDoubleOrNull() ?: item.sellRate
                    if (name.isNotBlank()) {
                        vm.updateItem(item.name, name, unit, st, ac, sr)
                    }
                }
            ) {
                Text("अपडेट करें")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showEditProductDialog = false }) {
                Text("रद्द करें")
            }
        }
    )
}

/** DIALOG: ADD CUSTOMER */
@Composable
fun AddCustomerDialog(vm: VM) {
    var name by remember { mutableStateOf(vm.initialDialogName) }
    var mobile by remember { mutableStateOf("") }
    var initialDue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { vm.showAddCustDialog = false },
        title = { Text("नया ग्राहक जोड़ें (Add Customer)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ग्राहक का नाम (Customer Name)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it.filter(Char::isDigit).take(10) },
                    label = { Text("10-Digit मोबाइल नंबर") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = initialDue,
                    onValueChange = { initialDue = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("पुराना बाकी उधार ₹ (यदि है)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val due = initialDue.toDoubleOrNull() ?: 0.0
                    val cleanMob = mobile.filter(Char::isDigit).let { if (it.length > 10) it.takeLast(10) else it }
                    if (name.isNotBlank() && cleanMob.length >= 10) {
                        vm.newCust(name, cleanMob, due)
                    }
                }
            ) {
                Text("ग्राहक जोड़ें")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showAddCustDialog = false }) {
                Text("रद्द करें")
            }
        }
    )
}

/** DIALOG: EDIT CUSTOMER (Wrong mobile/name fix) */
@Composable
fun EditCustomerDialog(vm: VM, customer: Customer) {
    var name by remember { mutableStateOf(customer.name) }
    var mobile by remember { mutableStateOf(customer.mobile) }
    var due by remember { mutableStateOf(fmt(customer.due)) }

    AlertDialog(
        onDismissRequest = { vm.showEditCustDialog = false },
        title = { Text("ग्राहक का विवरण बदलें (Edit)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ग्राहक का नाम") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it.filter(Char::isDigit).take(10) },
                    label = { Text("सही 10-Digit मोबाइल नंबर") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    label = { Text("बाकी उधार रकम ₹") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val d = due.toDoubleOrNull() ?: customer.due
                        val cleanMob = mobile.filter(Char::isDigit).let { if (it.length > 10) it.takeLast(10) else it }
                        if (name.isNotBlank() && cleanMob.length >= 10) {
                            vm.updateCust(customer.id, name, cleanMob, d)
                        }
                    }
                ) {
                    Text("सेव करें (Save)")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { vm.deleteCust(customer) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                ) {
                    Text("डिलीट करें")
                }
                TextButton(onClick = { vm.showEditCustDialog = false }) {
                    Text("रद्द करें")
                }
            }
        }
    )
}

/** DIALOG: ADD STOCK TO EXISTING ITEM */
@Composable
fun AddStockDialog(vm: VM, item: Item) {
    var qty by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { vm.showAddStockDialog = false },
        title = { Text("${item.name} का स्टॉक बढ़ाएं", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("वर्तमान स्टॉक: ${fmt(item.stock)} ${item.unit}", fontSize = 13.sp, color = Color(0xFF64748B))
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("कितना माल आया? (${item.unit})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("कुल खरीद मूल्य ₹ (Total Cost)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qty.toDoubleOrNull() ?: 0.0
                    val cp = costPrice.toDoubleOrNull() ?: 0.0
                    if (q > 0 && cp > 0) {
                        vm.buy(Line(item.name, q, item.unit, cp))
                    }
                }
            ) {
                Text("स्टॉक में जोड़ें")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showAddStockDialog = false }) {
                Text("रद्द करें")
            }
        }
    )
}

/** DIALOG: ADD EXPENSE */
@Composable
fun AddExpenseDialog(vm: VM) {
    var note by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { vm.showAddExpenseDialog = false },
        title = { Text("दुकान खर्च दर्ज करें (Add Expense)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("किस चीज़ का खर्च? (जैसे: डीजल, चाय, बिजली)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amt,
                    onValueChange = { amt = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("रकम ₹ (Amount)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = amt.toDoubleOrNull() ?: 0.0
                    if (a > 0) {
                        vm.addExpense(note, a)
                    }
                }
            ) {
                Text("खर्च जोड़ें")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showAddExpenseDialog = false }) {
                Text("रद्द करें")
            }
        }
    )
}
