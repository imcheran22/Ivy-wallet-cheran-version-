package com.ivy.transaction

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivy.data.model.Category
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.domain.usecase.split.SplitPart
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import kotlinx.collections.immutable.ImmutableList
import java.util.UUID

/**
 * A photo of the receipt, attached to the transaction itself.
 *
 * Uses the document picker rather than the photo picker so the read grant can be made
 * persistable - a receipt that stops loading after a reboot is worse than no receipt.
 */
@Composable
fun ReceiptSection(
    attachmentUrl: String?,
    onAttachmentChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onAttachmentChanged(uri.toString())
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        if (attachmentUrl == null) {
            IvyOutlinedButton(
                text = stringResource(R.string.attach_receipt),
                iconStart = R.drawable.ic_custom_camera_m,
            ) {
                picker.launch(arrayOf(IMAGE_MIME))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(UI.shapes.r4)
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                            android.net.Uri.parse(attachmentUrl),
                                            IMAGE_MIME
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                )
                            }
                        },
                    model = attachmentUrl,
                    contentDescription = stringResource(R.string.receipt),
                    contentScale = ContentScale.Crop,
                )

                Spacer(Modifier.width(16.dp))

                IvyOutlinedButton(
                    text = stringResource(R.string.remove_receipt),
                    iconStart = R.drawable.ic_delete,
                    textColor = Red,
                    iconTint = Red,
                ) {
                    onAttachmentChanged(null)
                }
            }
        }
    }
}

/**
 * Breaks a bill into shares.
 *
 * Whatever isn't assigned stays on the transaction, so the user's own share needs no row of its
 * own - which is also how people say it out loud: "₹300 of that was Ravi's".
 */
@Composable
fun SplitDialog(
    totalAmount: Double,
    currency: String,
    categories: ImmutableList<Category>,
    onDismiss: () -> Unit,
    onSplit: (List<SplitPart>) -> Unit,
) {
    var parts by remember { mutableStateOf(listOf(SplitDraftPart())) }

    val assigned = parts.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val remaining = totalAmount - assigned
    val canSplit = assigned > 0.0 && remaining > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_transaction)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = DIALOG_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.split_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                parts.forEachIndexed { index, part ->
                    SplitPartRow(
                        part = part,
                        categories = categories,
                        onChange = { updated ->
                            parts = parts.toMutableList().also { it[index] = updated }
                        },
                        onRemove = if (parts.size > 1) {
                            { parts = parts.filterIndexed { i, _ -> i != index } }
                        } else {
                            null
                        },
                    )
                }

                TextButton(onClick = { parts = parts + SplitDraftPart() }) {
                    Text(stringResource(R.string.add_share))
                }

                Text(
                    text = stringResource(
                        R.string.your_share_remaining,
                        remaining.coerceAtLeast(0.0).format(currency),
                        currency,
                    ),
                    style = UI.typo.b2.style(
                        color = if (remaining > 0) UI.colors.pureInverse else Red,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSplit,
                onClick = {
                    onSplit(
                        parts.mapNotNull { part ->
                            part.amount.toDoubleOrNull()
                                ?.takeIf { it > 0.0 }
                                ?.let {
                                    SplitPart(
                                        amount = it,
                                        categoryId = part.categoryId,
                                        title = part.owedBy.takeIf(String::isNotBlank),
                                        owedBy = part.owedBy.takeIf(String::isNotBlank),
                                    )
                                }
                        }
                    )
                },
            ) {
                Text(stringResource(R.string.split))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

data class SplitDraftPart(
    val amount: String = "",
    val categoryId: UUID? = null,
    val owedBy: String = "",
)

@Composable
private fun SplitPartRow(
    part: SplitDraftPart,
    categories: ImmutableList<Category>,
    onChange: (SplitDraftPart) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = part.amount,
                onValueChange = { onChange(part.copy(amount = it)) },
                label = { Text(stringResource(R.string.amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            if (onRemove != null) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
            }
        }

        OutlinedTextField(
            value = part.owedBy,
            onValueChange = { onChange(part.copy(owedBy = it)) },
            label = { Text(stringResource(R.string.owed_by_optional)) },
            singleLine = true,
        )

        if (part.owedBy.isBlank() && categories.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = part.categoryId == category.id.value,
                        onClick = {
                            onChange(
                                part.copy(
                                    categoryId = category.id.value
                                        .takeIf { it != part.categoryId }
                                )
                            )
                        },
                        label = { Text(category.name.value, maxLines = 1) },
                    )
                }
            }
        }
    }
}

/** What the split summary says once it's done. */
@Composable
fun SplitResultBanner(outcome: SplitOutcome, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable(onClick = onDismiss),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (outcome.partsExceedTotal) {
                stringResource(R.string.split_too_large)
            } else {
                stringResource(
                    R.string.split_done,
                    outcome.createdTransactions,
                    outcome.createdLoans,
                )
            },
            style = UI.typo.c.style(
                color = if (outcome.partsExceedTotal) Red else Gray,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

private const val IMAGE_MIME = "image/*"
private val DIALOG_MAX_HEIGHT = 420.dp
