package xyz.simoneesposito.writingassistant.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SetupManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("writingassistant_setup", Context.MODE_PRIVATE)

    val isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)

    fun markSetupComplete() {
        prefs.edit { putBoolean(KEY_SETUP_COMPLETE, true) }
    }

    companion object {
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }
}
