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

```
app/src/main/java/com/example/bankcardreader/
├── MainActivity.kt              # Activity handling NFC intents
├── nfc/
│   ├── EmvUtils.kt              # APDU command builders
│   ├── TlvParser.kt             # BER-TLV parser for EMV data
│   ├── CardValidator.kt         # Luhn algorithm, card type detection
│   ├── NfcCardReader.kt         # Card reading logic
│   └── NfcManager.kt            # NFC adapter management
└── ui/
    ├── NfcReaderScreen.kt       # Compose UI
    ├── NfcReaderViewModel.kt    # State management
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

`res/xml/nfc_tech_filter.xml` - filters for IsoDep (ISO 14443-4) cards.

### 3. Activity Configuration

- `launchMode="singleTop"` prevents multiple activity instances
- Intent filter for `ACTION_TECH_DISCOVERED`

## Usage

### Integration Example

```kotlin
// In your transfer flow activity/fragment
class TransferActivity : ComponentActivity() {
    private val nfcManager = NfcManager.create(this)
    private val viewModel: NfcReaderViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.updateNfcStatus(nfcManager.getNfcStatus())
        nfcManager.enableForegroundDispatch(this)
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nfcManager.getTagFromIntent(intent)?.let { tag ->
            viewModel.processTag(tag)
        }
    }
}
```

### Reading Flow

1. User taps "Scan Card" button
2. `NfcManager.enableForegroundDispatch()` activates NFC reader
3. User holds card to phone
4. `onNewIntent()` receives tag
5. `NfcCardReader.readCard()` processes:
   - SELECT PPSE → get available AIDs
   - SELECT AID → select payment app
   - GET PROCESSING OPTIONS → get file locators
   - READ RECORD → extract PAN from records
6. `TlvParser` extracts PAN from EMV TLV data
7. `CardValidator` validates and formats
8. UI shows result

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
- Luhn validation
- Card type detection
- TLV parsing
- APDU command building

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
2. **CVV/expiry not readable** - intentionally excluded for security
3. **Cardholder name often unavailable** via contactless
4. **Virtual cards** (Google Pay, Apple Pay) - different protocol
5. **Chip-only cards** - no contactless antenna

## Security Notes

- **Read-only**: Only extracts PAN, no transactions
- **No persistence**: Card data not stored
- **Memory cleared**: `clearCardData()` zeros out values after use
- **No logging of PAN**: Sensitive data excluded from logs
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

