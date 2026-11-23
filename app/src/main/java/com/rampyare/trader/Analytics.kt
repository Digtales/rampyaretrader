package com.rampyare.trader
import java.util.Calendar

data class Period(val label: String, val revenue: Double, val invest: Double, val gross: Double, val expense: Double) { val net get() = gross - expense }
data class ProdStat(val name: String, val qty: Double, val revenue: Double, val profit: Double)

object Analytics {
    private fun cal(t: Long) = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY; timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    fun weekStart(t: Long) = cal(t).apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }.timeInMillis
    fun monthStart(t: Long) = cal(t).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    fun shift(t: Long, field: Int, n: Int) = cal(t).apply { add(field, n) }.timeInMillis

    fun period(label: String, from: Long, to: Long, l: List<SaleLine>, p: List<Purchase>, e: List<Expense>): Period {
        val ls = l.filter { it.date in from until to }
        val rev = ls.sumOf { it.amount }
        return Period(label, rev, p.filter { it.date in from until to }.sumOf { it.total }, rev - ls.sumOf { it.cost },
            e.filter { it.date in from until to }.sumOf { it.amount })
    }

    fun products(l: List<SaleLine>, from: Long, to: Long): List<ProdStat> =
        l.filter { it.date in from until to && it.item != "Labour" }.groupBy { it.item }
            .map { (k, v) -> ProdStat(k, v.sumOf { it.qty }, v.sumOf { it.amount }, v.sumOf { it.amount - it.cost }) }
            .sortedByDescending { it.profit }
}
