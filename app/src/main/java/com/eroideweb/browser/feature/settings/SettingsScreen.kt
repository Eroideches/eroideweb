package com.eroideweb.browser.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.R
import com.eroideweb.browser.core.browsingdata.ClearRange
import com.eroideweb.browser.core.settings.AppTheme
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.feature.browser.BrowserActions
import com.eroideweb.browser.ui.icons.EroideIcons
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Impostazioni a schermo intero. Qui sono finite le preferenze che prima
 * stavano nel menu ⋮ della barra (posizione barra, motore, blocco pubblicità).
 *
 * La navigazione tra pagine è nel ViewModel: il tasto Indietro di sistema
 * passa da [SettingsViewModel.navigateUp] (gestito dal browser).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            SettingsTopBar(
                title = stringResource(pageTitle(state.page)),
                isRoot = state.page == SettingsPage.ROOT,
                onBack = { if (!viewModel.navigateUp()) onDismiss() },
                onClose = onDismiss,
            )

            AnimatedContent(
                targetState = state.page,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(tween(220)) { width -> if (forward) width / 4 else -width / 4 } +
                        fadeIn(tween(220))
                    val exit = slideOutHorizontally(tween(180)) { width -> if (forward) -width / 4 else width / 4 } +
                        fadeOut(tween(180))
                    enter togetherWith exit
                },
                label = "settings-page",
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    SettingsPage.ROOT -> RootPage(state, viewModel)
                    SettingsPage.SEARCH_ENGINE -> SearchEnginePage(state, viewModel)
                    SettingsPage.TEXT_SIZE -> TextSizePage(state, viewModel)
                    SettingsPage.ADBLOCK_SITES -> AdBlockSitesPage(state, viewModel)
                    SettingsPage.CLEAR_DATA -> ClearDataPage(state, viewModel)
                    SettingsPage.ABOUT -> AboutPage()
                }
            }
        }
    }
}

private fun pageTitle(page: SettingsPage): Int = when (page) {
    SettingsPage.ROOT -> R.string.settings_title
    SettingsPage.SEARCH_ENGINE -> R.string.settings_search_engine
    SettingsPage.TEXT_SIZE -> R.string.settings_text_size
    SettingsPage.ADBLOCK_SITES -> R.string.settings_adblock_sites
    SettingsPage.CLEAR_DATA -> R.string.clear_data_title
    SettingsPage.ABOUT -> R.string.settings_about
}

