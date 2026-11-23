package com.rampyare.trader
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object Export {
    private fun uri(c: Context, f: File) = FileProvider.getUriForFile(c, "${c.packageName}.fp", f)
    fun csv(c: Context, rows: List<List<String>>): Uri {
        val f = File(c.cacheDir, "RamPyare_Report.csv")
        f.writeText("\uFEFF" + rows.joinToString("\n") { r -> r.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } })
        return uri(c, f)
    }
    fun pdf(c: Context, rows: List<List<String>>): Uri {
        val d = PdfDocument(); val p = Paint().apply { textSize = 10f }
        var n = 1; var pg = d.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create()); var y = 40f
        rows.forEach {
            if (y > 800f) { d.finishPage(pg); n++; pg = d.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create()); y = 40f }
            pg.canvas.drawText(it.joinToString("   |   "), 30f, y, p); y += 16f
        }
        d.finishPage(pg)
        val f = File(c.cacheDir, "RamPyare_Report.pdf"); d.writeTo(FileOutputStream(f)); d.close()
        return uri(c, f)
    }
    fun share(c: Context, u: Uri, mime: String) {
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
    }
}
