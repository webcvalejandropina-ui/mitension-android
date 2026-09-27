package com.example.mitension.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.*
import android.os.*
import android.print.*
import com.example.mitension.model.Reading

/** Native print adapter supports selected page ranges without sharing files with a server. */
object ReportPrinter {
    fun print(context: Context, readings: List<Reading>) {
        val file = PdfReport.make(context, readings)
        val pageCount = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
        context.getSystemService(PrintManager::class.java).print("Mi Tensión", object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancellation: CancellationSignal,
                callback: LayoutResultCallback, extras: Bundle?) {
                if(cancellation.isCanceled) callback.onLayoutCancelled() else callback.onLayoutFinished(
                    PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pageCount).build(), true)
            }
            override fun onWrite(ranges: Array<out PageRange>, destination: ParcelFileDescriptor, cancellation: CancellationSignal,
                callback: WriteResultCallback) {
                Thread {
                    try {
                        val output = PdfDocument()
                        try {
                            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                                for(index in 0 until renderer.pageCount) {
                                    if(cancellation.isCanceled) { callback.onWriteCancelled(); return@Thread }
                                    if(ranges.none { index in it.start..it.end }) continue
                                    renderer.openPage(index).use { source ->
                                        val bitmap = Bitmap.createBitmap(source.width*2, source.height*2, Bitmap.Config.ARGB_8888)
                                        bitmap.eraseColor(Color.WHITE)
                                        source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                        val page = output.startPage(PdfDocument.PageInfo.Builder(source.width, source.height, index+1).create())
                                        page.canvas.drawBitmap(bitmap, null, Rect(0,0,source.width,source.height), Paint(Paint.FILTER_BITMAP_FLAG))
                                        output.finishPage(page); bitmap.recycle()
                                    }
                                }
                            }
                            ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output.writeTo(it) }
                            callback.onWriteFinished(ranges)
                        } finally { output.close() }
                    } catch(e: Exception) { callback.onWriteFailed(e.localizedMessage) }
                }.start()
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
