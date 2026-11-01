package com.lex.vrpquest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lex.vrpquest.Managers.CatalogApp
import com.lex.vrpquest.Managers.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CATALOG_PREFS = "rookie_catalog"
private const val CATALOG_URL_KEY = "catalog_url"

@Composable
fun CatalogLibraryPage(
    search: String,
    onSearchChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CatalogRepository() }

    var catalogUrl by remember {
        mutableStateOf(
            context.getSharedPreferences(
                CATALOG_PREFS,
                android.content.Context.MODE_PRIVATE
            ).getString(CATALOG_URL_KEY, "") ?: ""
        )
    }

    var apps by remember { mutableStateOf<List<CatalogApp>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    fun loadCatalog() {
        val requestedUrl = catalogUrl.trim()

        if (requestedUrl.isBlank()) {
            error = "Enter your catalog HTTPS address first."
            return
        }

        scope.launch {
            loading = true
            error = null

            try {
                val result = withContext(Dispatchers.IO) {
                    repository.fetchCatalog(requestedUrl)
                }

                context.getSharedPreferences(
                    CATALOG_PREFS,
                    android.content.Context.MODE_PRIVATE
                ).edit()
                    .putString(CATALOG_URL_KEY, requestedUrl)
                    .apply()

                apps = result
                loaded = true
            } catch (e: Exception) {
                error = e.message ?: "Unable to load catalog."
            } finally {
                loading = false
            }
        }
    }

    val filteredApps = remember(apps, search) {
        apps.filter {
            it.title.contains(search, ignoreCase = true) ||
            it.packageName.contains(search, ignoreCase = true) ||
            it.description.contains(search, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search applications") },
            shape = RoundedCornerShape(12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Catalog connection",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = catalogUrl,
                    onValueChange = { catalogUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("HTTPS catalog JSON URL") },
                    placeholder = {
                        Text("https://example.com/catalog.json")
                    }
                )

                Button(
                    onClick = { loadCatalog() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (loading) "Loading catalog..."
                        else "Connect / Refresh"
                    )
                }

                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (loaded && error == null) {
                    Text(
                        "${apps.size} catalog entries loaded",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (loaded && filteredApps.isEmpty() && !loading) {
            Text(
                if (apps.isEmpty()) "The catalog is empty."
                else "No matching applications found.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = filteredApps,
                key = { it.id }
            ) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            app.title,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            "Version ${app.versionName}",
                            style = MaterialTheme.typography.labelMedium
                        )

                        if (app.description.isNotBlank()) {
                            Text(
                                app.description,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
