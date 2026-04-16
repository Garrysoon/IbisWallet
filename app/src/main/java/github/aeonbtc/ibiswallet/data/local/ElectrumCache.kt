package github.aeonbtc.ibiswallet.data.local

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.core.database.sqlite.transaction
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.model.ConfirmationTime
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import java.io.File

/**
 * Persistent SQLite cache for Electrum server responses.
 *
 * Stores immutable data that does not need to be re-fetched from the server
 * on every app launch:
 *
 * - **Raw transaction hex** (`tx_raw`): keyed by txid. Once confirmed, tx hex
 *   never changes. Eliminates BDK's cold tx_cache penalty on startup.
 *
 * - **Verbose transaction JSON** (`tx_verbose`): keyed by txid. Contains
 *   size/vsize/weight fields used by fetchTransactionVsizeFromElectrum.
 *   Only permanently cached for confirmed txs; unconfirmed entries are pruned
 *   after 1 hour.
 *
 * The cache is shared across all wallets — transaction data is wallet-agnostic.
 * Database file: `<databases>/electrum_cache.db`
 */
/**
 * SECURITY FIX: SQLCipher encrypted database for Electrum cache.
 * All transaction metadata and cached responses are encrypted at rest.
 */
class ElectrumCache(
    context: Context,
    passphrase: String = getDefaultPassphrase(context),
) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    passphrase,
    null,
    DATABASE_VERSION,
    0, // flags
    null, // cursor factory
    false, // enableWriteAheadLogging - explicitly disabled for SQLCipher compatibility
) {
    private val appContext = context.applicationContext

    companion object {
        init {
            // SECURITY FIX: Load SQLCipher native libraries
            SQLiteDatabase.loadLibs(github.aeonbtc.ibiswallet.IbisApplication.context)
        }

        private const val TAG = "ElectrumCache"
        private const val DATABASE_NAME = "electrum_cache.db"
        private const val DATABASE_VERSION = 5 // SECURITY FIX: Increment version for SQLCipher migration

        /**
         * SECURITY FIX: Generates a device-specific passphrase for database encryption.
         * This uses Android ID combined with a static key to create a unique encryption key.
         * Note: This provides basic encryption at rest. For higher security, consider
         * using a key from Android Keystore via SecureStorage.
         */
        private fun getDefaultPassphrase(context: Context): String {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "default_fallback_key"
            // Combine Android ID with app-specific salt for deterministic passphrase
            return "ibis_cache_${androidId}_v1"
        }
        private const val SQLITE_IN_CHUNK_SIZE = 900
        private const val SQLITE_DELETE_CHUNK_SIZE = 500

        // Table: raw transaction hex (blockchain.transaction.get non-verbose)
        private const val TABLE_TX_RAW = "tx_raw"
        private const val COL_TXID = "txid"
        private const val COL_HEX = "hex"
        private const val COL_CACHED_AT = "cached_at"

        // Table: verbose transaction JSON (blockchain.transaction.get verbose=true)
        private const val TABLE_TX_VERBOSE = "tx_verbose"
        private const val COL_JSON = "json"
        private const val COL_CONFIRMED = "confirmed"

        // Table: persisted script hash statuses for smartSync on app relaunch.
        // Compared against subscription responses to detect changes while the app was closed.
        private const val TABLE_SCRIPT_HASH_STATUSES = "script_hash_statuses"
        private const val COL_SCRIPT_HASH = "script_hash"
        private const val COL_STATUS = "status"
        // COL_CACHED_AT reused from above

        // Table: cached scripthash history responses keyed by the status hash that
        // validated them. If the current status differs, the history is stale.
        private const val TABLE_SCRIPT_HASH_HISTORY = "script_hash_history"
        private const val COL_HISTORY_JSON = "history_json"

        // Table: wallet-specific confirmed transaction display rows.
        // Lets us reuse expensive per-tx enrichment work across launches.
        private const val TABLE_WALLET_TX_DETAILS = "wallet_tx_details"
        private const val COL_WALLET_ID = "wallet_id"
        private const val COL_DESCRIPTOR_KEY = "descriptor_key"
        // COL_TXID reused from above
        private const val COL_AMOUNT_SATS = "amount_sats"
        // COL_FEE reused from below
        private const val COL_FEE = "fee"
        private const val COL_WEIGHT = "weight"
        private const val COL_CONFIRMATION_HEIGHT = "confirmation_height"
        private const val COL_CONFIRMATION_TIMESTAMP = "confirmation_timestamp"
        private const val COL_ADDRESS = "address"
        private const val COL_ADDRESS_AMOUNT = "address_amount"
        private const val COL_CHANGE_ADDRESS = "change_address"
        private const val COL_CHANGE_AMOUNT = "change_amount"
        private const val COL_IS_SELF_TRANSFER = "is_self_transfer"

        // Prune unconfirmed verbose tx entries older than this
        private const val UNCONFIRMED_TTL_MS = 3_600_000L // 1 hour
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TX_RAW (
                $COL_TXID TEXT PRIMARY KEY,
                $COL_HEX TEXT NOT NULL,
                $COL_CACHED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TX_VERBOSE (
                $COL_TXID TEXT PRIMARY KEY,
                $COL_JSON TEXT NOT NULL,
                $COL_CONFIRMED INTEGER NOT NULL DEFAULT 0,
                $COL_CACHED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SCRIPT_HASH_STATUSES (
                $COL_SCRIPT_HASH TEXT PRIMARY KEY,
                $COL_STATUS TEXT,
                $COL_CACHED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SCRIPT_HASH_HISTORY (
                $COL_SCRIPT_HASH TEXT PRIMARY KEY,
                $COL_STATUS TEXT NOT NULL,
                $COL_HISTORY_JSON TEXT NOT NULL,
                $COL_CACHED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_WALLET_TX_DETAILS (
                $COL_WALLET_ID TEXT NOT NULL,
                $COL_DESCRIPTOR_KEY TEXT NOT NULL,
                $COL_TXID TEXT NOT NULL,
                $COL_AMOUNT_SATS INTEGER NOT NULL,
                $COL_FEE INTEGER,
                $COL_WEIGHT INTEGER,
                $COL_CONFIRMATION_HEIGHT INTEGER NOT NULL,
                $COL_CONFIRMATION_TIMESTAMP INTEGER NOT NULL,
                $COL_ADDRESS TEXT,
                $COL_ADDRESS_AMOUNT INTEGER,
                $COL_CHANGE_ADDRESS TEXT,
                $COL_CHANGE_AMOUNT INTEGER,
                $COL_IS_SELF_TRANSFER INTEGER NOT NULL DEFAULT 0,
                $COL_CACHED_AT INTEGER NOT NULL,
                PRIMARY KEY ($COL_WALLET_ID, $COL_DESCRIPTOR_KEY, $COL_TXID)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SCRIPT_HASH_STATUSES (
                    $COL_SCRIPT_HASH TEXT PRIMARY KEY,
                    $COL_STATUS TEXT,
                    $COL_CACHED_AT INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_WALLET_TX_DETAILS (
                    $COL_WALLET_ID TEXT NOT NULL,
                    $COL_DESCRIPTOR_KEY TEXT NOT NULL,
                    $COL_TXID TEXT NOT NULL,
                    $COL_AMOUNT_SATS INTEGER NOT NULL,
                    $COL_FEE INTEGER,
                    $COL_WEIGHT INTEGER,
                    $COL_CONFIRMATION_HEIGHT INTEGER NOT NULL,
                    $COL_CONFIRMATION_TIMESTAMP INTEGER NOT NULL,
                    $COL_ADDRESS TEXT,
                    $COL_ADDRESS_AMOUNT INTEGER,
                    $COL_CHANGE_ADDRESS TEXT,
                    $COL_CHANGE_AMOUNT INTEGER,
                    $COL_IS_SELF_TRANSFER INTEGER NOT NULL DEFAULT 0,
                    $COL_CACHED_AT INTEGER NOT NULL,
                    PRIMARY KEY ($COL_WALLET_ID, $COL_DESCRIPTOR_KEY, $COL_TXID)
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SCRIPT_HASH_HISTORY (
                    $COL_SCRIPT_HASH TEXT PRIMARY KEY,
                    $COL_STATUS TEXT NOT NULL,
                    $COL_HISTORY_JSON TEXT NOT NULL,
                    $COL_CACHED_AT INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        // SECURITY FIX: Version 5 introduces SQLCipher encryption
        // Previous unencrypted database is cleared and re-encrypted on upgrade
        if (oldVersion < 5) {
            // Clear old unencrypted data - will be re-downloaded and cached encrypted
            db.execSQL("DROP TABLE IF EXISTS $TABLE_TX_RAW")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_TX_VERBOSE")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SCRIPT_HASH_STATUSES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WALLET_TX_DETAILS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SCRIPT_HASH_HISTORY")
            // Recreate tables in encrypted format
            onCreate(db)
        }
    }

    // ==================== Raw Transaction Hex ====================

    /**
     * Get cached raw transaction hex by txid.
     * @return hex string or null if not cached
     */
    fun getRawTx(txid: String): String? {
        return try {
            readableDatabase.query(
                TABLE_TX_RAW,
                arrayOf(COL_HEX),
                "$COL_TXID = ?",
                arrayOf(txid),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to read tx cache for $txid: ${e.message}")
            null
        }
    }

    /**
     * Return the txids from [txids] that are not yet cached.
     * Uses chunked IN queries to avoid one SQLite lookup per txid.
     */
    fun findMissingRawTxids(txids: List<String>): List<String> {
        if (txids.isEmpty()) return emptyList()

        return try {
            val cachedTxids = HashSet<String>(txids.size)
            txids.chunked(SQLITE_IN_CHUNK_SIZE).forEach { chunk ->
                val placeholders = List(chunk.size) { "?" }.joinToString(",")
                readableDatabase.query(
                    TABLE_TX_RAW,
                    arrayOf(COL_TXID),
                    "$COL_TXID IN ($placeholders)",
                    chunk.toTypedArray(),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        cachedTxids += cursor.getString(0)
                    }
                }
            }

            txids.filterNot(cachedTxids::contains)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to batch-read tx cache: ${e.message}")
            txids
        }
    }

    /**
     * Cache raw transaction hex. Uses INSERT OR REPLACE for idempotency.
     */
    fun putRawTx(
        txid: String,
        hex: String,
    ) {
        try {
            val values =
                ContentValues().apply {
                    put(COL_TXID, txid)
                    put(COL_HEX, hex)
                    put(COL_CACHED_AT, System.currentTimeMillis())
                }
            writableDatabase.insertWithOnConflict(
                TABLE_TX_RAW,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to cache tx $txid: ${e.message}")
        }
    }

    // ==================== Verbose Transaction JSON ====================

    /**
     * Get cached verbose transaction JSON by txid.
     * @return JSON string or null if not cached
     */
    fun getVerboseTx(txid: String): String? {
        return try {
            readableDatabase.query(
                TABLE_TX_VERBOSE,
                arrayOf(COL_JSON),
                "$COL_TXID = ?",
                arrayOf(txid),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to read verbose tx cache for $txid: ${e.message}")
            null
        }
    }

    /**
     * Cache verbose transaction JSON.
     * @param confirmed whether the tx is confirmed (permanent cache) or unconfirmed (TTL-based)
     */
    fun putVerboseTx(
        txid: String,
        json: String,
        confirmed: Boolean,
    ) {
        try {
            val values =
                ContentValues().apply {
                    put(COL_TXID, txid)
                    put(COL_JSON, json)
                    put(COL_CONFIRMED, if (confirmed) 1 else 0)
                    put(COL_CACHED_AT, System.currentTimeMillis())
                }
            writableDatabase.insertWithOnConflict(
                TABLE_TX_VERBOSE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to cache verbose tx $txid: ${e.message}")
        }
    }

    // ==================== Block Headers ====================

    // ==================== Script Hash Statuses ====================

    /**
     * Persist script hash statuses to survive app restarts.
     * Updates only changed entries and removes hashes that disappeared.
     */
    fun saveScriptHashStatuses(statuses: Map<String, String?>) {
        if (statuses.isEmpty()) return
        try {
            writableDatabase.transaction {
                val existing = loadScriptHashStatuses(this)
                val removedHashes = existing.keys - statuses.keys
                val now = System.currentTimeMillis()
                removedHashes.chunked(SQLITE_DELETE_CHUNK_SIZE).forEach { chunk ->
                    val placeholders = List(chunk.size) { "?" }.joinToString(",")
                    delete(
                        TABLE_SCRIPT_HASH_STATUSES,
                        "$COL_SCRIPT_HASH IN ($placeholders)",
                        chunk.toTypedArray(),
                    )
                }
                for ((scriptHash, status) in statuses) {
                    if (existing[scriptHash] == status) continue
                    val values =
                        ContentValues().apply {
                            put(COL_SCRIPT_HASH, scriptHash)
                            put(COL_STATUS, status)
                            put(COL_CACHED_AT, now)
                        }
                    insertWithOnConflict(
                        TABLE_SCRIPT_HASH_STATUSES,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Persisted ${statuses.size} script hash statuses")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to persist script hash statuses: ${e.message}")
        }
    }

    /**
     * Load persisted script hash statuses from the previous session.
     * @return Map of scriptHash -> status (null for unused addresses), or empty map.
     */
    fun loadScriptHashStatuses(): Map<String, String?> {
        return try {
            loadScriptHashStatuses(readableDatabase)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to load script hash statuses: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Clear persisted script hash statuses.
     * Called on wallet switch or server change.
     */
    fun clearScriptHashStatuses() {
        try {
            writableDatabase.delete(TABLE_SCRIPT_HASH_STATUSES, null, null)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to clear script hash statuses: ${e.message}")
        }
    }

    private fun loadScriptHashStatuses(db: SQLiteDatabase): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        db.query(
            TABLE_SCRIPT_HASH_STATUSES,
            arrayOf(COL_SCRIPT_HASH, COL_STATUS),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val scriptHash = cursor.getString(0)
                val status = if (cursor.isNull(1)) null else cursor.getString(1)
                result[scriptHash] = status
            }
        }
        return result
    }

    // ==================== Script Hash History ====================

    /**
     * Get cached script hash history when [currentStatus] still matches the
     * status used to validate the cached history response.
     */
    fun getHistory(
        scriptHash: String,
        currentStatus: String,
    ): String? {
        return try {
            readableDatabase.query(
                TABLE_SCRIPT_HASH_HISTORY,
                arrayOf(COL_STATUS, COL_HISTORY_JSON),
                "$COL_SCRIPT_HASH = ?",
                arrayOf(scriptHash),
                null,
                null,
                null,
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val cachedStatus = cursor.getString(0)
                if (cachedStatus != currentStatus) return@use null
                cursor.getString(1)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to read history cache for $scriptHash: ${e.message}")
            null
        }
    }

    fun putHistory(
        scriptHash: String,
        status: String,
        historyJson: String,
    ) {
        try {
            val values =
                ContentValues().apply {
                    put(COL_SCRIPT_HASH, scriptHash)
                    put(COL_STATUS, status)
                    put(COL_HISTORY_JSON, historyJson)
                    put(COL_CACHED_AT, System.currentTimeMillis())
                }
            writableDatabase.insertWithOnConflict(
                TABLE_SCRIPT_HASH_HISTORY,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to cache history for $scriptHash: ${e.message}")
        }
    }

    fun clearAllHistory() {
        try {
            writableDatabase.delete(TABLE_SCRIPT_HASH_HISTORY, null, null)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to clear script hash history: ${e.message}")
        }
    }

    // ==================== Confirmed Transaction Details ====================

    fun loadConfirmedTransactionDetails(
        walletId: String,
        descriptorKey: String,
        txids: List<String>,
    ): Map<String, TransactionDetails> {
        if (txids.isEmpty()) return emptyMap()

        return try {
            val result = linkedMapOf<String, TransactionDetails>()
            txids.chunked(SQLITE_IN_CHUNK_SIZE).forEach { chunk ->
                val placeholders = List(chunk.size) { "?" }.joinToString(",")
                val args = arrayOf(walletId, descriptorKey, *chunk.toTypedArray())
                readableDatabase.query(
                    TABLE_WALLET_TX_DETAILS,
                    arrayOf(
                        COL_TXID,
                        COL_AMOUNT_SATS,
                        COL_FEE,
                        COL_WEIGHT,
                        COL_CONFIRMATION_HEIGHT,
                        COL_CONFIRMATION_TIMESTAMP,
                        COL_ADDRESS,
                        COL_ADDRESS_AMOUNT,
                        COL_CHANGE_ADDRESS,
                        COL_CHANGE_AMOUNT,
                        COL_IS_SELF_TRANSFER,
                    ),
                    "$COL_WALLET_ID = ? AND $COL_DESCRIPTOR_KEY = ? AND $COL_TXID IN ($placeholders)",
                    args,
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val txid = cursor.getString(0)
                        val amountSats = cursor.getLong(1)
                        val fee = if (cursor.isNull(2)) null else cursor.getLong(2).toULong()
                        val weight = if (cursor.isNull(3)) null else cursor.getLong(3).toULong()
                        val confirmationHeight = cursor.getLong(4).toUInt()
                        val confirmationTimestamp = cursor.getLong(5).toULong()
                        val address = if (cursor.isNull(6)) null else cursor.getString(6)
                        val addressAmount = if (cursor.isNull(7)) null else cursor.getLong(7).toULong()
                        val changeAddress = if (cursor.isNull(8)) null else cursor.getString(8)
                        val changeAmount = if (cursor.isNull(9)) null else cursor.getLong(9).toULong()
                        val isSelfTransfer = cursor.getInt(10) != 0
                        result[txid] =
                            TransactionDetails(
                                txid = txid,
                                amountSats = amountSats,
                                fee = fee,
                                weight = weight,
                                confirmationTime =
                                    ConfirmationTime(
                                        height = confirmationHeight,
                                        timestamp = confirmationTimestamp,
                                    ),
                                isConfirmed = true,
                                timestamp = confirmationTimestamp.toLong(),
                                address = address,
                                addressAmount = addressAmount,
                                changeAddress = changeAddress,
                                changeAmount = changeAmount,
                                isSelfTransfer = isSelfTransfer,
                            )
                    }
                }
            }
            result
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to load cached transaction details for $walletId: ${e.message}")
            }
            emptyMap()
        }
    }

    fun putConfirmedTransactionDetails(
        walletId: String,
        descriptorKey: String,
        transactions: Collection<TransactionDetails>,
    ) {
        if (transactions.isEmpty()) return

        try {
            val now = System.currentTimeMillis()
            writableDatabase.transaction {
                transactions.forEach { tx ->
                    if (!tx.isConfirmed) return@forEach
                    val confirmationTime = tx.confirmationTime ?: return@forEach
                    val values =
                        ContentValues().apply {
                            put(COL_WALLET_ID, walletId)
                            put(COL_DESCRIPTOR_KEY, descriptorKey)
                            put(COL_TXID, tx.txid)
                            put(COL_AMOUNT_SATS, tx.amountSats)
                            put(COL_FEE, tx.fee?.toLong())
                            put(COL_WEIGHT, tx.weight?.toLong())
                            put(COL_CONFIRMATION_HEIGHT, confirmationTime.height.toLong())
                            put(COL_CONFIRMATION_TIMESTAMP, confirmationTime.timestamp.toLong())
                            put(COL_ADDRESS, tx.address)
                            put(COL_ADDRESS_AMOUNT, tx.addressAmount?.toLong())
                            put(COL_CHANGE_ADDRESS, tx.changeAddress)
                            put(COL_CHANGE_AMOUNT, tx.changeAmount?.toLong())
                            put(COL_IS_SELF_TRANSFER, if (tx.isSelfTransfer) 1 else 0)
                            put(COL_CACHED_AT, now)
                        }
                    insertWithOnConflict(
                        TABLE_WALLET_TX_DETAILS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to cache confirmed transaction details for $walletId: ${e.message}")
            }
        }
    }

    fun clearConfirmedTransactionDetails(walletId: String) {
        try {
            writableDatabase.delete(
                TABLE_WALLET_TX_DETAILS,
                "$COL_WALLET_ID = ?",
                arrayOf(walletId),
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to clear cached transaction details for $walletId: ${e.message}")
            }
        }
    }

    // ==================== Maintenance ====================

    /**
     * Remove unconfirmed verbose tx entries older than [UNCONFIRMED_TTL_MS].
     * Called periodically to prevent stale unconfirmed data from accumulating.
     */
    fun pruneStaleUnconfirmed() {
        try {
            val cutoff = System.currentTimeMillis() - UNCONFIRMED_TTL_MS
            val deleted =
                writableDatabase.delete(
                    TABLE_TX_VERBOSE,
                    "$COL_CONFIRMED = 0 AND $COL_CACHED_AT < ?",
                    arrayOf(cutoff.toString()),
                )
            if (deleted > 0 && BuildConfig.DEBUG) {
                Log.d(TAG, "Pruned $deleted stale unconfirmed verbose tx entries")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Prune failed: ${e.message}")
        }
    }

    /**
     * Close the helper and remove the physical SQLite database plus sidecars.
     * Used during full wallet wipe to avoid leaving recoverable free pages behind.
     */
    fun deleteDatabaseFile(): Boolean {
        return try {
            close()
            appContext.deleteDatabase(DATABASE_NAME)

            val dbPath = appContext.getDatabasePath(DATABASE_NAME)
            val sqliteFiles = listOf(
                dbPath,
                File("${dbPath.path}-wal"),
                File("${dbPath.path}-shm"),
                File("${dbPath.path}-journal"),
            )
            val success = sqliteFiles.none { it.exists() }

            if (!success) {
                Log.e(TAG, "Failed to delete Electrum cache database file")
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "Deleted Electrum cache database file")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Electrum cache database file")
            if (BuildConfig.DEBUG) Log.e(TAG, "Electrum cache delete exception", e)
            false
        }
    }
}
