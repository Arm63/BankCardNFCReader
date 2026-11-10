package com.emvreader.nfc

import java.io.IOException

/**
 * Sends one APDU and returns the card's raw response (data followed by SW1 SW2).
 *
 * Seam between the EMV read logic in [EmvCardReader] and `android.nfc.tech.IsoDep`,
 * so the flow can be exercised against scripted cards in JVM unit tests.
 */
internal fun interface ApduTransport {
    @Throws(IOException::class)
    fun transceive(command: ByteArray): ByteArray
}
