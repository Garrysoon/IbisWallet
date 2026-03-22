package github.aeonbtc.ibiswallet.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * HCE service that emulates an NFC Forum Type 4 Tag containing an NDEF message.
 * When another phone or NFC reader taps this device, it receives the NDEF payload
 * (a bitcoin: URI) as if it scanned a physical NFC tag.
 *
 * Implements the NFC Forum Type 4 Tag Operation specification:
 * 1. SELECT NDEF Tag Application (AID D2760000850101)
 * 2. SELECT Capability Container (file ID E103)
 * 3. READ BINARY on CC → returns CC bytes describing the NDEF file
 * 4. SELECT NDEF file (file ID E104)
 * 5. READ BINARY on NDEF → returns 2-byte length + NDEF message bytes
 */
class NdefHostApduService : HostApduService() {

    companion object {
        // Current NDEF payload — set by ReceiveScreen via the static setter
        @Volatile
        private var currentNdefMessage: ByteArray? = null

        /**
         * Set the NDEF message to broadcast. Pass null to stop broadcasting.
         * Typically called with a Bitcoin, Liquid, or Lightning payload from the Receive screen.
         */
        fun setNdefPayload(uri: String?) {
            currentNdefMessage = uri?.let { buildNdefBytes(it) }
        }

        /**
         * Build raw NDEF message bytes from a supported send payload.
         */
        private fun buildNdefBytes(uri: String): ByteArray {
            val trimmed = uri.trim()
            val ndefRecord =
                if (hasSupportedUriScheme(trimmed)) {
                    NdefRecord.createUri(trimmed)
                } else {
                    createTextRecord(trimmed)
                }
            val ndefMessage = NdefMessage(arrayOf(ndefRecord))
            return ndefMessage.toByteArray()
        }

        private fun hasSupportedUriScheme(uri: String): Boolean {
            val scheme = uri.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.US)
            return scheme in setOf("bitcoin", "lightning", "liquidnetwork", "liquid")
        }

        private fun createTextRecord(text: String): NdefRecord {
            val language = "en".toByteArray(StandardCharsets.US_ASCII)
            val textBytes = text.toByteArray(StandardCharsets.UTF_8)
            val payload = ByteArray(1 + language.size + textBytes.size)
            payload[0] = language.size.toByte()
            System.arraycopy(language, 0, payload, 1, language.size)
            System.arraycopy(textBytes, 0, payload, 1 + language.size, textBytes.size)
            return NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                NdefRecord.RTD_TEXT,
                ByteArray(0),
                payload,
            )
        }

        // APDU status words
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_FUNC_NOT_SUPPORTED = byteArrayOf(0x6A.toByte(), 0x81.toByte())

        // File IDs
        private val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
        private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

        // NDEF Tag Application SELECT command. Readers may include an optional Le byte.
        private val NDEF_APP_SELECT = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )
        private val NDEF_APP_SELECT_WITH_LE = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01, 0x00,
        )
    }

    // Which file is currently selected
    private var selectedFile: SelectedFile = SelectedFile.NONE
    private var ndefFileBytes: ByteArray = byteArrayOf()

    private enum class SelectedFile { NONE, CC, NDEF }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (commandApdu.size < 4) return SW_FUNC_NOT_SUPPORTED

        val ins = commandApdu[1]

        return when (ins) {
            // SELECT command (INS = 0xA4)
            0xA4.toByte() -> handleSelect(commandApdu)
            // READ BINARY command (INS = 0xB0)
            0xB0.toByte() -> handleReadBinary(commandApdu)
            else -> SW_FUNC_NOT_SUPPORTED
        }
    }

    private fun handleSelect(apdu: ByteArray): ByteArray {
        // SELECT by AID (P1=04): NDEF Tag Application
        if (apdu.contentEquals(NDEF_APP_SELECT) || apdu.contentEquals(NDEF_APP_SELECT_WITH_LE)) {
            selectedFile = SelectedFile.NONE
            // Rebuild NDEF file bytes from current payload
            val ndefBytes = currentNdefMessage
            ndefFileBytes = if (ndefBytes != null) {
                // Prepend 2-byte length
                val len = ndefBytes.size
                byteArrayOf((len shr 8).toByte(), (len and 0xFF).toByte()) + ndefBytes
            } else {
                byteArrayOf(0x00, 0x00) // empty NDEF
            }
            return SW_OK
        }

        // SELECT by file ID (P1=00, P2=0C): CC or NDEF file
        if (apdu.size >= 7 && apdu[2] == 0x00.toByte() && apdu[3] == 0x0C.toByte()) {
            val fileId = byteArrayOf(apdu[5], apdu[6])
            return when {
                fileId.contentEquals(CC_FILE_ID) -> {
                    selectedFile = SelectedFile.CC
                    SW_OK
                }
                fileId.contentEquals(NDEF_FILE_ID) -> {
                    selectedFile = SelectedFile.NDEF
                    SW_OK
                }
                else -> SW_NOT_FOUND
            }
        }

        return SW_NOT_FOUND
    }

    private fun handleReadBinary(apdu: ByteArray): ByteArray {
        if (apdu.size < 5) return SW_FUNC_NOT_SUPPORTED

        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val length = apdu[4].toInt() and 0xFF

        val fileData = when (selectedFile) {
            SelectedFile.CC -> buildCapabilityContainer()
            SelectedFile.NDEF -> ndefFileBytes
            SelectedFile.NONE -> return SW_NOT_FOUND
        }

        if (offset >= fileData.size) return SW_NOT_FOUND

        val end = minOf(offset + length, fileData.size)
        val chunk = fileData.copyOfRange(offset, end)
        return chunk + SW_OK
    }

    /**
     * Build the Capability Container (CC) file.
     * Describes the NDEF file's location, size, and access permissions.
     *
     * MLe (max R-APDU data) and MLc (max C-APDU data) are set to 246 bytes
     * to allow reading a full bitcoin BIP21 URI in a single READ BINARY
     * command. Android HCE supports up to ~261-byte APDUs; 246 leaves room
     * for the 2-byte status word and APDU header overhead.
     */
    private fun buildCapabilityContainer(): ByteArray {
        val ndefLen = ndefFileBytes.size
        val maxNdefSize = maxOf(ndefLen, 512) // report at least 512 bytes capacity

        return byteArrayOf(
            0x00, 0x0F,                               // CC length (15 bytes)
            0x20,                                      // Mapping version 2.0
            0x00, 0xF6.toByte(),                       // Max R-APDU size (246 bytes)
            0x00, 0xF6.toByte(),                       // Max C-APDU size (246 bytes)
            // NDEF File Control TLV
            0x04,                                      // Type: NDEF File Control
            0x06,                                      // Length of value
            0xE1.toByte(), 0x04,                       // NDEF file ID
            (maxNdefSize shr 8).toByte(),              // Max NDEF size (high byte)
            (maxNdefSize and 0xFF).toByte(),           // Max NDEF size (low byte)
            0x00,                                      // Read access: no security
            0xFF.toByte(),                             // Write access: denied
        )
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = SelectedFile.NONE
    }
}
