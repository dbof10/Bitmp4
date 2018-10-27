package com.ctech.bitmp4.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.bumptech.glide.Glide
import com.ctech.bitmp4.Encoder
import com.ctech.bitmp4.MP4Encoder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.btExport
import kotlinx.android.synthetic.main.activity_main.btStop
import kotlinx.android.synthetic.main.activity_main.ivRecord
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS


class MainActivity : AppCompatActivity() {

    private lateinit var exportDisposable: Disposable
    private lateinit var encoder: Encoder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val downloarDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportedFile = File(downloarDir, "export.mp4")
        if (exportedFile.exists()) {
            exportedFile.delete()
        }

        encoder = MP4Encoder()
        encoder.setFrameDelay(50)
        encoder.setOutputFilePath(exportedFile.path)
        Glide.with(this)
                .asGif()
                .load(R.drawable.zzz)
                .into(ivRecord)


        btExport.setOnClickListener {
            startExport()
        }

        btStop.setOnClickListener {
            stopExport()
        }
    }

    private fun startExport() {
        encoder.setOutputSize(ivRecord.width, ivRecord.width)
        encoder.startEncode()
        exportDisposable = Observable.interval(30, MILLISECONDS)
                .map {
                    createBitmapFromView(ivRecord)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    encoder.addFrame(it)
                }
    }


    private fun createBitmapFromView(v: View): Bitmap {
        val bitmap = Bitmap.createBitmap(v.width,
                v.height,
                Bitmap.Config.ARGB_8888)

        val c = Canvas(bitmap)
        v.draw(c)
        return bitmap
    }

    private fun stopExport() {
        encoder.stopEncode()
        exportDisposable.dispose()
    }
}
