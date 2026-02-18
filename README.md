# Android Bank Card Reader 💳

[![](https://jitpack.io/v/ArmenAsatryan/android-bank-card-reader.svg)](https://jitpack.io/#ArmenAsatryan/android-bank-card-reader)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Read credit card and debit card numbers via NFC on Android.** 

A lightweight Android library for reading bank card numbers (PAN) from contactless payment cards using NFC. Works with Visa, Mastercard, American Express, Discover, UnionPay, JCB, and Mir cards.

**Now with Payment Source Detection!** Automatically detects if the scanned card is a physical plastic card or a digital wallet (Google Wallet, Apple Pay, Samsung Pay, etc.)

## 🔥 Features

- 📱 **Read card numbers** from NFC-enabled credit/debit cards
- 💳 **Multi-brand support**: Visa, Mastercard, Amex, Discover, UnionPay, JCB, Mir
- 🔍 **Payment Source Detection**: Distinguish between physical cards and digital wallets
- 📲 **Wallet Detection**: Identifies Google Wallet, Apple Pay, Samsung Pay, and more
- 🔒 **Safe**: Read-only access - cannot make payments or modify card data
- ⚡ **Kotlin Coroutines**: Modern async API
- 🎯 **Easy integration**: Simple Activity lifecycle management
- 📦 **Lightweight**: Minimal dependencies

## 📦 Installation

Add JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Arm63:BankCardNFCReader:1.1.2")
}
```

## 🚀 Quick Start

### Simple Usage (Recommended)

```kotlin
class MainActivity : AppCompatActivity() {
    private val cardReader = EmvCardReader()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onResume() {
        super.onResume()
        // Enable NFC reader mode
        nfcAdapter?.enableReaderMode(
            this,
            { tag -> handleNfcTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    private fun handleNfcTag(tag: Tag) {
        lifecycleScope.launch {
            val result = cardReader.readCard(tag)
            when (result) {
                is CardData.Success -> {
                    // Card read successfully!
                    val cardNumber = result.maskedPan       // "4111 **** **** 1111"
                    val cardType = result.cardType          // CardType.VISA
                    val source = result.paymentSource       // PaymentSource.PHYSICAL_CARD
                    val isWallet = result.isTokenizedWallet // false
                    
                    if (isWallet) {
                        showWalletCard(cardNumber, source.displayName)
                    } else {
                        showPhysicalCard(cardNumber, cardType.displayName)
                    }
                }
                is CardData.Error -> {
                    showError(result.message)
                }
            }
        }
    }
}
```

### With Jetpack Compose

```kotlin
@Composable
fun CardReaderScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    var cardData by remember { mutableStateOf<CardData?>(null) }
    val cardReader = remember { EmvCardReader() }
    
    DisposableEffect(Unit) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        nfcAdapter?.enableReaderMode(
            activity,
            { tag ->
                CoroutineScope(Dispatchers.Main).launch {
                    cardData = cardReader.readCard(tag)
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
            null
        )
        onDispose { 
            nfcAdapter?.disableReaderMode(activity)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val result = cardData) {
            is CardData.Success -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Show payment source
                    Text(
                        text = if (result.isTokenizedWallet) "📱 ${result.paymentSource.displayName}" 
                               else "💳 Physical Card",
                        fontSize = 18.sp
                    )
                    Text("${result.cardType.displayName}", fontSize = 24.sp)
                    Text(result.maskedPan, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                    
                    if (result.isTokenizedWallet) {
                        Text("Tokenized (DPAN)", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            is CardData.Error -> Text("❌ ${result.message}", color = Color.Red)
            null -> Text("Tap your card to read", fontSize = 18.sp)
        }
    }
}
```

## 🔍 Payment Source Detection

The library automatically detects whether the scanned card is a physical plastic card or a digital wallet:

### How It Works

The detection uses EMV tags from the card's response:

| EMV Tag | Name | Purpose |
|---------|------|---------|
| **9F6E** | Form Factor Indicator | Identifies device type (card vs mobile) |
| **9F19** | Token Requestor ID | Identifies specific wallet provider |
| **50** | Application Label | May contain wallet identifiers |

### Detection Logic

```
┌─────────────────────────────────────────────────────────────┐
│                    Payment Source Detection                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Check Token Requestor ID (9F19)                         │
│     ├─ Known ID → Specific Wallet (HIGH confidence)         │
│     └─ Unknown ID → Other Wallet (MEDIUM confidence)        │
│                                                             │
│  2. Check Form Factor Indicator (9F6E)                      │
│     ├─ Byte 2 Bit 8 = 0 → Physical Card (not connected)     │
│     └─ Byte 2 Bit 8 = 1 → Mobile Wallet (network connected) │
│                                                             │
│  3. Check Application Label (50)                            │
│     └─ Contains wallet keywords → Infer wallet type         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Supported Payment Sources

| Payment Source | Detection Method | Confidence |
|----------------|------------------|------------|
| **Physical Card** | Form Factor (not network-connected) | HIGH |
| **Google Wallet** | Token Requestor ID | HIGH |
| **Samsung Pay** | Token Requestor ID | HIGH |
| **Apple Pay** | Token Requestor ID | HIGH |
| **Garmin Pay** | Token Requestor ID | HIGH |
| **Fitbit Pay** | Token Requestor ID | HIGH |
| **Mobile Wallet** | Form Factor (network-connected, unknown provider) | HIGH |

### Example Detection Results

```kotlin
// Physical Visa card
result.paymentSource      // PaymentSource.PHYSICAL_CARD
result.isTokenizedWallet  // false
result.isPhysicalCard     // true

// Google Wallet on Android phone
result.paymentSource      // PaymentSource.MOBILE_WALLET (or GOOGLE_WALLET if Token ID present)
result.isTokenizedWallet  // true
result.isPhysicalCard     // false

// Apple Pay on iPhone
result.paymentSource      // PaymentSource.MOBILE_WALLET
result.isTokenizedWallet  // true
```

### Detection Result Details

For debugging or advanced use, access the full detection result:

```kotlin
when (result) {
    is CardData.Success -> {
        result.sourceDetectionResult?.let { detection ->
            Log.d("Detection", "Source: ${detection.source}")
            Log.d("Detection", "Confidence: ${detection.confidence}")
            Log.d("Detection", "Form Factor: ${detection.formFactorIndicator?.toHex()}")
            Log.d("Detection", "Token Requestor ID: ${detection.tokenRequestorId}")
            Log.d("Detection", "Debug Info: ${detection.debugInfo}")
        }
    }
}
```

## 📖 API Reference

### CardData.Success

| Property | Type | Example | Description |
|----------|------|---------|-------------|
| `pan` | String | `"4111111111111111"` | Raw card number (DPAN for wallets) |
| `formattedPan` | String | `"4111 1111 1111 1111"` | Formatted with spaces |
| `maskedPan` | String | `"4111 **** **** 1111"` | Masked for display |
| `cardType` | CardType | `CardType.VISA` | Card brand |
| `paymentSource` | PaymentSource | `PaymentSource.PHYSICAL_CARD` | Source type |
| `isTokenizedWallet` | Boolean | `false` | Is it a digital wallet? |
| `isPhysicalCard` | Boolean | `true` | Is it a physical card? |
| `sourceDetectionResult` | DetectionResult? | - | Full detection details |

### PaymentSource Enum

```kotlin
enum class PaymentSource {
    PHYSICAL_CARD,   // Traditional plastic card
    GOOGLE_WALLET,   // Google Wallet / Google Pay
    SAMSUNG_PAY,     // Samsung Pay
    APPLE_PAY,       // Apple Pay
    GARMIN_PAY,      // Garmin Pay
    FITBIT_PAY,      // Fitbit Pay
    MOBILE_WALLET,   // Generic mobile wallet (provider unknown)
    OTHER_WALLET,    // Other digital wallet
    UNKNOWN          // Could not determine
}
```

### Supported Card Types

| Card | Status | Detected By |
|------|--------|-------------|
| Visa | ✅ | Starts with 4 |
| Mastercard | ✅ | Starts with 51-55 or 2221-2720 |
| American Express | ✅ | Starts with 34 or 37 |
| Discover | ✅ | Starts with 6011, 65, or 644-649 |
| UnionPay | ✅ | Starts with 62 |
| JCB | ✅ | Starts with 35 |
| Mir | ✅ | Starts with 220 |

### Error Codes

| Code | When |
|------|------|
| `UNSUPPORTED_CARD` | Card doesn't support contactless |
| `PPSE_NOT_FOUND` | No payment app on card |
| `PAN_NOT_FOUND` | Could not read card number |
| `TAG_LOST` | Card removed too quickly |
| `COMMUNICATION_ERROR` | NFC read failed |

## 📱 AndroidManifest.xml

The library includes NFC permission automatically. Add this to your app if you want NFC to be required:

```xml
<manifest>
    <!-- Require NFC hardware -->
    <uses-feature android:name="android.hardware.nfc" android:required="true" />
</manifest>
```

## ⚠️ Security Notes

This library **only reads** publicly available card data:
- ✅ Card number (PAN / DPAN for wallets)
- ✅ Card type detection
- ✅ Payment source detection
- ❌ Cannot read CVV/CVC
- ❌ Cannot read PIN
- ❌ Cannot make transactions
- ❌ Cannot clone cards

**Important for Tokenized Wallets:**
- The PAN returned from digital wallets is a **DPAN** (Device Primary Account Number)
- This is a tokenized number, not the actual card number
- DPANs are device-specific and cannot be used on other devices

Handle card numbers according to PCI-DSS if applicable.

## 📋 Requirements

- Android 5.0+ (API 21+)
- Device with NFC hardware
- Kotlin 1.8+

## 🔧 Advanced Usage

### Custom Timeout

```kotlin
val reader = EmvCardReader(
    config = ReaderConfig(timeoutMs = 10000)  // 10 seconds
)
```

### Standalone Source Detection

Use the `CardSourceDetector` for custom detection logic:

```kotlin
// If you have TLV data from your own card reading
val detectionResult = CardSourceDetector.detect(tlvData)

Log.d("Detection", "Source: ${detectionResult.source}")
Log.d("Detection", "Confidence: ${detectionResult.confidence}")
Log.d("Detection", "Is Wallet: ${detectionResult.source.isDigitalWallet}")
```

## 🧪 Testing

### Testing with Physical Cards
- Any contactless Visa, Mastercard, etc. card should work
- The detection should return `PaymentSource.PHYSICAL_CARD`

### Testing with Digital Wallets
- **Google Wallet**: Add a card to Google Wallet, tap phone to your test device
- **Apple Pay**: Add a card to Apple Pay, tap iPhone to your test device
- **Samsung Pay**: Add a card to Samsung Pay, tap Samsung phone to your test device

Note: Without Token Requestor ID, wallets will be detected as `MOBILE_WALLET` (provider unknown).

## 📄 License

```
MIT License - Copyright (c) 2025 Armen Asatryan
```

## 🤝 Contributing

Pull requests welcome! Please open an issue first for major changes.

### Development Setup

```bash
git clone https://github.com/ArmenAsatryan/android-bank-card-reader.git
cd android-bank-card-reader
./gradlew build
```

## 📝 Changelog

### v1.1.2 (Current)
- Stabilized on Android SDK 35 with androidx.core 1.15.0
- Improved dependency compatibility and stability

### v1.1.1
- Updated dependencies for better compatibility
- Minor bug fixes and improvements

### v1.1.0
- Added payment source detection (physical cards vs digital wallets)
- Support for detecting Google Wallet, Apple Pay, Samsung Pay, and more
- Enhanced card reading with tokenized wallet detection
- Improved EMV tag parsing for Form Factor Indicator and Token Requestor ID

### v1.0.0
- Initial release with NFC card reading support
- Multi-brand support: Visa, Mastercard, Amex, Discover, UnionPay, JCB, Mir
- Simple API with `EmvCardReader`

## 🔗 Links

- [JitPack](https://jitpack.io/#ArmenAsatryan/android-bank-card-reader)
- [Report Issue](https://github.com/ArmenAsatryan/android-bank-card-reader/issues)

---

**Keywords**: android nfc card reader, read credit card android, bank card reader nfc, contactless card reader android, visa mastercard reader android, emv card reader, kotlin nfc library, google wallet detection, apple pay detection, digital wallet detection, tokenized card reader