@Composable
private fun SettingsTopBar(
    title: String,
    isRoot: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        if (!isRoot) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_close))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = state.settings
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.isPrivate) {
            item {
                SettingsParagraph(title = null, body = stringResource(R.string.settings_private_notice))
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_section_general)) }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_search_engine),
                subtitle = settings.searchEngine.displayName,
                icon = Icons.Filled.Search,
                onClick = { viewModel.openPage(SettingsPage.SEARCH_ENGINE) },
            )
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_toolbar_position),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    ToolbarPosition.entries.forEachIndexed { index, position ->
                        SegmentedButton(
                            selected = settings.toolbarPosition == position,
                            onClick = { viewModel.setToolbarPosition(position) },
                            shape = SegmentedButtonDefaults.itemShape(index, ToolbarPosition.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (position) {
                                        ToolbarPosition.TOP -> R.string.toolbar_top
                                        ToolbarPosition.BOTTOM -> R.string.toolbar_bottom
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (!state.isPrivate) {
            item { DefaultBrowserRow() }
            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_restore_tabs),
                    subtitle = stringResource(R.string.settings_restore_tabs_summary),
                    icon = EroideIcons.Tab,
                    checked = settings.restoreTabs,
                    onCheckedChange = viewModel::setRestoreTabs,
                )
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_section_appearance)) }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    AppTheme.entries.forEachIndexed { index, theme ->
                        SegmentedButton(
                            selected = settings.appTheme == theme,
                            onClick = { viewModel.setAppTheme(theme) },
                            shape = SegmentedButtonDefaults.itemShape(index, AppTheme.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (theme) {
                                        AppTheme.SYSTEM -> R.string.theme_system
                                        AppTheme.LIGHT -> R.string.theme_light
                                        AppTheme.DARK -> R.string.theme_dark
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_darken_pages),
                subtitle = stringResource(R.string.settings_darken_pages_summary),
                icon = EroideIcons.DarkMode,
                checked = settings.darkenWebPages,
                onCheckedChange = viewModel::setDarkenWebPages,
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_text_size),
                subtitle = stringResource(R.string.settings_text_size_value, settings.textZoom),
                icon = EroideIcons.TextFields,
                onClick = { viewModel.openPage(SettingsPage.TEXT_SIZE) },
            )
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_section_privacy)) }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_adblock),
                subtitle = state.adBlockRuleCount?.let {
                    stringResource(R.string.settings_adblock_rules, numberFormat.format(it))
                } ?: stringResource(R.string.settings_adblock_loading),
                icon = EroideIcons.Shield,
                checked = settings.adBlockEnabled,
                onCheckedChange = viewModel::setAdBlockEnabled,
            )
        }
        item {
            val count = settings.adBlockDisabledSites.size
            SettingsRow(
                title = stringResource(R.string.settings_adblock_sites),
                subtitle = if (count == 0) {
                    stringResource(R.string.settings_adblock_sites_none)
                } else {
                    stringResource(R.string.settings_adblock_sites_count, count)
                },
                icon = EroideIcons.Policy,
                onClick = { viewModel.openPage(SettingsPage.ADBLOCK_SITES) },
            )
        }

        item {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_third_party_cookies),
                subtitle = stringResource(R.string.settings_third_party_cookies_summary),
                icon = EroideIcons.Cookie,
                checked = settings.allowThirdPartyCookies,
                onCheckedChange = viewModel::setAllowThirdPartyCookies,
            )
        }
        if (viewModel.canClearData) {
            item {
                SettingsRow(
                    title = stringResource(R.string.clear_data_title),
                    icon = EroideIcons.Delete,
                    onClick = { viewModel.openPage(SettingsPage.CLEAR_DATA) },
                )
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_section_about)) }
        item {
            val context = LocalContext.current
            SettingsRow(
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version, BrowserActions.versionName(context)),
                icon = EroideIcons.Info,
                onClick = { viewModel.openPage(SettingsPage.ABOUT) },
            )
        }
    }
}

@Composable
private fun SearchEnginePage(state: SettingsUiState, viewModel: SettingsViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(SearchEngine.entries, key = { it.id }) { engine ->
            SettingsRadioRow(
                title = engine.displayName,
                selected = state.settings.searchEngine == engine,
                onSelect = { viewModel.setSearchEngine(engine) },
            )
        }
    }
}

@Composable
private fun TextSizePage(state: SettingsUiState, viewModel: SettingsViewModel) {
    val zoom = state.settings.textZoom
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.settings_text_size_value, zoom),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Slider(
            value = zoom.toFloat(),
            onValueChange = { value -> viewModel.setTextZoom((value / 10f).roundToInt() * 10) },
            valueRange = BrowserSettings.MIN_TEXT_ZOOM.toFloat()..BrowserSettings.MAX_TEXT_ZOOM.toFloat(),
            steps = (BrowserSettings.MAX_TEXT_ZOOM - BrowserSettings.MIN_TEXT_ZOOM) / 10 - 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = stringResource(R.string.settings_text_size_preview),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * (zoom / 100f),
            ),
            modifier = Modifier.padding(16.dp),
        )
        TextButton(
            onClick = { viewModel.setTextZoom(BrowserSettings.DEFAULT_TEXT_ZOOM) },
            enabled = zoom != BrowserSettings.DEFAULT_TEXT_ZOOM,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.settings_text_size_reset))
        }
    }
}

/** Stato del browser predefinito, ricontrollato ogni volta che si torna all'app. */
@Composable
private fun DefaultBrowserRow() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(DefaultBrowser.isDefault(context)) }
    LifecycleResumeEffect(Unit) {
        isDefault = DefaultBrowser.isDefault(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = DefaultBrowser.isDefault(context)
    }
    SettingsRow(
        title = stringResource(R.string.settings_default_browser),
        subtitle = stringResource(
            if (isDefault) R.string.settings_default_browser_yes else R.string.settings_default_browser_no,
        ),
        icon = EroideIcons.Public,
        trailing = if (isDefault) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        onClick = {
            try {
                launcher.launch(DefaultBrowser.requestIntent(context))
            } catch (_: ActivityNotFoundException) {
                DefaultBrowser.openSystemSettings(context)
            }
        },
    )
}

