package es.tesigno.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import es.tesigno.R
import es.tesigno.data.*
import es.tesigno.media.ExpressionPlayer

@Composable
fun TeSignoApp(vm: ContentViewModel = viewModel()) {
    val colors = if (vm.darkTheme) darkColorScheme(primary = Color(0xFF88D8CB), onPrimary = Color(0xFF00382F), background = Color(0xFF101A1B), surface = Color(0xFF101A1B))
        else lightColorScheme(primary = Color(0xFF175B53), background = Color(0xFFF6FAF8), surface = Color(0xFFF6FAF8))
    MaterialTheme(colorScheme = colors) {
        var route by rememberSaveable { mutableStateOf("catalog") }
        var expandedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var categoryEditId by rememberSaveable { mutableStateOf<String?>(null) }
        var playerBack by rememberSaveable { mutableStateOf("catalog") }
        var confirmLeave by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val catalog = vm.catalog
        fun back() {
            if (vm.busy) return
            route = when (route) {
                "settings" -> "catalog"
                "about", "manage" -> "settings"
                "categories", "expressions" -> "manage"
                "categoryForm", "expressionForm" -> { confirmLeave = true; route }
                "player" -> playerBack
                "preview" -> "expressionForm"
                else -> "catalog"
            }
        }
        BackHandler(route != "catalog" || vm.busy) { back() }
        Scaffold { padding ->
            Surface(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f)) {
                        if (catalog == null) {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(if (vm.busy) "Cargando catálogo…" else "No se ha podido abrir el catálogo. Tus datos se mantienen guardados.", style = MaterialTheme.typography.titleLarge)
                            }
                        } else when (route) {
                            "settings" -> FormPage("Ajustes", { back() }) {
                                LargeAction("Gestionar categorías y expresiones", !vm.busy) { route = "manage" }
                                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tema oscuro", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                    Switch(checked = vm.darkTheme, onCheckedChange = { vm.setDarkMode(it) }, modifier = Modifier.semantics { contentDescription = "Tema oscuro" })
                                }
                                LargeAction("Acerca de") { route = "about" }
                            }
                            "about" -> AboutScreen { back() }
                            "manage" -> FormPage("Gestionar contenidos", { back() }) {
                                LargeAction("Categorías") { route = "categories" }
                                LargeAction("Expresiones") { route = "expressions" }
                                Text("Los cambios se guardan en este teléfono. Solo incorpora vídeos autorizados.", style = MaterialTheme.typography.bodyLarge)
                            }
                            "categories" -> CategoryManagement(vm, { category -> categoryEditId = category?.id; route = "categoryForm" }, { back() })
                            "categoryForm" -> CategoryEditor(catalog.categories.find { it.id == categoryEditId }, vm, { route = "categories" }, { back() })
                            "expressions" -> ExpressionManagement(vm, { expression -> vm.beginExpression(expression); route = "expressionForm" }, { id -> selectedId = id; playerBack = "expressions"; route = "player" }, { back() })
                            "expressionForm" -> if (vm.draft != null) ExpressionEditor(vm, { route = "preview" }, { route = "expressions" }, { back() }) else FormPage("Sin borrador", { route = "expressions" }) { Text("Vuelve a elegir una expresión.") }
                            "preview" -> vm.draft?.let { expression -> key(expression.video) { ExpressionPlayer(expression, "Volver al formulario") { back() } } }
                            "player" -> {
                                val expression = catalog.expressions.find { it.id == selectedId }
                                if (expression != null) key(expression.id) { ExpressionPlayer(expression, "Volver") { back() } }
                                else FormPage("Expresión no disponible", { back() }) { Text("Esta expresión ya no está en el catálogo.") }
                            }
                            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Image(painterResource(R.drawable.logo_tesigno), contentDescription = null, modifier = Modifier.size(64.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("TeSigno", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(onClick = { route = "settings" }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("⚙ Ajustes", style = MaterialTheme.typography.titleLarge) }
                                        Text("Una forma sencilla de entendernos", style = MaterialTheme.typography.titleLarge)
                                        Text("Toca una categoría para elegir qué decir", style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                if (catalog.categories.isEmpty()) item { Text("No hay categorías. Puedes crearlas en Ajustes.", style = MaterialTheme.typography.titleLarge) }
                                catalog.categories.forEach { category ->
                                    item(key = category.id) {
                                        val expanded = expandedCategoryId == category.id
                                        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                            Column {
                                                Button(onClick = { expandedCategoryId = toggleCategory(expandedCategoryId, category.id) },
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).semantics { heading(); stateDescription = if (expanded) "Expandida" else "Contraída" },
                                                    shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(20.dp)) {
                                                    Text(categorySymbol(category.icon), Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.headlineSmall)
                                                    Spacer(Modifier.width(12.dp))
                                                    Text(category.text, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.width(12.dp))
                                                    Text(if (expanded) "⌃" else "⌄", Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.headlineMedium)
                                                }
                                                AnimatedVisibility(expanded, enter = expandVertically(tween(160)), exit = shrinkVertically(tween(160))) {
                                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        val entries = catalog.expressions.filter { it.category == category.id }
                                                        if (entries.isEmpty()) Text("Esta categoría todavía no tiene expresiones.", Modifier.padding(8.dp), style = MaterialTheme.typography.titleLarge)
                                                        entries.forEach { expression ->
                                                            FilledTonalButton(onClick = { selectedId = expression.id; playerBack = "catalog"; route = "player" }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), contentPadding = PaddingValues(20.dp)) {
                                                                Text(expression.text, style = MaterialTheme.typography.titleLarge)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("Descartar cambios") }, text = { Text("Los cambios sin guardar se perderán. El contenido anterior se conserva.") },
            confirmButton = { TextButton(onClick = { if (route == "expressionForm") vm.cancelDraft(); route = if (route == "categoryForm") "categories" else "expressions"; confirmLeave = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Seguir editando") } })
        vm.error?.let { message -> AlertDialog(onDismissRequest = vm::dismissError, title = { Text("No se ha podido completar") }, text = { Text(message) }, confirmButton = { TextButton(onClick = vm::dismissError, modifier = Modifier.heightIn(min = 56.dp)) { Text("Entendido") } }) }
    }
}
