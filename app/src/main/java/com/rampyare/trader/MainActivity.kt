package com.rampyare.trader
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); setContent { MaterialTheme { App() } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(vm: VM = viewModel()) {
    val ctx = LocalContext.current
    var typed by remember { mutableStateOf("") }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.handle(it) }
    }
    LaunchedEffect(vm.exportReq) {
        vm.exportReq?.let { k ->
            val rows = if (k == "bill") vm.lastBillRows else vm.reportRows()
            if (rows.isEmpty()) vm.msg = "Export ke liye kuch nahi hai"
            else if (k == "csv") Export.share(ctx, Export.csv(ctx, rows), "text/csv") else Export.share(ctx, Export.pdf(ctx, rows), "application/pdf")
            vm.exportReq = null
        }
    }
    LaunchedEffect(vm.waReq) {
        vm.waReq?.let { (mob, text) ->
            val num = mob.filter { it.isDigit() }.let { if (it.length == 10) "91$it" else it }
            val uri = Uri.parse("https://wa.me/$num?text=" + Uri.encode(text))
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")) }
            catch (e: Exception) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e2: Exception) { vm.msg = "WhatsApp nahi khula" }
            }
            vm.waReq = null
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("RamPyare Trader") }) },
        bottomBar = {
            NavigationBar {
                listOf("🧾 Bill", "📦 Stock", "👥 Customer", "📊 Hisab", "📈 Munafa").forEachIndexed { i, s ->
                    NavigationBarItem(selected = vm.tab == i, onClick = { vm.tab = i }, icon = { Text(s.take(2)) }, label = { Text(s.drop(3)) })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                mic.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN").putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo..."))
            }) { Text("🎤 BOLO") }
        }
    ) { p ->
        Column(Modifier.padding(p).padding(12.dp).fillMaxSize()) {
            Text(vm.msg, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), label = { Text("Ya type karo (same command)") }, singleLine = true)
                TextButton({ vm.handle(typed); typed = "" }) { Text("Send") }
            }
            when (vm.tab) { 0 -> BillTab(vm); 1 -> StockTab(vm); 2 -> CustTab(vm); 3 -> HisabTab(vm); else -> ProfitTab(vm) }
        }
    }
}

@Composable fun BillTab(vm: VM) {
    LazyColumn {
        item { Text(vm.cust?.let { "👤 ${it.name} • ${it.mobile} • Purana due ${m(it.due)}" } ?: "Customer chuno (bolo: customer ramesh)", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(vm.lines) { i, l ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("${l.item} ${fmt(l.qty)} ${l.unit}", Modifier.weight(1f)); Text(m(l.amount)); TextButton({ vm.lines.removeAt(i) }) { Text("✕") }
            }
        }
        item {
            Text("Total ${m(vm.total)}", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(if (vm.paid == 0.0) "" else fmt(vm.paid), { vm.paid = it.toDoubleOrNull() ?: 0.0 }, label = { Text("Paid (jama)") })
            Text("Is bill ka due ${m(vm.total - vm.paid)}")
            Text("Naya kul due (purana + is bill ka) = ${m((vm.cust?.due ?: 0.0) + vm.total - vm.paid)}", style = MaterialTheme.typography.titleMedium)
            Row { Button({ vm.save() }) { Text("Bill Save") }; Spacer(Modifier.width(8.dp))
                OutlinedButton({ vm.exportReq = "bill" }, enabled = vm.lastBillRows.isNotEmpty()) { Text("Last Bill PDF") } }
            Button({ vm.whatsapp() }, Modifier.padding(top = 8.dp), enabled = vm.lastBillText.isNotEmpty()) { Text("📲 WhatsApp pe bill bhejo") }
        }
    }
}

@Composable fun StockTab(vm: VM) {
    var n by remember { mutableStateOf("") }; var u by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }; var pr by remember { mutableStateOf("") }
    LazyColumn {
        items(vm.items.value) { i ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(10.dp)) {
                Text("${i.name}: ${fmt(i.stock)} ${i.unit}", style = MaterialTheme.typography.titleMedium)
                Text("Kharid avg ${m(i.avgCost)}/${i.unit} • Bikri rate ${m(i.sellRate)}/${i.unit} • Value ${m(i.stock * i.avgCost)}") } }
        }
        item {
            Text("Naya product ya purane ka stock add (bolo: naya item cement acc 100 bori 35000)", Modifier.padding(top = 12.dp))
            OutlinedTextField(n, { n = it }, label = { Text("Naam (jaise: Cement ACC)") })
            OutlinedTextField(u, { u = it }, label = { Text("Unit (bag/kg/tractor/pcs)") })
            OutlinedTextField(q, { q = it }, label = { Text("Quantity") })
            OutlinedTextField(pr, { pr = it }, label = { Text("Kul kharid price") })
            Button({ vm.addItem(n, u.ifBlank { "pcs" }, q.toDoubleOrNull() ?: 0.0, pr.toDoubleOrNull() ?: 0.0); n = ""; u = ""; q = ""; pr = "" }) { Text("Add Product / Stock") }
        }
    }
}

