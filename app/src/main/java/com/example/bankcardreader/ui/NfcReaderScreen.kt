package com.example.bankcardreader.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bankcardreader.nfc.CardValidator
import com.example.bankcardreader.nfc.NfcStatus

@Composable
fun NfcReaderScreen(
    uiState: NfcReaderUiState,
    nfcStatus: NfcStatus,
    cardNumber: String,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onCardNumberChange: (String) -> Unit,
    onConfirmCard: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF1B263B),
                        Color(0xFF415A77)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Header
            Text(
                text = "Card Scanner",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFFE0E1DD)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Scan your card to auto-fill details",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF778DA9)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Main content based on state
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "state_animation"
            ) { state ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is NfcReaderUiState.Idle -> {
                            IdleContent(
                                nfcStatus = nfcStatus,
                                cardNumber = cardNumber,
                                onStartScan = onStartScan,
                                onCardNumberChange = onCardNumberChange,
                                onConfirmCard = onConfirmCard
                            )
                        }
                        is NfcReaderUiState.Scanning -> {
                            ScanningContent(onStopScan = onStopScan)
                        }
                        is NfcReaderUiState.Reading -> {
                            ReadingContent()
                        }
                        is NfcReaderUiState.Success -> {
                            SuccessContent(
                                formattedPan = state.formattedPan,
                                cardType = state.cardType,
                                onConfirm = { onConfirmCard(state.pan) },
                                onReset = onReset
                            )
                        }
                        is NfcReaderUiState.Error -> {
                            ErrorContent(
                                message = state.message,
                                onRetry = onStartScan,
                                onManualEntry = onReset
                            )
                        }
                        is NfcReaderUiState.NfcDisabled -> {
                            NfcDisabledContent(onOpenSettings = onOpenNfcSettings)
                        }
                        is NfcReaderUiState.NfcNotAvailable -> {
                            NfcNotAvailableContent(
                                cardNumber = cardNumber,
                                onCardNumberChange = onCardNumberChange,
                                onConfirmCard = onConfirmCard
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    nfcStatus: NfcStatus,
    cardNumber: String,
    onStartScan: () -> Unit,
    onCardNumberChange: (String) -> Unit,
    onConfirmCard: (String) -> Unit
) {
    // NFC Scan Card
    NfcScanCard(
        isEnabled = nfcStatus == NfcStatus.ENABLED,
        onClick = onStartScan
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Divider with "or"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF415A77)
        )
        Text(
            text = "  or enter manually  ",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF778DA9)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF415A77)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Manual entry
    ManualCardEntry(
        cardNumber = cardNumber,
        onCardNumberChange = onCardNumberChange,
        onConfirm = { onConfirmCard(cardNumber) }
    )
}

@Composable
private fun NfcScanCard(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B263B),
            disabledContainerColor = Color(0xFF1B263B).copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF415A77).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Nfc,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (isEnabled) Color(0xFF4CC9F0) else Color(0xFF778DA9)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (isEnabled) "Tap to Scan Card" else "NFC Unavailable",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isEnabled) Color(0xFFE0E1DD) else Color(0xFF778DA9)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isEnabled) "Hold your card to the back of your phone" 
                           else "Your device doesn't support NFC",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF778DA9),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ManualCardEntry(
    cardNumber: String,
    onCardNumberChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val formattedNumber = remember(cardNumber) {
        CardValidator.formatCardNumber(cardNumber)
    }
    val isValid = remember(cardNumber) {
        CardValidator.isValidCardNumber(cardNumber)
    }
    val cardType = remember(cardNumber) {
        CardValidator.detectCardType(cardNumber)
    }

    OutlinedTextField(
        value = formattedNumber,
        onValueChange = { onCardNumberChange(it.filter { c -> c.isDigit() }.take(19)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Card Number") },
        placeholder = { Text("1234 5678 9012 3456") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.CreditCard,
                contentDescription = null,
                tint = Color(0xFF778DA9)
            )
        },
        trailingIcon = {
            if (cardNumber.isNotEmpty()) {
                if (isValid) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Valid",
                        tint = Color(0xFF52B788)
                    )
                } else if (cardNumber.length >= 13) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Invalid",
                        tint = Color(0xFFE63946)
                    )
                }
            }
        },
        supportingText = {
            if (cardNumber.isNotEmpty() && cardType != CardValidator.CardType.UNKNOWN) {
                Text(
                    text = cardType.displayName,
                    color = Color(0xFF4CC9F0)
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF4CC9F0),
            unfocusedBorderColor = Color(0xFF415A77),
            focusedLabelColor = Color(0xFF4CC9F0),
            unfocusedLabelColor = Color(0xFF778DA9),
            cursorColor = Color(0xFF4CC9F0),
            focusedTextColor = Color(0xFFE0E1DD),
            unfocusedTextColor = Color(0xFFE0E1DD),
            focusedPlaceholderColor = Color(0xFF778DA9),
            unfocusedPlaceholderColor = Color(0xFF778DA9)
        )
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onConfirm,
        enabled = isValid,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CC9F0),
            disabledContainerColor = Color(0xFF415A77)
        )
    ) {
        Text(
            text = "Continue",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF0D1B2A)
        )
    }
}

