package id.co.adiva.displayscreen

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.SeekBar
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import id.co.adiva.displayscreen.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import org.threeten.bp.LocalTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var binding: ActivityMainBinding

    private lateinit var pauseBar: ImageView
    private lateinit var seekBar: SeekBar

    private val handler = Handler()
    private lateinit var sharedPreferencesHelper : SharedPreferencesHelper
    private var VideoPlay : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        supportActionBar?.hide()

        sharedPreferencesHelper = SharedPreferencesHelper(applicationContext)

        videoView = binding.videoView
        pauseBar = binding.imagePause
        seekBar = binding.seekBar
        binding.status.text = "Online"

        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val currentDateandTime: String = sdf.format(Date())
        binding.tanggal.text = currentDateandTime.toString()

        val currentTime = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val formattedTime = currentTime.format(formatter)
        binding.jam.text = formattedTime.toString()

        checkInternet()

    }

    private fun checkInternet() {
        if (isConnectedToNetwork()) {
            VideoPlay = sharedPreferencesHelper.getVideoLink()
            playVideoFromUrl(VideoPlay)
            Log.i("DATA_API", VideoPlay.toString())

            // Memulai permintaan pertama
            requestScreenConfig()

        } else {
            intentNoConneect()
        }
    }

    private fun intentNoConneect() {
        val intent = Intent(this, NoConnectedActivity::class.java)
        startActivity(intent)
        finish()
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

    private fun requestScreenConfig() {

        val url = "https://cob-display.adiva.co.id/api/mobile-app/get-screen-config/1"
        val request = Request.Builder()
            .url(url)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {

                val json = response.body?.string()
                val isActive = parseIsActiveFromJson(json)

                // Menjalankan perubahan UI di thread UI
                runOnUiThread {
                    handleVideoViewVisibility(isActive)
                    if (isActive) {
                        val videoLink = parseVideoLinkFromJson(json)
                        if (!VideoPlay.isNullOrEmpty()) {
                            if (videoLink == VideoPlay) {
                                // Jika isActive false, tunggu 2 detik kemudian lakukan permintaan API kembali
                                handler.postDelayed({
                                    requestScreenConfig()
                                }, 5000L)


                                // Memulai permintaan berulang setelah pemutaran video selesai
                                videoView.setOnCompletionListener {
                                   checkInternet()
                                }
                            } else {
                                sharedPreferencesHelper.saveVideoLink(videoLink.toString())
                                VideoPlay = sharedPreferencesHelper.getVideoLink()
                                playVideoFromUrl(VideoPlay)

                                // Panggil fungsi downloadVideo jika izin telah diberikan
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    ActivityCompat.requestPermissions(
                                        this@MainActivity,
                                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                        0
                                    )
                                } else {
                                    downloadVideo(videoLink)
                                }

                                // Jika isActive false, tunggu 2 detik kemudian lakukan permintaan API kembali
                                handler.postDelayed({
                                    requestScreenConfig()
                                }, 5000L)

                                // Memulai permintaan berulang setelah pemutaran video selesai
                                videoView.setOnCompletionListener {
                                 checkInternet()
                                }
                            }
                        }
                    } else {
                        videoView.stopPlayback()
                        // Jika isActive false, tunggu 2 detik kemudian lakukan permintaan API kembali
                        handler.postDelayed({
                            checkInternet()
                        }, 5000L)
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                // Tangani kesalahan jika gagal mengambil respons JSON
                e.printStackTrace()

                // Jika terjadi kesalahan, tunggu 2 detik kemudian lakukan permintaan API kembali
                handler.postDelayed({
                    checkInternet()
                }, 5000L)
            }
        })

    }

    private fun downloadVideo(videoLink: String?) {
        val directory = File(
            Environment.getExternalStorageDirectory().toString() + "/COB/Video"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val fileName = "video1.mp4"
        val outputFile = File(directory, fileName)

        GlobalScope.launch(Dispatchers.Main) {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(videoLink)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()

                    val inputStream = BufferedInputStream(url.openStream())
                    val outputStream = FileOutputStream(outputFile)
                    val data = ByteArray(1024)
                    var count: Int
                    while (inputStream.read(data, 0, 1024).also { count = it } != -1) {
                        outputStream.write(data, 0, count)
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                }
                Toast.makeText(this@MainActivity, "Succes", Toast.LENGTH_SHORT).show()
                Log.i("DATA_API", outputFile.absolutePath)
            } catch (e: Exception) {
               // Toast.makeText(this@MainActivity, "Video gagal didownload", Toast.LENGTH_SHORT).show()
                e.printStackTrace()

            }
        }
    }

    private fun parseIsActiveFromJson(json: String?): Boolean {
        try {
            val jsonObject = JSONObject(json)
            val isActive = jsonObject.getJSONObject("displayScreen").getBoolean("is_active")
            return isActive
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun parseVideoLinkFromJson(json: String?): String? {
        try {
            val jsonObject = JSONObject(json)
            val fileVideoLink = jsonObject.getJSONObject("displayScreen").getString("file_video_link")
            return fileVideoLink
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun handleVideoViewVisibility(isActive: Boolean) {
        if (isActive) {
            binding.layout.visibility = View.VISIBLE
            binding.video.visibility = View.VISIBLE
        } else {
            binding.layout.visibility = View.GONE
            binding.video.visibility = View.GONE
        }
    }

    private fun playVideoFromUrl(videoUrl: String?) {
        videoUrl?.let {
            val videoUri = Uri.parse(it)

            // Menetapkan URI video ke VideoView
            videoView.setVideoURI(videoUri)

            // Menambahkan MediaController untuk mengontrol pemutaran video
            val mediaController = MediaController(this)
            mediaController.setAnchorView(videoView)
            videoView.setMediaController(mediaController)

            // Memulai pemutaran video
            videoView.start()

            // Menambahkan listener untuk menangani perubahan posisi putaran video
            videoView.setOnPreparedListener { mediaPlayer ->
                val durationInMinutes = mediaPlayer.duration / 1000 / 60

                // Mengubah posisi putaran video ke menit ke-2 (misalnya)
                val desiredPositionInMinutes = 0
                val desiredPositionInMillis = desiredPositionInMinutes * 60 * 1000
                mediaPlayer.seekTo(desiredPositionInMillis)
            }

            // Menambahkan listener pada VideoView untuk menampilkan/menyembunyikan bar pause dan seek saat pemutaran video dijeda/dilanjutkan
            videoView.setOnCompletionListener {
                showPauseBar(false)
                showSeekBar(false)
                requestScreenConfig()
            }

            videoView.setOnTouchListener { _, _ ->
                togglePauseBarVisibility()
                toggleSeekBarVisibility()
                false
            }

            // Menambahkan listener pada seek bar untuk mengubah posisi putaran video
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        videoView.seekTo(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun togglePauseBarVisibility() {
        if (pauseBar.visibility == View.GONE) {
            showPauseBar(false)
        } else {
            showPauseBar(true)
        }
    }

    private fun toggleSeekBarVisibility() {
        if (seekBar.visibility == View.GONE) {
            showSeekBar(false)
        } else {
            showSeekBar(true)
        }
    }

    private fun showPauseBar(show: Boolean) {
        pauseBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showSeekBar(show: Boolean) {
        seekBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        // Hentikan permintaan berulang saat aktivitas dihancurkan
        handler.removeCallbacksAndMessages(null)
    }
}