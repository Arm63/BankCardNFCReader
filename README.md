# BankCardNFCReader — Android NFC Credit Card Reader Library (Kotlin, EMV, Google Wallet detection)

[![JitPack](https://jitpack.io/v/Arm63/BankCardNFCReader.svg)](https://jitpack.io/#Arm63/BankCardNFCReader)
[![GitHub stars](https://img.shields.io/github/stars/Arm63/BankCardNFCReader?style=social)](https://github.com/Arm63/BankCardNFCReader/stargazers)
[![GitHub last commit](https://img.shields.io/github/last-commit/Arm63/BankCardNFCReader)](https://github.com/Arm63/BankCardNFCReader/commits/main)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9%2B-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Code size](https://img.shields.io/github/languages/code-size/Arm63/BankCardNFCReader)](https://github.com/Arm63/BankCardNFCReader)
[![Issues](https://img.shields.io/github/issues/Arm63/BankCardNFCReader)](https://github.com/Arm63/BankCardNFCReader/issues)

**Read PAN from contactless EMV cards on Android in Kotlin.** BankCardNFCReader is a lightweight Android NFC credit card reader library that extracts the card number (PAN/DPAN), card type (Visa, Mastercard, Amex, Discover, UnionPay, JCB, Mir), cardholder name, AID, and detects Google Wallet, Apple Pay, Samsung Pay, Garmin Pay and Fitbit Pay vs a physical card — all via standard EMV tags over NFC.

<p align="center">
  <img src="docs/media/demo.gif" alt="Android NFC credit card reader library demo: scanning a contactless Visa card and showing masked PAN, cardholder name and Google Wallet detection" width="320" />
</p>

<p align="center">
  <img src="docs/media/screenshot-physical-card.png" alt="Physical Visa card scanned via NFC on Android, showing masked PAN 4111 **** **** 1111 and AID Visa Credit/Debit" width="240" />
  <img src="docs/media/screenshot-google-wallet.png" alt="Google Wallet token detected on Android via EMV tag 9F19 Token Requestor ID" width="240" />
  <img src="docs/media/screenshot-pin-tries.png" alt="EMV tag 9F17 offline PIN tries remaining displayed on card UI" width="240" />
</p>

## Table of Contents
- [Features](#features)
- [Why this library?](#why-this-library)
- [Use cases](#use-cases)
- [Installation](#installation)
- [Quick Start (View)](#quick-start-view)
- [Quick Start (Jetpack Compose)](#quick-start-jetpack-compose)
- [API Reference](#api-reference)
- [Payment Source Detection](#payment-source-detection)
- [Cardholder Name & Offline PIN Tries](#cardholder-name--offline-pin-tries)
- [AID & Friendly Names](#aid--friendly-names)
- [AndroidManifest & Permissions](#androidmanifest--permissions)
- [ProGuard / R8](#proguard--r8)
- [Security & PCI-DSS](#security--pci-dss)
- [Requirements](#requirements)
- [Advanced Usage](#advanced-usage)
- [Testing](#testing)
- [FAQ](#faq)
- [Roadmap](#roadmap)
- [Changelog](#changelog)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Read card numbers** from NFC-enabled credit/debit cards (PAN / DPAN)
- **Cardholder name** from EMV tag `5F20` (when exposed)
- **AID + friendly name** via `AidLabels` (Visa Credit/Debit, Mastercard, Amex, …)
- **Offline PIN tries remaining** via tag `9F17`
- **Multi-brand**: Visa, Mastercard, American Express, Discover, UnionPay, JCB, Mir
- **Payment Source Detection**: physical card vs Google Wallet, Apple Pay, Samsung Pay, Garmin Pay, Fitbit Pay
- **Form Factor (`9F6E`) + Token Requestor ID (`9F19`) parsing** for wallet identification
- **Read-only**: cannot make payments or modify card data
- **Kotlin coroutines** suspend API
- **Lightweight**: depends only on `androidx.core` and `kotlinx-coroutines-android`

## Why this library?

| Library | Lang | Min SDK | EMV PAN | Cardholder name (5F20) | Wallet vs physical (9F6E/9F19) | Coroutines | Last update | License |
|---|---|---|---|---|---|---|---|---|
| **Arm63/BankCardNFCReader** (this) | Kotlin | 21 | yes | yes | **yes** | yes | active | MIT |
| [devnied/EMV-NFC-Paycard-Enrollment](https://github.com/devnied/EMV-NFC-Paycard-Enrollment) | Java | 14 | yes | yes | no | no | low | Apache-2.0 |
| [pro100svitlo/CreditCardNfcReader](https://github.com/pro100svitlo/CreditCardNfcReader) | Java | 14 | yes | partial | no | no | low | Apache-2.0 |
| [sasc999/javaemvreader](https://github.com/sasc999/javaemvreader) | Java | n/a | yes | yes | no | no | archive | LGPL |
| [balysv/android-card-form](https://github.com/balysv/android-card-form) | Java | 16 | n/a (manual UI) | n/a | n/a | no | low | Apache-2.0 |
| [stripe/stripe-android](https://github.com/stripe/stripe-android) | Kotlin | 21 | tokenization SDK, no raw NFC PAN | n/a | n/a | yes | active | MIT |

Differentiators:

- **Kotlin-first, coroutine-first** EMV NFC Android library.
- Surfaces **EMV `9F6E` Form Factor Indicator** and **`9F19` Token Requestor ID** for **Google Wallet detection on Android**, plus Apple Pay / Samsung Pay / Garmin Pay / Fitbit Pay.
- Surfaces **offline PIN tries (`9F17`)** and **cardholder name (`5F20`)** when the card exposes them.
- AID friendly-name resolver (`AidLabels`) for Visa, Mastercard, Amex, Discover, UnionPay, JCB, Mir.

## Use cases

- **KYC & onboarding** — prefill card brand and last-4 in a fintech sign-up flow.
- **Bank apps** — card-on-file enrollment without manual PAN entry.
- **Expense / receipt apps** — tag a transaction to the physical card it was paid with.
- **P2P payment apps** — account funding via tap-to-add-card.
- **Loyalty / membership apps** — link a customer's contactless card as a soft identifier (last-4 + AID) without storing the full PAN.
- **Internal tools / fraud ops** — distinguish a scanned plastic card from a tokenized wallet (DPAN) for risk scoring.
- **Hardware integrations** — kiosk / POS companion apps reading a customer card on an Android tablet.

## Installation

Add JitPack to your root `settings.gradle.kts`:

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
    implementation("com.github.Arm63:BankCardNFCReader:1.1.4")
}
```

> Latest version is shown on the JitPack badge above. Replace `1.1.4` if a newer release is published.

## Quick Start (View)

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
            when (val result = cardReader.readCard(tag)) {
                is CardData.Success -> {
                    val cardNumber = result.maskedPan       // "4111 **** **** 1111"
                    val cardType = result.cardType          // CardType.VISA
                    val source = result.paymentSource       // PaymentSource.PHYSICAL_CARD
                    val isWallet = result.isTokenizedWallet // false
                    val owner = result.maskedOwnerName()    // "A**** A****" or null
                    val aid = result.aidDisplayName         // "Visa Credit/Debit" or null
                    val pinTries = result.pinTriesRemaining // 3 or null

                    if (isWallet) showWalletCard(cardNumber, source.displayName)
                    else showPhysicalCard(cardNumber, cardType.displayName)
                }
                is CardData.Error -> showError(result.message)
            }
        }
    }
}
```

## Quick Start (Jetpack Compose)

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
        onDispose { nfcAdapter?.disableReaderMode(activity) }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val result = cardData) {
            is CardData.Success -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (result.isTokenizedWallet) "📱 ${result.paymentSource.displayName}"
                           else "💳 Physical Card",
                    fontSize = 18.sp
                )
                Text(result.cardType.displayName, fontSize = 24.sp)
                Text(result.maskedPan, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                result.maskedOwnerName()?.let { Text(it, fontSize = 14.sp) }
                result.aidDisplayName?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                result.pinTriesRemaining?.let { Text("PIN tries left: $it", fontSize = 12.sp) }
                if (result.isTokenizedWallet) Text("Tokenized (DPAN)", fontSize = 12.sp, color = Color.Gray)
            }
            is CardData.Error -> Text("❌ ${result.message}", color = Color.Red)
            null -> Text("Tap your card to read", fontSize = 18.sp)
        }
    }
}
```

## API Reference

### `CardData.Success`

| Property | Type | Example | Description |
|---|---|---|---|
| `pan` | `String` | `"4111111111111111"` | Raw PAN. **DPAN** (Device PAN, tokenized) for digital wallets. |
| `formattedPan` | `String` | `"4111 1111 1111 1111"` | PAN grouped in 4-digit blocks. |
| `maskedPan` | `String` | `"4111 **** **** 1111"` | Display-safe masked PAN. |
| `cardType` | `CardType` | `CardType.VISA` | Detected brand. |
| `paymentSource` | `PaymentSource` | `PaymentSource.GOOGLE_WALLET` | Resolved payment source (physical card vs wallet). |
| `sourceDetectionResult` | `CardSourceDetector.DetectionResult?` | – | Form Factor bytes, Token Requestor ID, confidence, debug. |
| `cardholderName` | `String?` | `"ASATRYAN/ARMEN"` | EMV tag **`5F20`**. Often `null` on contactless for privacy. |
| `aid` | `String?` | `"A0000000031010"` | Selected AID (tag `4F`), uppercase hex. |
| `aidDisplayName` | `String?` | `"Visa Credit/Debit"` | Friendly label resolved by `AidLabels`. `null` if unknown. |
| `pinTriesRemaining` | `Int?` | `3` | Offline PIN Try Counter (tag **`9F17`**). `null` if not exposed. |
| `isTokenizedWallet` | `Boolean` | `true` | `paymentSource.isDigitalWallet`. |
| `isPhysicalCard` | `Boolean` | `false` | `paymentSource.isPhysicalCard`. |

### Methods on `CardData.Success`

| Method | Returns | Description |
|---|---|---|
| `maskedOwnerName()` | `String?` | Privacy-safe display form of `cardholderName`, e.g. `"A**** A****"`. Splits on whitespace and `/`, keeps first letter of each token. Returns `null` when name is missing or blank. |

### `PaymentSource` enum

```kotlin
enum class PaymentSource {
    PHYSICAL_CARD,
    GOOGLE_WALLET,
    SAMSUNG_PAY,
    APPLE_PAY,
    GARMIN_PAY,
    FITBIT_PAY,
    MOBILE_WALLET,
    OTHER_WALLET,
    UNKNOWN
}
```

### Supported card types

| Card | Status | Detected by |
|---|---|---|
| Visa | ✅ | Starts with 4 |
| Mastercard | ✅ | Starts with 51-55 or 2221-2720 |
| American Express | ✅ | Starts with 34 or 37 |
| Discover | ✅ | Starts with 6011, 65, or 644-649 |
| UnionPay | ✅ | Starts with 62 |
| JCB | ✅ | Starts with 35 |
| Mir | ✅ | Starts with 220 |

### Error codes

| Code | When |
|---|---|
| `UNSUPPORTED_CARD` | Card doesn't support contactless |
| `PPSE_NOT_FOUND` | No payment app on card |
| `AID_NOT_FOUND` | No supported payment application found |
| `GPO_FAILED` | GET PROCESSING OPTIONS command failed |
| `PAN_NOT_FOUND` | Could not read card number |
| `TAG_LOST` | Card removed too quickly |
| `COMMUNICATION_ERROR` | NFC read failed |

## Payment Source Detection

The library automatically detects whether the scanned card is a physical plastic card or a digital wallet using EMV tags from the card response:

| EMV Tag | Name | Purpose |
|---|---|---|
| **9F6E** | Form Factor Indicator | Identifies device type (card vs mobile) |
| **9F19** | Token Requestor ID | Identifies specific wallet provider |
| **50** | Application Label | May contain wallet identifiers |

### Detection logic

```
1. Check Token Requestor ID (9F19)
   - Known ID → specific wallet (HIGH confidence)
   - Unknown ID → other wallet (MEDIUM confidence)

2. Check Form Factor Indicator (9F6E)
   - Byte 2 Bit 8 = 0 → physical card (not connected)
   - Byte 2 Bit 8 = 1 → mobile wallet (network connected)

3. Check Application Label (50)
   - Contains wallet keywords → infer wallet type
```

### Supported payment sources

| Payment source | Detection method | Confidence |
|---|---|---|
| Physical Card | Form Factor (not network-connected) | HIGH |
| Google Wallet | Token Requestor ID | HIGH |
| Samsung Pay | Token Requestor ID | HIGH |
| Apple Pay | Token Requestor ID | HIGH |
| Garmin Pay | Token Requestor ID | HIGH |
| Fitbit Pay | Token Requestor ID | HIGH |
| Mobile Wallet | Form Factor (network-connected, unknown provider) | HIGH |

### Detection result details

```kotlin
when (result) {
    is CardData.Success -> result.sourceDetectionResult?.let { detection ->
        Log.d("Detection", "Source: ${detection.source}")
        Log.d("Detection", "Confidence: ${detection.confidence}")
        Log.d("Detection", "Form Factor: ${detection.formFactorIndicator?.toHex()}")
        Log.d("Detection", "Token Requestor ID: ${detection.tokenRequestorId}")
        Log.d("Detection", "Debug Info: ${detection.debugInfo}")
    }
    else -> Unit
}
```

## Cardholder Name & Offline PIN Tries

Some EMV cards expose the cardholder name (tag `5F20`) and the offline PIN Try Counter (tag `9F17`) during contactless reads. Both are optional per card / issuer profile and are frequently `null` on tap.

```kotlin
when (val r = cardReader.readCard(tag)) {
    is CardData.Success -> {
        r.cardholderName        // "ASATRYAN/ARMEN" or null
        r.maskedOwnerName()     // "A**** A****" or null
        r.pinTriesRemaining     // 3 or null
    }
    else -> Unit
}
```

## AID & Friendly Names

`AidLabels.displayName(aidHex)` maps RID/AID prefixes to a friendly name:

| AID prefix | Friendly name |
|---|---|
| `A0000000031010`, `A0000000032010` | Visa Credit/Debit |
| `A000000003*` | Visa |
| `A0000000041010`, `A0000000043060`, `A0000000043010` | Mastercard |
| `A000000025*` | American Express |
| `A0000001523010` | Discover |
| `A000000333*` | UnionPay |
| `A0000000651010` | JCB |
| `A0000006581010` | Mir |

You can also call `AidLabels.displayName(aidHex)` directly if you have an AID hex string from your own reader code.

## AndroidManifest & Permissions

The library declares NFC permission automatically. To require NFC hardware on Play Store:

```xml
<manifest>
    <uses-feature android:name="android.hardware.nfc" android:required="true" />
</manifest>
```

## ProGuard / R8

The library ships `consumer-rules.pro` so no extra rules are required. If you proxy results through reflection or serialization (Gson / Moshi / kotlinx.serialization), keep the data classes:

```pro
-keep class com.emvreader.nfc.CardData$Success { *; }
-keep class com.emvreader.nfc.CardSourceDetector$DetectionResult { *; }
-keepclassmembers enum com.emvreader.nfc.** { *; }
```

## Security & PCI-DSS

This library **only reads** publicly available card data:

- ✅ Card number (PAN / DPAN for wallets)
- ✅ Card type detection
- ✅ Payment source detection
- ✅ Cardholder name (when exposed)
- ❌ Cannot read CVV/CVC
- ❌ Cannot read PIN
- ❌ Cannot make transactions
- ❌ Cannot clone cards

**Tokenized wallets:** the PAN returned from digital wallets is a **DPAN** (Device PAN), tokenized and device-bound. It cannot be used on other devices.

Handle card numbers according to PCI-DSS in your application. The library does not transmit, store, or persist any card data.

## Requirements

- Android 5.0+ (API 21+)
- Device with NFC hardware
- Kotlin 1.8+

## Advanced Usage

### Custom timeout

```kotlin
val reader = EmvCardReader(
    config = ReaderConfig(timeoutMs = 10000)
)
```

### Standalone source detection

```kotlin
val detectionResult = CardSourceDetector.detect(tlvData)
Log.d("Detection", "Source: ${detectionResult.source}")
Log.d("Detection", "Confidence: ${detectionResult.confidence}")
Log.d("Detection", "Is Wallet: ${detectionResult.source.isDigitalWallet}")
```

## Testing

### Physical cards
Any contactless Visa, Mastercard, etc. card. Detection returns `PaymentSource.PHYSICAL_CARD`.

### Digital wallets
- **Google Wallet** — add a card to Google Wallet, tap phone to test device.
- **Apple Pay** — add a card to Apple Pay, tap iPhone to test device.
- **Samsung Pay** — add a card to Samsung Pay, tap Samsung phone to test device.

Without Token Requestor ID, wallets are detected as `MOBILE_WALLET` (provider unknown).

NFC card emulation requires real hardware. Emulators do not work for wallet testing.

## FAQ

### How do I read a credit card number on Android using NFC in Kotlin?
Add the JitPack dependency, instantiate `EmvCardReader`, call `enableReaderMode` on `NfcAdapter`, and pass the `Tag` to `cardReader.readCard(tag)` from a coroutine. See [Quick Start](#quick-start-view).

### Can I read CVV / CVC over NFC?
No. CVV/CVC is not in the contactless EMV response. This library cannot read it.

### Can I distinguish Google Wallet from a physical card on Android?
Yes. The library inspects EMV tag `9F6E` (Form Factor Indicator) and `9F19` (Token Requestor ID) and returns `PaymentSource.GOOGLE_WALLET` / `PHYSICAL_CARD` / `MOBILE_WALLET`.

### What is the difference between PAN and DPAN?
PAN is the real card number embossed on the plastic. DPAN is a tokenized device-bound number returned by Google Wallet, Apple Pay, Samsung Pay etc. This library returns whichever the card chose to expose; `isTokenizedWallet` tells you which.

### Does this library work with Apple Pay on iPhone?
The Android device reads whatever the iPhone presents in card-emulation mode. The PAN you receive is the iPhone's DPAN, and `paymentSource` resolves to `MOBILE_WALLET` (or `APPLE_PAY` if Apple's Token Requestor ID is present in `9F19`).

### What minimum Android API does it require?
API 21 (Android 5.0). Min SDK 21, Kotlin, coroutines.

### Can I get the cardholder name from a contactless tap?
Sometimes. EMV tag `5F20` is optional on contactless and many issuers strip it for privacy. When present it is exposed as `cardholderName`, with `maskedOwnerName()` for display.

### How do I check offline PIN tries remaining over NFC?
Read the card and check `pinTriesRemaining` (EMV tag `9F17`). It is `null` when the card does not expose it on contactless.

### Is this library PCI-DSS compliant?
The library is read-only and does not transmit, store, or persist PAN. Whatever your app does with the returned PAN/DPAN is your PCI scope, not the library's. See [Security & PCI-DSS](#security--pci-dss).

### Does this work without Google Play Services?
Yes. The library only depends on `androidx.core` and `kotlinx-coroutines-android`.

### Can I use this from Java?
Yes. The API is Kotlin but interop-friendly. Use `CardData.Success` getters from Java; suspend `readCard` is callable via `kotlinx-coroutines-jvm` interop helpers.

### Does it work on emulators?
No. NFC card emulation between two real devices is required for wallet testing. Use real hardware.

## Roadmap

- [ ] Track 2 equivalent data parsing (`57`)
- [ ] Application Expiration Date (`5F24`) exposure
- [ ] Application Currency Code (`9F42`)
- [ ] Maven Central publishing in addition to JitPack
- [ ] CI release workflow with GitHub Actions

## Changelog

### v1.1.4 (Current)
- Read **cardholder name** from EMV tag `5F20` and expose `cardholderName` + `maskedOwnerName()` on `CardData.Success`
- Show **remaining offline PIN tries** (`9F17`) via `pinTriesRemaining`
- Expose selected **AID** (`4F`) and friendly name via `aid` + `aidDisplayName` (backed by `AidLabels`)
- Internal: rename app package to `com.emvreader.bankcardreader`

### v1.1.3
- Removed unused `NfcCardManager` class (use `EmvCardReader` directly)
- Removed all debug logging for production builds
- Simplified API with single reader class
- Updated documentation and examples

### v1.1.2
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

## Contributing

1. Open an issue first for non-trivial work.
2. See [CONTRIBUTING.md](CONTRIBUTING.md) for branch / commit / test conventions.
3. By contributing you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).
4. Security issues go through [SECURITY.md](SECURITY.md), not public issues.

### Development setup

```bash
git clone https://github.com/Arm63/BankCardNFCReader.git
cd BankCardNFCReader
./gradlew :android-bank-card-reader:assemble :android-bank-card-reader:test
```

## License

```
MIT License - Copyright (c) 2025 Armen Asatryan
```

## Links

- [JitPack](https://jitpack.io/#Arm63/BankCardNFCReader)
- [Report Issue](https://github.com/Arm63/BankCardNFCReader/issues)

---

**Keywords**: android nfc credit card reader library, read PAN from contactless card kotlin, EMV nfc android library, google wallet detection android, apple pay detection android, samsung pay nfc detection, dpan vs pan android, kotlin coroutines nfc reader, emv tag 5f20 cardholder name, emv tag 9f17 pin try counter, emv tag 9f6e form factor indicator, emv tag 9f19 token requestor id, visa mastercard nfc kotlin, jitpack android library
