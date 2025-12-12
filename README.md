# Android Bank Card Reader 💳

[![](https://jitpack.io/v/ArmenAsatryan/android-bank-card-reader.svg)](https://jitpack.io/#ArmenAsatryan/android-bank-card-reader)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Read credit card and debit card numbers via NFC on Android.** 

A lightweight Android library for reading bank card numbers (PAN) from contactless payment cards using NFC. Works with Visa, Mastercard, American Express, Discover, UnionPay, JCB, and Mir cards.

## 🔥 Features

- 📱 **Read card numbers** from NFC-enabled credit/debit cards
- 💳 **Multi-brand support**: Visa, Mastercard, Amex, Discover, UnionPay, JCB, Mir
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
    implementation("com.github.Arm63:BankCardNFCReader:1.0.0")
```

## 🚀 Quick Start

### Simple Usage (Recommended)

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var cardReader: NfcCardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        cardReader = NfcCardManager(this) { result ->
            when (result) {
                is CardData.Success -> {
                    // Card read successfully!
                    val cardNumber = result.maskedPan  // "4111 **** **** 1111"
                    val cardType = result.cardType     // CardType.VISA
                    showCard(cardNumber, cardType.displayName)
                }
                is CardData.Error -> {
                    showError(result.message)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cardReader.enableReading()  // Start listening for cards
    }

    override fun onPause() {
        super.onPause()
        cardReader.disableReading() // Stop when app is in background
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
    
    DisposableEffect(Unit) {
        val manager = NfcCardManager(activity) { cardData = it }
        manager.enableReading()
        onDispose { manager.disableReading() }
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val result = cardData) {
            is CardData.Success -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💳 ${result.cardType.displayName}", fontSize = 24.sp)
                    Text(result.maskedPan, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                }
            }
            is CardData.Error -> Text("❌ ${result.message}", color = Color.Red)
            null -> Text("Tap your card to read", fontSize = 18.sp)
        }
    }
}
```

## 📖 API Reference

### CardData.Success

| Property | Type | Example |
|----------|------|---------|
| `pan` | String | `"4111111111111111"` |
| `formattedPan` | String | `"4111 1111 1111 1111"` |
| `maskedPan` | String | `"4111 **** **** 1111"` |
| `cardType` | CardType | `CardType.VISA` |

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
- ✅ Card number (PAN)
- ✅ Card type detection
- ❌ Cannot read CVV/CVC
- ❌ Cannot read PIN
- ❌ Cannot make transactions
- ❌ Cannot clone cards

Handle card numbers according to PCI-DSS if applicable.

## 📋 Requirements

- Android 5.0+ (API 21+)
- Device with NFC hardware
- Kotlin 1.8+

## 🔧 Advanced Usage

### Low-Level Reader

For more control, use `EmvCardReader` directly:

```kotlin
val reader = EmvCardReader()

// In your NFC callback
nfcAdapter.enableReaderMode(activity, { tag ->
    lifecycleScope.launch {
        val result = reader.readCard(tag)
        // Handle result
    }
}, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
```

### Custom Timeout

```kotlin
val manager = NfcCardManager(
    activity = this,
    config = ReaderConfig(timeoutMs = 10000),  // 10 seconds
    onCardRead = { result -> /* ... */ }
)
```

## 📄 License

```
MIT License - Copyright (c) 2025 Armen Asatryan
```

## 🤝 Contributing

Pull requests welcome! Please open an issue first for major changes.

## 🔗 Links

- [JitPack](https://jitpack.io/#ArmenAsatryan/android-bank-card-reader)
- [Report Issue](https://github.com/ArmenAsatryan/android-bank-card-reader/issues)

---

**Keywords**: android nfc card reader, read credit card android, bank card reader nfc, contactless card reader android, visa mastercard reader android, emv card reader, kotlin nfc library
