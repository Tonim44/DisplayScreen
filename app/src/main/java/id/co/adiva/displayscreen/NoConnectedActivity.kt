package id.co.adiva.displayscreen

import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import id.co.adiva.displayscreen.databinding.ActivityMainBinding
import org.threeten.bp.LocalTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class NoConnectedActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        supportActionBar?.hide()

        videoView = binding.videoView
        binding.status.text = "Offline"

        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val currentDateandTime: String = sdf.format(Date())
        binding.tanggal.text = currentDateandTime.toString()

        val currentTime = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val formattedTime = currentTime.format(formatter)
        binding.jam.text = formattedTime.toString()

        val directory = File(
            Environment.getExternalStorageDirectory().toString() + "/COB/Video"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val fileName = "video1.mp4"
        val outputFile = File(directory, fileName)
        Log.i("DATA_API", outputFile.absolutePath)

        // Memeriksa apakah video ada di lokasi yang ditentukan
        if (isVideoAvailable(outputFile.absolutePath)) {
            // Menetapkan sumber video ke VideoView
            videoView.setVideoPath(outputFile.absolutePath)

            // Mengatur media controller agar dapat memutar dan mengontrol video
            val mediaController = MediaController(this)
            mediaController.setAnchorView(videoView)
            videoView.setMediaController(mediaController)

            // Menjalankan pemutaran video
            videoView.start()

            // Menambahkan listener OnCompletionListener untuk repeat pemutaran video
            videoView.setOnCompletionListener {
                checkInternet()
            }

            handler.postDelayed({
                checkInternet()
            }, 5000L)
        } else {
            Toast.makeText(this, "Video tidak ditemukan", Toast.LENGTH_SHORT).show()
            handler.postDelayed({
                checkInternet()
            }, 5000L)
        }
    }

    private fun checkInternet() {
        if (isConnectedToNetwork()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {

            // Mengulang pemutaran video dari awal
            videoView.start()

            handler.postDelayed({
                checkInternet()
            }, 2000L)
        }
    }

    private fun isConnectedToNetwork(): Boolean {

        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        var isConnected = false

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            isConnected =
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        } else {
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            isConnected = networkInfo?.isConnected ?: false
        }

        return isConnected
    }

    private fun isVideoAvailable(filePath: String): Boolean {
        val videoFile = File(filePath)
        return videoFile.exists()
    }
}