package com.rampyare.trader

data class Line(val item: String, val qty: Double, val unit: String, val amount: Double)

object Parser {
    val units = mapOf(
        "kg" to "kg", "kilo" to "kg", "किलो" to "kg", "किलोग्राम" to "kg", "केजी" to "kg",
        "bori" to "bag", "bag" to "bag", "bags" to "bag", "बोरी" to "bag", "बैग" to "bag", "बोरियां" to "bag",
        "tractor" to "tractor", "tractors" to "tractor", "ट्रैक्टर" to "tractor",
        "trolley" to "trolley", "trolleys" to "trolley", "ट्रॉली" to "trolley", "ट्राली" to "trolley",
        "pcs" to "pcs", "piece" to "pcs", "pieces" to "pcs", "पीस" to "pcs", "नग" to "pcs", "eent" to "pcs",
        "ton" to "ton", "टन" to "ton", "quintal" to "quintal", "क्विंटल" to "quintal"
    )

    val rateWords = setOf("rate", "रेट", "per", "prati", "प्रति", "dar", "दर", "bhav", "भाव", "hisaab", "हिसाब")
    val currencyWords = setOf("rs", "rs.", "inr", "rupaye", "rupayee", "rupiya", "rupee", "rupees", "रुपये", "रुपया", "रू", "₹")

    val stop = setOf(
        "customer", "naya", "new", "ka", "ki", "ke", "hisab", "grahak", "ग्राहक", "नया", "का", "की", "के",
        "हिसाब", "mobile", "number", "नंबर", "dikhao", "kholo", "batao", "दिखाओ", "खोलो", "बताओ",
        "add", "ऐड", "एड", "jodo", "जोड़ो", "karo", "करो", "product", "item", "प्रोडक्ट", "आइटम",
        "maal", "माल", "saman", "सामान", "stock", "स्टॉक", "chahiye", "dedo", "bhejo", "aur", "and", "mein", "me"
    )

