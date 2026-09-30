package com.example.memorysteps.ui.screens.licenses

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.memorysteps.R
import com.example.memorysteps.ui.components.ActionButton
import com.example.memorysteps.ui.components.Page
import com.example.memorysteps.ui.components.PageTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val license by produceState<String?>(null, context) {
        value = withContext(Dispatchers.IO) {
            context.assets.open("licenses/Pretendard-OFL-1.1.txt").bufferedReader().use { it.readText() }
        }
    }
    Page {
        PageTitle(stringResource(R.string.licenses_title))
        ActionButton(stringResource(R.string.licenses_back), onBack, primary = false)
        Text(stringResource(R.string.licenses_font), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.licenses_description))
        Text(license ?: stringResource(R.string.licenses_loading), style = MaterialTheme.typography.bodyMedium)
    }
}