@Composable
private fun ClearDataPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    val request = state.clearRequest
    var rangeMenu by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsParagraph(title = null, body = stringResource(R.string.clear_data_intro))
        }
        item {
            Box {
                SettingsRow(
                    title = stringResource(R.string.clear_data_range),
                    subtitle = stringResource(clearRangeLabel(request.range)),
                    icon = EroideIcons.History,
                    onClick = { rangeMenu = true },
                )
                DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                    ClearRange.entries.forEach { range ->
                        DropdownMenuItem(
                            text = { Text(stringResource(clearRangeLabel(range))) },
                            leadingIcon = { RadioButton(selected = range == request.range, onClick = null) },
                            onClick = {
                                rangeMenu = false
                                viewModel.updateClearRequest { it.copy(range = range) }
                            },
                        )
                    }
                }
            }
        }
        item { SettingsDivider() }
        item {
            ClearCheckRow(R.string.clear_history, R.string.clear_history_summary, request.history) { checked ->
                viewModel.updateClearRequest { it.copy(history = checked) }
            }
        }
        item {
            ClearCheckRow(R.string.clear_cookies, R.string.clear_cookies_summary, request.cookiesAndSiteData) { checked ->
                viewModel.updateClearRequest { it.copy(cookiesAndSiteData = checked) }
            }
        }
        item {
            ClearCheckRow(R.string.clear_cache, R.string.clear_cache_summary, request.cache) { checked ->
                viewModel.updateClearRequest { it.copy(cache = checked) }
            }
        }
        item {
            ClearCheckRow(R.string.clear_downloads, R.string.clear_downloads_summary, request.downloads) { checked ->
                viewModel.updateClearRequest { it.copy(downloads = checked) }
            }
        }
        item {
            ClearCheckRow(R.string.clear_tabs, R.string.clear_tabs_summary, request.openTabs) { checked ->
                viewModel.updateClearRequest { it.copy(openTabs = checked) }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = viewModel::clearData,
                    enabled = !request.isEmpty && !state.isClearing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.clear_data_action))
                }
                if (state.isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClearCheckRow(title: Int, subtitle: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    )
}

private fun clearRangeLabel(range: ClearRange): Int = when (range) {
    ClearRange.LAST_HOUR -> R.string.clear_range_hour
    ClearRange.LAST_DAY -> R.string.clear_range_day
    ClearRange.LAST_WEEK -> R.string.clear_range_week
    ClearRange.LAST_FOUR_WEEKS -> R.string.clear_range_four_weeks
    ClearRange.ALL -> R.string.clear_range_all
}

@Composable
private fun AdBlockSitesPage(state: SettingsUiState, viewModel: SettingsViewModel) {
    val sites = remember(state.settings.adBlockDisabledSites) {
        state.settings.adBlockDisabledSites.sorted()
    }
    if (sites.isEmpty()) {
        SettingsParagraph(title = null, body = stringResource(R.string.settings_adblock_sites_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sites, key = { it }) { host ->
            SettingsRow(
                title = host,
                icon = EroideIcons.Public,
                trailing = {
                    IconButton(onClick = { viewModel.removeAdBlockException(host) }) {
                        Icon(
                            imageVector = EroideIcons.Delete,
                            contentDescription = stringResource(R.string.settings_adblock_site_remove, host),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsParagraph(
                title = stringResource(R.string.app_name) + " " + BrowserActions.versionName(context),
                body = stringResource(R.string.about_privacy),
            )
        }
        item { SettingsDivider() }
        item {
            SettingsParagraph(
                title = stringResource(R.string.about_filters_title),
                body = stringResource(R.string.about_filters_body),
            )
        }
        item {
            SettingsParagraph(
                title = stringResource(R.string.about_reader_title),
                body = stringResource(R.string.about_reader_body),
            )
        }
        item {
            SettingsParagraph(
                title = stringResource(R.string.about_icons_title),
                body = stringResource(R.string.about_icons_body),
            )
        }
        item {
            SettingsParagraph(
                title = stringResource(R.string.about_license_title),
                body = stringResource(R.string.about_license_body),
            )
        }
    }
}
