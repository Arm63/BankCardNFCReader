package com.example.bankcardreader.nfc

/**
 * Card number validation utilities.
 * Implements Luhn algorithm and card type detection.
 */
object CardValidator {

    enum class CardType(val displayName: String) {
        VISA("Visa"),
        MASTERCARD("Mastercard"),
        AMEX("American Express"),
        DISCOVER("Discover"),
        JCB("JCB"),
        UNIONPAY("UnionPay"),
        MIR("Mir"),
        DINERS("Diners Club"),
        UNKNOWN("Unknown")
    }

    /**
     * Validate card number using Luhn algorithm (Mod 10)
     * 
     * Algorithm:
     * 1. Starting from rightmost digit, double every second digit
     * 2. If doubling results in number > 9, subtract 9
     * 3. Sum all digits
     * 4. Valid if sum is divisible by 10
     */
    fun isValidLuhn(cardNumber: String): Boolean {
        val digits = cardNumber.filter { it.isDigit() }
        
        if (digits.length < 13 || digits.length > 19) {
            return false
        }

        var sum = 0
        var isSecond = false

        for (i in digits.length - 1 downTo 0) {
            var digit = digits[i].digitToInt()

            if (isSecond) {
                digit *= 2
                if (digit > 9) {
                    digit -= 9
                }
            }

            sum += digit
            isSecond = !isSecond
        }

        return sum % 10 == 0
    }

    /**
     * Detect card type based on IIN (Issuer Identification Number)
     * IIN is the first 6-8 digits of the card number
     */
    fun detectCardType(cardNumber: String): CardType {
        val digits = cardNumber.filter { it.isDigit() }
        
        if (digits.length < 4) return CardType.UNKNOWN

        return when {
            // Visa: starts with 4
            digits.startsWith("4") -> CardType.VISA

            // Mastercard: 51-55, 2221-2720
            digits.startsWith("51") || digits.startsWith("52") ||
            digits.startsWith("53") || digits.startsWith("54") ||
            digits.startsWith("55") ||
            (digits.substring(0, 4).toIntOrNull()?.let { it in 2221..2720 } == true) ->
                CardType.MASTERCARD

            // Amex: 34, 37
            digits.startsWith("34") || digits.startsWith("37") -> CardType.AMEX

            // Discover: 6011, 644-649, 65
            digits.startsWith("6011") || digits.startsWith("65") ||
            (digits.substring(0, 3).toIntOrNull()?.let { it in 644..649 } == true) ->
                CardType.DISCOVER

            // JCB: 3528-3589
            (digits.substring(0, 4).toIntOrNull()?.let { it in 3528..3589 } == true) ->
                CardType.JCB

            // UnionPay: 62
            digits.startsWith("62") -> CardType.UNIONPAY

            // Mir: 2200-2204
            (digits.substring(0, 4).toIntOrNull()?.let { it in 2200..2204 } == true) ->
                CardType.MIR

            // Diners Club: 300-305, 36, 38-39
            digits.startsWith("36") || digits.startsWith("38") || digits.startsWith("39") ||
            (digits.substring(0, 3).toIntOrNull()?.let { it in 300..305 } == true) ->
                CardType.DINERS

            else -> CardType.UNKNOWN
        }
    }

    /**
     * Format card number with spaces for display
     * e.g., "4111111111111111" -> "4111 1111 1111 1111"
     */
    fun formatCardNumber(cardNumber: String): String {
        val digits = cardNumber.filter { it.isDigit() }
        
        return when (detectCardType(digits)) {
            CardType.AMEX -> {
                // Amex format: 4-6-5 (xxxx xxxxxx xxxxx)
                buildString {
                    digits.forEachIndexed { index, c ->
                        if (index == 4 || index == 10) append(' ')
                        append(c)
                    }
                }
            }
            else -> {
                // Standard format: 4-4-4-4
                digits.chunked(4).joinToString(" ")
            }
        }
    }

    /**
     * Mask card number for display (show only last 4 digits)
     * e.g., "4111111111111111" -> "**** **** **** 1111"
     */
    fun maskCardNumber(cardNumber: String): String {
        val digits = cardNumber.filter { it.isDigit() }
        
        if (digits.length < 4) return cardNumber

        val lastFour = digits.takeLast(4)
        val masked = "*".repeat(digits.length - 4) + lastFour
        
        return formatCardNumber(masked)
    }

    /**
     * Check if card number has valid length
     */
    fun isValidLength(cardNumber: String): Boolean {
        val length = cardNumber.filter { it.isDigit() }.length
        return length in 13..19
    }

    /**
     * Full validation: length + Luhn
     */
    fun isValidCardNumber(cardNumber: String): Boolean {
        return isValidLength(cardNumber) && isValidLuhn(cardNumber)
    }

    /**
     * Clean card number input (remove non-digits)
     */
    fun cleanCardNumber(input: String): String {
        return input.filter { it.isDigit() }
    }
}

