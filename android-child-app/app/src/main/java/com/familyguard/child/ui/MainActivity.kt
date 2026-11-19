package com.familyguard.child.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.familyguard.child.R
import com.familyguard.child.collect.UsageCollector
import com.familyguard.child.data.Prefs
import com.familyguard.child.service.MonitorService
import com.familyguard.child.util.NetworkUtil
import com.familyguard.child.util.Permissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = fgColors()) { RootScreen() } }
    }
}

@Composable
private fun fgColors(): ColorScheme {
    val brand = Color(0xFF2E7D32)
    return if (androidx.compose.foundation.isSystemInDarkTheme())
        darkColorScheme(primary = Color(0xFF4CAF50), secondary = brand)
    else lightColorScheme(primary = brand, secondary = Color(0xFF1B5E20))
}

private enum class Step { CONSENT, PIN, HOME }

@Composable
private fun RootScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var step by remember {
        mutableStateOf(
            when {
                !prefs.consentAccepted -> Step.CONSENT
                !prefs.isPinSet -> Step.PIN
                else -> Step.HOME
            }
        )
    }

    when (step) {
        Step.CONSENT -> ConsentScreen(onAgree = {
            prefs.consentAccepted = true
            step = if (prefs.isPinSet) Step.HOME else Step.PIN
        })
        Step.PIN -> PinSetupScreen(onDone = {
            step = Step.HOME
            MonitorService.start(ctx)
        })
        Step.HOME -> HomeScreen()
    }
}

@Composable
private fun ConsentScreen(onAgree: () -> Unit) {
    Scaffold(topBar = { FgTopBar() }) { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.consent_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.consent_body), fontSize = 15.sp, lineHeight = 22.sp)
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BulletRow("Collected: app usage & screen time, websites visited, and location.")
                    BulletRow("NOT collected: message text, photos, microphone, or camera.")
                    BulletRow("A permanent notification stays visible while monitoring is on.")
                    BulletRow("Data stays on this phone and is shared only over your local Wi-Fi.")
                }
            }
            Text(
                "Use this only on a device you are legally responsible for (e.g. your minor child). " +
                    "Monitoring another adult without consent may be illegal.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAgree, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.consent_agree))
            }
        }
    }
}

@Composable
private fun PinSetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { FgTopBar() }) { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Create a parent PIN", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "You'll enter this PIN on your own phone to view the reports. " +
                    "Minimum 4 digits. It is stored only as an encrypted hash.",
                fontSize = 14.sp
            )
            OutlinedTextField(
                value = pin, onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) pin = it },
                label = { Text("PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirm, onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) confirm = it },
                label = { Text("Confirm PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Button(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN must be at least 4 digits."
                        pin != confirm -> error = "PINs do not match."
                        else -> { prefs.setPin(pin); onDone() }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save PIN & start") }
        }
    }
}

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }

    // Re-check permission states whenever the screen resumes (user returns from settings).
    var refreshKey by remember { mutableStateOf(0) }
    ObserveResume { refreshKey++ }

    val usageGranted = remember(refreshKey) { UsageCollector(ctx).hasUsageAccess() }
    val accessibilityOn = remember(refreshKey) { Permissions.isAccessibilityEnabled(ctx) }
    val notifGranted = remember(refreshKey) { hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS) || Build.VERSION.SDK_INT < 33 }
    val fineLocation = remember(refreshKey) { hasPerm(ctx, Manifest.permission.ACCESS_FINE_LOCATION) }
    val bgLocation = remember(refreshKey) {
        Build.VERSION.SDK_INT < 29 || hasPerm(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    val ip = remember(refreshKey) { NetworkUtil.localIpv4() }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }
    val bgLocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    Scaffold(topBar = { FgTopBar() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Monitoring", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val ready = usageGranted && notifGranted
                    Text(
                        if (ready) "Active — a permanent notification is showing." else "Finish the steps below to start.",
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Parent dashboard address", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        if (ip != null) "http://$ip:${prefs.serverPort}" else "Connect to Wi-Fi to get an address",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "On your own phone, make sure it's on the SAME Wi-Fi, open that address in a browser (or the Family Guard parent app), and enter your PIN.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Set up permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            PermissionCard(
                title = "Usage access (app & screen time)",
                desc = "Required to see which apps are used and for how long.",
                granted = usageGranted,
                onClick = { Permissions.openUsageAccessSettings(ctx) }
            )
            PermissionCard(
                title = "Notifications",
                desc = "Shows the always-on monitoring notice (required).",
                granted = notifGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else Permissions.openAppDetailsSettings(ctx)
                }
            )
            PermissionCard(
                title = "Location",
                desc = "Lets the dashboard show the phone's location.",
                granted = fineLocation,
                onClick = {
                    locLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
            if (fineLocation && !bgLocation) {
                PermissionCard(
                    title = "Allow location all the time",
                    desc = "Needed to record location while apps are closed. Choose \"Allow all the time\".",
                    granted = false,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 29)
                            bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                )
            }
            PermissionCard(
                title = "Web-address logging (accessibility)",
                desc = "Required to record websites visited in browsers.",
                granted = accessibilityOn,
                onClick = { Permissions.openAccessibilitySettings(ctx) }
            )

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { MonitorService.start(ctx); refreshKey++ }, modifier = Modifier.weight(1f)) {
                    Text("Start / Restart")
                }
                OutlinedButton(onClick = { MonitorService.stop(ctx); refreshKey++ }, modifier = Modifier.weight(1f)) {
                    Text("Stop")
                }
            }
            Text(
                "Family Guard is intentionally visible in the app list and shows a permanent notification. " +
                    "It does not hide itself.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!granted) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClick) { Text("Grant") }
            }
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•  ", fontWeight = FontWeight.Bold)
        Text(text, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FgTopBar() {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White
        )
    )
}

@Composable
private fun ObserveResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

private fun hasPerm(ctx: Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
