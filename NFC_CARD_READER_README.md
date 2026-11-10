# NFC Card Reader Module

Android NFC reader for extracting card numbers from contactless payment cards (EMV).

## Features

- Read PAN (Primary Account Number) from Visa, Mastercard, Amex, Discover, JCB, UnionPay, Mir cards
- Luhn validation and card type detection
- Material Design 3 UI with Jetpack Compose
- Haptic feedback on success/error
- Manual entry fallback
- Security-focused: read-only, no data persistence

## Project Structure

EMV reading (APDUs, TLV parsing, wallet detection) lives in the `android-bank-card-reader`
library (`EmvCardReader`). The demo app only adds NFC plumbing and UI:

```
app/src/main/java/com/emvreader/bankcardreader/
├── MainActivity.kt              # Reader mode, haptics, FLAG_SECURE
├── nfc/
│   ├── CardReadError.kt         # UI-facing error messages
│   ├── CardValidator.kt         # Luhn, card type, formatting for manual entry
│   └── NfcManager.kt            # NFC adapter status + reader mode
└── ui/
    ├── NfcReaderScreen.kt       # Compose UI
    ├── NfcReaderViewModel.kt    # State management (calls EmvCardReader)
    └── theme/                   # Material 3 theming
```

## Setup

### 1. Permissions (already configured)

`AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="false" />
```

### 2. Tech Filter (already configured)

`res/xml/nfc_tech_filter.xml` - IsoDep (ISO 14443-4) only, so plain NFC tags don't launch the app.

### 3. Activity Configuration

- `launchMode="singleTop"` prevents multiple activity instances
- Intent filter for `ACTION_TECH_DISCOVERED` (card tapped while the app is in the background)

## Usage

### Integration Example

```kotlin
// In your transfer flow activity/fragment
class TransferActivity : ComponentActivity() {
    private lateinit var nfcManager: NfcManager
    private val viewModel: NfcReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcManager = NfcManager.create(this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateNfcStatus(nfcManager.getNfcStatus())
        nfcManager.enableReaderMode(this) { tag -> viewModel.processTag(tag) }
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableReaderMode(this)
    }
}
```

### Reading Flow

1. User taps "Scan Card" button
2. Reader mode (enabled in `onResume`) delivers the tag — no NDEF check, no platform sound
3. User holds card to phone
4. `EmvCardReader.readCard()` (library) processes:
   - SELECT PPSE → every listed application, highest priority first
   - SELECT AID → select payment app
   - GET PROCESSING OPTIONS → get file locators
   - READ RECORD → extract PAN from records
5. UI shows result; one haptic pulse per finished read

## EMV Commands

| Command | Description |
|---------|-------------|
| SELECT PPSE | Find contactless payment apps |
| SELECT AID | Select specific app (Visa, MC, etc) |
| GET PROCESSING OPTIONS | Initialize transaction, get AFL |
| READ RECORD | Read data from card files |

## Testing

### Unit Tests

```bash
./gradlew test
```

Tests cover:
- Library: TLV/PPSE parsing, wallet detection, and the full read flow against scripted cards
- App: Luhn validation, card type detection, formatting/masking, ViewModel haptics and secure-screen rules

### Device Testing

- **Emulators cannot test NFC** - physical device required
- Enable Developer Options → "NFC" and "Android Beam" (if available)
- Supported cards:
  - Visa contactless ✓
  - Mastercard contactless ✓
  - Amex contactless ✓
  - Some cards block NFC PAN reading (security feature)

### Testing Tips

1. Remove phone case for better NFC contact
2. Hold card flat against phone back (NFC antenna location varies)
3. Keep card steady for 1-2 seconds
4. If repeated failures, card may not expose PAN via NFC

## Known Limitations

1. **Some cards don't expose PAN via NFC** - security feature on some issuers
2. **CVV not readable** - not present in contactless EMV response
3. **Expiry (`5F24`)** - exposed by most physical cards; many wallets strip it
4. **Cardholder name often unavailable** via contactless
5. **Virtual cards** (Google Pay, Apple Pay) - different protocol
6. **Chip-only cards** - no contactless antenna

## Security Notes

- **Read-only**: Only extracts PAN, no transactions
- **No persistence**: Card data not stored
- **Cleared after use**: `clearCardData()` drops the card number once confirmed
- **Screen protected**: `FLAG_SECURE` while card data is on screen (no screenshots, recordings or Recents thumbnail)
- **Logging**: release builds log nothing; debug builds set `ReaderConfig(debugEmv = true)`, which logs raw APDUs including the PAN
- **User consent**: Clear UI indication of card reading

## Error Handling

| Error | Cause | Resolution |
|-------|-------|------------|
| `UNSUPPORTED_CARD` | Not IsoDep compatible | Use manual entry |
| `PPSE_NOT_FOUND` | No contactless payment app | Card may be chip-only |
| `TAG_LOST` | Card moved too quickly | Hold steady |
| `PAN_NOT_FOUND` | Card blocks NFC access | Use manual entry |

## Dependencies

```kotlin
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
implementation("androidx.compose.material:material-icons-extended")
// NFC APIs are part of Android SDK, no external deps
```

