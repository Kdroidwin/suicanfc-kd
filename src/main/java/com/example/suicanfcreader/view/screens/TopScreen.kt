package com.example.suicanfcreader.view.screens

import android.content.ContentResolver
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.suicanfcreader.model.SuicaCardSummary
import com.example.suicanfcreader.security.SensitiveClipboard
import com.example.suicanfcreader.viewModel.TopScreenViewModel
import java.util.Locale
import com.example.suicanfcreader.model.Card as TransitHistoryRecord

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopScreen(
    topScreenViewModel: TopScreenViewModel,
    onOpenStats: () -> Unit
) {
    val summaries = topScreenViewModel.cardSummaries.observeAsState(emptyList())
    val selectedCardId = topScreenViewModel.selectedCardId.observeAsState()
    val selectedHistory = topScreenViewModel.selectedHistory.observeAsState(emptyList())
    val searchQuery = topScreenViewModel.searchQuery.observeAsState("")
    val balanceColorHex = topScreenViewModel.balanceColorHex.observeAsState("#8AD7C8")
    val summaryBackgroundColorHex = topScreenViewModel.summaryBackgroundColorHex.observeAsState("#103F3A")
    val noticeBackgroundColorHex = topScreenViewModel.noticeBackgroundColorHex.observeAsState("#174F47")
    val deleteButtonColorHex = topScreenViewModel.deleteButtonColorHex.observeAsState("#533232")
    val balanceBackgroundColorHex = topScreenViewModel.balanceBackgroundColorHex.observeAsState("#0A2528")
    val otherCardBackgroundColorHex = topScreenViewModel.otherCardBackgroundColorHex.observeAsState("#101C24")
    val summaryBackgroundImageUri = topScreenViewModel.summaryBackgroundImageUri.observeAsState()
    val defaultBackgroundImageUri = topScreenViewModel.defaultBackgroundImageUri.observeAsState()
    val cardBackgroundImageUris = topScreenViewModel.cardBackgroundImageUris.observeAsState(emptyMap())
    val showLegacySearchBar = topScreenViewModel.showLegacySearchBar.observeAsState(false)
    val searchDialogVisible = topScreenViewModel.searchDialogVisible.observeAsState(false)
    val showCardBalances = topScreenViewModel.showCardBalances.observeAsState(true)
    val useModernUi = topScreenViewModel.useModernUi.observeAsState(true)
    val showDeleteButton = topScreenViewModel.showDeleteButton.observeAsState(true)
    val showReadNotice = topScreenViewModel.showReadNotice.observeAsState(true)
    val showStatisticsButton = topScreenViewModel.showStatisticsButton.observeAsState(true)
    val showInternalCodes = topScreenViewModel.showInternalCodes.observeAsState(false)
    val showHistoryHeader = topScreenViewModel.showHistoryHeader.observeAsState(true)
    val showBalanceDate = topScreenViewModel.showBalanceDate.observeAsState(true)
    val showHistoryBalances = topScreenViewModel.showHistoryBalances.observeAsState(true)
    val showHistoryIcons = topScreenViewModel.showHistoryIcons.observeAsState(true)
    val transparentContentSurfaces = topScreenViewModel.useTransparentContentSurfaces.observeAsState(false)
    val readCardIds = topScreenViewModel.readCardIds.observeAsState(emptySet())
    val context = LocalContext.current
    val selectedSummary = summaries.value.firstOrNull { it.cardId == selectedCardId.value }
    val balanceColor = balanceColorHex.value.toComposeColor() ?: MaterialTheme.colorScheme.primary
    val summaryBackgroundColor = summaryBackgroundColorHex.value.toComposeColor() ?: Color(0xFF103F3A)
    val noticeBackgroundColor = noticeBackgroundColorHex.value.toComposeColor() ?: Color(0xFF174F47)
    val deleteButtonColor = deleteButtonColorHex.value.toComposeColor() ?: Color(0xFF533232)
    val balanceBackgroundColor = balanceBackgroundColorHex.value.toComposeColor() ?: Color(0xFF0A2528)
    val otherCardBackgroundColor = otherCardBackgroundColorHex.value.toComposeColor() ?: Color(0xFF101C24)
    val summaryBackgroundImage = rememberSummaryBackgroundImage(summaryBackgroundImageUri.value)
    val groupedHistory = selectedHistory.value
        .mapIndexed { index, card -> index to card }
        .groupBy { it.second.date.orEmpty() }
    var aliasDialogCard by remember { mutableStateOf<SuicaCardSummary?>(null) }
    var editingRecord by remember { mutableStateOf<TransitHistoryRecord?>(null) }
    val balanceSummaryContent: @Composable (Modifier) -> Unit = { modifier ->
        BalanceSummary(
            modifier = modifier,
            summary = selectedSummary,
            hasFreshData = selectedSummary?.cardId in readCardIds.value,
            balanceColor = balanceColor,
            summaryBackgroundColor = summaryBackgroundColor,
            noticeBackgroundColor = noticeBackgroundColor,
            balanceBackgroundColor = balanceBackgroundColor,
            deleteButtonColor = deleteButtonColor,
            summaryBackgroundImage = summaryBackgroundImage,
            modern = useModernUi.value,
            transparent = transparentContentSurfaces.value,
            showBalanceDate = showBalanceDate.value,
            showReadNotice = showReadNotice.value,
            showDeleteButton = showDeleteButton.value,
            onCopyJson = { SensitiveClipboard.copy(context, "SuicaNFC KD JSON", topScreenViewModel.exportSelectedJson()) },
            onCopyCsv = { SensitiveClipboard.copy(context, "SuicaNFC KD CSV", topScreenViewModel.exportSelectedCsv()) },
            onCopyNotion = { SensitiveClipboard.copy(context, "SuicaNFC KD Notion", topScreenViewModel.exportSelectedNotionMarkdown()) },
            onRename = { selectedSummary?.let { aliasDialogCard = it } },
            onClearSelected = topScreenViewModel::clearSelectedCardHistory,
            canCopy = selectedSummary != null,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            if (!useModernUi.value) {
                CardSelector(
                    summaries = summaries.value,
                    selectedCardId = selectedCardId.value,
                    balanceColor = balanceColor,
                    onSelectCard = topScreenViewModel::selectCard
                )
            }
        }

        item {
            if (useModernUi.value && summaries.value.size > 1) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val mainWidth = (maxWidth - 10.dp) * (2f / 3f)
                    val mainHeight = mainWidth * (9f / 16f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        balanceSummaryContent(
                            Modifier
                                .width(mainWidth)
                                .requiredHeight(mainHeight)
                        )
                        ModernSideCards(
                            modifier = Modifier
                                .weight(1f)
                                .height(mainHeight),
                            summaries = summaries.value,
                            selectedCardId = selectedCardId.value,
                            balanceColor = balanceColor,
                            backgroundColor = otherCardBackgroundColor,
                            transparent = transparentContentSurfaces.value,
                            cardBackgroundImageUris = cardBackgroundImageUris.value,
                            defaultBackgroundImageUri = defaultBackgroundImageUri.value,
                            onSelectCard = topScreenViewModel::selectCard
                        )
                    }
                }
            } else {
                balanceSummaryContent(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            }
        }

        item {
            CardActionArea(
                showStatisticsButton = showStatisticsButton.value,
                enabled = selectedSummary != null,
                onStats = onOpenStats
            )
        }

        if (showCardBalances.value && summaries.value.size > 1) {
            item {
                SectionHeader(
                    title = "カード別残高",
                    supportingText = "${summaries.value.size}枚"
                )
            }
            items(summaries.value, key = { it.cardId }) { summary ->
                CardBalanceRow(
                    summary = summary,
                    selected = summary.cardId == selectedCardId.value,
                    isRead = summary.cardId in readCardIds.value,
                    balanceColor = balanceColor,
                    otherCardBackgroundColor = otherCardBackgroundColor,
                    transparent = transparentContentSurfaces.value,
                    backgroundImageUri = cardBackgroundImageUris.value[summary.cardId] ?: defaultBackgroundImageUri.value,
                    onClick = { topScreenViewModel.selectCard(summary.cardId) }
                )
            }
        }

        if (showLegacySearchBar.value) {
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = searchQuery.value,
                    onValueChange = topScreenViewModel::setSearchQuery,
                    label = { Text("履歴検索・フィルタ") },
                    singleLine = true
                )
            }
        }

        if (showHistoryHeader.value) {
            item {
                SectionHeader(
                    title = "選択中カードの履歴",
                    supportingText = "${selectedHistory.value.size}件"
                )
            }
        }

        if (selectedHistory.value.isEmpty()) {
            item {
                EmptyHistoryCard(transparentContentSurfaces.value)
            }
        } else {
            groupedHistory.forEach { (date, records) ->
                item(key = "date-$date") {
                    DateHeader(date.toJapaneseDateLabel())
                }
                items(records, key = { (_, card) -> "${card.cardId}-${card.number}-${card.date}" }) { (index, card) ->
                    if (useModernUi.value) {
                        ModernHistoryItem(
                            card = card,
                            balanceColor = balanceColor,
                            showInternalCodes = showInternalCodes.value,
                            transparent = transparentContentSurfaces.value,
                            showBalance = showHistoryBalances.value,
                            showIcon = showHistoryIcons.value,
                            onEdit = { editingRecord = card }
                        )
                    } else {
                        HistoryCard(
                            card = card,
                            index = index + 1,
                            isLatest = index == 0,
                            balanceColor = balanceColor,
                            showInternalCodes = showInternalCodes.value,
                            transparent = transparentContentSurfaces.value,
                            showBalance = showHistoryBalances.value,
                            showIcon = showHistoryIcons.value,
                            onEdit = { editingRecord = card }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    aliasDialogCard?.let { summary ->
        AliasDialog(
            summary = summary,
            onDismiss = { aliasDialogCard = null },
            onSave = { alias ->
                topScreenViewModel.setCardAlias(summary.cardId, alias)
                aliasDialogCard = null
            }
        )
    }

    editingRecord?.let { record ->
        EditHistoryDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { updated ->
                topScreenViewModel.updateRecord(record.number, updated)
                editingRecord = null
            }
        )
    }

    if (searchDialogVisible.value) {
        SearchDialog(
            query = searchQuery.value,
            onQueryChange = topScreenViewModel::setSearchQuery,
            onDismiss = topScreenViewModel::dismissSearchDialog,
            resultCount = selectedHistory.value.size
        )
    }

}

@Composable
private fun CardSelector(
    summaries: List<SuicaCardSummary>,
    selectedCardId: String?,
    balanceColor: Color,
    onSelectCard: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "カード",
            supportingText = if (summaries.isEmpty()) "未登録" else "${summaries.size}枚"
        )
        if (summaries.isEmpty()) {
            Text(
                text = "交通系ICカードをかざすとカードごとに履歴と残高を保存します。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(summaries, key = { it.cardId }) { summary ->
                    val selected = summary.cardId == selectedCardId
                    Surface(
                        modifier = Modifier.clickable { onSelectCard(summary.cardId) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (selected) 0.dp else 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = summary.title,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = summary.latestRecord.balanceText(),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else balanceColor,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernCardChips(
    summaries: List<SuicaCardSummary>,
    selectedCardId: String?,
    balanceColor: Color,
    onSelectCard: (String) -> Unit
) {
    if (summaries.size <= 1) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(summaries.filter { it.cardId != selectedCardId }, key = { it.cardId }) { summary ->
            Surface(
                modifier = Modifier.clickable { onSelectCard(summary.cardId) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = summary.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary.latestRecord.balanceText(),
                        color = balanceColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernSideCards(
    modifier: Modifier,
    summaries: List<SuicaCardSummary>,
    selectedCardId: String?,
    balanceColor: Color,
    backgroundColor: Color,
    transparent: Boolean,
    cardBackgroundImageUris: Map<String, String>,
    defaultBackgroundImageUri: String?,
    onSelectCard: (String) -> Unit
) {
    val otherCards = summaries.filter { it.cardId != selectedCardId }
    val requiresScroll = otherCards.size > 1
    val scrollState = rememberScrollState()
    Column(
        modifier = if (requiresScroll) modifier.verticalScroll(scrollState) else modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        otherCards.forEach { summary ->
            val backgroundImage = rememberSummaryBackgroundImage(
                cardBackgroundImageUris[summary.cardId] ?: defaultBackgroundImageUri
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clickable { onSelectCard(summary.cardId) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (transparent) Color.Transparent else backgroundColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box {
                    if (backgroundImage != null) {
                        Image(
                            bitmap = backgroundImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.42f
                        )
                    }
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = summary.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = summary.latestRecord.balanceText(),
                            color = balanceColor,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceSummary(
    modifier: Modifier,
    summary: SuicaCardSummary?,
    hasFreshData: Boolean,
    balanceColor: Color,
    summaryBackgroundColor: Color,
    noticeBackgroundColor: Color,
    balanceBackgroundColor: Color,
    deleteButtonColor: Color,
    summaryBackgroundImage: ImageBitmap?,
    modern: Boolean,
    transparent: Boolean,
    showBalanceDate: Boolean,
    showReadNotice: Boolean,
    showDeleteButton: Boolean,
    onCopyJson: () -> Unit,
    onCopyCsv: () -> Unit,
    onCopyNotion: () -> Unit,
    onRename: () -> Unit,
    onClearSelected: () -> Unit,
    canCopy: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (transparent) Color.Transparent else balanceBackgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (summaryBackgroundImage != null) {
                Image(
                    bitmap = summaryBackgroundImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.72f
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = summary?.title ?: "SuicaNFC KD",
                            color = Color(0xFFDDF7EF),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            modifier = Modifier.size(34.dp),
                            enabled = canCopy,
                            onClick = onRename
                        ) {
                            PencilIcon(Color(0xFFDDF7EF))
                        }
                        ExportMenuIconButton(
                            enabled = canCopy,
                            onCopyJson = onCopyJson,
                            onCopyCsv = onCopyCsv,
                            onCopyNotion = onCopyNotion
                        )
                    }
                    if (showReadNotice) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = noticeBackgroundColor
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                text = if (hasFreshData) "このカードを読み取りました" else "交通系ICカードをかざしてください",
                                color = Color(0xFFE8FFF8),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                StatusDot(isActive = hasFreshData)
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = summary?.latestRecord.balanceText(),
                        color = balanceColor,
                        style = if (modern) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (showBalanceDate) {
                        Text(
                            text = summary?.latestRecord?.date ?: "履歴はまだありません",
                            color = Color(0xFFC2E8DE),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (showDeleteButton) {
                    IconButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(42.dp),
                        enabled = canCopy,
                        onClick = onClearSelected
                    ) {
                        DeleteIcon(deleteButtonColor)
                    }
                }
            }

            }
        }
    }
}

@Composable
private fun CardActionArea(
    showStatisticsButton: Boolean,
    enabled: Boolean,
    onStats: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showStatisticsButton) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStats,
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("統計")
                }
            }
        }
    }
}

@Composable
private fun CardBalanceRow(
    summary: SuicaCardSummary,
    selected: Boolean,
    isRead: Boolean,
    balanceColor: Color,
    otherCardBackgroundColor: Color,
    transparent: Boolean,
    backgroundImageUri: String?,
    onClick: () -> Unit
) {
    val backgroundImage = rememberSummaryBackgroundImage(backgroundImageUri)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (transparent) {
                Color.Transparent
            } else if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                otherCardBackgroundColor
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            if (backgroundImage != null) {
                Image(
                    bitmap = backgroundImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.32f
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = summary.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${summary.recordCount}件 / ${summary.latestRecord.date ?: "-"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(isActive = isRead)
                    Text(
                        text = summary.latestRecord.balanceText(),
                        color = balanceColor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportMenuButton(
    enabled: Boolean,
    onCopyJson: () -> Unit,
    onCopyCsv: () -> Unit,
    onCopyNotion: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Button(
        onClick = { expanded = true },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text("エクスポート")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("CSV") },
            onClick = {
                onCopyCsv()
                expanded = false
            }
        )
        DropdownMenuItem(
            text = { Text("JSON") },
            onClick = {
                onCopyJson()
                expanded = false
            }
        )
        DropdownMenuItem(
            text = { Text("Notion") },
            onClick = {
                onCopyNotion()
                expanded = false
            }
        )
    }
}

@Composable
private fun ExportMenuIconButton(
    enabled: Boolean,
    onCopyJson: () -> Unit,
    onCopyCsv: () -> Unit,
    onCopyNotion: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            modifier = Modifier.size(34.dp),
            enabled = enabled,
            onClick = { expanded = true }
        ) {
            ExportIcon(Color(0xFFDDF7EF))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(text = { Text("CSV") }, onClick = { onCopyCsv(); expanded = false })
            DropdownMenuItem(text = { Text("JSON") }, onClick = { onCopyJson(); expanded = false })
            DropdownMenuItem(text = { Text("Notion") }, onClick = { onCopyNotion(); expanded = false })
        }
    }
}

@Composable
private fun StatusDot(isActive: Boolean) {
    val color = if (isActive) Color(0xFF9BE15D) else Color(0xFFFFC857)
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun PencilIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.10f
        drawLine(
            color = color,
            start = Offset(size.width * 0.25f, size.height * 0.78f),
            end = Offset(size.width * 0.72f, size.height * 0.31f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.64f, size.height * 0.23f),
            end = Offset(size.width * 0.78f, size.height * 0.37f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.20f, size.height * 0.84f),
            end = Offset(size.width * 0.36f, size.height * 0.80f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun ExportIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.10f
        drawLine(color, Offset(size.width * .5f, size.height * .13f), Offset(size.width * .5f, size.height * .65f), stroke)
        drawLine(color, Offset(size.width * .28f, size.height * .43f), Offset(size.width * .5f, size.height * .65f), stroke)
        drawLine(color, Offset(size.width * .72f, size.height * .43f), Offset(size.width * .5f, size.height * .65f), stroke)
        drawLine(color, Offset(size.width * .18f, size.height * .82f), Offset(size.width * .82f, size.height * .82f), stroke)
        drawLine(color, Offset(size.width * .18f, size.height * .82f), Offset(size.width * .18f, size.height * .65f), stroke)
        drawLine(color, Offset(size.width * .82f, size.height * .82f), Offset(size.width * .82f, size.height * .65f), stroke)
    }
}

@Composable
private fun DeleteIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.10f
        drawLine(color, Offset(size.width * .25f, size.height * .29f), Offset(size.width * .75f, size.height * .29f), stroke)
        drawLine(color, Offset(size.width * .43f, size.height * .18f), Offset(size.width * .57f, size.height * .18f), stroke)
        drawLine(color, Offset(size.width * .32f, size.height * .31f), Offset(size.width * .37f, size.height * .82f), stroke)
        drawLine(color, Offset(size.width * .68f, size.height * .31f), Offset(size.width * .63f, size.height * .82f), stroke)
        drawLine(color, Offset(size.width * .37f, size.height * .82f), Offset(size.width * .63f, size.height * .82f), stroke)
        drawLine(color, Offset(size.width * .46f, size.height * .43f), Offset(size.width * .46f, size.height * .70f), stroke)
        drawLine(color, Offset(size.width * .54f, size.height * .43f), Offset(size.width * .54f, size.height * .70f), stroke)
    }
}

@Composable
private fun CashRegisterIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = size.minDimension * 0.08f)
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.18f, size.height * 0.42f),
            size = Size(size.width * 0.64f, size.height * 0.38f),
            style = stroke
        )
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.30f, size.height * 0.20f),
            size = Size(size.width * 0.40f, size.height * 0.20f),
            style = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, size.height * 0.62f),
            end = Offset(size.width * 0.72f, size.height * 0.62f),
            strokeWidth = size.minDimension * 0.07f
        )
        drawCircle(color, radius = size.minDimension * 0.04f, center = Offset(size.width * 0.36f, size.height * 0.52f))
        drawCircle(color, radius = size.minDimension * 0.04f, center = Offset(size.width * 0.50f, size.height * 0.52f))
        drawCircle(color, radius = size.minDimension * 0.04f, center = Offset(size.width * 0.64f, size.height * 0.52f))
    }
}

@Composable
private fun SectionHeader(title: String, supportingText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = supportingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun DateHeader(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ModernHistoryItem(
    card: TransitHistoryRecord,
    balanceColor: Color,
    showInternalCodes: Boolean,
    transparent: Boolean,
    showBalance: Boolean,
    showIcon: Boolean,
    onEdit: () -> Unit
) {
    val special = card.isSpecialActivity()
    val primary = when {
        special -> card.activityLabel().let { label ->
            card.activityPlace()?.let { "$label ($it)" } ?: label
        }
        card.hasInPlace() || card.hasOutPlace() -> listOf(card.inStation, card.outStation)
            .mapNotNull { it.firstReadableOrNull() }
            .joinToString("  ->  ")
            .ifBlank { card.action ?: card.kind ?: "利用" }
        else -> card.action ?: card.kind ?: "利用"
    }
    val secondary = if (special) {
        listOf(card.activityCompany(), card.activityLine()).mapNotNull { it.firstReadableOrNull() }.joinToString(" / ")
    } else {
        listOf(card.inCompany, card.inLine, card.outCompany, card.outLine)
            .mapNotNull { it.firstReadableOrNull() }
            .distinct()
            .joinToString(" / ")
    }.ifBlank { card.device ?: "交通系IC" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (showIcon) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = if (special) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            HistoryRecordIcon(card = card, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = primary,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showInternalCodes && !card.internalCode.isNullOrBlank()) {
                        Text(
                            text = card.internalCode.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!card.memo.isNullOrBlank() || !card.tags.isNullOrBlank()) {
                        Text(
                            text = listOfNotNull(card.memo, card.tags?.let { "#$it" }).joinToString("  "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onEdit) { Text("修正") }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = card.amountText(),
                        color = card.amountColor(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (showBalance) {
                        Text(
                            text = card.balanceText(),
                            color = balanceColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordIcon(card: TransitHistoryRecord, color: Color) {
    when {
        card.isBusActivity() -> BusIcon(color)
        card.activityLabel() == "物販" -> CashRegisterIcon(color)
        card.activityLabel() == "チャージ" -> Text(
            text = "+",
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        else -> TrainIcon(color)
    }
}

@Composable
private fun TrainIcon(color: Color) {
    Canvas(modifier = Modifier.size(19.dp)) {
        val stroke = Stroke(width = size.minDimension * .1f)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * .22f, size.height * .08f),
            size = Size(size.width * .56f, size.height * .72f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .12f, size.width * .12f),
            style = stroke
        )
        drawLine(color, Offset(size.width * .33f, size.height * .35f), Offset(size.width * .67f, size.height * .35f), stroke.width)
        drawCircle(color, radius = size.minDimension * .07f, center = Offset(size.width * .38f, size.height * .63f))
        drawCircle(color, radius = size.minDimension * .07f, center = Offset(size.width * .62f, size.height * .63f))
        drawLine(color, Offset(size.width * .35f, size.height * .87f), Offset(size.width * .65f, size.height * .87f), stroke.width)
    }
}

@Composable
private fun BusIcon(color: Color) {
    Canvas(modifier = Modifier.size(19.dp)) {
        val stroke = Stroke(width = size.minDimension * .1f)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * .08f, size.height * .19f),
            size = Size(size.width * .84f, size.height * .55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .1f, size.width * .1f),
            style = stroke
        )
        drawLine(color, Offset(size.width * .22f, size.height * .39f), Offset(size.width * .78f, size.height * .39f), stroke.width)
        drawCircle(color, radius = size.minDimension * .09f, center = Offset(size.width * .27f, size.height * .8f))
        drawCircle(color, radius = size.minDimension * .09f, center = Offset(size.width * .73f, size.height * .8f))
    }
}

@Composable
private fun HistoryCard(
    card: TransitHistoryRecord,
    index: Int,
    isLatest: Boolean,
    balanceColor: Color,
    showInternalCodes: Boolean,
    transparent: Boolean,
    showBalance: Boolean,
    showIcon: Boolean,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLatest) 2.dp else 0.dp)
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = card.action ?: card.kind ?: "利用履歴",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = card.date ?: "-",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = card.amountText(),
                            color = card.amountColor(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (showBalance) {
                            Text(
                                text = card.balanceText(),
                                color = balanceColor,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        modifier = Modifier.weight(1f),
                        onClick = {},
                        label = {
                            Text(
                                text = card.device ?: "端末不明",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    AssistChip(
                        modifier = Modifier.weight(1f),
                        onClick = {},
                        label = {
                            Text(
                                text = "No.${card.number ?: index}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                val hasInPlace = card.hasInPlace()
                val hasOutPlace = card.hasOutPlace()
                if (card.isSpecialActivity()) {
                    ActivityLine(card = card)
                } else if (card.isBusActivity()) {
                    RouteLine(
                        label = "入",
                        company = card.inCompany,
                        line = card.inLine,
                        station = card.inStation
                    )
                    RouteLine(
                        label = "出",
                        company = card.outCompany,
                        line = card.outLine,
                        station = card.outStation
                    )
                } else if (hasInPlace || hasOutPlace) {
                    if (hasInPlace) {
                        RouteLine(
                            label = "入",
                            company = card.inCompany,
                            line = card.inLine,
                            station = card.inStation
                        )
                    }
                    if (hasOutPlace) {
                        RouteLine(
                            label = "出",
                            company = card.outCompany,
                            line = card.outLine,
                            station = card.outStation
                        )
                    }
                } else {
                    ActivityLine(card = card)
                }
                if (showInternalCodes && !card.internalCode.isNullOrBlank()) {
                    Text(
                        text = card.internalCode.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (!card.memo.isNullOrBlank() || !card.tags.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(card.memo, card.tags?.let { "#$it" }).joinToString("  "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onEdit) {
                    Text(if (card.manuallyEdited) "修正済み・再編集" else "手動修正")
                }
            }
        }
    }
}

@Composable
private fun ActivityLine(card: TransitHistoryRecord) {
    val label = card.activityLabel()
    val place = card.activityPlace()
    val title = if (place != null && (label == "チャージ" || label == "物販")) {
        "$label ($place)"
    } else {
        label
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = when (label) {
                "チャージ" -> Color(0xFFDDF7EF)
                "物販" -> Color(0xFFFFE8B7)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (label == "物販") {
                    CashRegisterIcon(Color(0xFF203238))
                } else {
                    Text(
                        text = card.activityBadge(),
                        color = Color(0xFF203238),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(card.activityCompany(), card.activityLine(), card.device ?: card.action ?: card.kind)
                    .mapNotNull { it.firstReadableOrNull() }
                    .distinct()
                    .joinToString(" / ")
                    .ifBlank { "交通系IC" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RouteLine(label: String, company: String?, line: String?, station: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (label == "入") Color(0xFFDEEEF5) else Color(0xFFFFE4D6)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = Color(0xFF203238),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            val primary = station.firstReadableOrNull()
                ?: line.firstReadableOrNull()
                ?: company.firstReadableOrNull()
                ?: "未記録"
            val secondary = listOf(company, line)
                .mapNotNull { it.firstReadableOrNull() }
                .distinct()
                .joinToString(" / ")
            Text(
                text = primary,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondary.ifBlank { "交通系IC" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard(transparent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "このカードの履歴はまだありません",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "交通系ICカードをかざすとカード別に保存されます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AliasDialog(
    summary: SuicaCardSummary,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var alias by remember(summary.cardId) { mutableStateOf(summary.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("カード名を編集") },
        text = {
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text("愛称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(alias) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun EditHistoryDialog(
    record: TransitHistoryRecord,
    onDismiss: () -> Unit,
    onSave: (TransitHistoryRecord) -> Unit
) {
    var title by remember(record.number) { mutableStateOf(record.action ?: record.kind.orEmpty()) }
    var date by remember(record.number) { mutableStateOf(record.date.orEmpty()) }
    var amount by remember(record.number) { mutableStateOf(record.amount.orEmpty()) }
    var balance by remember(record.number) { mutableStateOf(record.balance.orEmpty()) }
    var inCompany by remember(record.number) { mutableStateOf(record.inCompany.orEmpty()) }
    var inLine by remember(record.number) { mutableStateOf(record.inLine.orEmpty()) }
    var inStation by remember(record.number) { mutableStateOf(record.inStation.orEmpty()) }
    var outCompany by remember(record.number) { mutableStateOf(record.outCompany.orEmpty()) }
    var outLine by remember(record.number) { mutableStateOf(record.outLine.orEmpty()) }
    var outStation by remember(record.number) { mutableStateOf(record.outStation.orEmpty()) }
    var memo by remember(record.number) { mutableStateOf(record.memo.orEmpty()) }
    var tags by remember(record.number) { mutableStateOf(record.tags.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("履歴を手動修正") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { EditField("タイトル", title) { title = it } }
                item { EditField("日付", date) { date = it } }
                item { EditField("差額（+/-）", amount) { amount = it } }
                item { EditField("残高", balance) { balance = it } }
                item { EditField("入場会社・店舗種別", inCompany) { inCompany = it } }
                item { EditField("入場路線・分類", inLine) { inLine = it } }
                item { EditField("入場駅・店舗", inStation) { inStation = it } }
                item { EditField("出場会社・店舗種別", outCompany) { outCompany = it } }
                item { EditField("出場路線・分類", outLine) { outLine = it } }
                item { EditField("出場駅・店舗", outStation) { outStation = it } }
                item { EditField("メモ", memo) { memo = it } }
                item { EditField("タグ", tags) { tags = it } }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        record.copy(
                            action = title.ifBlank { record.action },
                            date = date.ifBlank { null },
                        amount = amount.ifBlank { null },
                        balance = balance.ifBlank { null },
                        inCompany = inCompany.ifBlank { null },
                        inLine = inLine.ifBlank { null },
                        inStation = inStation.ifBlank { null },
                        outCompany = outCompany.ifBlank { null },
                        outLine = outLine.ifBlank { null },
                        outStation = outStation.ifBlank { null },
                            memo = memo.ifBlank { null },
                            tags = tags.ifBlank { null },
                            manuallyEdited = true
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true
    )
}

@Composable
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    resultCount: Int
) {
    var localQuery by remember(query) { mutableStateOf(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text("履歴検索") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = localQuery,
                    onValueChange = {
                        localQuery = it
                        onQueryChange(it)
                    },
                    label = { Text("履歴検索・フィルタ") },
                    singleLine = true
                )
                Text(
                    text = if (localQuery.isBlank()) "検索語を入力してください" else "${resultCount}件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    localQuery = ""
                    onQueryChange("")
                }
            ) { Text("クリア") }
        }
    )
}

@Composable
private fun TextDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SelectionContainer {
                Text(text = text.ifBlank { "データがありません" })
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text("コピー") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
fun StatsScreen(
    viewModel: TopScreenViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        PageHeader(title = "統計", onBack = onBack)
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = viewModel.statsText().ifBlank { "統計を表示できる履歴がありません" },
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge
        )
        TextButton(onClick = { SensitiveClipboard.copy(context, "SuicaNFC KD 統計", viewModel.statsText()) }) {
            Text("統計をコピー")
        }
        Text(
            modifier = Modifier.padding(top = 20.dp),
            text = viewModel.allCardsStatsText().ifBlank { "全カードの統計を表示できる履歴がありません" },
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge
        )
        TextButton(onClick = { SensitiveClipboard.copy(context, "SuicaNFC KD 全カード統計", viewModel.allCardsStatsText()) }) {
            Text("全カード統計をコピー")
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: TopScreenViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistSafeImageUri(context, it) }?.let(viewModel::setSummaryBackgroundImageUri)
    }
    val widgetImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistSafeImageUri(context, it) }?.let(viewModel::setWidgetBackgroundImageUri)
    }
    val themeImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistSafeImageUri(context, it) }?.let(viewModel::setThemeBackgroundImageUri)
    }
    val backupSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { writeBackupFile(context, it, viewModel.exportBackupJson()) }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        readBackupFile(context, uri)?.let(viewModel::importBackupJson)
    }
    SettingsContent(
        appTitle = viewModel.appTitle.observeAsState("SuicaNFC KD").value,
        accentColorHex = viewModel.accentColorHex.observeAsState("#8AD7C8").value,
        balanceColorHex = viewModel.balanceColorHex.observeAsState("#8AD7C8").value,
        summaryBackgroundColorHex = viewModel.summaryBackgroundColorHex.observeAsState("#103F3A").value,
        noticeBackgroundColorHex = viewModel.noticeBackgroundColorHex.observeAsState("#174F47").value,
        deleteButtonColorHex = viewModel.deleteButtonColorHex.observeAsState("#533232").value,
        balanceBackgroundColorHex = viewModel.balanceBackgroundColorHex.observeAsState("#0A2528").value,
        otherCardBackgroundColorHex = viewModel.otherCardBackgroundColorHex.observeAsState("#101C24").value,
        widgetBackgroundColorHex = viewModel.widgetBackgroundColorHex.observeAsState("#000000").value,
        summaryBackgroundImageUri = viewModel.summaryBackgroundImageUri.observeAsState().value,
        widgetBackgroundImageUri = viewModel.widgetBackgroundImageUri.observeAsState().value,
        themeBackgroundImageUri = viewModel.themeBackgroundImageUri.observeAsState().value,
        widgetPrivacyMode = viewModel.widgetPrivacyMode.observeAsState(true).value,
        transparentContentSurfaces = viewModel.useTransparentContentSurfaces.observeAsState(false).value,
        useSearchIcon = viewModel.useSearchIcon.observeAsState(true).value,
        showLegacySearchBar = viewModel.showLegacySearchBar.observeAsState(false).value,
        showCardBalances = viewModel.showCardBalances.observeAsState(true).value,
        useModernUi = viewModel.useModernUi.observeAsState(true).value,
        showBottomTabLabels = viewModel.showBottomTabLabels.observeAsState(true).value,
        showDeleteButton = viewModel.showDeleteButton.observeAsState(true).value,
        showReadNotice = viewModel.showReadNotice.observeAsState(true).value,
        showStatisticsButton = viewModel.showStatisticsButton.observeAsState(true).value,
        showPaletteIcon = viewModel.showPaletteIcon.observeAsState(true).value,
        showMoreMenu = viewModel.showMoreMenu.observeAsState(true).value,
        showInternalCodes = viewModel.showInternalCodes.observeAsState(false).value,
        showHistoryHeader = viewModel.showHistoryHeader.observeAsState(true).value,
        showBalanceDate = viewModel.showBalanceDate.observeAsState(true).value,
        showHistoryBalances = viewModel.showHistoryBalances.observeAsState(true).value,
        showHistoryIcons = viewModel.showHistoryIcons.observeAsState(true).value,
        demoMode = viewModel.demoMode.observeAsState(false).value,
        featureFlags = viewModel.featureFlags.observeAsState(emptyMap()).value,
        onDismiss = onBack,
        onAppTitleSave = viewModel::setAppTitle,
        onAccentSave = viewModel::setAccentColor,
        onBalanceColorSave = viewModel::setBalanceColor,
        onSummaryBackgroundColorSave = viewModel::setSummaryBackgroundColor,
        onNoticeBackgroundColorSave = viewModel::setNoticeBackgroundColor,
        onDeleteButtonColorSave = viewModel::setDeleteButtonColor,
        onBalanceBackgroundColorSave = viewModel::setBalanceBackgroundColor,
        onOtherCardBackgroundColorSave = viewModel::setOtherCardBackgroundColor,
        onWidgetBackgroundColorSave = viewModel::setWidgetBackgroundColor,
        onPickSummaryBackgroundImage = { imagePicker.launch(arrayOf("image/*")) },
        onClearSummaryBackgroundImage = { viewModel.setSummaryBackgroundImageUri(null) },
        onClearAllBackgroundImages = viewModel::clearAllBackgroundImages,
        onPickWidgetBackgroundImage = { widgetImagePicker.launch(arrayOf("image/*")) },
        onClearWidgetBackgroundImage = { viewModel.setWidgetBackgroundImageUri(null) },
        onPickThemeBackgroundImage = { themeImagePicker.launch(arrayOf("image/*")) },
        onClearThemeBackgroundImage = { viewModel.setThemeBackgroundImageUri(null) },
        onWidgetPrivacyModeChanged = viewModel::setWidgetPrivacyMode,
        onTransparentContentSurfacesChanged = viewModel::setUseTransparentContentSurfaces,
        onUseSearchIconChanged = viewModel::setUseSearchIcon,
        onShowLegacySearchBarChanged = viewModel::setShowLegacySearchBar,
        onShowCardBalancesChanged = viewModel::setShowCardBalances,
        onUseModernUiChanged = viewModel::setUseModernUi,
        onShowBottomTabLabelsChanged = viewModel::setShowBottomTabLabels,
        onShowDeleteButtonChanged = viewModel::setShowDeleteButton,
        onShowReadNoticeChanged = viewModel::setShowReadNotice,
        onShowStatisticsButtonChanged = viewModel::setShowStatisticsButton,
        onShowPaletteIconChanged = viewModel::setShowPaletteIcon,
        onShowMoreMenuChanged = viewModel::setShowMoreMenu,
        onShowInternalCodesChanged = viewModel::setShowInternalCodes,
        onShowHistoryHeaderChanged = viewModel::setShowHistoryHeader,
        onShowBalanceDateChanged = viewModel::setShowBalanceDate,
        onShowHistoryBalancesChanged = viewModel::setShowHistoryBalances,
        onShowHistoryIconsChanged = viewModel::setShowHistoryIcons,
        onDemoModeChanged = viewModel::setDemoMode,
        onFeatureChanged = viewModel::setFeatureEnabled,
        onExportBackup = { backupSaveLauncher.launch("suicanfc-kd-backup.json") },
        onImportBackupFile = { backupImportLauncher.launch(arrayOf("application/json", "text/plain")) },
        onImportBackup = viewModel::importBackupJson
    )
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onBack) { Text("戻る") }
    }
}

@Composable
private fun SettingsContent(
    appTitle: String,
    accentColorHex: String,
    balanceColorHex: String,
    summaryBackgroundColorHex: String,
    noticeBackgroundColorHex: String,
    deleteButtonColorHex: String,
    balanceBackgroundColorHex: String,
    otherCardBackgroundColorHex: String,
    widgetBackgroundColorHex: String,
    summaryBackgroundImageUri: String?,
    widgetBackgroundImageUri: String?,
    themeBackgroundImageUri: String?,
    widgetPrivacyMode: Boolean,
    transparentContentSurfaces: Boolean,
    useSearchIcon: Boolean,
    showLegacySearchBar: Boolean,
    showCardBalances: Boolean,
    useModernUi: Boolean,
    showBottomTabLabels: Boolean,
    showDeleteButton: Boolean,
    showReadNotice: Boolean,
    showStatisticsButton: Boolean,
    showPaletteIcon: Boolean,
    showMoreMenu: Boolean,
    showInternalCodes: Boolean,
    showHistoryHeader: Boolean,
    showBalanceDate: Boolean,
    showHistoryBalances: Boolean,
    showHistoryIcons: Boolean,
    demoMode: Boolean,
    featureFlags: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onAppTitleSave: (String) -> Unit,
    onAccentSave: (String) -> Unit,
    onBalanceColorSave: (String) -> Unit,
    onSummaryBackgroundColorSave: (String) -> Unit,
    onNoticeBackgroundColorSave: (String) -> Unit,
    onDeleteButtonColorSave: (String) -> Unit,
    onBalanceBackgroundColorSave: (String) -> Unit,
    onOtherCardBackgroundColorSave: (String) -> Unit,
    onWidgetBackgroundColorSave: (String) -> Unit,
    onPickSummaryBackgroundImage: () -> Unit,
    onClearSummaryBackgroundImage: () -> Unit,
    onClearAllBackgroundImages: () -> Unit,
    onPickWidgetBackgroundImage: () -> Unit,
    onClearWidgetBackgroundImage: () -> Unit,
    onPickThemeBackgroundImage: () -> Unit,
    onClearThemeBackgroundImage: () -> Unit,
    onWidgetPrivacyModeChanged: (Boolean) -> Unit,
    onTransparentContentSurfacesChanged: (Boolean) -> Unit,
    onUseSearchIconChanged: (Boolean) -> Unit,
    onShowLegacySearchBarChanged: (Boolean) -> Unit,
    onShowCardBalancesChanged: (Boolean) -> Unit,
    onUseModernUiChanged: (Boolean) -> Unit,
    onShowBottomTabLabelsChanged: (Boolean) -> Unit,
    onShowDeleteButtonChanged: (Boolean) -> Unit,
    onShowReadNoticeChanged: (Boolean) -> Unit,
    onShowStatisticsButtonChanged: (Boolean) -> Unit,
    onShowPaletteIconChanged: (Boolean) -> Unit,
    onShowMoreMenuChanged: (Boolean) -> Unit,
    onShowInternalCodesChanged: (Boolean) -> Unit,
    onShowHistoryHeaderChanged: (Boolean) -> Unit,
    onShowBalanceDateChanged: (Boolean) -> Unit,
    onShowHistoryBalancesChanged: (Boolean) -> Unit,
    onShowHistoryIconsChanged: (Boolean) -> Unit,
    onDemoModeChanged: (Boolean) -> Unit,
    onFeatureChanged: (String, Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackupFile: () -> Unit,
    onImportBackup: (String) -> Boolean
) {
    var title by remember(appTitle) { mutableStateOf(appTitle) }
    var accent by remember(accentColorHex) { mutableStateOf(accentColorHex) }
    var balanceColor by remember(balanceColorHex) { mutableStateOf(balanceColorHex) }
    var summaryBackgroundColor by remember(summaryBackgroundColorHex) { mutableStateOf(summaryBackgroundColorHex) }
    var noticeBackgroundColor by remember(noticeBackgroundColorHex) { mutableStateOf(noticeBackgroundColorHex) }
    var deleteButtonColor by remember(deleteButtonColorHex) { mutableStateOf(deleteButtonColorHex) }
    var balanceBackgroundColor by remember(balanceBackgroundColorHex) { mutableStateOf(balanceBackgroundColorHex) }
    var otherCardBackgroundColor by remember(otherCardBackgroundColorHex) { mutableStateOf(otherCardBackgroundColorHex) }
    var widgetBackgroundColor by remember(widgetBackgroundColorHex) { mutableStateOf(widgetBackgroundColorHex) }
    var backupText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PageHeader(title = "設定", onBack = onDismiss)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                item {
                    EditField("アプリ名", title) { title = it }
                }
                item {
                    Button(
                        onClick = {
                            onAppTitleSave(title)
                            message = "アプリ名を保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("アプリ名を保存")
                    }
                }
                item {
                    EditField("アクセントカラー (#RRGGBB)", accent) { accent = it }
                }
                item {
                    Button(
                        onClick = {
                            onAccentSave(accent)
                            message = "アクセントカラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("アクセントカラーを保存")
                    }
                }
                item {
                    EditField("残高表示カラー (#RRGGBB)", balanceColor) { balanceColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onBalanceColorSave(balanceColor)
                            message = "残高表示カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("残高表示カラーを保存")
                    }
                }
                item {
                    EditField("カードヘッダー背景カラー (#RRGGBB)", summaryBackgroundColor) { summaryBackgroundColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onSummaryBackgroundColorSave(summaryBackgroundColor)
                            message = "カードヘッダー背景カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("カードヘッダー背景カラーを保存")
                    }
                }
                item {
                    EditField("残高エリア背景カラー (#RRGGBB)", balanceBackgroundColor) { balanceBackgroundColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onBalanceBackgroundColorSave(balanceBackgroundColor)
                            message = "残高エリア背景カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("残高エリア背景カラーを保存")
                    }
                }
                item {
                    EditField("選択外カード背景カラー (#RRGGBB)", otherCardBackgroundColor) { otherCardBackgroundColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onOtherCardBackgroundColorSave(otherCardBackgroundColor)
                            message = "選択外カード背景カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("選択外カード背景カラーを保存")
                    }
                }
                item {
                    EditField("ウィジェット背景カラー (#RRGGBB)", widgetBackgroundColor) { widgetBackgroundColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onWidgetBackgroundColorSave(widgetBackgroundColor)
                            message = "ウィジェット背景カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ウィジェット背景カラーを保存")
                    }
                }
                item {
                    Button(onClick = onPickWidgetBackgroundImage, shape = RoundedCornerShape(8.dp)) {
                        Text("ウィジェット背景画像を選択")
                    }
                }
                item {
                    FeatureFlagRow(
                        label = "ウィジェットに残高・履歴を表示",
                        checked = !widgetPrivacyMode,
                        onCheckedChange = { onWidgetPrivacyModeChanged(!it) }
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "残高・履歴エリアを透明にする",
                        checked = transparentContentSurfaces,
                        onCheckedChange = onTransparentContentSurfacesChanged
                    )
                }
                if (widgetBackgroundImageUri != null) {
                    item {
                        OutlinedButton(onClick = onClearWidgetBackgroundImage, shape = RoundedCornerShape(8.dp)) {
                            Text("ウィジェット背景画像を解除")
                        }
                    }
                }
                item {
                    Button(onClick = onPickSummaryBackgroundImage, shape = RoundedCornerShape(8.dp)) {
                        Text("選択中カードの残高背景画像を選択")
                    }
                }
                if (summaryBackgroundImageUri != null) {
                    item {
                        OutlinedButton(onClick = onClearSummaryBackgroundImage, shape = RoundedCornerShape(8.dp)) {
                            Text("選択中カードの残高背景画像を解除")
                        }
                    }
                }
                item {
                    Button(onClick = onPickThemeBackgroundImage, shape = RoundedCornerShape(8.dp)) {
                        Text("アプリ全体の画像テーマを選択")
                    }
                }
                if (themeBackgroundImageUri != null) {
                    item {
                        OutlinedButton(onClick = onClearThemeBackgroundImage, shape = RoundedCornerShape(8.dp)) {
                            Text("アプリ全体の画像テーマを解除")
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = onClearAllBackgroundImages, shape = RoundedCornerShape(8.dp)) {
                        Text("すべての背景画像を解除")
                    }
                }
                item {
                    EditField("読み取り案内背景カラー (#RRGGBB)", noticeBackgroundColor) { noticeBackgroundColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onNoticeBackgroundColorSave(noticeBackgroundColor)
                            message = "読み取り案内背景カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("読み取り案内背景カラーを保存")
                    }
                }
                item {
                    EditField("削除アイコンカラー (#RRGGBB)", deleteButtonColor) { deleteButtonColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onDeleteButtonColorSave(deleteButtonColor)
                            message = "削除アイコンカラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("削除アイコンカラーを保存")
                    }
                }
                item {
                    FeatureFlagRow(
                        label = "虫眼鏡アイコン検索を表示",
                        checked = useSearchIcon,
                        onCheckedChange = onUseSearchIconChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "従来の履歴検索・フィルタを表示",
                        checked = showLegacySearchBar,
                        onCheckedChange = onShowLegacySearchBarChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "カード別残高を表示",
                        checked = showCardBalances,
                        onCheckedChange = onShowCardBalancesChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "モダンUIを使用",
                        checked = useModernUi,
                        onCheckedChange = onUseModernUiChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "デモモード（実データを変更しない）",
                        checked = demoMode,
                        onCheckedChange = onDemoModeChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "下部タブのラベルを表示",
                        checked = showBottomTabLabels,
                        onCheckedChange = onShowBottomTabLabelsChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "このカードを削除を表示",
                        checked = showDeleteButton,
                        onCheckedChange = onShowDeleteButtonChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "かざしてください案内を表示",
                        checked = showReadNotice,
                        onCheckedChange = onShowReadNoticeChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "統計ボタンを表示",
                        checked = showStatisticsButton,
                        onCheckedChange = onShowStatisticsButtonChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "パレットアイコンを表示",
                        checked = showPaletteIcon,
                        onCheckedChange = onShowPaletteIconChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "上部のその他メニューを表示",
                        checked = showMoreMenu,
                        onCheckedChange = onShowMoreMenuChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "駅・バス停の内部コードを表示",
                        checked = showInternalCodes,
                        onCheckedChange = onShowInternalCodesChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "履歴の見出しと件数を表示",
                        checked = showHistoryHeader,
                        onCheckedChange = onShowHistoryHeaderChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "残高エリアの日付を表示",
                        checked = showBalanceDate,
                        onCheckedChange = onShowBalanceDateChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "履歴の取引後残高を表示",
                        checked = showHistoryBalances,
                        onCheckedChange = onShowHistoryBalancesChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "履歴の交通・物販・チャージアイコンを表示",
                        checked = showHistoryIcons,
                        onCheckedChange = onShowHistoryIconsChanged
                    )
                }
                item {
                    Text(
                        text = "追加機能はデフォルトで有効です。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                items(featureFlags.entries.toList(), key = { it.key }) { entry ->
                    FeatureFlagRow(
                        label = entry.key.featureLabel(),
                        checked = entry.value,
                        onCheckedChange = { onFeatureChanged(entry.key, it) }
                    )
                }
                item {
                    Button(
                        onClick = {
                            onExportBackup()
                            message = "保存先を選択してください"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("バックアップをファイルに保存")
                    }
                }
                item {
                    OutlinedButton(onClick = onImportBackupFile, shape = RoundedCornerShape(8.dp)) {
                        Text("バックアップファイルを読み込む")
                    }
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = backupText,
                        onValueChange = { backupText = it },
                        label = { Text("インポートJSON") },
                        minLines = 3
                    )
                }
                item {
                    Button(
                        onClick = {
                            message = if (onImportBackup(backupText)) {
                                "インポートしました"
                            } else {
                                "インポートに失敗しました"
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("インポート")
                    }
                }
                if (message.isNotBlank()) {
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
        }
    }
}

@Composable
private fun FeatureFlagRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun String.featureLabel(): String =
    when (this) {
        "manual_edit" -> "手動修正"
        "memo_tags" -> "メモ・タグ"
        "search_filter" -> "履歴検索・フィルタ"
        "statistics" -> "統計"
        "exports" -> "CSV/JSON/Notionエクスポート"
        "widget" -> "ウィジェット"
        "card_alias" -> "カード名編集"
        else -> this
    }

private fun TransitHistoryRecord?.balanceText(): String {
    val balanceValue = this?.balance?.toIntOrNull()
    return if (balanceValue == null) {
        "¥--"
    } else {
        String.format(Locale.JAPAN, "¥%,d", balanceValue)
    }
}

private fun TransitHistoryRecord?.amountText(): String {
    val amountValue = this?.amount?.toIntOrNull()
    return if (amountValue == null) {
        "±--"
    } else {
        String.format(Locale.JAPAN, "%+,.0f", amountValue.toDouble())
    }
}

@Composable
private fun TransitHistoryRecord?.amountColor(): Color {
    val amountValue = this?.amount?.toIntOrNull() ?: return MaterialTheme.colorScheme.onSurfaceVariant
    return if (amountValue >= 0) Color(0xFF64D98A) else Color(0xFFFFB199)
}

private fun TransitHistoryRecord.hasInPlace(): Boolean =
    listOf(inCompany, inLine, inStation).any { !it.isNullOrBlank() && it != "-" }

private fun TransitHistoryRecord.hasOutPlace(): Boolean =
    listOf(outCompany, outLine, outStation).any { !it.isNullOrBlank() && it != "-" }

private fun TransitHistoryRecord.activityLabel(): String {
    val text = listOf(action, kind, device).joinToString(" ")
    return when {
        "チャージ" in text || (amount?.toIntOrNull() ?: 0) > 0 -> "チャージ"
        "物販" in text -> "物販"
        "バス" in text -> "バス"
        else -> action ?: kind ?: "利用"
    }
}

private fun TransitHistoryRecord.activityBadge(): String =
    when (activityLabel()) {
        "チャージ" -> "+"
        "物販" -> "POS"
        else -> "IC"
    }

private fun TransitHistoryRecord.isSpecialActivity(): Boolean {
    val text = listOf(action, kind).joinToString(" ")
    return "チャージ" in text || "物販" in text
}

private fun TransitHistoryRecord.isBusActivity(): Boolean {
    val text = listOf(action, kind, device).joinToString(" ")
    return "バス" in text
}

private fun TransitHistoryRecord.activityPlace(): String? =
    inStation.firstReadableOrNull()
        ?: outStation.firstReadableOrNull()
        ?: inLine.firstReadableOrNull()
        ?: outLine.firstReadableOrNull()
        ?: inCompany.firstReadableOrNull()
        ?: outCompany.firstReadableOrNull()

private fun TransitHistoryRecord.activityCompany(): String? =
    inCompany.firstReadableOrNull() ?: outCompany.firstReadableOrNull()

private fun TransitHistoryRecord.activityLine(): String? =
    inLine.firstReadableOrNull() ?: outLine.firstReadableOrNull()

private fun String?.firstReadableOrNull(): String? {
    return this?.takeIf { it.isNotBlank() && it != "-" && it != "未記録" }
}

private fun String.toJapaneseDateLabel(): String {
    val parts = split("/")
    if (parts.size != 3) return ifBlank { "日付なし" }
    val year = parts[0].toIntOrNull() ?: return this
    val month = parts[1].toIntOrNull() ?: return this
    val day = parts[2].toIntOrNull() ?: return this
    return String.format(Locale.JAPAN, "%d年%d月%d日", year, month, day)
}

@Composable
private fun rememberSummaryBackgroundImage(uriText: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(uriText) {
        uriText?.let { rawUri ->
            runCatching {
                val uri = Uri.parse(rawUri)
                require(isSafeImageUri(context, uri))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val width = info.size.width
                        val height = info.size.height
                        val largestSide = maxOf(width, height)
                        if (largestSide > 2048) {
                            val ratio = 2048f / largestSide.toFloat()
                            decoder.setTargetSize((width * ratio).toInt(), (height * ratio).toInt())
                        }
                    }.asImageBitmap()
                } else {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                    var sampleSize = 1
                    while (maxOf(options.outWidth / sampleSize, options.outHeight / sampleSize) > 2048) {
                        sampleSize *= 2
                    }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        })
                    }?.asImageBitmap() ?: error("Unable to decode image")
                }
            }.getOrNull()
        }
    }
}

private fun persistSafeImageUri(context: android.content.Context, uri: Uri): String? {
    if (!isSafeImageUri(context, uri)) return null
    val persisted = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess
    return uri.toString().takeIf { persisted }
}

private fun writeBackupFile(context: android.content.Context, uri: Uri, content: String): Boolean = runCatching {
    require(uri.scheme == ContentResolver.SCHEME_CONTENT)
    context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
        writer.write(content)
    } ?: error("Could not open backup destination")
}.isSuccess

private fun readBackupFile(context: android.content.Context, uri: Uri?): String? = runCatching {
    val sourceUri = requireNotNull(uri)
    require(sourceUri.scheme == ContentResolver.SCHEME_CONTENT)
    val mimeType = context.contentResolver.getType(sourceUri)?.lowercase(Locale.ROOT)
    require(mimeType == "application/json" || mimeType == "text/json" || mimeType == "text/plain")
    val length = context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { it.length } ?: -1L
    require(length < 0 || length <= MAX_BACKUP_FILE_BYTES)
    context.contentResolver.openInputStream(sourceUri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
        buildString {
            val buffer = CharArray(8_192)
            var totalChars = 0
            while (totalChars <= MAX_BACKUP_FILE_BYTES) {
                val read = reader.read(buffer)
                if (read < 0) break
                totalChars += read
                require(totalChars <= MAX_BACKUP_FILE_BYTES) { "Backup is too large" }
                append(buffer, 0, read)
            }
        }
    } ?: error("Could not open backup source")
}.getOrNull()

private fun isSafeImageUri(context: android.content.Context, uri: Uri): Boolean = runCatching {
    require(uri.scheme == ContentResolver.SCHEME_CONTENT)
    require(!uri.authority.isNullOrBlank())
    val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
    require(mimeType?.startsWith("image/") == true)
    val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    require(length < 0 || length <= MAX_BACKGROUND_IMAGE_BYTES)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth in 1..MAX_BACKGROUND_IMAGE_DIMENSION)
    require(bounds.outHeight in 1..MAX_BACKGROUND_IMAGE_DIMENSION)
    require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_BACKGROUND_IMAGE_PIXELS)
    true
}.getOrDefault(false)

private const val MAX_BACKGROUND_IMAGE_BYTES = 20L * 1024L * 1024L
private const val MAX_BACKUP_FILE_BYTES = 2 * 1024 * 1024
private const val MAX_BACKGROUND_IMAGE_DIMENSION = 8_192
private const val MAX_BACKGROUND_IMAGE_PIXELS = 24_000_000L

private fun String.toComposeColor(): Color? {
    val normalized = trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}$").matches(normalized)) return null
    return normalized.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}
