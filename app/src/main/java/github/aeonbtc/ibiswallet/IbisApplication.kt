package github.aeonbtc.ibiswallet

import android.app.Application
import net.sqlcipher.database.SQLiteDatabase

/**
 * Application class for Ibis Wallet.
 * 
 * SECURITY FIX: Initializes SQLCipher for encrypted database support.
 * Provides application-wide context for library initialization.
 */
class IbisApplication : Application() {

    companion object {
        lateinit var context: Application
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        
        // SECURITY FIX: Load SQLCipher native libraries for database encryption
        SQLiteDatabase.loadLibs(this)
    }
}