@Composable fun CustTab(vm: VM) {
    var s by remember { mutableStateOf("") }; var n by remember { mutableStateOf("") }; var mo by remember { mutableStateOf("") }
    val list = if (s.isBlank()) vm.matches.ifEmpty { vm.customers.value } else vm.customers.value.filter { it.name.contains(s, true) || it.mobile.contains(s) }
    LazyColumn {
        item { OutlinedTextField(s, { s = it }, Modifier.fillMaxWidth(), label = { Text("Naam ya mobile se dhundo") }) }
        items(list) { c ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { vm.pick(c); vm.tab = 0 }) {
                Column(Modifier.padding(10.dp)) { Text("${c.name}  •  📱 ${c.mobile}", style = MaterialTheme.typography.titleMedium); Text("Due ${m(c.due)}") }
            }
        }
        item {
            Text("Naya customer", Modifier.padding(top = 12.dp))
            OutlinedTextField(n, { n = it }, label = { Text("Naam") })
            OutlinedTextField(mo, { mo = it.filter(Char::isDigit).take(10) }, label = { Text("Mobile (10 digit)") })
            Button({ vm.newCust(n, mo); n = ""; mo = "" }) { Text("Add Customer") }
        }
    }
}

@Composable fun HisabTab(vm: VM) {
    val df = SimpleDateFormat("dd-MM HH:mm", Locale.US)
    LazyColumn {
        item {
            Text("Stock value: ${m(vm.items.value.sumOf { it.stock * it.avgCost })}")
            Text("Customers se lena (due): ${m(vm.customers.value.sumOf { it.due })}")
            Text("Kul bikri: ${m(vm.bills.value.sumOf { it.total })}")
            Text("Munafa (labour sahit): ${m(vm.bills.value.sumOf { it.total - it.cost })}")
            Row(Modifier.padding(vertical = 8.dp)) { Button({ vm.exportReq = "pdf" }) { Text("PDF") }; Spacer(Modifier.width(8.dp)); Button({ vm.exportReq = "csv" }) { Text("Excel") } }
            vm.cust?.let { Text("${it.name} (${it.mobile}) ledger • due ${m(it.due)}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton({ vm.remind() }) { Text("📲 Due yaad dilao (WhatsApp)") } }
        }
        items(vm.ledger) { Text("${df.format(Date(it.date))}  ${if (it.type == "BILL") "Bill" else "Jama"}  ${m(it.amount)}") }
    }
}

@Composable fun Bar(frac: Double) {
    Box(Modifier.fillMaxWidth(frac.toFloat().coerceIn(0.02f, 1f)).height(6.dp).background(MaterialTheme.colorScheme.primary))
}

@Composable fun PeriodRow(p: Period, max: Double) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(p.label, style = MaterialTheme.typography.titleSmall)
        Text("Bikri ${m(p.revenue)} • Kharid ${m(p.invest)} • Profit ${m(p.gross)} • Kharcha ${m(p.expense)}")
        Text("Pure profit ${m(p.net)}", color = if (p.net < 0) Color.Red else Color(0xFF2E7D32))
        Bar(p.revenue / max.coerceAtLeast(1.0))
    }
}

