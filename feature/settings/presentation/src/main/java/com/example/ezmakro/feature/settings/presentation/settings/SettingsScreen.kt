package com.example.ezmakro.feature.settings.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ezmakro.feature.settings.presentation.R

/**
 * Settings hub (A9): the single gear of the diary opens this screen, which
 * lists the settings areas (today: goals and backup) plus the static
 * „Datenquellen" attribution section required by the BLS (CC BY 4.0) and
 * OpenFoodFacts (ODbL) seed/cache data (ADR-008).
 */
@Composable
fun SettingsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToBackup: () -> Unit,
) {
    SettingsScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToGoals = onNavigateToGoals,
        onNavigateToBackup = onNavigateToBackup,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToBackup: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsRow(
                icon = Icons.Filled.DateRange,
                titleRes = R.string.settings_goals_title,
                subtitleRes = R.string.settings_goals_subtitle,
                onClick = onNavigateToGoals,
            )
            HorizontalDivider()
            SettingsRow(
                icon = Icons.Filled.Refresh,
                titleRes = R.string.settings_backup_title,
                subtitleRes = R.string.settings_backup_subtitle,
                onClick = onNavigateToBackup,
            )
            HorizontalDivider()
            DataSourcesSection()
        }
    }
}

/**
 * Static attribution for the bundled BLS seed and the OpenFoodFacts cache
 * (CC BY 4.0 / ODbL). The source names and the DOI are links; everything else
 * is plain legal text.
 */
@Composable
private fun DataSourcesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.settings_datasources_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.settings_datasource_bls_prefix))
                withLink(LinkAnnotation.Url(BLS_URL)) {
                    append(stringResource(R.string.settings_datasource_bls_name))
                }
                append(stringResource(R.string.settings_datasource_bls_suffix))
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.settings_datasource_bls_citation))
                append(" ")
                withLink(LinkAnnotation.Url(BLS_DOI_URL)) {
                    append(stringResource(R.string.settings_datasource_bls_doi))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.settings_datasource_off_prefix))
                withLink(LinkAnnotation.Url(OFF_URL)) {
                    append(stringResource(R.string.settings_datasource_off_name))
                }
                append(stringResource(R.string.settings_datasource_off_suffix))
                withLink(LinkAnnotation.Url(OFF_ODBL_URL)) {
                    append(stringResource(R.string.settings_datasource_off_license))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val BLS_URL = "https://www.blsdb.de/"
private const val BLS_DOI_URL = "https://doi.org/10.25826/Data20251217-134202-0"
private const val OFF_URL = "https://world.openfoodfacts.org/"
private const val OFF_ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"

/**
 * One entry of the settings list. The icons are decorative — the row label is
 * always adjacent, so both leading and trailing icons carry no
 * contentDescription.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    titleRes: Int,
    subtitleRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(onNavigateBack = {}, onNavigateToGoals = {}, onNavigateToBackup = {})
    }
}
