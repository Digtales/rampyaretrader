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
    var msg by mutableStateOf("🎤 BOLO dabao: \"1 tractor balu 6 hazar\" ya \"add new product\"")
    var tab by mutableStateOf(0)
    var exportReq by mutableStateOf<String?>(null)
    var ledger by mutableStateOf(listOf<Txn>())
    var lastBillRows by mutableStateOf(listOf<List<String>>())
    var lastBillText by mutableStateOf("")
    var lastBillMobile by mutableStateOf("")
    var waReq by mutableStateOf<Pair<String, String>?>(null)

    // Dialog controllers
    var showAddProductDialog by mutableStateOf(false)
    var showAddCustDialog by mutableStateOf(false)
    var showAddExpenseDialog by mutableStateOf(false)
    var showAddStockDialog by mutableStateOf(false)
    var selectedStockItem by mutableStateOf<Item?>(null)
    var initialDialogName by mutableStateOf("")

    val total get() = lines.sumOf { it.amount }
    private val now get() = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            listOf(
                Item("Cement UltraTech", "bag", 100.0, 340.0, "ultratech,अल्ट्राटेक,cement,सीमेंट,seement", 380.0),
                Item("Cement Ambuja", "bag", 80.0, 330.0, "ambuja,अंबुजा", 370.0),
                Item("Cement ACC", "bag", 60.0, 335.0, "acc,एसीसी", 375.0),
                Item("Balu (Sand)", "tractor", 8.0, 5000.0, "balu,sand,बालू,रेत,baloo", 6000.0),
                Item("Gitti 20mm", "tractor", 6.0, 6500.0, "gitti,गिट्टी,stone,रोड़ी", 7500.0),
                Item("Gitti 10mm", "tractor", 4.0, 6000.0, "churi,चूरी,10mm,dust", 7000.0),
                Item("Sariya (TMT)", "kg", 800.0, 62.0, "sariya,सरिया,iron,tmt,loha,लोहा", 68.0),
                Item("Char / Stone Dust", "kg", 2000.0, 2.5, "char,chaar,चार,stone dust", 3.5),
                Item("Brick (ईंट)", "pcs", 5000.0, 7.5, "brick,bricks,eent,ईंट,ईट,इंट", 9.0)
            ).forEach { if (dao.item(it.name) == null) dao.put(it) }
        }
    }

    fun pick(c: Customer) {
        cust = c
        matches = emptyList()
        viewModelScope.launch { ledger = dao.txns(c.id) }
    }

    fun find(q: String) {
        val digits = q.filter { it.isDigit() }
        val r = customers.value.filter {
            if (digits.length >= 4) it.mobile.contains(digits) else it.name.contains(q.trim(), true)
        }
        matches = r
        when {
            r.size == 1 -> { pick(r[0]); msg = "👤 ${r[0].name} (${r[0].mobile}) chun liya. Baaki due: ${m(r[0].due)}" }
            r.isEmpty() -> { msg = "Customer nahi mila. 'Add Customer' dabayein ya bolo: naya customer <naam> <mobile>" }
            else -> { tab = 2; msg = "${r.size} customer mile - list mein se chunein" }
        }
    }

    fun newCust(name: String, mobile: String, initialDue: Double = 0.0) = viewModelScope.launch {
        val nm = name.trim().split(Regex("\\s+")).joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        val mob = mobile.filter(Char::isDigit).takeLast(10)
        if (nm.isBlank() || mob.length < 10) {
            msg = "⚠️ Naam aur 10-digit mobile number zaroori hai"
            return@launch
        }
        val existing = customers.value.firstOrNull { it.mobile == mob || it.name.equals(nm, true) }
        if (existing != null) {
            pick(existing)
            msg = "Ye customer pehle se hai: ${existing.name} (${existing.mobile})"
            return@launch
        }
        try {
            val id = dao.add(Customer(name = nm, mobile = mob, due = initialDue))
            val c = dao.cust(id)!!
            pick(c)
            showAddCustDialog = false
            msg = "✅ Naya customer: $nm (${mob}) jud gaya!"
        } catch (e: Exception) {
            msg = "Grahak jodane mein error: ${e.message}"
        }
    }

    fun deleteCust(c: Customer) = viewModelScope.launch {
        dao.delCust(c.id)
        if (cust?.id == c.id) cust = null
        msg = "Customer ${c.name} hata diya gaya"
    }

    fun buy(l: Line) = viewModelScope.launch {
        val it = dao.item(l.item) ?: return@launch
        val q = it.stock + l.qty
        val avg = if (q > 0) (it.stock * it.avgCost + l.amount) / q else 0.0
        dao.put(it.copy(stock = q, avgCost = avg))
        dao.addPurchase(Purchase(0, now, l.item, l.qty, l.amount))
        tab = 1
        showAddStockDialog = false
        msg = "✅ Stock badh gaya: ${l.item} +${fmt(l.qty)} ${l.unit} | Naya stock: ${fmt(q)} (Avg ${m(avg)})"
    }

    fun addItem(name: String, unit: String, qty: Double, total: Double, sellRate: Double = 0.0) = viewModelScope.launch {
        val n = name.trim().split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }
        if (n.isBlank()) {
            msg = "⚠️ Product ka naam zaroori hai"
            return@launch
        }
        val ex = items.value.firstOrNull { it.name.equals(n, true) }
        val finalUnit = unit.ifBlank { ex?.unit ?: "bag" }
        val avgCost = if (qty > 0 && total > 0) total / qty else if (ex != null) ex.avgCost else 0.0
        val finalSellRate = if (sellRate > 0) sellRate else if (ex != null && ex.sellRate > 0) ex.sellRate else if (avgCost > 0) avgCost * 1.1 else 0.0

        if (ex != null) {
            val newStock = ex.stock + qty
            val newAvg = if (newStock > 0 && (ex.stock * ex.avgCost + total) > 0) (ex.stock * ex.avgCost + total) / newStock else ex.avgCost
            dao.put(ex.copy(stock = newStock, avgCost = newAvg, sellRate = finalSellRate))
            if (qty > 0 && total > 0) dao.addPurchase(Purchase(0, now, ex.name, qty, total))
            msg = "✅ Stock updated: ${ex.name} (Kul: ${fmt(newStock)} $finalUnit)"
        } else {
            dao.put(Item(n, finalUnit, qty, avgCost, sellRate = finalSellRate))
            if (qty > 0 && total > 0) dao.addPurchase(Purchase(0, now, n, qty, total))
            msg = "✅ Naya product '$n' stock mein add ho gaya!"
        }
        showAddProductDialog = false
        tab = 1 // Switch to Stock Tab so user sees the new product!
    }

    fun deleteItem(i: Item) = viewModelScope.launch {
        dao.delItem(i.name)
        msg = "Product '${i.name}' hata diya gaya"
    }

    fun addExpense(note: String, amt: Double) = viewModelScope.launch {
        if (amt <= 0) { msg = "⚠️ Kharcha ki sahi rakam bharein"; return@launch }
        val n = note.ifBlank { "Dukaan Kharcha" }
        dao.addExpense(Expense(0, now, n, amt))
        showAddExpenseDialog = false
        msg = "✅ Kharcha ${m(amt)} ($n) likha gaya"
    }

    fun whatsapp() {
        if (lastBillText.isEmpty()) {
            msg = "⚠️ Pehle bill save karein fir WhatsApp bhejein"
        } else {
            waReq = lastBillMobile to lastBillText
        }
    }

    fun remind() {
        val c = cust ?: run { msg = "Pehle customer chuno"; return }
        waReq = c.mobile to "*RamPyare Trader - Cement & Building Materials*\nNamaste ${c.name} ji, aapka dukaan par baaki due ${m(c.due)} hai. Kripya samay par jama kar dein. Dhanyavaad!"
    }

    fun pay(a: Double) {
        val c = cust ?: run { msg = "Pehle customer chuno"; return }
        if (lines.isNotEmpty()) {
            paid = a
            msg = "Paid ${m(a)} likha | Is bill ka baaki: ${m(total - a)}"
            return
        }
        viewModelScope.launch {
            dao.upd(c.copy(due = c.due - a))
            dao.addTxn(Txn(0, c.id, now, "PAY", a))
            pick(dao.cust(c.id)!!)
            msg = "✅ ${c.name} ji se ${m(a)} jama hue. Ab baaki: ${m(c.due - a)}"
        }
    }

    fun save() {
        val c = cust ?: run { msg = "⚠️ Kripya pehle customer chunein"; return }
        if (lines.isEmpty()) { msg = "⚠️ Bill khaali hai. Item jodein"; return }
        val ls = lines.toList()
        val tot = total
        val pd = paid
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
            dao.addTxn(Txn(0, c.id, now, "BILL", tot))
            if (pd > 0) dao.addTxn(Txn(0, c.id, now, "PAY", pd))
            val newDue = c.due + tot - pd
            dao.upd(c.copy(due = newDue))
            pick(dao.cust(c.id)!!)

            val df = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US)
            lastBillRows = listOf(
                listOf("RamPyare Trader - Cement & Building Materials", df.format(Date())),
                listOf("Customer", c.name, "Mobile: " + c.mobile),
                listOf("")
            ) + ls.map { listOf(it.item, "${fmt(it.qty)} ${it.unit}", m(it.amount)) } + listOf(
                listOf(""),
                listOf("Subtotal", m(tot)),
                listOf("Jama (Paid)", m(pd)),
                listOf("Is Bill Ka Baaki", m(tot - pd)),
                listOf("Purana + Naya Kul Baaki", m(newDue))
            )
            lastBillMobile = c.mobile
            lastBillText = "*रामप्यारे ट्रेडर्स (सीमेंट एवं भवन निर्माण)*\n" +
                "तारीख: ${df.format(Date())}\n" +
                "ग्राहक: ${c.name} (${c.mobile})\n" +
                "---------------------------------\n" +
                ls.joinToString("\n") { "• ${it.item} - ${fmt(it.qty)} ${it.unit} = ${m(it.amount)}" } +
                "\n---------------------------------\n" +
                "कुल बिल (Total): ${m(tot)}\n" +
                "जमा (Paid): ${m(pd)}\n" +
                "इस बिल का बाकी: ${m(tot - pd)}\n" +
                "👉 कुल पुराना+नया बाकी: ${m(newDue)}\n\n" +
                "धन्यवाद! फिर सेवा का अवसर दें 🙏"

            lines.clear()
            paid = 0.0
            msg = "✅ Bill save ho gaya! Kul: ${m(tot)}, Jama: ${m(pd)}, Baaki: ${m(newDue)}"
        }
    }

    suspend fun reportRows(): List<List<String>> {
        val r = mutableListOf<List<String>>()
        val df = SimpleDateFormat("dd-MM-yy HH:mm", Locale.US)
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

    private fun askMsg(a: List<String>) {
        msg = "Kaunsa product? ${a.distinct().joinToString(" / ")} - poora naam bolein"
    }

    /** Core Voice and Text Command Parser with Strict Disambiguation */
    fun handle(raw: String) {
        val t = Parser.norm(raw)
        fun has(vararg k: String) = k.any { t.contains(it) }

        when {
            // 1. Clear / Cancel
            has("cancel", "clear", "रद्द", "हटाओ", "delete bill", "khali karo") -> {
                lines.clear()
                paid = 0.0
                msg = "Bill clear ho gaya"
            }

            // 2. WhatsApp Share
            has("whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप", "वाट्सएप") -> whatsapp()

            // 3. Reports & Navigation
            has("munafa", "profit", "मुनाफा", "नफा") -> { tab = 4; msg = "Munafa & Analytics report" }
            has("pdf") -> exportReq = "pdf"
            has("excel", "csv", "एक्सेल") -> exportReq = "csv"
            has("hisab", "हिसाब", "ledger", "khata book") -> { tab = 3; msg = "Dukaan ka hisab kitab" }

            // 4. PRODUCT / STOCK MANAGEMENT INTENTS (Strictly prioritized BEFORE Customer)
            has(
                "add new product", "add product", "new product", "naya product", "product add",
                "add new item", "add item", "new item", "naya item", "item add",
                "naya maal", "maal add", "add maal", "saman add", "naya saman",
                "नया प्रोडक्ट", "नया आइटम", "नया सामान", "नया माल",
                "ऐड प्रोडक्ट", "एड प्रोडक्ट", "ऐड न्यू प्रोडक्ट", "एड न्यू प्रोडक्ट",
                "प्रोडक्ट जोड़ो", "आइटम जोड़ो", "माल जोड़ो", "सामान जोड़ो"
            ) -> {
                val tok = t.split(Regex("\\s+"))
                val n = Parser.nums(t)
                val unit = tok.firstNotNullOfOrNull { Parser.units[it] } ?: "bag"
                val name = Parser.cleanName(t)

                // If user gave name and quantity/rate, directly add to inventory!
                if (name.isNotBlank() && n.isNotEmpty() && n.first() > 0) {
                    val q = n.first()
                    val tot = if (n.size >= 2) (if (tok.any { it in Parser.rateWords }) n.last() * q else n.last()) else 0.0
                    addItem(name, unit, q, tot)
                } else {
                    // User said just "add new product" or "add product cement" without full numbers -> Open Dialog!
                    initialDialogName = name
                    showAddProductDialog = true
                    tab = 1
                    msg = if (name.isNotBlank()) "Naya product '$name' ke details bharein" else "Naya product add karne ke liye details bharein"
                }
            }

            // 5. Stock View & Stock Purchase
            has("kharid", "खरीद", "purchase", "maal aaya", "stock add", "stock badhao") -> {
                val (ok, ask) = Parser.parse(t, items.value)
                if (ok.isNotEmpty()) {
                    buy(ok[0])
                } else if (ask.isNotEmpty()) {
                    askMsg(ask)
                } else {
                    val clean = Parser.cleanName(t)
                    val it = items.value.firstOrNull { clean.contains(it.name, true) || it.name.contains(clean, true) }
                    if (it != null) {
                        selectedStockItem = it
                        showAddStockDialog = true
                    } else {
                        showAddProductDialog = true
                    }
                    tab = 1
                    msg = "Stock badhane ke liye details bharein"
                }
            }

            has("stock", "स्टॉक") -> { tab = 1; msg = "Dukaan Stock & Products" }

            // 6. CUSTOMER INTENTS
            has(
                "naya customer", "new customer", "add new customer", "add customer", "customer add",
                "नया ग्राहक", "नया कस्टमर", "ऐड कस्टमर", "एड कस्टमर", "ऐड न्यू कस्टमर", "एड न्यू कस्टमर", "ग्राहक जोड़ो"
            ) -> {
                val clean = Parser.cleanName(t)
                val digits = t.filter(Char::isDigit)
                if (clean.isNotBlank() && digits.length >= 10) {
                    newCust(clean, digits.takeLast(10))
                } else {
                    initialDialogName = clean
                    showAddCustDialog = true
                    tab = 2
                    msg = "Naya customer add karne ke liye details bharein"
                }
            }

            has("customer", "ग्राहक", "grahak") -> {
                val clean = Parser.cleanName(t)
                val digits = t.filter(Char::isDigit)
                if (digits.length >= 4 || clean.isNotBlank()) {
                    find(if (digits.length >= 4) digits else clean)
                } else {
                    tab = 2
                    msg = "Grahak list - customer chunein"
                }
            }

            // 7. EXPENSE (Kharcha)
            has("kharcha", "खर्चा", "खर्च", "expense") -> {
                val a = Parser.nums(t).lastOrNull()
                val clean = Parser.cleanName(t)
                if (a != null && a > 0) {
                    addExpense(clean.ifBlank { "Dukaan Kharcha" }, a)
                } else {
                    showAddExpenseDialog = true
                    msg = "Kharcha likhne ke liye details bharein"
                }
            }

            // 8. LABOUR CHARGE
            has("labour", "लेबर", "मजदूरी", "majduri", "mazdoori", "pallebaazi") -> {
                val a = Parser.nums(t).lastOrNull() ?: 0.0
                lines.add(Line("Labour (लेबर)", 1.0, "charge", a))
                msg = "✅ Labour charge ${m(a)} bill mein jud gaya"
            }

            // 9. PAYMENT / JAMA
            has("jama", "paid", "diya", "जमा", "दिया", "payment", "received") -> {
                val a = Parser.nums(t).lastOrNull() ?: 0.0
                pay(a)
            }

            // 10. BILL SAVE
            has("save", "सेव", "bill banao", "बिल बनाओ", "bill save", "done") -> save()

            // 11. DEFAULT: SALE LINE ITEM
            else -> {
                val (ls0, ask) = Parser.parse(t, items.value)
                val ls = ls0.map { l ->
                    if (l.amount == 0.0) {
                        items.value.firstOrNull { it.name == l.item }?.let { i ->
                            if (i.sellRate > 0) l.copy(amount = l.qty * i.sellRate) else l
                        } ?: l
                    } else l
                }
                if (ls.isNotEmpty()) {
                    lines.addAll(ls)
                    tab = 0
                    msg = "✅ Add: " + ls.joinToString { "${it.item} ${fmt(it.qty)} ${it.unit} (${m(it.amount)})" }
                } else if (ask.isNotEmpty()) {
                    askMsg(ask)
                } else {
                    msg = "Samajh nahi aaya: \"$raw\". BOLO: '1 tractor balu', 'add new product', ya 'customer ramesh'"
                }
            }
        }
    }
}
