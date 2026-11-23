package com.rampyare.trader
import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)
fun m(d: Double) = "₹" + fmt(d)

class VM(app: Application) : AndroidViewModel(app) {
    private val dao = DB.get(app).dao()
    val items = dao.items().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customers = dao.customers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bills = dao.bills().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val saleLines = dao.saleLines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val purchases = dao.purchaseFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val expenses = dao.expenses().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var cust by mutableStateOf<Customer?>(null)
    var matches by mutableStateOf(listOf<Customer>())
    val lines = mutableStateListOf<Line>()
    var paid by mutableStateOf(0.0)
    var msg by mutableStateOf("🎤 BOLO dabao: \"1 tractor balu 6 hazar\"")
    var tab by mutableStateOf(0)
    var exportReq by mutableStateOf<String?>(null)
    var ledger by mutableStateOf(listOf<Txn>())
    var lastBillRows by mutableStateOf(listOf<List<String>>())
    var lastBillText by mutableStateOf(""); var lastBillMobile by mutableStateOf("")
    var waReq by mutableStateOf<Pair<String, String>?>(null)
    val total get() = lines.sumOf { it.amount }
    private val now get() = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            listOf(Item("Cement", "bag", aliases = "सीमेंट,seement"), Item("Char", "kg", aliases = "chaar,चार"), Item("Churi", "kg", aliases = "choori,चूरी"),
                Item("Balu", "tractor", aliases = "sand,बालू,रेत"), Item("Brick", "pcs", aliases = "bricks,eent,ईंट,ईट,इंट"))
                .forEach { if (dao.item(it.name) == null) dao.put(it) }
        }
    }

    fun pick(c: Customer) { cust = c; matches = emptyList(); viewModelScope.launch { ledger = dao.txns(c.id) } }

    fun find(q: String) {
        val digits = q.filter { it.isDigit() }
        val r = customers.value.filter { if (digits.length >= 4) it.mobile.contains(digits) else it.name.contains(q.trim(), true) }
        matches = r
        when {
            r.size == 1 -> { pick(r[0]); msg = "${r[0].name} (${r[0].mobile}) due ${m(r[0].due)}" }
            r.isEmpty() -> msg = "Nahi mila. Bolo: naya customer <naam> <mobile>"
            else -> { tab = 2; msg = "${r.size} customer mile - mobile number dekh kar chuno" }
        }
    }

    fun newCust(name: String, mobile: String) = viewModelScope.launch {
        if (name.isBlank() || mobile.length < 10) { msg = "Naam aur 10 digit mobile chahiye"; return@launch }
        val nm = name.trim()
        if (customers.value.any { it.name.equals(nm, true) && it.mobile == mobile }) {
            msg = "Ye naam + mobile pehle se hai"
            return@launch
        }
        try { val id = dao.add(Customer(name = nm, mobile = mobile)); pick(dao.cust(id)!!); msg = "Naya customer: $nm" }
        catch (e: Exception) { msg = "Ye naam + mobile pehle se hai" }
    }

    fun buy(l: Line) = viewModelScope.launch {
        val it = dao.item(l.item) ?: return@launch
        val q = it.stock + l.qty
        val avg = if (q > 0) (it.stock * it.avgCost + l.amount) / q else 0.0
        dao.put(it.copy(stock = q, avgCost = avg))
        dao.addPurchase(Purchase(0, now, l.item, l.qty, l.amount))
        msg = "Stock add: ${l.item} ${fmt(l.qty)} ${l.unit} = ${m(l.amount)} | naya avg ${m(avg)}"
    }

    fun addItem(name: String, unit: String, qty: Double, total: Double) = viewModelScope.launch {
        val n = name.trim().split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        if (n.isBlank() || qty <= 0) { msg = "Naam aur quantity chahiye"; return@launch }
        val ex = items.value.firstOrNull { it.name.equals(n, true) }
        if (ex != null) { buy(Line(ex.name, qty, ex.unit, total)); return@launch }
        dao.put(Item(n, unit, qty, total / qty)); dao.addPurchase(Purchase(0, now, n, qty, total))
        msg = "Naya product $n: ${fmt(qty)} $unit, kharid avg ${m(total / qty)}/$unit"
    }

    fun addExpense(note: String, amt: Double) = viewModelScope.launch {
        if (amt <= 0) { msg = "Kharcha ki rakam batao"; return@launch }
        val n = note.ifBlank { "kharcha" }
        dao.addExpense(Expense(0, now, n, amt)); msg = "Kharcha ${m(amt)} ($n) likha"
    }

    fun whatsapp() { if (lastBillText.isEmpty()) msg = "Pehle bill save karo" else waReq = lastBillMobile to lastBillText }

    fun remind() {
        val c = cust ?: run { msg = "Pehle customer chuno"; return }
        waReq = c.mobile to "*RamPyare Trader*\nNamaste ${c.name}, aapka baaki due Rs ${fmt(c.due)} hai. Kripya jama kar dein. Dhanyavaad!"
    }

    fun pay(a: Double) {
        val c = cust ?: run { msg = "Pehle customer chuno"; return }
        if (lines.isNotEmpty()) { paid = a; msg = "Paid ${m(a)} | due ${m(total - a)}"; return }
        viewModelScope.launch {
            dao.upd(c.copy(due = c.due - a)); dao.addTxn(Txn(0, c.id, now, "PAY", a))
            pick(dao.cust(c.id)!!); msg = "${c.name}: ${m(a)} jama. Baaki due ${m(c.due - a)}"
        }
    }

    fun save() {
        val c = cust ?: run { msg = "Pehle customer chuno"; return }
        if (lines.isEmpty()) { msg = "Bill khaali hai"; return }
        val ls = lines.toList(); val tot = total; val pd = paid
        viewModelScope.launch {
            val ts = now
            val costs = ls.map { l ->
                dao.item(l.item)?.let {
                    dao.put(it.copy(stock = it.stock - l.qty, sellRate = if (l.qty > 0 && l.amount > 0) l.amount / l.qty else it.sellRate))
                    it.avgCost * l.qty
                } ?: 0.0
            }
            val cost = costs.sum()
            val bid = dao.addBill(Bill(0, c.id, ts, tot, pd, cost, ls.joinToString(";") { "${it.item} ${fmt(it.qty)} ${it.unit}=${fmt(it.amount)}" }))
            ls.forEachIndexed { i, l -> dao.addSaleLine(SaleLine(0, bid, ts, l.item, l.qty, l.amount, costs[i])) }
            dao.addTxn(Txn(0, c.id, now, "BILL", tot)); if (pd > 0) dao.addTxn(Txn(0, c.id, now, "PAY", pd))
            dao.upd(c.copy(due = c.due + tot - pd)); pick(dao.cust(c.id)!!)
            val df = SimpleDateFormat("dd-MM-yy HH:mm", Locale.US)
            lastBillRows = listOf(listOf("RamPyare Trader - Bill", df.format(Date())), listOf("Customer", c.name, c.mobile), listOf("")) +
                ls.map { listOf(it.item, "${fmt(it.qty)} ${it.unit}", "Rs ${fmt(it.amount)}") } +
                listOf(listOf(""), listOf("Total", "Rs ${fmt(tot)}"), listOf("Paid", "Rs ${fmt(pd)}"), listOf("Is bill ka due", "Rs ${fmt(tot - pd)}"),
                    listOf("Kul purana+naya due", "Rs ${fmt(c.due + tot - pd)}"))
            lastBillMobile = c.mobile
            lastBillText = "*RamPyare Trader*\n${df.format(Date())}\nCustomer: ${c.name}\n\n" +
                ls.joinToString("\n") { "${it.item} ${fmt(it.qty)} ${it.unit} = Rs ${fmt(it.amount)}" } +
                "\n\nTotal: Rs ${fmt(tot)}\nPaid: Rs ${fmt(pd)}\nIs bill ka due: Rs ${fmt(tot - pd)}\nKul baaki due: Rs ${fmt(c.due + tot - pd)}\n\nDhanyavaad!"
            lines.clear(); paid = 0.0
            msg = "Bill save. Total ${m(tot)}, paid ${m(pd)}, due ${m(c.due + tot - pd)}"
        }
    }

    suspend fun reportRows(): List<List<String>> {
        val r = mutableListOf<List<String>>(); val df = SimpleDateFormat("dd-MM-yy HH:mm", Locale.US)
        val names = customers.value.associate { it.id to it.name }
        r.add(listOf("RamPyare Trader - Report", df.format(Date()))); r.add(listOf(""))
        r.add(listOf("STOCK")); r.add(listOf("Item", "Qty", "Unit", "Avg Cost", "Value"))
        items.value.forEach { r.add(listOf(it.name, fmt(it.stock), it.unit, fmt(it.avgCost), fmt(it.stock * it.avgCost))) }
        r.add(listOf("Stock value", fmt(items.value.sumOf { it.stock * it.avgCost }))); r.add(listOf(""))
        r.add(listOf("CUSTOMERS")); r.add(listOf("Name", "Mobile", "Due"))
        customers.value.forEach { r.add(listOf(it.name, it.mobile, fmt(it.due))) }
        r.add(listOf("Total due", fmt(customers.value.sumOf { it.due }))); r.add(listOf(""))
        r.add(listOf("BILLS")); r.add(listOf("Date", "Customer", "Total", "Paid", "Due", "Items"))
        bills.value.forEach { r.add(listOf(df.format(Date(it.date)), names[it.customerId] ?: "?", fmt(it.total), fmt(it.paid), fmt(it.total - it.paid), it.lines)) }
        r.add(listOf("Total sale", fmt(bills.value.sumOf { it.total }), "Profit", fmt(bills.value.sumOf { it.total - it.cost }))); r.add(listOf(""))
        val nw = System.currentTimeMillis()
        r.add(listOf("MUNAFA", "Bikri", "Kharid (investment)", "Gross profit", "Kharcha", "Pure profit"))
        listOf("Is hafte" to Analytics.weekStart(nw), "Is mahine" to Analytics.monthStart(nw), "Total" to 0L).forEach { (n, f) ->
            val p = Analytics.period(n, f, Long.MAX_VALUE, saleLines.value, purchases.value, expenses.value)
            r.add(listOf(p.label, fmt(p.revenue), fmt(p.invest), fmt(p.gross), fmt(p.expense), fmt(p.net)))
        }
        r.add(listOf("")); r.add(listOf("PRODUCT-WISE (total)", "Qty", "Bikri", "Profit"))
        Analytics.products(saleLines.value, 0L, Long.MAX_VALUE).forEach { r.add(listOf(it.name, fmt(it.qty), fmt(it.revenue), fmt(it.profit))) }
        r.add(listOf(""))
        r.add(listOf("PURCHASES")); r.add(listOf("Date", "Item", "Qty", "Total"))
        dao.purchases().forEach { r.add(listOf(df.format(Date(it.date)), it.item, fmt(it.qty), fmt(it.total))) }
        return r
    }

    private fun askMsg(a: List<String>) { msg = "Kaunsa? ${a.distinct().joinToString(" / ")} - poora naam bolo" }

    fun handle(raw: String) {
        val t = Parser.norm(raw); fun has(vararg k: String) = k.any { t.contains(it) }
        when {
            has("cancel", "clear", "रद्द", "हटाओ") -> { lines.clear(); paid = 0.0; msg = "Bill clear" }
            has("whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप", "वाट्सएप") -> whatsapp()
            has("kharcha", "खर्चा", "खर्च", "expense") -> {
                val w = setOf("kharcha", "खर्चा", "खर्च", "expense")
                addExpense(Parser.name(t).split(" ").filter { it !in w }.joinToString(" "), Parser.nums(t).lastOrNull() ?: 0.0)
            }
            has("munafa", "profit", "मुनाफा", "नफा") -> { tab = 4; msg = "Munafa report" }
            has("pdf") -> exportReq = "pdf"
            has("excel", "csv", "एक्सेल") -> exportReq = "csv"
            has("naya item", "new item", "naya product", "new product", "naya maal", "नया आइटम", "नया प्रोडक्ट", "नया माल") -> {
                val tok = t.split(Regex("\\s+")); val n = Parser.nums(t)
                val unit = tok.firstNotNullOfOrNull { Parser.units[it] } ?: "pcs"
                val skip = setOf("item", "product", "maal", "kharid", "आइटम", "प्रोडक्ट", "माल", "खरीद", "qty", "price", "naam", "नाम")
                val nm = tok.filter { it !in Parser.stop && it !in Parser.units && it !in Parser.rateWords && it !in skip && !it.any(Char::isDigit) }.joinToString(" ")
                val q = n.firstOrNull() ?: 0.0
                val tot = if (n.size >= 2) (if (tok.any { it in Parser.rateWords }) n.last() * q else n.last()) else 0.0
                addItem(nm, unit, q, tot)
            }
            has("kharid", "खरीद", "purchase", "maal aaya", "stock add") -> {
                val (ok, ask) = Parser.parse(t, items.value)
                if (ok.isNotEmpty()) buy(ok[0]) else if (ask.isNotEmpty()) askMsg(ask) else msg = "Bolo: cement 100 bori kharid 35000"
            }
            has("stock", "स्टॉक") -> { tab = 1; msg = "Stock" }
            has("naya customer", "new customer", "नया ग्राहक", "नया कस्टमर", "नया customer") -> newCust(Parser.name(t), t.filter { it.isDigit() }.takeLast(10))
            has("customer", "ग्राहक", "grahak", "hisab", "हिसाब", "mobile") -> find(if (t.count { it.isDigit() } >= 4) t else Parser.name(t))
            has("labour", "लेबर", "मजदूरी", "majduri", "mazdoori") -> {
                val a = Parser.nums(t).lastOrNull() ?: 0.0; lines.add(Line("Labour", 1.0, "", a)); msg = "Labour ${m(a)} add"
            }
            has("jama", "paid", "diya", "जमा", "दिया", "payment") -> pay(Parser.nums(t).lastOrNull() ?: 0.0)
            has("save", "सेव", "bill banao", "बिल बनाओ", "done") -> save()
            else -> {
                val (ls0, ask) = Parser.parse(t, items.value)
                // price nahi bola to us item ka pichla bikri rate use hoga
                val ls = ls0.map { l ->
                    if (l.amount == 0.0) items.value.firstOrNull { it.name == l.item }?.let { i -> if (i.sellRate > 0) l.copy(amount = l.qty * i.sellRate) else l } ?: l else l
                }
                if (ls.isNotEmpty()) {
                    lines.addAll(ls); tab = 0
                    msg = "Add: " + ls.joinToString { "${it.item} ${fmt(it.qty)} ${m(it.amount)}" } + if (ask.isNotEmpty()) " | Kaunsa? ${ask.distinct().joinToString(" / ")}" else ""
                } else if (ask.isNotEmpty()) askMsg(ask) else msg = "Samajh nahi aaya: $raw"
            }
        }
    }
}
