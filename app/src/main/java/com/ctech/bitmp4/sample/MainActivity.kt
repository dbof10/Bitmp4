package com.ctech.bitmp4.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.ctech.bitmp4.Encoder
import com.ctech.bitmp4.MP4Encoder
import com.ctech.bitmp4.sample.databinding.ActivityMainBinding
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var exportDisposable: Disposable
    private lateinit var encoder: Encoder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val downloarDir = filesDir
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
                .into(binding.ivRecord)


        binding.btExport.setOnClickListener {
            startExport()
        }

        binding.btStop.setOnClickListener {
            stopExport()
        }
    }

    private fun startExport() {
        encoder.setOutputSize(binding.ivRecord.width, binding.ivRecord.width)
        encoder.startEncode()
        exportDisposable = Observable.interval(30, MILLISECONDS)
                .map {
                    createBitmapFromView(binding.ivRecord)
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
