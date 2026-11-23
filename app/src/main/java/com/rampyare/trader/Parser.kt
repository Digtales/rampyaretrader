package com.rampyare.trader

data class Line(val item: String, val qty: Double, val unit: String, val amount: Double)

object Parser {
    val units = mapOf(
        "kg" to "kg", "kilo" to "kg", "किलो" to "kg", "किलोग्राम" to "kg",
        "bori" to "bag", "bag" to "bag", "bags" to "bag", "बोरी" to "bag", "बैग" to "bag",
        "tractor" to "tractor", "tractors" to "tractor", "ट्रैक्टर" to "tractor",
        "trolley" to "trolley", "trolleys" to "trolley", "ट्रॉली" to "trolley", "ट्राली" to "trolley",
        "pcs" to "pcs", "piece" to "pcs", "pieces" to "pcs", "पीस" to "pcs", "नग" to "pcs", "eent" to "pcs",
        "ton" to "ton", "टन" to "ton", "quintal" to "quintal", "क्विंटल" to "quintal"
    )
    val rateWords = setOf("rate", "रेट", "per", "prati", "प्रति", "dar", "दर", "bhav", "भाव")
    val stop = setOf(
        "customer", "naya", "new", "ka", "ki", "ke", "hisab", "grahak", "ग्राहक", "नया", "का", "की", "के",
        "हिसाब", "mobile", "number", "नंबर", "dikhao", "kholo", "batao", "दिखाओ", "खोलो", "बताओ",
        "add", "ऐड", "एड", "jodo", "जोड़ो", "karo", "करो", "product", "item", "प्रोडक्ट", "आइटम",
        "maal", "माल", "saman", "सामान", "stock", "स्टॉक", "chahiye", "dedo", "bhejo"
    )

    fun norm(x: String): String {
        var s = x.lowercase().map { if (it in '०'..'९') '0' + (it - '०') else it }.joinToString("")
        s = s.replace(Regex("(?<=\\d),(?=\\d)"), "")
        s = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:hazar|hazaar|hajar|हजार|हज़ार|thousand|k\\b)")
            .replace(s) { (it.groupValues[1].toDouble() * 1000).toLong().toString() }
        s = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:lakh|lac|लाख)")
            .replace(s) { (it.groupValues[1].toDouble() * 100000).toLong().toString() }
        return s.replace(Regex("(\\d)([a-z])"), "$1 $2")
    }

    fun nums(s: String) = Regex("\\d+(\\.\\d+)?").findAll(s).map { it.value.toDouble() }.toList()

    fun cleanName(t: String): String {
        val words = t.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in stop && it !in units && it !in rateWords && !it.any(Char::isDigit) }
        return words.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    private fun keys(i: Item) = norm(i.name).split(" ").filter { it.isNotBlank() }

    /** Best matching items for one spoken chunk. Full name match wins; else partial; 2+ results = ambiguous. */
    private fun cands(tok: List<String>, items: List<Item>): List<Item> {
        val scored = items.map { i ->
            val k = keys(i)
            val al = i.aliases.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val keyScore = if (k.isNotEmpty() && k.all { it in tok }) k.size * 2 else if (k.any { it in tok }) 1 else 0
            val aliasScore = if (al.any { it in tok }) 2 else 0
            i to maxOf(keyScore, aliasScore)
        }
        val best = scored.maxOfOrNull { it.second } ?: 0
        if (best > 0) return scored.filter { it.second == best }.map { it.first }
        return emptyList()
    }

    private fun build(s: String, i: Item): Line? {
        val tok = s.trim().split(Regex("\\s+"))
        val n = nums(s)
        if (n.isEmpty()) return null
        val unit = tok.firstNotNullOfOrNull { units[it] } ?: i.unit
        val qty = n.first()
        val amt = if (n.size >= 2) (if (tok.any { it in rateWords }) n.last() * qty else n.last()) else 0.0
        return Line(i.name, qty, unit, amt)
    }

    /** Returns (lines found, names of ambiguous items to ask about). Chunks split on , ; aur and */
    fun parse(t: String, items: List<Item>): Pair<List<Line>, List<String>> {
        val ok = mutableListOf<Line>()
        val ask = mutableListOf<String>()
        t.split(Regex("[,;]|\\baur\\b|\\band\\b|और")).forEach { ch ->
            val c = cands(ch.trim().split(Regex("\\s+")), items)
            if (c.size == 1) build(ch, c[0])?.let { ok.add(it) }
            else if (c.size > 1 && nums(ch).isNotEmpty()) ask.addAll(c.map { it.name })
        }
        return ok to ask
    }
}
