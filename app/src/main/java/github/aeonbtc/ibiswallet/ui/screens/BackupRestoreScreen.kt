package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupWalletEntry(
    val id: String,
    val name: String,
    val type: String,
    val isWatchOnly: Boolean,
)

data class FullBackupPreview(
    val walletNames: List<String>,
    val walletCount: Int,
    val hasLabels: Boolean,
    val hasServers: Boolean,
    val hasLiquidServers: Boolean,
    val hasAppSettings: Boolean,
    val exportedAt: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    wallets: List<BackupWalletEntry>,
    onBack: () -> Unit,
    onExportFullBackup: (uri: Uri, walletIds: List<String>, includeLabels: Boolean, includeServers: Boolean, includeAppSettings: Boolean, password: String?) -> Unit,
    onParseFullBackup: suspend (uri: Uri, password: String?) -> FullBackupPreview,
    onImportFullBackup: (uri: Uri, password: String?, importWallets: Boolean, importLabels: Boolean, importServers: Boolean, importAppSettings: Boolean) -> Unit,
    isLoading: Boolean = false,
    resultMessage: String? = null,
    onClearResult: () -> Unit = {},
) {
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Backup / Restore",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (resultMessage != null) {
                ResultBanner(
                    message = resultMessage,
                    onDismiss = onClearResult,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            BackupSection(
                wallets = wallets,
                onExport = onExportFullBackup,
                isLoading = isLoading,
            )

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = BorderColor)

            Spacer(modifier = Modifier.height(20.dp))

            RestoreSection(
                onParseFullBackup = onParseFullBackup,
                onImportFullBackup = onImportFullBackup,
                isLoading = isLoading,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BackupSection(
    wallets: List<BackupWalletEntry>,
    onExport: (uri: Uri, walletIds: List<String>, includeLabels: Boolean, includeServers: Boolean, includeAppSettings: Boolean, password: String?) -> Unit,
    isLoading: Boolean,
) {
    val context = LocalContext.current
    val selectedWallets = remember { mutableStateMapOf<String, Boolean>() }
    wallets.forEach { w ->
        if (w.id !in selectedWallets) selectedWallets[w.id] = true
    }

    var includeLabels by remember { mutableStateOf(true) }
    var includeServers by remember { mutableStateOf(true) }
    var includeAppSettings by remember { mutableStateOf(true) }
    var encryptBackup by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var exportFileName by remember { mutableStateOf<String?>(null) }

    val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val suggestedFileName = "ibis-full-backup-$dateStr.json"

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            exportUri = uri
            exportFileName = try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
            } catch (_: Exception) { null } ?: suggestedFileName
        }
    }

    val passwordsMatch = password == confirmPassword
    val passwordLongEnough = password.length >= 8
    val encryptionValid = !encryptBackup || (passwordLongEnough && passwordsMatch)
    val selectedIds = selectedWallets.filter { it.value }.keys.toList()
    val canExport = exportUri != null && encryptionValid && selectedIds.isNotEmpty() && !isLoading

    Text(
        text = "Create Backup",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Export all wallets, labels, and settings to a single file.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Wallets",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (wallets.isEmpty()) {
                Text(
                    text = "No wallets available",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            } else {
                wallets.forEach { wallet ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedWallets[wallet.id] = !(selectedWallets[wallet.id] ?: true)
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wallet.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = wallet.type + if (wallet.isWatchOnly) " (watch-only)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        SquareToggle(
                            checked = selectedWallets[wallet.id] ?: true,
                            onCheckedChange = { selectedWallets[wallet.id] = it },
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            ToggleRow(
                title = "Include Labels",
                subtitle = "Address and transaction labels",
                checked = includeLabels,
                onCheckedChange = { includeLabels = it },
            )

            Spacer(modifier = Modifier.height(10.dp))

            ToggleRow(
                title = "Include Server Settings",
                subtitle = "Electrum and Liquid server configs",
                checked = includeServers,
                onCheckedChange = { includeServers = it },
            )

            Spacer(modifier = Modifier.height(10.dp))

            ToggleRow(
                title = "Include App Settings",
                subtitle = "Denomination, fee source, Tor, etc.",
                checked = includeAppSettings,
                onCheckedChange = { includeAppSettings = it },
            )

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(10.dp))

            ToggleRow(
                title = "Encrypt Backup",
                subtitle = "Protect with a password (AES-256)",
                checked = encryptBackup,
                onCheckedChange = { encryptBackup = it },
            )

            if (encryptBackup) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show",
                                tint = TextSecondary,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = BitcoinOrange,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = BitcoinOrange,
                    ),
                )
                if (password.isNotEmpty() && !encryptionValid) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (!passwordLongEnough) "Min 8 characters" else "Passwords do not match",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (exportUri != null && exportFileName != null) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = BorderColor.copy(alpha = 0.18f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = exportFileName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = { filePickerLauncher.launch(suggestedFileName) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            enabled = !isLoading,
        ) {
            Text(if (exportUri == null) "Choose Location" else "Change")
        }
        Button(
            onClick = {
                exportUri?.let { uri ->
                    onExport(
                        uri,
                        selectedIds,
                        includeLabels,
                        includeServers,
                        includeAppSettings,
                        if (encryptBackup) password else null,
                    )
                }
            },
            enabled = canExport,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BitcoinOrange,
                disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
            ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Export")
            }
        }
    }
}

