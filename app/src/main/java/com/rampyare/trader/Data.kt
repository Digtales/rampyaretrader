package com.rampyare.trader
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity data class Item(@PrimaryKey val name: String, val unit: String, val stock: Double = 0.0, val avgCost: Double = 0.0, val aliases: String = "", val sellRate: Double = 0.0)
@Entity
data class Customer(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val mobile: String, val due: Double = 0.0)
@Entity data class Bill(@PrimaryKey(autoGenerate = true) val id: Long = 0, val customerId: Long, val date: Long, val total: Double, val paid: Double, val cost: Double, val lines: String)
@Entity data class Txn(@PrimaryKey(autoGenerate = true) val id: Long = 0, val customerId: Long, val date: Long, val type: String, val amount: Double)
@Entity data class SaleLine(@PrimaryKey(autoGenerate = true) val id: Long = 0, val billId: Long, val date: Long, val item: String, val qty: Double, val amount: Double, val cost: Double)
@Entity data class Expense(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: Long, val note: String, val amount: Double)
@Entity data class Purchase(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: Long, val item: String, val qty: Double, val total: Double)

@Dao interface Dao {
    @Query("SELECT * FROM Item") fun items(): Flow<List<Item>>
    @Query("SELECT * FROM Item WHERE name=:n") suspend fun item(n: String): Item?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(i: Item)
    @Query("SELECT * FROM Customer ORDER BY name") fun customers(): Flow<List<Customer>>
    @Query("SELECT * FROM Customer WHERE id=:id") suspend fun cust(id: Long): Customer?
    @Insert suspend fun add(c: Customer): Long
    @Update suspend fun upd(c: Customer)
    @Insert suspend fun addBill(b: Bill): Long
    @Insert suspend fun addSaleLine(l: SaleLine)
    @Insert suspend fun addExpense(e: Expense)
    @Query("SELECT * FROM SaleLine") fun saleLines(): Flow<List<SaleLine>>
    @Query("SELECT * FROM Purchase") fun purchaseFlow(): Flow<List<Purchase>>
    @Query("SELECT * FROM Expense") fun expenses(): Flow<List<Expense>>
    @Insert suspend fun addTxn(t: Txn)
    @Insert suspend fun addPurchase(p: Purchase)
    @Query("SELECT * FROM Bill ORDER BY date DESC") fun bills(): Flow<List<Bill>>
    @Query("SELECT * FROM Txn WHERE customerId=:c ORDER BY date DESC") suspend fun txns(c: Long): List<Txn>
    @Query("SELECT * FROM Purchase ORDER BY date DESC") suspend fun purchases(): List<Purchase>
}

@Database(entities = [Item::class, Customer::class, Bill::class, Txn::class, Purchase::class, SaleLine::class, Expense::class], version = 3, exportSchema = false)
abstract class DB : RoomDatabase() {
    abstract fun dao(): Dao
    companion object {
        @Volatile private var I: DB? = null
        fun get(c: Context): DB = I ?: synchronized(this) {
            I ?: Room.databaseBuilder(c.applicationContext, DB::class.java, "rampyare.db").addMigrations(MIG_2_3).fallbackToDestructiveMigration().build().also { I = it }
        }
    }
}

/** Purane bills (data safe): lines text se SaleLine banata hai, cost bill-cost ko amount ke hisab se baant kar. */
val MIG_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `SaleLine` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `billId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `item` TEXT NOT NULL, `qty` REAL NOT NULL, `amount` REAL NOT NULL, `cost` REAL NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `Expense` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `note` TEXT NOT NULL, `amount` REAL NOT NULL)")
        val cur = db.query("SELECT id, date, cost, lines FROM Bill")
        while (cur.moveToNext()) {
            val id = cur.getLong(0); val date = cur.getLong(1); val cost = cur.getDouble(2)
            val parsed = cur.getString(3).split(";").mapNotNull { p ->
                val i = p.lastIndexOf('='); if (i < 0) return@mapNotNull null
                val head = p.substring(0, i).trim(); val amt = p.substring(i + 1).toDoubleOrNull() ?: 0.0
                if (head.startsWith("Labour")) Triple("Labour", 1.0, amt)
                else { val t = head.split(" "); Triple(t.dropLast(2).joinToString(" "), t.getOrNull(t.size - 2)?.toDoubleOrNull() ?: 0.0, amt) }
            }
            val stockTotal = parsed.filter { it.first != "Labour" }.sumOf { it.third }
            parsed.forEach { (name, qty, amt) ->
                val share = if (name != "Labour" && stockTotal > 0) cost * amt / stockTotal else 0.0
                db.execSQL("INSERT INTO SaleLine(billId, date, item, qty, amount, cost) VALUES(?,?,?,?,?,?)", arrayOf<Any>(id, date, name, qty, amt, share))
            }
        }
        cur.close()
    }
}
