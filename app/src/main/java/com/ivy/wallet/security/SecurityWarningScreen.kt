package com.ivy.wallet.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SecurityWarningScreen(
    status: SecurityManager.SecurityStatus,
    onAcknowledge: () -> Unit,
    onExit: () -> Unit,
) {
    val hasBlockingThreat = status.hasSecurityThreat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Icon(
            imageVector = if (hasBlockingThreat) Icons.Default.Warning else Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = if (hasBlockingThreat) Color(0xFFFF6B6B) else Color(0xFFFFD93D),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = if (hasBlockingThreat) "Security Threat Detected" else "Security Warning",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (hasBlockingThreat) {
                "Your financial data may be at risk. The app has detected a security threat that could compromise your data."
            } else {
                "The app detected potential security concerns on this device. Your data is still protected, but please review the warnings below."
            },
            fontSize = 14.sp,
            color = Color(0xFFB0B0B0),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        if (status.isDebuggerAttached) {
            WarningCard(
                title = "Debugger Attached",
                description = "A debugger is connected to this app. This could allow someone to inspect your financial data in memory.",
                isBlocking = true,
            )
        }

        if (status.isDebuggable) {
            WarningCard(
                title = "Debug Build Detected",
                description = "This is a debug build of the app. Debug builds have weaker security protections. Please use the release version.",
                isBlocking = true,
            )
        }

        if (status.isRooted) {
            WarningCard(
                title = "Rooted Device",
                description = "This device appears to be rooted. Rooted devices can bypass app security protections. Proceed with caution.",
                isBlocking = false,
            )
        }

        if (status.isEmulator) {
            WarningCard(
                title = "Emulator Detected",
                description = "The app appears to be running on an emulator. For best security, use a physical device.",
                isBlocking = false,
            )
        }

        if (!status.isFromAllowedSource) {
            WarningCard(
                title = "Unknown Installation Source",
                description = "This app was installed from an unrecognized source. For safety, download only from Google Play or trusted sources like Google Drive.",
                isBlocking = false,
            )
        }

        Spacer(Modifier.weight(1f))

        if (hasBlockingThreat) {
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B6B),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Exit App",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            Button(
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "I Understand, Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WarningCard(
    title: String,
    description: String,
    isBlocking: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                color = if (isBlocking) Color(0x33FF6B6B) else Color(0x33FFD93D),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isBlocking) Color(0xFFFF6B6B) else Color(0xFFFFD93D),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            color = Color(0xFFD0D0D0),
        )
    }
}