@Composable
private fun RestoreSection(
    onParseFullBackup: suspend (uri: Uri, password: String?) -> FullBackupPreview,
    onImportFullBackup: (uri: Uri, password: String?, importWallets: Boolean, importLabels: Boolean, importServers: Boolean, importAppSettings: Boolean) -> Unit,
    isLoading: Boolean,
) {
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreFileName by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    val showPasswordState = remember { mutableStateOf(false) }
    val previewState = remember { mutableStateOf<FullBackupPreview?>(null) }
    val parseErrorState = remember { mutableStateOf<String?>(null) }
    val needsPasswordState = remember { mutableStateOf(false) }
    val isParsingState = remember { mutableStateOf(false) }

    var importWallets by remember { mutableStateOf(true) }
    var importLabels by remember { mutableStateOf(true) }
    var importServers by remember { mutableStateOf(true) }
    var importAppSettings by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            restoreUri = uri
            restoreFileName = try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
            } catch (_: Exception) { null } ?: "backup.json"
            previewState.value = null
            parseErrorState.value = null
            needsPasswordState.value = false
            password = ""
        }
    }

    Text(
        text = "Restore from Backup",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Import wallets, settings, and labels from a full backup file.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = { filePickerLauncher.launch(arrayOf("application/json", "*/*")) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
        enabled = !isLoading && !isParsingState.value,
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (restoreUri == null) "Select Backup File" else "Change File")
    }

    if (restoreUri != null && restoreFileName != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = restoreFileName ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }

    if (restoreUri != null && (needsPasswordState.value || previewState.value == null)) {
        Spacer(modifier = Modifier.height(12.dp))

        if (needsPasswordState.value || previewState.value == null) {
            if (needsPasswordState.value) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Backup Password") },
                    singleLine = true,
                    visualTransformation = if (showPasswordState.value) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPasswordState.value = !showPasswordState.value }) {
                            Icon(
                                imageVector = if (showPasswordState.value) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = BitcoinOrange,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    restoreUri?.let { uri ->
                        isParsingState.value = true
                        parseErrorState.value = null
                        scope.launch {
                            try {
                                val result = onParseFullBackup(uri, password.ifEmpty { null })
                                previewState.value = result
                                needsPasswordState.value = false
                                parseErrorState.value = null
                            } catch (e: Exception) {
                                val msg = e.message ?: "Failed to parse backup"
                                if (msg.contains("encrypted", ignoreCase = true) || msg.contains("password", ignoreCase = true)) {
                                    needsPasswordState.value = true
                                    parseErrorState.value = msg
                                } else {
                                    parseErrorState.value = msg
                                }
                                previewState.value = null
                            } finally {
                                isParsingState.value = false
                            }
                        }
                    }
                },
                enabled = !isParsingState.value && !isLoading && restoreUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BitcoinOrange,
                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                ),
            ) {
                if (isParsingState.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (needsPasswordState.value) "Decrypt & Preview" else "Preview Backup")
                }
            }
        }
    }

    val preview = previewState.value
    val parseError = parseErrorState.value

    if (parseError != null && preview == null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = parseError,
            style = MaterialTheme.typography.bodySmall,
            color = ErrorRed,
        )
    }

    if (preview != null) {
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Backup Contents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Exported: ${preview.exportedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(12.dp))

                PreviewRow(
                    label = "Wallets (${preview.walletCount})",
                    detail = preview.walletNames.joinToString(", "),
                    available = preview.walletCount > 0,
                    checked = importWallets,
                    onCheckedChange = { importWallets = it },
                )

                Spacer(modifier = Modifier.height(8.dp))

                PreviewRow(
                    label = "Labels",
                    detail = if (preview.hasLabels) "Included" else "Not included",
                    available = preview.hasLabels,
                    checked = importLabels,
                    onCheckedChange = { importLabels = it },
                )

                Spacer(modifier = Modifier.height(8.dp))

                PreviewRow(
                    label = "Server Settings",
                    detail = if (preview.hasServers || preview.hasLiquidServers) "Included" else "Not included",
                    available = preview.hasServers || preview.hasLiquidServers,
                    checked = importServers,
                    onCheckedChange = { importServers = it },
                )

                Spacer(modifier = Modifier.height(8.dp))

                PreviewRow(
                    label = "App Settings",
                    detail = if (preview.hasAppSettings) "Included" else "Not included",
                    available = preview.hasAppSettings,
                    checked = importAppSettings,
                    onCheckedChange = { importAppSettings = it },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Existing wallets with the same seed will be skipped.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                restoreUri?.let { uri ->
                    onImportFullBackup(
                        uri,
                        password.ifEmpty { null },
                        importWallets,
                        importLabels,
                        importServers,
                        importAppSettings,
                    )
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BitcoinOrange,
                disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
            ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Restore")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        SquareToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PreviewRow(
    label: String,
    detail: String,
    available: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = available) { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (available) MaterialTheme.colorScheme.onBackground else TextSecondary.copy(alpha = 0.5f),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
            )
        }
        if (available) {
            SquareToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun ResultBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    val isSuccess = message.startsWith("Success", ignoreCase = true) || message.contains("exported", ignoreCase = true) || message.contains("restored", ignoreCase = true)
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
        ),
        onClick = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isSuccess) SuccessGreen else ErrorRed,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