@Composable fun ProfitTab(vm: VM) {
    val L = vm.saleLines.value; val P = vm.purchases.value; val E = vm.expenses.value
    val now = System.currentTimeMillis(); val end = Long.MAX_VALUE
    val ws = Analytics.weekStart(now); val ms = Analytics.monthStart(now)
    var sel by remember { mutableStateOf(1) }
    var en by remember { mutableStateOf("") }; var ea by remember { mutableStateOf("") }
    val names = listOf("Is hafte", "Is mahine", "Total"); val froms = listOf(ws, ms, 0L)
    val per = Analytics.period(names[sel], froms[sel], end, L, P, E)
    val prods = Analytics.products(L, froms[sel], end)
    val maxRev = prods.maxOfOrNull { it.revenue } ?: 1.0
    val stockVal = vm.items.value.sumOf { it.stock * it.avgCost }
    val sdf = SimpleDateFormat("dd MMM", Locale.US); val msdf = SimpleDateFormat("MMM yy", Locale.US)
    val weeks = (0..7).map { k -> val f = Analytics.shift(ws, Calendar.WEEK_OF_YEAR, -k)
        Analytics.period("Hafta " + sdf.format(Date(f)), f, Analytics.shift(f, Calendar.WEEK_OF_YEAR, 1), L, P, E) }
    val months = (0..5).map { k -> val f = Analytics.shift(ms, Calendar.MONTH, -k)
        Analytics.period(msdf.format(Date(f)), f, Analytics.shift(f, Calendar.MONTH, 1), L, P, E) }
    val margin = if (per.revenue > 0) per.net / per.revenue * 100 else 0.0
    LazyColumn {
        item {
            Row { names.forEachIndexed { i, n -> if (sel == i) Button({ sel = i }) { Text(n) } else OutlinedButton({ sel = i }) { Text(n) }; Spacer(Modifier.width(6.dp)) } }
            Text(if (per.revenue == 0.0 && per.invest == 0.0) "Is period mein abhi koi bikri nahi" else if (per.net >= 0) "✅ Munafa ho raha hai" else "⚠️ Ghata ho raha hai",
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text("Bikri (revenue): ${m(per.revenue)}")
            Text("Investment (naya maal kharida): ${m(per.invest)}")
            Text("Maal ki cost nikal kar profit: ${m(per.gross)}")
            Text("Kharcha: ${m(per.expense)}")
            Text("Pure profit: ${m(per.net)} (${"%.1f".format(margin)}%)", style = MaterialTheme.typography.titleMedium, color = if (per.net < 0) Color.Red else Color(0xFF2E7D32))
            Text("Stock mein abhi bandha paisa: ${m(stockVal)}")
            prods.firstOrNull()?.let { h ->
                Text("🏆 Hero product (sabse zyada profit): ${h.name} - ${m(h.profit)}", Modifier.padding(top = 8.dp))
                prods.maxByOrNull { it.revenue }?.let { Text("💰 Sabse zyada bikri: ${it.name} - ${m(it.revenue)}") }
            }
            prods.filter { it.profit < 0 }.takeIf { it.isNotEmpty() }?.let { Text("❌ Ghate mein bik rahe: " + it.joinToString { p -> p.name }, color = Color.Red) }
            Text("Product-wise", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
        }
        items(prods) { p ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text("${if (p === prods.first()) "🏆 " else ""}${p.name}: ${fmt(p.qty)} bika")
                Text("Bikri ${m(p.revenue)} • Profit ${m(p.profit)} • Margin ${"%.1f".format(if (p.revenue > 0) p.profit / p.revenue * 100 else 0.0)}%")
                Bar(p.revenue / maxRev)
            }
        }
        item { Text("Hafte-wise (pichhle 8)", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium) }
        items(weeks) { PeriodRow(it, weeks.maxOf { w -> w.revenue }) }
        item { Text("Mahine-wise (pichhle 6)", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium) }
        items(months) { PeriodRow(it, months.maxOf { w -> w.revenue }) }
        item {
            Text("Kharcha likho (bolo: kharcha diesel 500)", Modifier.padding(top = 12.dp))
            OutlinedTextField(en, { en = it }, label = { Text("Kis cheez ka") })
            OutlinedTextField(ea, { ea = it }, label = { Text("Rakam") })
            Button({ vm.addExpense(en, ea.toDoubleOrNull() ?: 0.0); en = ""; ea = "" }) { Text("Kharcha Add") }
        }
    }
}
