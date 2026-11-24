package com.eroideweb.browser.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.R
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.feature.browser.BrowserActions
import com.eroideweb.browser.ui.icons.EroideIcons
import java.text.NumberFormat

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
                    SettingsPage.ADBLOCK_SITES -> AdBlockSitesPage(state, viewModel)
                    SettingsPage.ABOUT -> AboutPage()
                }
            }
        }
    }
}

private fun pageTitle(page: SettingsPage): Int = when (page) {
    SettingsPage.ROOT -> R.string.settings_title
    SettingsPage.SEARCH_ENGINE -> R.string.settings_search_engine
    SettingsPage.ADBLOCK_SITES -> R.string.settings_adblock_sites
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
