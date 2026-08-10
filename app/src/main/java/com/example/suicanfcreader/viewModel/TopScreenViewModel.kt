package com.example.suicanfcreader.viewModel

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suicanfcreader.lib.SuicaReader
import com.example.suicanfcreader.model.AppThemeMode
import com.example.suicanfcreader.model.Card
import com.example.suicanfcreader.model.SuicaCardSummary
import com.example.suicanfcreader.storage.SecurePreferences
import com.example.suicanfcreader.widget.BalanceWidgetProvider
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TopScreenViewModel(
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(appContext)
    private val preferences: SharedPreferences =
        SecurePreferences.open(appContext)

    private val initialDemoMode = preferences.getBoolean(KEY_DEMO_MODE, false)
    private val initialHistory = if (initialDemoMode) demoHistory() else loadHistory()
    private val initialSelectedCardId =
        preferences.getString(KEY_SELECTED_CARD_ID, null)
            ?.takeIf { selectedId -> initialHistory.any { it.resolvedCardId() == selectedId } }
            ?: buildSummaries(initialHistory).firstOrNull()?.cardId

    private val _nfcData = MutableLiveData("")
    val nfcData: LiveData<String> get() = _nfcData

    private val _showNoNfcDialog = MutableLiveData(false)
    val showNoNfcDialog: LiveData<Boolean> = _showNoNfcDialog

    private val _isDataRefreshed = MutableLiveData(false)
    val isDataRefreshed: LiveData<Boolean> = _isDataRefreshed

    private val _readCardIds = MutableLiveData<Set<String>>(emptySet())
    val readCardIds: LiveData<Set<String>> = _readCardIds

    private val _latestCards = MutableLiveData<List<Card>>(emptyList())
    val latestCards: LiveData<List<Card>> = _latestCards

    private val _history = MutableLiveData(initialHistory)
    val history: LiveData<List<Card>> = _history

    private val _cardSummaries = MutableLiveData(buildSummaries(initialHistory))
    val cardSummaries: LiveData<List<SuicaCardSummary>> = _cardSummaries

    private val _selectedCardId = MutableLiveData(initialSelectedCardId)
    val selectedCardId: LiveData<String?> = _selectedCardId

    private val _selectedHistory =
        MutableLiveData(filterHistory(initialHistory, initialSelectedCardId, ""))
    val selectedHistory: LiveData<List<Card>> = _selectedHistory

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    private val _themeMode =
        MutableLiveData(AppThemeMode.fromName(preferences.getString(KEY_THEME_MODE, null)))
    val themeMode: LiveData<AppThemeMode> = _themeMode

    private val _accentColorHex =
        MutableLiveData(preferences.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR)
    val accentColorHex: LiveData<String> = _accentColorHex

    private val _balanceColorHex =
        MutableLiveData(preferences.getString(KEY_BALANCE_COLOR, DEFAULT_BALANCE_COLOR) ?: DEFAULT_BALANCE_COLOR)
    val balanceColorHex: LiveData<String> = _balanceColorHex

    private val _summaryBackgroundColorHex =
        MutableLiveData(preferences.getString(KEY_SUMMARY_BACKGROUND_COLOR, DEFAULT_SUMMARY_BACKGROUND_COLOR) ?: DEFAULT_SUMMARY_BACKGROUND_COLOR)
    val summaryBackgroundColorHex: LiveData<String> = _summaryBackgroundColorHex

    private val _noticeBackgroundColorHex =
        MutableLiveData(preferences.getString(KEY_NOTICE_BACKGROUND_COLOR, DEFAULT_NOTICE_BACKGROUND_COLOR) ?: DEFAULT_NOTICE_BACKGROUND_COLOR)
    val noticeBackgroundColorHex: LiveData<String> = _noticeBackgroundColorHex

    private val _deleteButtonColorHex =
        MutableLiveData(preferences.getString(KEY_DELETE_BUTTON_COLOR, DEFAULT_DELETE_BUTTON_COLOR) ?: DEFAULT_DELETE_BUTTON_COLOR)
    val deleteButtonColorHex: LiveData<String> = _deleteButtonColorHex

    private val _balanceBackgroundColorHex =
        MutableLiveData(preferences.getString(KEY_BALANCE_BACKGROUND_COLOR, DEFAULT_BALANCE_BACKGROUND_COLOR) ?: DEFAULT_BALANCE_BACKGROUND_COLOR)
    val balanceBackgroundColorHex: LiveData<String> = _balanceBackgroundColorHex

    private val _otherCardBackgroundColorHex =
        MutableLiveData(preferences.getString(KEY_OTHER_CARD_BACKGROUND_COLOR, DEFAULT_OTHER_CARD_BACKGROUND_COLOR) ?: DEFAULT_OTHER_CARD_BACKGROUND_COLOR)
    val otherCardBackgroundColorHex: LiveData<String> = _otherCardBackgroundColorHex

    private val _widgetBackgroundColorHex =
        MutableLiveData(preferences.getString(KEY_WIDGET_BACKGROUND_COLOR, DEFAULT_WIDGET_BACKGROUND_COLOR) ?: DEFAULT_WIDGET_BACKGROUND_COLOR)
    val widgetBackgroundColorHex: LiveData<String> = _widgetBackgroundColorHex

    private val _summaryBackgroundImageUri =
        MutableLiveData(cardBackgroundUriFor(initialSelectedCardId))
    val summaryBackgroundImageUri: LiveData<String?> = _summaryBackgroundImageUri

    private val _defaultBackgroundImageUri = MutableLiveData(
        trustedPersistedImageUriOrNull(preferences.getString(KEY_SUMMARY_BACKGROUND_IMAGE_URI, null))
    )
    val defaultBackgroundImageUri: LiveData<String?> = _defaultBackgroundImageUri

    private val _cardBackgroundImageUris = MutableLiveData(loadCardBackgroundImageUris())
    val cardBackgroundImageUris: LiveData<Map<String, String>> = _cardBackgroundImageUris

    private val _widgetBackgroundImageUri =
        MutableLiveData(preferences.getString(KEY_WIDGET_BACKGROUND_IMAGE_URI, null))
    val widgetBackgroundImageUri: LiveData<String?> = _widgetBackgroundImageUri

    private val _appTitle =
        MutableLiveData(preferences.getString(KEY_APP_TITLE, DEFAULT_APP_TITLE) ?: DEFAULT_APP_TITLE)
    val appTitle: LiveData<String> = _appTitle

    private val _useSearchIcon =
        MutableLiveData(preferences.getBoolean(KEY_USE_SEARCH_ICON, true))
    val useSearchIcon: LiveData<Boolean> = _useSearchIcon

    private val _showLegacySearchBar =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_LEGACY_SEARCH_BAR, false))
    val showLegacySearchBar: LiveData<Boolean> = _showLegacySearchBar

    private val _isSearchExpanded = MutableLiveData(false)
    val isSearchExpanded: LiveData<Boolean> = _isSearchExpanded

    private val _searchDialogVisible = MutableLiveData(false)
    val searchDialogVisible: LiveData<Boolean> = _searchDialogVisible

    private val _showCardBalances =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_CARD_BALANCES, true))
    val showCardBalances: LiveData<Boolean> = _showCardBalances

    private val _useModernUi = MutableLiveData(preferences.getBoolean(KEY_USE_MODERN_UI, true))
    val useModernUi: LiveData<Boolean> = _useModernUi

    private val _showBottomTabLabels =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_BOTTOM_TAB_LABELS, true))
    val showBottomTabLabels: LiveData<Boolean> = _showBottomTabLabels

    private val _showDeleteButton =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_DELETE_BUTTON, true))
    val showDeleteButton: LiveData<Boolean> = _showDeleteButton

    private val _showReadNotice =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_READ_NOTICE, true))
    val showReadNotice: LiveData<Boolean> = _showReadNotice

    private val _showStatisticsButton =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_STATISTICS_BUTTON, true))
    val showStatisticsButton: LiveData<Boolean> = _showStatisticsButton

    private val _showPaletteIcon =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_PALETTE_ICON, true))
    val showPaletteIcon: LiveData<Boolean> = _showPaletteIcon

    private val _showMoreMenu =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_MORE_MENU, true))
    val showMoreMenu: LiveData<Boolean> = _showMoreMenu

    private val _showInternalCodes =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_INTERNAL_CODES, false))
    val showInternalCodes: LiveData<Boolean> = _showInternalCodes

    private val _showHistoryHeader =
        MutableLiveData(preferences.getBoolean(KEY_SHOW_HISTORY_HEADER, true))
    val showHistoryHeader: LiveData<Boolean> = _showHistoryHeader

    private val _demoMode = MutableLiveData(initialDemoMode)
    val demoMode: LiveData<Boolean> = _demoMode

    private val _statsDialogVisible = MutableLiveData(false)
    val statsDialogVisible: LiveData<Boolean> = _statsDialogVisible

    private val _settingsDialogVisible = MutableLiveData(false)
    val settingsDialogVisible: LiveData<Boolean> = _settingsDialogVisible

    private val _featureFlags = MutableLiveData(loadFeatureFlags())
    val featureFlags: LiveData<Map<String, Boolean>> = _featureFlags

    init {
        migrateBackgroundImageFallback()
        BalanceWidgetProvider.requestUpdate(appContext)
    }

    fun enableNfcForegroundDispatch(activity: Activity) {
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                val nfcIntentFilter = arrayOf(
                    IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                    IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                    IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
                )

                val pendingIntent =
                    PendingIntent.getActivity(
                        activity,
                        0,
                        Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        // NFC dispatch adds EXTRA_TAG to this intent, so immutable cannot be used here.
                        PendingIntent.FLAG_MUTABLE
                    )
                adapter.enableForegroundDispatch(activity, pendingIntent, nfcIntentFilter, null)
            } else {
                _showNoNfcDialog.postValue(true)
            }
        }
    }

    fun disableNfcForegroundDispatch(activity: Activity) {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    fun handleNfcIntent(intent: Intent?, context: Context) {
        if (_demoMode.value == true) {
            _nfcData.value = "デモモード中はNFC読み取りを行いません"
            _isDataRefreshed.value = true
            return
        }
        val action = intent?.action ?: return
        if (action !in NFC_ACTIONS) return

        @Suppress("DEPRECATION")
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        if (tag.id?.size != FELICA_ID_LENGTH || NfcF.get(tag) == null) {
            _nfcData.value = "交通系ICカードとして読み取れませんでした"
            _isDataRefreshed.value = true
            return
        }

        viewModelScope.launch {
            val data = readTagData(tag, context)
            _nfcData.value = data.rawText
            _latestCards.value = data.cards
            if (data.cards.isNotEmpty()) {
                val mergedHistory = mergeHistory(data.cards, loadHistory())
                saveHistory(mergedHistory)
                _readCardIds.value = _readCardIds.value.orEmpty() + data.cardId
                setSelectedCard(data.cardId)
                refreshDerivedState(mergedHistory, data.cardId)
            }
            _isDataRefreshed.value = true
        }
    }

    fun selectCard(cardId: String) {
        setSelectedCard(cardId)
        refreshDerivedState(activeHistory(), cardId)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _selectedHistory.value = filterHistory(activeHistory(), _selectedCardId.value, query)
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAppTitle(title: String) {
        val normalized = title.trim().ifBlank { DEFAULT_APP_TITLE }
        preferences.edit().putString(KEY_APP_TITLE, normalized).apply()
        _appTitle.value = normalized
    }

    fun setAccentColor(hex: String) {
        val normalized = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        if (!Regex("^#[0-9a-fA-F]{6}$").matches(normalized)) return
        preferences.edit().putString(KEY_ACCENT_COLOR, normalized).apply()
        _accentColorHex.value = normalized
    }

    fun setBalanceColor(hex: String) {
        saveColor(KEY_BALANCE_COLOR, hex) { _balanceColorHex.value = it }
    }

    fun setSummaryBackgroundColor(hex: String) {
        saveColor(KEY_SUMMARY_BACKGROUND_COLOR, hex) {
            _summaryBackgroundColorHex.value = it
            preferences.edit().putString(KEY_BALANCE_BACKGROUND_COLOR, it).apply()
            _balanceBackgroundColorHex.value = it
        }
    }

    fun setNoticeBackgroundColor(hex: String) {
        saveColor(KEY_NOTICE_BACKGROUND_COLOR, hex) { _noticeBackgroundColorHex.value = it }
    }

    fun setDeleteButtonColor(hex: String) {
        saveColor(KEY_DELETE_BUTTON_COLOR, hex) { _deleteButtonColorHex.value = it }
    }

    fun setBalanceBackgroundColor(hex: String) {
        saveColor(KEY_BALANCE_BACKGROUND_COLOR, hex) { _balanceBackgroundColorHex.value = it }
    }

    fun setOtherCardBackgroundColor(hex: String) {
        saveColor(KEY_OTHER_CARD_BACKGROUND_COLOR, hex) { _otherCardBackgroundColorHex.value = it }
    }

    fun setWidgetBackgroundColor(hex: String) {
        saveColor(KEY_WIDGET_BACKGROUND_COLOR, hex) {
            _widgetBackgroundColorHex.value = it
            BalanceWidgetProvider.requestUpdate(appContext)
        }
    }

    fun setSummaryBackgroundImageUri(uri: String?) {
        val cardId = _selectedCardId.value ?: return
        if (uri != null && !isTrustedPersistedImageUri(uri)) return
        val backgrounds = loadCardBackgroundImages()
        backgrounds.put(cardId, uri ?: NO_BACKGROUND_IMAGE)
        val editor = preferences.edit().putString(KEY_CARD_BACKGROUND_IMAGES, backgrounds.toString())
        if (uri == null) {
            editor.remove(KEY_SUMMARY_BACKGROUND_IMAGE_URI)
        } else {
            editor.putString(KEY_SUMMARY_BACKGROUND_IMAGE_URI, uri)
        }
        editor.apply()
        _summaryBackgroundImageUri.value = uri
        _defaultBackgroundImageUri.value = uri
        _cardBackgroundImageUris.value = loadCardBackgroundImageUris()
    }

    fun setWidgetBackgroundImageUri(uri: String?) {
        if (uri != null && !isTrustedPersistedImageUri(uri)) return
        preferences.edit().putString(KEY_WIDGET_BACKGROUND_IMAGE_URI, uri).apply()
        _widgetBackgroundImageUri.value = uri
        BalanceWidgetProvider.requestUpdate(appContext)
    }

    fun clearAllBackgroundImages() {
        preferences.edit()
            .remove(KEY_CARD_BACKGROUND_IMAGES)
            .remove(KEY_SUMMARY_BACKGROUND_IMAGE_URI)
            .remove(KEY_WIDGET_BACKGROUND_IMAGE_URI)
            .apply()
        _summaryBackgroundImageUri.value = null
        _defaultBackgroundImageUri.value = null
        _cardBackgroundImageUris.value = emptyMap()
        _widgetBackgroundImageUri.value = null
        BalanceWidgetProvider.requestUpdate(appContext)
    }

    fun setDemoMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        _demoMode.value = enabled
        _readCardIds.value = emptySet()
        _latestCards.value = emptyList()
        _nfcData.value = ""
        _isDataRefreshed.value = false
        refreshDerivedState(activeHistory(), _selectedCardId.value)
    }

    fun setUseSearchIcon(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_USE_SEARCH_ICON, enabled).apply()
        _useSearchIcon.value = enabled
        if (!enabled) _isSearchExpanded.value = false
    }

    fun toggleSearchExpanded() {
        if (_useSearchIcon.value != true) return
        _isSearchExpanded.value = !(_isSearchExpanded.value ?: false)
    }

    fun showSearchDialog() {
        if (_useSearchIcon.value != true) return
        _searchDialogVisible.value = true
    }

    fun dismissSearchDialog() {
        _searchDialogVisible.value = false
    }

    fun setShowLegacySearchBar(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_LEGACY_SEARCH_BAR, show).apply()
        _showLegacySearchBar.value = show
    }

    fun setShowCardBalances(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_CARD_BALANCES, show).apply()
        _showCardBalances.value = show
    }

    fun setUseModernUi(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_USE_MODERN_UI, enabled).apply()
        _useModernUi.value = enabled
    }

    fun setShowBottomTabLabels(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_BOTTOM_TAB_LABELS, show).apply()
        _showBottomTabLabels.value = show
    }

    fun setShowDeleteButton(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_DELETE_BUTTON, show).apply()
        _showDeleteButton.value = show
    }

    fun setShowReadNotice(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_READ_NOTICE, show).apply()
        _showReadNotice.value = show
    }

    fun setShowStatisticsButton(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_STATISTICS_BUTTON, show).apply()
        _showStatisticsButton.value = show
    }

    fun setShowPaletteIcon(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_PALETTE_ICON, show).apply()
        _showPaletteIcon.value = show
    }

    fun setShowMoreMenu(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_MORE_MENU, show).apply()
        _showMoreMenu.value = show
    }

    fun setShowInternalCodes(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_INTERNAL_CODES, show).apply()
        _showInternalCodes.value = show
    }

    fun setShowHistoryHeader(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_HISTORY_HEADER, show).apply()
        _showHistoryHeader.value = show
    }

    fun showStatsDialog() {
        _statsDialogVisible.value = true
    }

    fun dismissStatsDialog() {
        _statsDialogVisible.value = false
    }

    fun showSettingsDialog() {
        _settingsDialogVisible.value = true
    }

    fun dismissSettingsDialog() {
        _settingsDialogVisible.value = false
    }

    fun setFeatureEnabled(key: String, enabled: Boolean) {
        val flags = loadFeatureFlags().toMutableMap()
        flags[key] = enabled
        val obj = JSONObject()
        flags.forEach { (flag, value) -> obj.put(flag, value) }
        preferences.edit().putString(KEY_FEATURE_FLAGS, obj.toString()).apply()
        _featureFlags.value = flags
    }

    fun setCardAlias(cardId: String, alias: String) {
        val aliases = loadAliases().toMutableMap()
        if (alias.isBlank()) {
            aliases.remove(cardId)
        } else {
            aliases[cardId] = alias.trim()
        }
        saveAliases(aliases)
        BalanceWidgetProvider.requestUpdate(appContext)
        refreshDerivedState(activeHistory(), cardId)
    }

    fun updateRecord(originalNumber: String?, updated: Card) {
        if (_demoMode.value == true) return
        val selectedCardId = _selectedCardId.value ?: return
        val updatedHistory = loadHistory().map { card ->
            if (card.resolvedCardId() == selectedCardId && card.number == originalNumber) {
                updated.copy(cardId = selectedCardId, manuallyEdited = true)
            } else {
                card
            }
        }
        saveHistory(updatedHistory)
        refreshDerivedState(updatedHistory, selectedCardId)
    }

    fun exportSelectedJson(): String {
        val array = JSONArray()
        filterHistory(activeHistory(), _selectedCardId.value, _searchQuery.value.orEmpty())
            .forEach { card -> array.put(card.toJson()) }
        return array.toString(2)
    }

    fun exportSelectedCsv(): String {
        val header = listOf(
            "date", "amount", "balance", "action", "device",
            "in_company", "in_line", "in_station", "out_company", "out_line", "out_station",
            "memo", "tags", "internal_code", "number"
        ).joinToString(",")
        val rows = filterHistory(activeHistory(), _selectedCardId.value, _searchQuery.value.orEmpty())
            .joinToString("\n") { card ->
                listOf(
                    card.date,
                    card.amount,
                    card.balance,
                    card.action ?: card.kind,
                    card.device,
                    card.inCompany,
                    card.inLine,
                    card.inStation,
                    card.outCompany,
                    card.outLine,
                    card.outStation,
                    card.memo,
                    card.tags,
                    card.internalCode,
                    card.number
                ).joinToString(",") { it.csvEscape() }
            }
        return "$header\n$rows"
    }

    fun exportSelectedNotionMarkdown(): String {
        val rows = filterHistory(activeHistory(), _selectedCardId.value, _searchQuery.value.orEmpty())
        val header = "| 日付 | 差額 | 残高 | タイトル | 場所 | メモ | タグ |\n|---|---:|---:|---|---|---|---|"
        val body = rows.joinToString("\n") { card ->
            val place = listOf(card.inStation, card.outStation).filterNot { it.isNullOrBlank() }.joinToString(" -> ")
            "| ${card.date.orEmpty()} | ${card.amount.orEmpty()} | ${card.balance.orEmpty()} | ${(card.action ?: card.kind).orEmpty()} | $place | ${card.memo.orEmpty()} | ${card.tags.orEmpty()} |"
        }
        return "$header\n$body"
    }

    fun statsText(): String {
        val rows = filterHistory(activeHistory(), _selectedCardId.value, "")
        val spending = rows.sumOf { card ->
            val amount = card.amount?.toIntOrNull() ?: 0
            if (amount < 0) -amount else 0
        }
        val charges = rows.sumOf { card ->
            val amount = card.amount?.toIntOrNull() ?: 0
            if (amount > 0) amount else 0
        }
        val movements = rows.count { !it.inStation.isNullOrBlank() || !it.outStation.isNullOrBlank() }
        val byMonth = rows.groupBy { it.date?.take(7).orEmpty() }
            .filterKeys { it.isNotBlank() }
            .toSortedMap(compareByDescending { it })
            .entries
            .take(12)
            .joinToString("\n") { (month, records) ->
                val monthSpending = records.sumOf { card ->
                    val amount = card.amount?.toIntOrNull() ?: 0
                    if (amount < 0) -amount else 0
                }
                "$month  支出 ${monthSpending}円 / 利用 ${records.size}件"
            }
        return buildString {
            appendLine("総支出: ${spending}円")
            appendLine("総チャージ: ${charges}円")
            appendLine("利用回数: ${rows.size}件")
            appendLine("移動回数: ${movements}回")
            if (byMonth.isNotBlank()) {
                appendLine()
                appendLine(byMonth)
            }
        }.trim()
    }

    fun exportBackupJson(): String {
        return JSONObject().apply {
            put("schema", 1)
            put("history", JSONArray(preferences.getString(KEY_HISTORY, "[]") ?: "[]"))
            put("cardAliases", JSONObject(preferences.getString(KEY_CARD_ALIASES, "{}") ?: "{}"))
            put("themeMode", _themeMode.value?.name ?: AppThemeMode.AMOLED.name)
            put("appTitle", _appTitle.value ?: DEFAULT_APP_TITLE)
            put("accentColor", _accentColorHex.value ?: DEFAULT_ACCENT_COLOR)
            put("balanceColor", _balanceColorHex.value ?: DEFAULT_BALANCE_COLOR)
            put("summaryBackgroundColor", _summaryBackgroundColorHex.value ?: DEFAULT_SUMMARY_BACKGROUND_COLOR)
            put("noticeBackgroundColor", _noticeBackgroundColorHex.value ?: DEFAULT_NOTICE_BACKGROUND_COLOR)
            put("deleteButtonColor", _deleteButtonColorHex.value ?: DEFAULT_DELETE_BUTTON_COLOR)
            put("balanceBackgroundColor", _balanceBackgroundColorHex.value ?: DEFAULT_BALANCE_BACKGROUND_COLOR)
            put("otherCardBackgroundColor", _otherCardBackgroundColorHex.value ?: DEFAULT_OTHER_CARD_BACKGROUND_COLOR)
            put("widgetBackgroundColor", _widgetBackgroundColorHex.value ?: DEFAULT_WIDGET_BACKGROUND_COLOR)
            put(
                "summaryBackgroundImageUri",
                trustedPersistedImageUriOrNull(preferences.getString(KEY_SUMMARY_BACKGROUND_IMAGE_URI, null))
            )
            put("cardBackgroundImages", loadCardBackgroundImages())
            put("widgetBackgroundImageUri", trustedPersistedImageUriOrNull(_widgetBackgroundImageUri.value))
            put("useSearchIcon", _useSearchIcon.value ?: true)
            put("showLegacySearchBar", _showLegacySearchBar.value ?: false)
            put("showCardBalances", _showCardBalances.value ?: true)
            put("useModernUi", _useModernUi.value ?: true)
            put("showBottomTabLabels", _showBottomTabLabels.value ?: true)
            put("showDeleteButton", _showDeleteButton.value ?: true)
            put("showReadNotice", _showReadNotice.value ?: true)
            put("showStatisticsButton", _showStatisticsButton.value ?: true)
            put("showPaletteIcon", _showPaletteIcon.value ?: true)
            put("showMoreMenu", _showMoreMenu.value ?: true)
            put("showInternalCodes", _showInternalCodes.value ?: false)
            put("showHistoryHeader", _showHistoryHeader.value ?: true)
            put("features", JSONObject(preferences.getString(KEY_FEATURE_FLAGS, "{}") ?: "{}"))
        }.toString(2)
    }

    fun importBackupJson(rawJson: String): Boolean {
        return runCatching {
            require(rawJson.length <= MAX_BACKUP_JSON_LENGTH) { "Backup is too large" }
            val obj = JSONObject(rawJson)
            val history = obj.optJSONArray("history") ?: JSONArray()
            require(history.length() <= MAX_HISTORY_ITEMS) { "Too many history records" }
            val summaryImageUri = trustedPersistedImageUriOrNull(
                obj.optString("summaryBackgroundImageUri").ifBlank { null }
            )
            val widgetImageUri = trustedPersistedImageUriOrNull(
                obj.optString("widgetBackgroundImageUri").ifBlank { null }
            )
            val cardBackgrounds = sanitizeCardBackgroundImages(obj.optJSONObject("cardBackgroundImages"))
            preferences.edit()
                .putString(KEY_HISTORY, history.toString())
                .putString(KEY_CARD_ALIASES, obj.optJSONObject("cardAliases")?.toString() ?: "{}")
                .putString(KEY_THEME_MODE, obj.optString("themeMode", AppThemeMode.AMOLED.name))
                .putString(KEY_APP_TITLE, obj.optString("appTitle", DEFAULT_APP_TITLE))
                .putString(KEY_ACCENT_COLOR, obj.optString("accentColor", DEFAULT_ACCENT_COLOR))
                .putString(KEY_BALANCE_COLOR, obj.optString("balanceColor", DEFAULT_BALANCE_COLOR))
                .putString(KEY_SUMMARY_BACKGROUND_COLOR, obj.optString("summaryBackgroundColor", DEFAULT_SUMMARY_BACKGROUND_COLOR))
                .putString(KEY_NOTICE_BACKGROUND_COLOR, obj.optString("noticeBackgroundColor", DEFAULT_NOTICE_BACKGROUND_COLOR))
                .putString(KEY_DELETE_BUTTON_COLOR, obj.optString("deleteButtonColor", DEFAULT_DELETE_BUTTON_COLOR))
                .putString(KEY_BALANCE_BACKGROUND_COLOR, obj.optString("balanceBackgroundColor", DEFAULT_BALANCE_BACKGROUND_COLOR))
                .putString(KEY_OTHER_CARD_BACKGROUND_COLOR, obj.optString("otherCardBackgroundColor", DEFAULT_OTHER_CARD_BACKGROUND_COLOR))
                .putString(KEY_WIDGET_BACKGROUND_COLOR, obj.optString("widgetBackgroundColor", DEFAULT_WIDGET_BACKGROUND_COLOR))
                .putString(KEY_SUMMARY_BACKGROUND_IMAGE_URI, summaryImageUri)
                .putString(KEY_CARD_BACKGROUND_IMAGES, cardBackgrounds.toString())
                .putString(KEY_WIDGET_BACKGROUND_IMAGE_URI, widgetImageUri)
                .putBoolean(KEY_USE_SEARCH_ICON, obj.optBoolean("useSearchIcon", true))
                .putBoolean(KEY_SHOW_LEGACY_SEARCH_BAR, obj.optBoolean("showLegacySearchBar", false))
                .putBoolean(KEY_SHOW_CARD_BALANCES, obj.optBoolean("showCardBalances", true))
                .putBoolean(KEY_USE_MODERN_UI, obj.optBoolean("useModernUi", true))
                .putBoolean(KEY_SHOW_BOTTOM_TAB_LABELS, obj.optBoolean("showBottomTabLabels", true))
                .putBoolean(KEY_SHOW_DELETE_BUTTON, obj.optBoolean("showDeleteButton", true))
                .putBoolean(KEY_SHOW_READ_NOTICE, obj.optBoolean("showReadNotice", true))
                .putBoolean(KEY_SHOW_STATISTICS_BUTTON, obj.optBoolean("showStatisticsButton", true))
                .putBoolean(KEY_SHOW_PALETTE_ICON, obj.optBoolean("showPaletteIcon", true))
                .putBoolean(KEY_SHOW_MORE_MENU, obj.optBoolean("showMoreMenu", true))
                .putBoolean(KEY_SHOW_INTERNAL_CODES, obj.optBoolean("showInternalCodes", false))
                .putBoolean(KEY_SHOW_HISTORY_HEADER, obj.optBoolean("showHistoryHeader", true))
                .putString(KEY_FEATURE_FLAGS, obj.optJSONObject("features")?.toString() ?: "{}")
                .apply()
            _themeMode.value = AppThemeMode.fromName(preferences.getString(KEY_THEME_MODE, null))
            _appTitle.value = preferences.getString(KEY_APP_TITLE, DEFAULT_APP_TITLE) ?: DEFAULT_APP_TITLE
            _accentColorHex.value = preferences.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR
            _balanceColorHex.value = preferences.getString(KEY_BALANCE_COLOR, DEFAULT_BALANCE_COLOR) ?: DEFAULT_BALANCE_COLOR
            _summaryBackgroundColorHex.value = preferences.getString(KEY_SUMMARY_BACKGROUND_COLOR, DEFAULT_SUMMARY_BACKGROUND_COLOR) ?: DEFAULT_SUMMARY_BACKGROUND_COLOR
            _noticeBackgroundColorHex.value = preferences.getString(KEY_NOTICE_BACKGROUND_COLOR, DEFAULT_NOTICE_BACKGROUND_COLOR) ?: DEFAULT_NOTICE_BACKGROUND_COLOR
            _deleteButtonColorHex.value = preferences.getString(KEY_DELETE_BUTTON_COLOR, DEFAULT_DELETE_BUTTON_COLOR) ?: DEFAULT_DELETE_BUTTON_COLOR
            _balanceBackgroundColorHex.value = preferences.getString(KEY_BALANCE_BACKGROUND_COLOR, DEFAULT_BALANCE_BACKGROUND_COLOR) ?: DEFAULT_BALANCE_BACKGROUND_COLOR
            _otherCardBackgroundColorHex.value = preferences.getString(KEY_OTHER_CARD_BACKGROUND_COLOR, DEFAULT_OTHER_CARD_BACKGROUND_COLOR) ?: DEFAULT_OTHER_CARD_BACKGROUND_COLOR
            _widgetBackgroundColorHex.value = preferences.getString(KEY_WIDGET_BACKGROUND_COLOR, DEFAULT_WIDGET_BACKGROUND_COLOR) ?: DEFAULT_WIDGET_BACKGROUND_COLOR
            _summaryBackgroundImageUri.value = cardBackgroundUriFor(_selectedCardId.value)
            _widgetBackgroundImageUri.value = trustedPersistedImageUriOrNull(
                preferences.getString(KEY_WIDGET_BACKGROUND_IMAGE_URI, null)
            )
            _showInternalCodes.value = preferences.getBoolean(KEY_SHOW_INTERNAL_CODES, false)
            _showHistoryHeader.value = preferences.getBoolean(KEY_SHOW_HISTORY_HEADER, true)
            _useSearchIcon.value = preferences.getBoolean(KEY_USE_SEARCH_ICON, true)
            _showLegacySearchBar.value = preferences.getBoolean(KEY_SHOW_LEGACY_SEARCH_BAR, false)
            _showCardBalances.value = preferences.getBoolean(KEY_SHOW_CARD_BALANCES, true)
            _useModernUi.value = preferences.getBoolean(KEY_USE_MODERN_UI, true)
            _showBottomTabLabels.value = preferences.getBoolean(KEY_SHOW_BOTTOM_TAB_LABELS, true)
            _showDeleteButton.value = preferences.getBoolean(KEY_SHOW_DELETE_BUTTON, true)
            _showReadNotice.value = preferences.getBoolean(KEY_SHOW_READ_NOTICE, true)
            _showStatisticsButton.value = preferences.getBoolean(KEY_SHOW_STATISTICS_BUTTON, true)
            _showPaletteIcon.value = preferences.getBoolean(KEY_SHOW_PALETTE_ICON, true)
            _showMoreMenu.value = preferences.getBoolean(KEY_SHOW_MORE_MENU, true)
            _featureFlags.value = loadFeatureFlags()
            _defaultBackgroundImageUri.value = trustedPersistedImageUriOrNull(
                preferences.getString(KEY_SUMMARY_BACKGROUND_IMAGE_URI, null)
            )
            _cardBackgroundImageUris.value = loadCardBackgroundImageUris()
            refreshDerivedState(activeHistory(), _selectedCardId.value)
            BalanceWidgetProvider.requestUpdate(appContext)
        }.isSuccess
    }

    fun clearSelectedCardHistory() {
        if (_demoMode.value == true) return
        val selectedCardId = _selectedCardId.value ?: return
        val remainingHistory = loadHistory().filterNot { it.resolvedCardId() == selectedCardId }
        saveHistory(remainingHistory)
        _readCardIds.value = _readCardIds.value.orEmpty() - selectedCardId
        _latestCards.value = emptyList()
        _nfcData.value = ""
        _isDataRefreshed.value = false
        BalanceWidgetProvider.requestUpdate(appContext)
        refreshDerivedState(remainingHistory, buildSummaries(remainingHistory).firstOrNull()?.cardId)
    }

    fun clearAllHistory() {
        if (_demoMode.value == true) return
        preferences.edit()
            .remove(KEY_HISTORY)
            .remove(KEY_SELECTED_CARD_ID)
            .apply()
        _history.value = emptyList()
        _cardSummaries.value = emptyList()
        _selectedCardId.value = null
        _selectedHistory.value = emptyList()
        _latestCards.value = emptyList()
        _readCardIds.value = emptySet()
        _nfcData.value = ""
        _isDataRefreshed.value = false
    }

    private suspend fun readTagData(tag: Tag, context: Context): ReadResult =
        withContext(Dispatchers.IO) {
            val id = tag.id
            if (id.size != FELICA_ID_LENGTH) {
                return@withContext ReadResult("", emptyList(), "交通系ICカードとして読み取れませんでした")
            }
            val cardId = id.toHexString()
            val felica = NfcF.get(tag)
                ?: return@withContext ReadResult(cardId, emptyList(), "交通系ICカードとして読み取れませんでした")
            try {
                felica.connect()
                val readChunks = readHistoryChunks(felica, id, context, cardId)
                val cards = withCalculatedAmounts(readChunks.cards)
                ReadResult(
                    cardId = cardId,
                    cards = cards,
                    rawText = buildRawText(cardId, cards, readChunks.rawResponses)
                )
            } catch (_: Exception) {
                ReadResult(
                    cardId = cardId,
                    cards = emptyList(),
                    rawText = "NFCタグの読み取りに失敗しました"
                )
            } finally {
                runCatching { felica.close() }
            }
        }

    private fun readHistoryChunks(
        felica: NfcF,
        id: ByteArray,
        context: Context,
        cardId: String
    ): ReadChunks {
        val cards = mutableListOf<Card>()
        val rawResponses = mutableListOf<ByteArray>()

        for (startBlock in 0 until MAX_READ_BLOCKS step READ_BLOCK_CHUNK_SIZE) {
            val requestSize = minOf(READ_BLOCK_CHUNK_SIZE, MAX_READ_BLOCKS - startBlock)
            val response = try {
                felica.transceive(SuicaReader.readWithoutEncryption(id, startBlock, requestSize))
            } catch (e: Exception) {
                if (startBlock == 0) throw e
                break
            }

            if (!isValidHistoryResponse(response, requestSize)) {
                if (startBlock == 0) {
                    throw IllegalStateException("Invalid FeliCa history response")
                }
                break
            }

            rawResponses.add(response)
            val chunkCards = fromData(response, context, cardId)
            cards.addAll(chunkCards)
            if ((response[12].toInt() and 0xff) == 0) break
        }

        return ReadChunks(
            cards = cards.distinctBy { listOf(it.number, it.date).joinToString("|") },
            rawResponses = rawResponses
        )
    }

    private fun fromData(data: ByteArray, context: Context, cardId: String): List<Card> {
        if (data.size < HISTORY_RESPONSE_HEADER_SIZE) return emptyList()

        val size: Int = data[12].toInt() and 0xff
        if (size > MAX_HISTORY_RECORDS_PER_RESPONSE || data.size < HISTORY_RESPONSE_HEADER_SIZE + size * HISTORY_BLOCK_SIZE) {
            return emptyList()
        }
        val cards = mutableListOf<Card>()
        for (i in 0 until size) {
            val offset = HISTORY_RESPONSE_HEADER_SIZE + i * HISTORY_BLOCK_SIZE
            val felica = runCatching { SuicaReader.parse(data, offset) }.getOrNull() ?: continue
            if (!felica.hasPlausibleHistoryValues()) continue
            val card: Card = Card.getCard(context, felica)
            card.cardId = cardId
            cards.add(card)
        }
        return cards
    }

    private fun isValidHistoryResponse(response: ByteArray, requestedBlocks: Int): Boolean {
        if (response.size < HISTORY_RESPONSE_HEADER_SIZE) return false
        if ((response[0].toInt() and 0xff) != response.size || (response[1].toInt() and 0xff) != 0x07) return false
        if (response[10].toInt() != 0 || response[11].toInt() != 0) return false
        val blockCount = response[12].toInt() and 0xff
        if (blockCount > requestedBlocks || blockCount > MAX_HISTORY_RECORDS_PER_RESPONSE) return false
        return response.size >= HISTORY_RESPONSE_HEADER_SIZE + blockCount * HISTORY_BLOCK_SIZE
    }

    private fun withCalculatedAmounts(cards: List<Card>): List<Card> {
        return cards.mapIndexed { index, card ->
            val current = card.balance?.toIntOrNull()
            val previous = cards.getOrNull(index + 1)?.balance?.toIntOrNull()
            if (current != null && previous != null) {
                card.amount = (current - previous).toString()
            }
            card
        }
    }

    private fun buildRawText(cardId: String, cards: List<Card>, rawResponses: List<ByteArray>): String {
        val sb = StringBuilder()
        sb.appendLine("カードID: ${cardId.maskForDisplay()}")
        sb.appendLine("読み取り件数: ${cards.size}件")
        cards.forEachIndexed { index, card ->
            sb.appendLine("=== %02d ===".format(index + 1))
            sb.appendLine("端末種別: ${card.device.orEmpty()}")
            sb.appendLine("処理: ${card.action ?: card.kind ?: ""}")
            sb.appendLine("日付: ${card.date.orEmpty()}")
            val inPlace = listOf(card.inCompany, card.inLine, card.inStation).readableJoin().takeIf { it != "-" }
            val outPlace = listOf(card.outCompany, card.outLine, card.outStation).readableJoin().takeIf { it != "-" }
            if (inPlace != null || outPlace != null) {
                inPlace?.let { sb.appendLine("入場: $it") }
                outPlace?.let { sb.appendLine("出場: $it") }
            } else {
                sb.appendLine("種別: ${card.activityLabel()}")
            }
            sb.appendLine("差額: ${card.amount.orEmpty()}")
            sb.appendLine("残高: ${card.balance.orEmpty()}")
            sb.appendLine()
        }
        sb.appendLine("BIN:")
        rawResponses.forEachIndexed { index, data ->
            sb.appendLine("[chunk ${index + 1}] ${data.joinToString(" ") { "%02x".format(it) }}")
        }
        return sb.toString().trim()
    }

    private fun mergeHistory(newCards: List<Card>, savedCards: List<Card>): List<Card> {
        val savedByRecord = savedCards.associateBy { it.recordKey() }
        val mergedNewCards = newCards.map { fresh ->
            savedByRecord[fresh.recordKey()]?.let { saved -> fresh.withUserEditsFrom(saved) } ?: fresh
        }
        return (mergedNewCards + savedCards)
            .distinctBy { it.recordKey() }
            .take(MAX_HISTORY_ITEMS)
    }

    private fun Card.recordKey(): String =
        listOf(resolvedCardId(), number ?: date.orEmpty()).joinToString("|")

    private fun Card.withUserEditsFrom(saved: Card): Card {
        val merged = copy(
            memo = saved.memo ?: memo,
            tags = saved.tags ?: tags
        )
        if (!saved.manuallyEdited) return merged
        return merged.copy(
            date = saved.date,
            amount = saved.amount,
            kind = saved.kind,
            device = saved.device,
            action = saved.action,
            inCompany = saved.inCompany,
            inLine = saved.inLine,
            inStation = saved.inStation,
            outCompany = saved.outCompany,
            outLine = saved.outLine,
            outStation = saved.outStation,
            balance = saved.balance,
            manuallyEdited = true
        )
    }

    private fun refreshDerivedState(allCards: List<Card>, preferredCardId: String?) {
        val summaries = buildSummaries(allCards)
        val selectedCardId = preferredCardId
            ?.takeIf { id -> summaries.any { it.cardId == id } }
            ?: summaries.firstOrNull()?.cardId

        _history.value = allCards
        _cardSummaries.value = summaries
        _selectedCardId.value = selectedCardId
        _selectedHistory.value = filterHistory(allCards, selectedCardId, _searchQuery.value.orEmpty())
        _summaryBackgroundImageUri.value = cardBackgroundUriFor(selectedCardId)
        setSelectedCard(selectedCardId)
    }

    private fun setSelectedCard(cardId: String?) {
        if (cardId == null) {
            preferences.edit().remove(KEY_SELECTED_CARD_ID).apply()
        } else {
            preferences.edit().putString(KEY_SELECTED_CARD_ID, cardId).apply()
        }
        _selectedCardId.value = cardId
    }

    private fun buildSummaries(cards: List<Card>): List<SuicaCardSummary> {
        val aliases = loadAliases()
        return cards
            .groupBy { it.resolvedCardId() }
            .map { (cardId, records) ->
                SuicaCardSummary(
                    cardId = cardId,
                    title = aliases[cardId] ?: cardId.displayTitle(records),
                    latestRecord = records.first(),
                    recordCount = records.size
                )
            }
            .sortedByDescending { it.latestRecord.number?.toIntOrNull() ?: 0 }
    }

    private fun filterHistory(cards: List<Card>, cardId: String?, query: String): List<Card> {
        val base = cardId?.let { id -> cards.filter { it.resolvedCardId() == id } } ?: emptyList()
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return base
        return base.filter { card ->
            listOf(
                card.date, card.amount, card.balance, card.kind, card.device, card.action,
                card.inCompany, card.inLine, card.inStation, card.outCompany, card.outLine,
                card.outStation, card.memo, card.tags
            ).any { it?.contains(normalizedQuery, ignoreCase = true) == true }
        }
    }

    private fun loadHistory(): List<Card> {
        val rawJson = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            List(minOf(array.length(), MAX_HISTORY_ITEMS)) { index ->
                array.getJSONObject(index).toCard()
            }
        }.getOrDefault(emptyList())
    }

    private fun activeHistory(): List<Card> =
        if (_demoMode.value == true) demoHistory() else loadHistory()

    private fun saveHistory(cards: List<Card>) {
        val array = JSONArray()
        cards.forEach { card -> array.put(card.toJson()) }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
        BalanceWidgetProvider.requestUpdate(appContext)
    }

    private fun loadAliases(): Map<String, String> {
        val rawJson = preferences.getString(KEY_CARD_ALIASES, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(rawJson)
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        }.getOrDefault(emptyMap())
    }

    private fun saveAliases(aliases: Map<String, String>) {
        val obj = JSONObject()
        aliases.forEach { (key, value) -> obj.put(key, value) }
        preferences.edit().putString(KEY_CARD_ALIASES, obj.toString()).apply()
    }

    private fun saveColor(key: String, hex: String, onSaved: (String) -> Unit) {
        val normalized = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        if (!Regex("^#[0-9a-fA-F]{6}$").matches(normalized)) return
        preferences.edit().putString(key, normalized).apply()
        onSaved(normalized)
    }

    private fun loadFeatureFlags(): Map<String, Boolean> {
        val defaults = linkedMapOf(
            "manual_edit" to true,
            "memo_tags" to true,
            "search_filter" to true,
            "statistics" to true,
            "exports" to true,
            "widget" to true,
            "card_alias" to true
        )
        val rawJson = preferences.getString(KEY_FEATURE_FLAGS, null) ?: return defaults
        return runCatching {
            val obj = JSONObject(rawJson)
            defaults.mapValues { (key, defaultValue) -> obj.optBoolean(key, defaultValue) }
        }.getOrDefault(defaults)
    }

    private fun Card.toJson(): JSONObject {
        return JSONObject().apply {
            putNullable("cardId", cardId)
            putNullable("date", date)
            putNullable("number", number)
            putNullable("payment", payment)
            putNullable("amount", amount)
            putNullable("kind", kind)
            putNullable("device", device)
            putNullable("action", action)
            putNullable("inLine", inLine)
            putNullable("inStation", inStation)
            putNullable("outLine", outLine)
            putNullable("outStation", outStation)
            putNullable("balance", balance)
            putNullable("inCompany", inCompany)
            putNullable("outCompany", outCompany)
            putNullable("memo", memo)
            putNullable("tags", tags)
            putNullable("internalCode", internalCode)
            put("manuallyEdited", manuallyEdited)
        }
    }

    private fun JSONObject.toCard(): Card {
        return Card(
            cardId = nullableString("cardId"),
            date = nullableString("date"),
            number = nullableString("number"),
            payment = nullableString("payment"),
            amount = nullableString("amount"),
            kind = nullableString("kind"),
            device = nullableString("device"),
            action = nullableString("action"),
            inLine = nullableString("inLine"),
            inStation = nullableString("inStation"),
            outLine = nullableString("outLine"),
            outStation = nullableString("outStation"),
            balance = nullableString("balance"),
            inCompany = nullableString("inCompany"),
            outCompany = nullableString("outCompany"),
            memo = nullableString("memo"),
            tags = nullableString("tags"),
            internalCode = nullableString("internalCode"),
            manuallyEdited = optBoolean("manuallyEdited", false)
        )
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }

    private fun Card.resolvedCardId(): String = cardId ?: LEGACY_CARD_ID

    private fun loadCardBackgroundImages(): JSONObject = sanitizeCardBackgroundImages(
        runCatching { JSONObject(preferences.getString(KEY_CARD_BACKGROUND_IMAGES, "{}") ?: "{}") }.getOrNull()
    )

    private fun loadCardBackgroundImageUris(): Map<String, String> {
        val images = loadCardBackgroundImages()
        return images.keys().asSequence().mapNotNull { cardId ->
            trustedPersistedImageUriOrNull(images.optString(cardId))?.let { cardId to it }
        }.toMap()
    }

    private fun cardBackgroundUriFor(cardId: String?): String? {
        if (cardId == null) return null
        val saved = loadCardBackgroundImages().optString(cardId)
        return when {
            saved == NO_BACKGROUND_IMAGE -> null
            saved.isNotBlank() -> trustedPersistedImageUriOrNull(saved)
            else -> trustedPersistedImageUriOrNull(
                preferences.getString(KEY_SUMMARY_BACKGROUND_IMAGE_URI, null)
            )
        }
    }

    private fun migrateBackgroundImageFallback() {
        if (trustedPersistedImageUriOrNull(
                preferences.getString(KEY_SUMMARY_BACKGROUND_IMAGE_URI, null)
            ) != null
        ) return
        val fallback = loadCardBackgroundImages().keys().asSequence()
            .mapNotNull { cardId -> trustedPersistedImageUriOrNull(loadCardBackgroundImages().optString(cardId)) }
            .firstOrNull()
            ?: return
        preferences.edit().putString(KEY_SUMMARY_BACKGROUND_IMAGE_URI, fallback).apply()
        _defaultBackgroundImageUri.value = fallback
    }

    private fun sanitizeCardBackgroundImages(source: JSONObject?): JSONObject {
        val sanitized = JSONObject()
        source?.keys()?.forEach { cardId ->
            if (!CARD_ID_PATTERN.matches(cardId)) return@forEach
            val uri = source.optString(cardId)
            when {
                uri == NO_BACKGROUND_IMAGE -> sanitized.put(cardId, uri)
                trustedPersistedImageUriOrNull(uri) != null -> sanitized.put(cardId, uri)
            }
        }
        return sanitized
    }

    private fun trustedPersistedImageUriOrNull(rawUri: String?): String? {
        if (rawUri.isNullOrBlank()) return null
        return rawUri.takeIf(::isTrustedPersistedImageUri)
    }

    private fun isTrustedPersistedImageUri(rawUri: String): Boolean = runCatching {
        val uri = Uri.parse(rawUri)
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank())
        appContext.contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.uri == uri
        }
    }.getOrDefault(false)

    private fun demoHistory(): List<Card> = listOf(
        Card(
            cardId = "01010112951DDA13",
            date = "2026/08/09",
            number = "9003",
            amount = "-240",
            kind = "JR",
            device = "改札機",
            action = "運賃支払",
            inCompany = "JR西日本",
            inLine = "大阪環状線",
            inStation = "大阪",
            outCompany = "JR西日本",
            outLine = "大阪環状線",
            outStation = "天王寺",
            balance = "2,760"
        ),
        Card(
            cardId = "01010112951DDA13",
            date = "2026/08/08",
            number = "9002",
            amount = "1000",
            kind = "チャージ",
            device = "券売機",
            action = "チャージ",
            inCompany = "JR西日本",
            inLine = "大阪環状線",
            inStation = "大阪",
            balance = "3,000"
        ),
        Card(
            cardId = "02020222AABBCCDD",
            date = "2026/08/07",
            number = "8101",
            amount = "-190",
            kind = "公営/私鉄",
            device = "改札機",
            action = "運賃支払",
            inCompany = "Osaka Metro",
            inLine = "谷町線",
            inStation = "野江内代",
            outCompany = "Osaka Metro",
            outLine = "谷町線",
            outStation = "東梅田",
            balance = "1,320"
        ),
        Card(
            cardId = "03030333EEFF1020",
            date = "2026/08/06",
            number = "7201",
            amount = "-178",
            kind = "JR",
            device = "改札機",
            action = "運賃支払",
            inCompany = "JR東日本",
            inLine = "中央線",
            inStation = "東京",
            outCompany = "JR東日本",
            outLine = "中央線",
            outStation = "御茶ノ水",
            balance = "4,680"
        )
    )

    private fun String.displayTitle(records: List<Card>): String {
        return if (this == LEGACY_CARD_ID) {
            "保存済みカード"
        } else {
            "${records.inferTransitBrand()} ${maskForDisplay()}"
        }
    }

    private fun List<Card>.inferTransitBrand(): String {
        val text = flatMap { card ->
            listOf(card.inCompany, card.outCompany, card.inLine, card.outLine)
        }.joinToString(" ")
        return when {
            "JR西日本" in text || "Osaka Metro" in text -> "ICOCA/PiTaPa系"
            "JR東日本" in text -> "Suica/PASMO系"
            "JR東海" in text -> "TOICA/manaca系"
            "JR九州" in text -> "SUGOCA/nimoca系"
            else -> "交通系IC"
        }
    }

    private fun String.maskForDisplay(): String {
        return if (length <= 4) this else "****${takeLast(4)}"
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            String.format(Locale.US, "%02X", byte.toInt() and 0xff)
        }
    }

    private fun String?.csvEscape(): String {
        val raw = this.orEmpty()
        return "\"${raw.replace("\"", "\"\"")}\""
    }

    private fun List<String?>.readableJoin(): String =
        filterNot { it.isNullOrBlank() }.joinToString(" / ").ifBlank { "-" }

    private fun Card.activityLabel(): String {
        val text = listOf(action, kind, device).joinToString(" ")
        return when {
            "チャージ" in text || (amount?.toIntOrNull() ?: 0) > 0 -> "チャージ"
            "物販" in text -> "物販"
            "バス" in text -> "バス"
            else -> "利用"
        }
    }

    private data class ReadResult(
        val cardId: String,
        val cards: List<Card>,
        val rawText: String
    )

    private data class ReadChunks(
        val cards: List<Card>,
        val rawResponses: List<ByteArray>
    )

    companion object {
        private const val KEY_HISTORY = "history"
        private const val KEY_SELECTED_CARD_ID = "selected_card_id"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CARD_ALIASES = "card_aliases"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_BALANCE_COLOR = "balance_color"
        private const val KEY_SUMMARY_BACKGROUND_COLOR = "summary_background_color"
        private const val KEY_NOTICE_BACKGROUND_COLOR = "notice_background_color"
        private const val KEY_DELETE_BUTTON_COLOR = "delete_button_color"
        private const val KEY_BALANCE_BACKGROUND_COLOR = "balance_background_color"
        private const val KEY_OTHER_CARD_BACKGROUND_COLOR = "other_card_background_color"
        private const val KEY_WIDGET_BACKGROUND_COLOR = "widget_background_color"
        private const val KEY_SUMMARY_BACKGROUND_IMAGE_URI = "summary_background_image_uri"
        private const val KEY_CARD_BACKGROUND_IMAGES = "card_background_images"
        private const val KEY_WIDGET_BACKGROUND_IMAGE_URI = "widget_background_image_uri"
        private const val KEY_APP_TITLE = "app_title"
        private const val KEY_USE_SEARCH_ICON = "use_search_icon"
        private const val KEY_SHOW_LEGACY_SEARCH_BAR = "show_legacy_search_bar"
        private const val KEY_SHOW_CARD_BALANCES = "show_card_balances"
        private const val KEY_USE_MODERN_UI = "use_modern_ui"
        private const val KEY_SHOW_BOTTOM_TAB_LABELS = "show_bottom_tab_labels"
        private const val KEY_SHOW_DELETE_BUTTON = "show_delete_button"
        private const val KEY_SHOW_READ_NOTICE = "show_read_notice"
        private const val KEY_SHOW_STATISTICS_BUTTON = "show_statistics_button"
        private const val KEY_SHOW_PALETTE_ICON = "show_palette_icon"
        private const val KEY_SHOW_MORE_MENU = "show_more_menu"
        private const val KEY_SHOW_INTERNAL_CODES = "show_internal_codes"
        private const val KEY_SHOW_HISTORY_HEADER = "show_history_header"
        private const val KEY_DEMO_MODE = "demo_mode"
        private const val KEY_FEATURE_FLAGS = "feature_flags"
        private const val DEFAULT_ACCENT_COLOR = "#8AD7C8"
        private const val DEFAULT_BALANCE_COLOR = "#8AD7C8"
        private const val DEFAULT_SUMMARY_BACKGROUND_COLOR = "#103F3A"
        private const val DEFAULT_NOTICE_BACKGROUND_COLOR = "#174F47"
        private const val DEFAULT_DELETE_BUTTON_COLOR = "#533232"
        private const val DEFAULT_BALANCE_BACKGROUND_COLOR = "#0A2528"
        private const val DEFAULT_OTHER_CARD_BACKGROUND_COLOR = "#101C24"
        private const val DEFAULT_WIDGET_BACKGROUND_COLOR = "#000000"
        private const val DEFAULT_APP_TITLE = "SuicaNFC KD"
        private const val LEGACY_CARD_ID = "legacy"
        private const val NO_BACKGROUND_IMAGE = "__none__"
        private val CARD_ID_PATTERN = Regex("(?:[0-9A-Fa-f]{8,64}|legacy)")
        private const val READ_BLOCK_CHUNK_SIZE = 10
        private const val MAX_READ_BLOCKS = 50
        private const val MAX_HISTORY_ITEMS = 300
        private const val MAX_BACKUP_JSON_LENGTH = 2 * 1024 * 1024
        private const val FELICA_ID_LENGTH = 8
        private const val HISTORY_RESPONSE_HEADER_SIZE = 13
        private const val HISTORY_BLOCK_SIZE = 16
        private const val MAX_HISTORY_RECORDS_PER_RESPONSE = 10
        private val NFC_ACTIONS = setOf(
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED
        )
    }
}

class TopScreenViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TopScreenViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TopScreenViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
