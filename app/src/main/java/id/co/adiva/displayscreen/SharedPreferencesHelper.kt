package id.co.adiva.displayscreen

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper(context: Context) {
    companion object {
        private const val SHARED_PREF_NAME = "MySharedPrefs"
        private const val VIDEO_LINK_KEY = "videoLink"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = sharedPreferences.edit()

    fun saveVideoLink(videoLink: String) {
        editor.putString(VIDEO_LINK_KEY, videoLink)
        editor.apply()
    }

    fun getVideoLink(): String? {
        return sharedPreferences.getString(VIDEO_LINK_KEY, null)
    }
}
