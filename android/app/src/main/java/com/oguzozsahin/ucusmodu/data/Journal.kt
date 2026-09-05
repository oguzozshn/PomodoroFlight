package com.oguzozsahin.ucusmodu.data

import android.content.Context
import java.io.File

/**
 * Tamamlanan pomodorolarin eklemeli gunlugu.
 *
 * Neden [Store] icindeki JSON'a konmadi: o blob 12 ayri yerden `persist()` ile
 * bastan yaziliyor. Buyuyen bir listeyi oraya koymak her duraklatmada butun
 * gecmisi yeniden yazmak olurdu. Burada ekleme dosyanin sonuna tek satir.
 */
class Journal(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun append(record: FocusRecord) {
        try {
            val fresh = !file.exists() || file.length() == 0L
            file.appendText(
                buildString {
                    if (fresh) {
                        append(JournalFormat.HEADER)
                        append('\n')
                    }
                    append(JournalFormat.encode(record))
                    append('\n')
                },
            )
            if (file.length() > MAX_BYTES) compact()
        } catch (e: Exception) {
            // Gunluk tutulamiyorsa sayac calismaya devam etsin.
        }
    }

    /**
     * Butun kayitlar, eskiden yeniye. Yarim yazilmis son satir (yazma sirasinda
     * okunmus olabilir) sessizce elenir.
     */
    fun read(): List<FocusRecord> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().mapNotNull { JournalFormat.decode(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() {
        try {
            file.delete()
        } catch (e: Exception) {
            // silinemezse bir sonraki denemede yine denenir
        }
    }

    /** Dosya sisirdiginde en yeni kayitlar tutulur, gerisi atilir. */
    private fun compact() {
        try {
            val kept = read().takeLast(KEEP_RECORDS)
            file.writeText(
                buildString {
                    append(JournalFormat.HEADER)
                    append('\n')
                    for (record in kept) {
                        append(JournalFormat.encode(record))
                        append('\n')
                    }
                },
            )
        } catch (e: Exception) {
            // sikistirma basarisizsa dosya oldugu gibi kalir
        }
    }

    private companion object {
        const val FILE_NAME = "ucus-gunlugu.tsv"

        /** Yaklasik yedi yillik yogun kullanim. */
        const val KEEP_RECORDS = 50_000
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}