@Composable
private fun ScanningContent(onStopScan: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Outer pulsing ring
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .border(
                        width = 3.dp,
                        color = Color(0xFF4CC9F0).copy(alpha = alpha * 0.5f),
                        shape = CircleShape
                    )
            )
            
            // Middle ring
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .border(
                        width = 2.dp,
                        color = Color(0xFF4CC9F0).copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
            
            // Center icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B263B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Nfc,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF4CC9F0)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Ready to Scan",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFFE0E1DD)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hold your card to the back of the phone",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF778DA9),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedButton(
            onClick = onStopScan,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFE0E1DD)
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF415A77), Color(0xFF415A77))
                )
            )
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ReadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = Color(0xFF4CC9F0),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Reading Card...",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFFE0E1DD)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Keep the card steady",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF778DA9)
        )
    }
}

@Composable
private fun SuccessContent(
    formattedPan: String,
    cardType: CardValidator.CardType,
    onConfirm: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF52B788).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF52B788)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Card Detected",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFFE0E1DD)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Card preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1B263B)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF415A77),
                                Color(0xFF1B263B),
                                Color(0xFF0D1B2A)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CreditCard,
                            contentDescription = null,
                            tint = Color(0xFFE0E1DD),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = cardType.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF4CC9F0)
                        )
                    }

                    Text(
                        text = formattedPan,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = Color(0xFFE0E1DD)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF52B788)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Use This Card",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onReset) {
            Text(
                text = "Scan Different Card",
                color = Color(0xFF778DA9)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onManualEntry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFE63946).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFE63946)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Couldn't Read Card",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFFE0E1DD)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF778DA9),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CC9F0)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF0D1B2A)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onManualEntry) {
            Text(
                text = "Enter Card Manually",
                color = Color(0xFF778DA9)
            )
        }
    }
}

@Composable
private fun NfcDisabledContent(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFF4A261).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.NfcOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFF4A261)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "NFC is Disabled",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFFE0E1DD)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enable NFC in your device settings to scan cards",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF778DA9),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF4A261)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Open Settings",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF0D1B2A)
            )
        }
    }
}

@Composable
private fun NfcNotAvailableContent(
    cardNumber: String,
    onCardNumberChange: (String) -> Unit,
    onConfirmCard: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF778DA9).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF778DA9)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "NFC Not Available",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFFE0E1DD)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your device doesn't support NFC.\nPlease enter your card number manually.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF778DA9),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        ManualCardEntry(
            cardNumber = cardNumber,
            onCardNumberChange = onCardNumberChange,
            onConfirm = { onConfirmCard(cardNumber) }
        )
    }
}

// Extension for NfcOff icon (not in default icons)
private val Icons.NfcOff: ImageVector
    get() = Icons.Outlined.WifiOff // Using WifiOff as placeholder, replace with actual NfcOff if available