    fun norm(x: String): String {
        var s = x.lowercase().map { if (it in '०'..'९') '0' + (it - '०') else it }.joinToString("")
        s = s.replace(Regex("(?<=\\d),(?=\\d)"), "")

        // Normalize Hindi/English spelling variants for Cement brands & materials
        s = s.replace("cament", "cement")
            .replace("seement", "cement")
            .replace("सेमेंट", "cement")
            .replace("केमेंट", "cement")
            .replace("सीमेंट", "cement")

        // ACC Hindi variations
        s = s.replace("एसीसी", "acc")
            .replace("ऐसीसी", "acc")
            .replace("एसिसी", "acc")
            .replace("ए.सी.सी.", "acc")
            .replace("ऐ.सी.सी.", "acc")
            .replace("ए.सी.सी", "acc")
            .replace("ऐ.सी.सी", "acc")
            .replace("ए सी सी", "acc")
            .replace("ऐ सी सी", "acc")
            .replace("a.c.c.", "acc")
            .replace("a.c.c", "acc")
            .replace("a c c", "acc")

        // Birla Gold and Plus variations
        s = s.replace("बिरला", "birla")
            .replace("गोल्ड", "gold")
            .replace("प्लस", "plus")
            .replace("एमपी", "mp")

        // Kamdhenu & Sariya
        s = s.replace("कामधेनु", "kamdhenu")
            .replace("कामधेनू", "kamdhenu")
            .replace("सरिया", "sariya")
            .replace("लोहा", "sariya")
            .replace("टीएमटी", "tmt")

        // Other brands & materials
        s = s.replace("अल्ट्राटेक", "ultratech")
            .replace("अंबुजा", "ambuja")
            .replace("बांगुर", "bangur")
            .replace("चूरी", "churi")
            .replace("चुरी", "churi")
            .replace("चूड़ी", "churi")
            .replace("छुरी", "churi")
            .replace("डस्ट", "dust")

        // Handle "hazar", "lakh"
        s = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:hazar|hazaar|hajar|हजार|हज़ार|thousand|k\\b)")
            .replace(s) { (it.groupValues[1].toDouble() * 1000).toLong().toString() }
        s = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:lakh|lac|लाख)")
            .replace(s) { (it.groupValues[1].toDouble() * 100000).toLong().toString() }

        // Separate attached unit numbers in English and Hindi e.g. "2kg" -> "2 kg", "200bag" -> "200 bag", "2किलो" -> "2 किलो"
        s = s.replace(Regex("(\\d+)(kg|kilo|bag|bags|bori|tractor|trolley|pcs|ton|quintal|टन|बोरी|बैग|किलो|केजी|क्विंटल)"), "$1 $2")
        s = s.replace(Regex("(\\d+)(rs|rupaye|रुपये|रुपया|रू|₹)"), "$1 $2")

        return s
    }

    fun nums(s: String) = Regex("\\d+(\\.\\d+)?").findAll(s).map { it.value.toDouble() }.toList()

    fun cleanName(t: String): String {
        val words = t.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in stop && it !in units && it !in rateWords && it !in currencyWords && !it.any(Char::isDigit) }
        return words.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    private fun keys(i: Item) = norm(i.name).split(" ").filter { it.isNotBlank() }

    /**
     * Best matching items for one spoken chunk with Brand-Priority scoring.
     * Specific brand names (e.g. ACC, Birla Gold, Birla Plus, UltraTech, Ambuja, Kamdhenu)
     * get much higher score than generic words like "cement" or "sariya".
     */
    fun cands(tok: List<String>, items: List<Item>): List<Item> {
        val scored = items.map { i ->
            val k = keys(i)
            val al = i.aliases.split(",").map { norm(it.trim()) }.filter { it.isNotEmpty() }

            var score = 0
            // Exact full name match
            if (k.isNotEmpty() && k.all { it in tok }) {
                score += k.size * 6
            }
            // Check alias matches
            for (alias in al) {
                val aliasTokens = alias.split(" ")
                if (aliasTokens.all { it in tok }) {
                    score += 10 // Specific brand alias match gets highest priority
                } else if (aliasTokens.any { it in tok }) {
                    score += 3
                }
            }
            // Partial name match
            val matches = k.count { it in tok }
            score += matches * 2

            i to score
        }

        val best = scored.maxOfOrNull { it.second } ?: 0
        if (best >= 4) {
            return scored.filter { it.second == best }.map { it.first }
        }
        return emptyList()
    }

    /**
     * Intelligent extractor:
     * Correctly identifies which number is QUANTITY and which number is AMOUNT/RATE.
     * e.g., "1 tractor balu 6000 rs" -> qty=1.0 (tractor), amount=6000.0
     * e.g., "6000 rupaye ka 1 tractor balu" -> qty=1.0 (tractor), amount=6000.0
     */
    fun buildSmart(s: String, matchedItem: Item?): Line? {
        val tok = s.trim().split(Regex("\\s+"))
        val numMatches = Regex("(\\d+(?:\\.\\d+)?)").findAll(s).toList()
        if (numMatches.isEmpty()) return null

        var detectedQty: Double? = null
        var detectedAmt: Double? = null
        var detectedUnit: String? = null

        // 1. Look for number immediately followed or preceded by a unit (e.g., "1 tractor", "2 kg", "200 bag")
        for (m in numMatches) {
            val numVal = m.value.toDouble()
            val startIdx = m.range.first
            val endIdx = m.range.last + 1

            val afterSubstring = s.substring(endIdx).trimStart().split(Regex("\\s+")).firstOrNull() ?: ""
            val beforeSubstring = s.substring(0, startIdx).trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""

            val unitAfter = units[afterSubstring]
            val unitBefore = units[beforeSubstring]
            val isCurrencyAfter = afterSubstring in currencyWords || afterSubstring in rateWords
            val isCurrencyBefore = beforeSubstring in currencyWords || beforeSubstring in rateWords

            if (isCurrencyAfter || isCurrencyBefore) {
                detectedAmt = numVal
            } else if (unitAfter != null) {
                detectedQty = numVal
                detectedUnit = unitAfter
            } else if (unitBefore != null) {
                detectedQty = numVal
                detectedUnit = unitBefore
            }
        }

        val allNums = numMatches.map { it.value.toDouble() }

        // If qty not explicitly detected from unit proximity:
        if (detectedQty == null) {
            if (allNums.size == 1) {
                detectedQty = allNums[0]
            } else if (allNums.size >= 2) {
                val first = allNums.first()
                val last = allNums.last()
                if (detectedAmt != null) {
                    detectedQty = if (allNums.first() == detectedAmt) allNums.last() else allNums.first()
                } else if (first <= 100 && last >= 500) {
                    detectedQty = first
                    detectedAmt = last
                } else {
                    detectedQty = first
                    detectedAmt = last
                }
            }
        } else if (detectedAmt == null && allNums.size >= 2) {
            detectedAmt = allNums.firstOrNull { it != detectedQty } ?: allNums.last()
        }

        val finalQty = detectedQty ?: 1.0
        val rawUnit = detectedUnit ?: tok.firstNotNullOfOrNull { units[it] } ?: matchedItem?.unit ?: "pcs"
        val finalUnit = if (matchedItem?.name?.contains("Sariya", true) == true && (rawUnit == "bag" || rawUnit == "bori")) {
            "kg"
        } else {
            rawUnit
        }
        val itemName = matchedItem?.name ?: cleanName(s).ifBlank { "Cement" }

        val isRateSpoken = tok.any { it in rateWords }
        var finalAmt = detectedAmt ?: 0.0
        if (isRateSpoken && detectedAmt != null) {
            finalAmt = detectedAmt * finalQty
        }

        return Line(itemName, finalQty, finalUnit, finalAmt)
    }

    /**
     * Parses chunk into Line items.
     * Auto-resolves cement brands and kamdhenu sariya.
     */
    fun parse(t: String, items: List<Item>): Pair<List<Line>, List<String>> {
        val ok = mutableListOf<Line>()
        val ask = mutableListOf<String>()

        t.split(Regex("[,;]|\\baur\\b|\\band\\b|और")).forEach { ch ->
            val tok = ch.trim().split(Regex("\\s+"))
            val c = cands(tok, items)

            if (c.size == 1) {
                buildSmart(ch, c[0])?.let { ok.add(it) }
            } else if (c.size > 1) {
                // Check if any candidate has a specific alias matching in tok
                val specific = c.filter { item ->
                    item.aliases.split(",").any { norm(it.trim()) in tok }
                }
                if (specific.size == 1) {
                    buildSmart(ch, specific[0])?.let { ok.add(it) }
                } else if (nums(ch).isNotEmpty()) {
                    ask.addAll(c.map { it.name })
                }
            } else {
                // If not in database:
                // Check Kamdhenu -> ALWAYS SARIYA
                if (tok.any { it.contains("kamdhenu") } && nums(ch).isNotEmpty()) {
                    val sariya = items.firstOrNull { it.name.contains("Kamdhenu", true) }
                        ?: Item("Kamdhenu Sariya (TMT)", "kg", 0.0, 0.0, "", 70.0)
                    buildSmart(ch, sariya)?.let { ok.add(it) }
                } else if (tok.any { it in listOf("cement", "सीमेंट", "seement", "cament") } && nums(ch).isNotEmpty()) {
                    val brandName = cleanName(ch).ifBlank { "Cement" }
                    val fullName = if (brandName.startsWith("Cement", true)) brandName else "Cement $brandName"
                    buildSmart(ch, Item(fullName, "bag", 0.0, 0.0, "", 370.0))?.let { ok.add(it) }
                } else if (tok.any { it in listOf("sariya", "सरिया", "loha") } && nums(ch).isNotEmpty()) {
                    val sariyaName = cleanName(ch).ifBlank { "Sariya (TMT)" }
                    buildSmart(ch, Item(sariyaName, "kg", 0.0, 0.0, "", 68.0))?.let { ok.add(it) }
                }
            }
        }
        return ok to ask
    }
}
