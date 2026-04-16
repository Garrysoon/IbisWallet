package github.aeonbtc.ibiswallet.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.model.ConfirmationTime
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
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
class ElectrumCache(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "ElectrumCache"
        private const val DATABASE_NAME = "electrum_cache.db"
        private const val DATABASE_VERSION = 4
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
                $COL_CONFIRMED INTEGER NOT NULL,
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
                null, null, null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_HEX))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache raw transaction hex.
     */
    fun putRawTx(txid: String, hex: String) {
        try {
            val values = ContentValues().apply {
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
        } catch (_: Exception) {
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
                null, null, null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_JSON))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache verbose transaction JSON.
     * @param confirmed If true, entry is kept permanently; if false, pruned after UNCONFIRMED_TTL_MS
     */
    fun putVerboseTx(txid: String, json: String, confirmed: Boolean) {
        try {
            val values = ContentValues().apply {
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
        } catch (_: Exception) {
        }
    }

    /**
     * Prune unconfirmed verbose tx entries older than UNCONFIRMED_TTL_MS.
     * Called periodically to keep the cache from growing indefinitely.
     */
    fun pruneStaleVerboseTxs() {
        val cutoff = System.currentTimeMillis() - UNCONFIRMED_TTL_MS
        try {
            writableDatabase.delete(
                TABLE_TX_VERBOSE,
                "$COL_CONFIRMED = 0 AND $COL_CACHED_AT < ?",
                arrayOf(cutoff.toString()),
            )
        } catch (_: Exception) {
        }
    }

    // ==================== Script Hash Statuses ====================

    fun getScriptHashStatus(scriptHash: String): String? {
        return try {
            readableDatabase.query(
                TABLE_SCRIPT_HASH_STATUSES,
                arrayOf(COL_STATUS),
                "$COL_SCRIPT_HASH = ?",
                arrayOf(scriptHash),
                null, null, null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun putScriptHashStatus(scriptHash: String, status: String?) {
        try {
            val values = ContentValues().apply {
                put(COL_SCRIPT_HASH, scriptHash)
                put(COL_STATUS, status)
                put(COL_CACHED_AT, System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                TABLE_SCRIPT_HASH_STATUSES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (_: Exception) {
        }
    }

    fun deleteScriptHashStatus(scriptHash: String) {
        try {
            writableDatabase.delete(
                TABLE_SCRIPT_HASH_STATUSES,
                "$COL_SCRIPT_HASH = ?",
                arrayOf(scriptHash),
            )
        } catch (_: Exception) {
        }
    }

    // ==================== Script Hash History ====================

    /**
     * Get cached history JSON for a script hash, keyed by the status hash.
     * @return history JSON string or null if not cached
     */
    fun getScriptHashHistory(scriptHash: String): String? {
        return try {
            readableDatabase.query(
                TABLE_SCRIPT_HASH_HISTORY,
                arrayOf(COL_HISTORY_JSON),
                "$COL_SCRIPT_HASH = ?",
                arrayOf(scriptHash),
                null, null, null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_JSON))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache the history JSON for a script hash.
     * Keyed by the status hash, so if the status changes, the history is invalidated.
     */
    fun putScriptHashHistory(scriptHash: String, historyJson: String) {
        try {
            val values = ContentValues().apply {
                put(COL_SCRIPT_HASH, scriptHash)
                put(COL_HISTORY_JSON, historyJson)
                put(COL_CACHED_AT, System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                TABLE_SCRIPT_HASH_HISTORY,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (_: Exception) {
        }
    }

    // ==================== Wallet Transaction Details ====================

    data class WalletTxDetails(
        val walletId: String,
        val descriptorKey: String,
        val txid: String,
        val amountSats: Long,
        val fee: Long?,
        val weight: Int?,
        val confirmationHeight: Int,
        val confirmationTimestamp: Long,
        val address: String?,
        val addressAmount: Long?,
        val changeAddress: String?,
        val changeAmount: Long?,
        val isSelfTransfer: Boolean,
        val cachedAt: Long,
    )

    fun getWalletTxDetails(
        walletId: String,
        descriptorKey: String,
        txid: String,
    ): WalletTxDetails? {
        return try {
            readableDatabase.query(
                TABLE_WALLET_TX_DETAILS,
                null,
                "$COL_WALLET_ID = ? AND $COL_DESCRIPTOR_KEY = ? AND $COL_TXID = ?",
                arrayOf(walletId, descriptorKey, txid),
                null, null, null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    WalletTxDetails(
                        walletId = cursor.getString(cursor.getColumnIndexOrThrow(COL_WALLET_ID)),
                        descriptorKey = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTOR_KEY)),
                        txid = cursor.getString(cursor.getColumnIndexOrThrow(COL_TXID)),
                        amountSats = cursor.getLong(cursor.getColumnIndexOrThrow(COL_AMOUNT_SATS)),
                        fee = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FEE)).takeIf { !cursor.isNull(cursor.getColumnIndexOrThrow(COL_FEE)) },
                        weight = cursor.getInt(cursor.getColumnIndexOrThrow(COL_WEIGHT)).takeIf { !cursor.isNull(cursor.getColumnIndexOrThrow(COL_WEIGHT)) },
                        confirmationHeight = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CONFIRMATION_HEIGHT)),
                        confirmationTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONFIRMATION_TIMESTAMP)),
                        address = cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDRESS)).takeIf { !cursor.isNull(cursor.getColumnIndexOrThrow(COL_ADDRESS)) },
                        addressAmount = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ADDRESS_AMOUNT)).takeIf { !cursor.isNull(cursor.getColumnIndexOrThrow(COL_ADDRESS_AMOUNT)) },
                        changeAddress = cursor.getString(cursor.getColumnIndexOrThrow(COL_CHANGE_ADDRESS)).takeIf { !cursor.isNull(cursor.getColumnIndexOrThrow(COL_CHANGE_ADDRESS)) },
                        changeAmount = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CHANGE_AMOUNT)).takeIf { !cursor.isNull(cursor.getColumnIndexOrThrow(COL_CHANGE_AMOUNT)) },
                        isSelfTransfer = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SELF_TRANSFER)) == 1,
                        cachedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CACHED_AT)),
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Batch insert wallet transaction details. Uses chunked inserts for large batches.
     */
    fun putWalletTxDetailsBatch(detailsList: List<WalletTxDetails>) {
        if (detailsList.isEmpty()) return
        try {
            writableDatabase.transaction {
                for (chunk in detailsList.chunked(SQLITE_IN_CHUNK_SIZE)) {
                    for (details in chunk) {
                        val values = ContentValues().apply {
                            put(COL_WALLET_ID, details.walletId)
                            put(COL_DESCRIPTOR_KEY, details.descriptorKey)
                            put(COL_TXID, details.txid)
                            put(COL_AMOUNT_SATS, details.amountSats)
                            put(COL_FEE, details.fee)
                            put(COL_WEIGHT, details.weight)
                            put(COL_CONFIRMATION_HEIGHT, details.confirmationHeight)
                            put(COL_CONFIRMATION_TIMESTAMP, details.confirmationTimestamp)
                            put(COL_ADDRESS, details.address)
                            put(COL_ADDRESS_AMOUNT, details.addressAmount)
                            put(COL_CHANGE_ADDRESS, details.changeAddress)
                            put(COL_CHANGE_AMOUNT, details.changeAmount)
                            put(COL_IS_SELF_TRANSFER, if (details.isSelfTransfer) 1 else 0)
                            put(COL_CACHED_AT, details.cachedAt)
                        }
                        insertWithOnConflict(
                            TABLE_WALLET_TX_DETAILS,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_REPLACE,
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // ==================== Maintenance ====================

    /**
     * Delete all data for a specific wallet from the cache.
     */
    fun clearWallet(walletId: String) {
        try {
            writableDatabase.delete(
                TABLE_WALLET_TX_DETAILS,
                "$COL_WALLET_ID = ?",
                arrayOf(walletId),
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Prune cached wallet transaction display rows that reference txids
     * no longer present in the raw transaction cache. This happens when
     * BDK drops transactions during a deep reorg or wallet reset.
     */
    fun pruneOrphanedWalletTxDetails() {
        try {
            // Find all txids in wallet_tx_details that don't exist in tx_raw
            val orphanTxids = mutableListOf<String>()
            readableDatabase.rawQuery(
                """
                SELECT wt.$COL_TXID FROM $TABLE_WALLET_TX_DETAILS wt
                LEFT JOIN $TABLE_TX_RAW tr ON wt.$COL_TXID = tr.$COL_TXID
                WHERE tr.$COL_TXID IS NULL
                """.trimIndent(),
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    orphanTxids.add(cursor.getString(0))
                }
            }
            if (orphanTxids.isEmpty()) return
            // Chunked delete to avoid SQLite variable limit
            for (chunk in orphanTxids.chunked(SQLITE_DELETE_CHUNK_SIZE)) {
                val placeholders = List(chunk.size) { "?" }.joinToString(",")
                writableDatabase.delete(
                    TABLE_WALLET_TX_DETAILS,
                    "$COL_TXID IN ($placeholders)",
                    chunk.toTypedArray(),
                )
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Compute total cache size on disk.
     */
    fun getCacheSizeBytes(): Long {
        return try {
            val dbFile = appContext.getDatabasePath(DATABASE_NAME)
            dbFile?.length() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Clear all cached data.
     */
    fun clearAll() {
        try {
            writableDatabase.delete(TABLE_TX_RAW, null, null)
            writableDatabase.delete(TABLE_TX_VERBOSE, null, null)
            writableDatabase.delete(TABLE_SCRIPT_HASH_STATUSES, null, null)
            writableDatabase.delete(TABLE_WALLET_TX_DETAILS, null, null)
            writableDatabase.delete(TABLE_SCRIPT_HASH_HISTORY, null, null)
        } catch (_: Exception) {
        }
    }

    // ==================== Additional methods for compatibility ====================

    /**
     * Get cached history JSON for a script hash by its status hash.
     * @return history JSON string or null if not cached or status mismatch
     */
    fun getHistory(scriptHash: String, status: String): String? {
        return try {
            readableDatabase.query(
                TABLE_SCRIPT_HASH_HISTORY,
                arrayOf(COL_HISTORY_JSON),
                "$COL_SCRIPT_HASH = ? AND $COL_STATUS = ?",
                arrayOf(scriptHash, status),
                null, null, null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_JSON))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache the history JSON for a script hash keyed by its status hash.
     */
    fun putHistory(scriptHash: String, status: String, historyJson: String) {
        try {
            val values = ContentValues().apply {
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
        } catch (_: Exception) {
        }
    }

    /**
     * Prune stale unconfirmed verbose transactions.
     * Alias for pruneStaleVerboseTxs() for API compatibility.
     */
    fun pruneStaleUnconfirmed() {
        pruneStaleVerboseTxs()
    }

    /**
     * Find which txids from the list are missing from the raw transaction cache.
     * @return list of txids not present in the cache
     */
    fun findMissingRawTxids(txids: List<String>): List<String> {
        if (txids.isEmpty()) return emptyList()
        return try {
            // Query existing txids
            val placeholders = txids.joinToString(",") { "?" }
            val existingTxids = mutableSetOf<String>()
            readableDatabase.rawQuery(
                "SELECT $COL_TXID FROM $TABLE_TX_RAW WHERE $COL_TXID IN ($placeholders)",
                txids.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    existingTxids.add(cursor.getString(0))
                }
            }
            // Return txids not in the existing set
            txids.filter { it !in existingTxids }
        } catch (_: Exception) {
            txids // Return all as missing on error
        }
    }

    /**
     * Load all persisted script hash statuses.
     * @return map of script hash to status hash
     */
    fun loadScriptHashStatuses(): Map<String, String> {
        return try {
            val result = mutableMapOf<String, String>()
            readableDatabase.query(
                TABLE_SCRIPT_HASH_STATUSES,
                arrayOf(COL_SCRIPT_HASH, COL_STATUS),
                null, null, null, null, null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val scriptHash = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCRIPT_HASH))
                    val status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))
                    if (status != null) {
                        result[scriptHash] = status
                    }
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Save script hash statuses to the cache.
     */
    fun saveScriptHashStatuses(statuses: Map<String, String?>) {
        try {
            writableDatabase.transaction {
                for ((scriptHash, status) in statuses) {
                    if (status != null) {
                        val values = ContentValues().apply {
                            put(COL_SCRIPT_HASH, scriptHash)
                            put(COL_STATUS, status)
                            put(COL_CACHED_AT, System.currentTimeMillis())
                        }
                        insertWithOnConflict(
                            TABLE_SCRIPT_HASH_STATUSES,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_REPLACE,
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Clear all script hash statuses from the cache.
     */
    fun clearScriptHashStatuses() {
        try {
            writableDatabase.delete(TABLE_SCRIPT_HASH_STATUSES, null, null)
        } catch (_: Exception) {
        }
    }

    /**
     * Clear all history cache entries.
     */
    fun clearAllHistory() {
        try {
            writableDatabase.delete(TABLE_SCRIPT_HASH_HISTORY, null, null)
        } catch (_: Exception) {
        }
    }

    /**
     * Clear confirmed transaction details for a specific wallet.
     */
    fun clearConfirmedTransactionDetails(walletId: String) {
        clearWallet(walletId)
    }

    /**
     * Load confirmed transaction details from the cache.
     * @return map of txid to TransactionDetails
     */
    fun loadConfirmedTransactionDetails(
        walletId: String,
        descriptorKey: String,
        txids: List<String>,
    ): Map<String, TransactionDetails> {
        if (txids.isEmpty()) return emptyMap()
        return try {
            val result = mutableMapOf<String, TransactionDetails>()
            val placeholders = txids.joinToString(",") { "?" }
            val selection = "$COL_WALLET_ID = ? AND $COL_DESCRIPTOR_KEY = ? AND $COL_TXID IN ($placeholders)"
            val selectionArgs = arrayOf(walletId, descriptorKey, *txids.toTypedArray())

            readableDatabase.query(
                TABLE_WALLET_TX_DETAILS,
                null,
                selection,
                selectionArgs,
                null, null, null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val txid = cursor.getString(cursor.getColumnIndexOrThrow(COL_TXID))
                    val details = WalletTxDetails(
                        walletId = cursor.getString(cursor.getColumnIndexOrThrow(COL_WALLET_ID)),
                        descriptorKey = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTOR_KEY)),
                        txid = txid,
                        amountSats = cursor.getLong(cursor.getColumnIndexOrThrow(COL_AMOUNT_SATS)),
                        fee = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FEE)).takeIf {
                            !cursor.isNull(cursor.getColumnIndexOrThrow(COL_FEE))
                        },
                        weight = cursor.getInt(cursor.getColumnIndexOrThrow(COL_WEIGHT)).takeIf {
                            !cursor.isNull(cursor.getColumnIndexOrThrow(COL_WEIGHT))
                        },
                        confirmationHeight = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CONFIRMATION_HEIGHT)),
                        confirmationTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONFIRMATION_TIMESTAMP)),
                        address = cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDRESS)).takeIf {
                            !cursor.isNull(cursor.getColumnIndexOrThrow(COL_ADDRESS))
                        },
                        addressAmount = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ADDRESS_AMOUNT)).takeIf {
                            !cursor.isNull(cursor.getColumnIndexOrThrow(COL_ADDRESS_AMOUNT))
                        },
                        changeAddress = cursor.getString(cursor.getColumnIndexOrThrow(COL_CHANGE_ADDRESS)).takeIf {
                            !cursor.isNull(cursor.getColumnIndexOrThrow(COL_CHANGE_ADDRESS))
                        },
                        changeAmount = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CHANGE_AMOUNT)).takeIf {
                            !cursor.isNull(cursor.getColumnIndexOrThrow(COL_CHANGE_AMOUNT))
                        },
                        isSelfTransfer = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SELF_TRANSFER)) == 1,
                        cachedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CACHED_AT)),
                    )
                    // Convert WalletTxDetails to TransactionDetails
                    val confirmationTime = if (details.confirmationHeight > 0) {
                        ConfirmationTime(
                            height = details.confirmationHeight.toULong(),
                            timestamp = details.confirmationTimestamp
                        )
                    } else null
                    result[txid] = TransactionDetails(
                        txid = txid,
                        isConfirmed = true,
                        confirmationTime = confirmationTime,
                        timestamp = details.confirmationTimestamp,
                        amountSats = details.amountSats, // Already Long
                        fee = details.fee?.toULong(),
                        weight = null, // Not in cache
                        address = details.address,
                        addressAmount = details.addressAmount?.toULong(),
                        changeAddress = details.changeAddress,
                        changeAmount = details.changeAmount?.toULong(),
                        isSelfTransfer = details.isSelfTransfer,
                    )
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Save confirmed transaction details to the cache.
     */
    fun putConfirmedTransactionDetails(
        walletId: String,
        descriptorKey: String,
        details: List<TransactionDetails>,
    ) {
        val walletTxDetails = details.map { tx ->
            WalletTxDetails(
                walletId = walletId,
                descriptorKey = descriptorKey,
                txid = tx.txid,
                amountSats = tx.amountSats, // Already Long
                fee = tx.fee?.toLong(),
                weight = tx.weight?.toInt(),
                confirmationHeight = tx.confirmationTime?.height?.toInt() ?: 0,
                confirmationTimestamp = tx.confirmationTime?.timestamp ?: 0,
                address = tx.address,
                addressAmount = tx.addressAmount?.toLong(),
                changeAddress = tx.changeAddress,
                changeAmount = tx.changeAmount?.toLong(),
                isSelfTransfer = tx.isSelfTransfer,
                cachedAt = System.currentTimeMillis(),
            )
        }
        putWalletTxDetailsBatch(walletTxDetails)
    }

    /**
     * Delete the database file from disk.
     * @return true if deletion was successful
     */
    fun deleteDatabaseFile(): Boolean {
        return try {
            val dbFile = appContext.getDatabasePath(DATABASE_NAME)
            if (dbFile != null && dbFile.exists()) {
                dbFile.delete()
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
