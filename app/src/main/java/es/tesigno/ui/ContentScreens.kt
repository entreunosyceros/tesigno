package es.tesigno.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.tesigno.data.*

fun categorySymbol(icon: String) = when (icon) { "hand" -> "👋"; "cup" -> "☕"; "home" -> "⌂"; "heart" -> "♥"; "star" -> "★"; else -> "💬" }

@Composable
fun LargeAction(text: String, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors()) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun FormPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        content()
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("Volver", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
fun CategoryChooser(categories: List<Category>, selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text("Categoría", style = MaterialTheme.typography.titleLarge)
    OutlinedButton(onClick = { open = true }, enabled = enabled && categories.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Text(categories.find { it.id == selected }?.text ?: "Elige una categoría", style = MaterialTheme.typography.titleLarge)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Elige una categoría") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { category -> LargeAction(category.text) { onSelect(category.id); open = false } }
        }
    }, confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") } })
}

@Composable
fun CategoryEditor(category: Category?, vm: ContentViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    var id by rememberSaveable { mutableStateOf(category?.id ?: newContentId()) }
    var text by rememberSaveable { mutableStateOf(category?.text ?: "") }
    var icon by rememberSaveable { mutableStateOf(category?.icon ?: "chat") }
    FormPage(if (category == null) "Nueva categoría" else "Editar categoría", onCancel) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Nombre obligatorio") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
        Text("Icono", style = MaterialTheme.typography.titleLarge)
        categoryIcons.forEach { (key, label) ->
            OutlinedButton(onClick = { icon = key }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("${if (icon == key) "✓ " else ""}${categorySymbol(key)} $label", style = MaterialTheme.typography.titleLarge)
            }
        }
        LargeAction("Guardar", enabled = !vm.busy && text.isNotBlank()) { vm.mutate(onDone) { saveCategory(Category(id, text, icon)) } }
        LargeAction("Cancelar", enabled = !vm.busy, onClick = onCancel)
    }
}

@Composable
fun CategoryManagement(vm: ContentViewModel, onEdit: (Category?) -> Unit, onBack: () -> Unit) {
    val catalog = vm.catalog ?: return
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var moveTo by rememberSaveable { mutableStateOf("") }
    var confirmCascade by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Categorías", style = MaterialTheme.typography.headlineLarge) }
        item { LargeAction("Crear categoría", !vm.busy) { onEdit(null) } }
        items(catalog.categories, key = { it.id }) { category ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${categorySymbol(category.icon)} ${category.text}", style = MaterialTheme.typography.headlineSmall)
                    Text("${catalog.expressions.count { it.category == category.id }} expresiones · ${if (category.id.startsWith("user_")) "Personal" else "Incorporada"}", style = MaterialTheme.typography.bodyLarge)
                    LargeAction("Editar ${category.text}", !vm.busy) { onEdit(category) }
                    OrderButtons(catalog.categories.indexOf(category), catalog.categories.size, !vm.busy, category.text,
                        { vm.mutate { moveCategory(category.id, -1) } }, { vm.mutate { moveCategory(category.id, 1) } })
                    LargeAction("Eliminar ${category.text}", !vm.busy, destructive = true) { deleting = category.id; moveTo = ""; confirmCascade = false }
                }
            }
        }
        item { LargeAction("Volver", !vm.busy, onClick = onBack) }
    }
    val category = catalog.categories.find { it.id == deleting }
    if (category != null) {
        val count = catalog.expressions.count { it.category == category.id }
        AlertDialog(onDismissRequest = { if (!vm.busy) deleting = null }, title = { Text(if (confirmCascade) "Confirmar eliminación" else "Eliminar ${category.text}") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (confirmCascade) "Se eliminarán esta categoría y sus $count expresiones. Esta acción no se puede deshacer." else "Esta categoría contiene $count expresiones. Puedes trasladarlas o eliminarlas junto con la categoría.", style = MaterialTheme.typography.bodyLarge)
                if (count > 0 && !confirmCascade && catalog.categories.size > 1) {
                    CategoryChooser(catalog.categories.filterNot { it.id == category.id }, moveTo, !vm.busy) { moveTo = it }
                    LargeAction("Trasladar y eliminar categoría", !vm.busy && moveTo.isNotEmpty()) {
                        vm.mutate({ deleting = null }) { deleteCategory(category.id, moveTo) }
                    }
                }
                LargeAction(if (count == 0) "Confirmar eliminación" else if (confirmCascade) "Sí, eliminar categoría y expresiones" else "Eliminar también sus expresiones", !vm.busy, destructive = true) {
                    if (count > 0 && !confirmCascade) confirmCascade = true
                    else vm.mutate({ deleting = null }) { deleteCategory(category.id) }
                }
            }
        }, confirmButton = { TextButton(onClick = { deleting = null }, enabled = !vm.busy, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") } })
    }
}

@Composable
fun OrderButtons(index: Int, size: Int, enabled: Boolean, label: String, up: () -> Unit, down: () -> Unit) {
    // Separate rows keep full labels readable with large system font sizes.
    OutlinedButton(onClick = up, enabled = enabled && index > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("↑ Subir $label", style = MaterialTheme.typography.titleMedium) }
    OutlinedButton(onClick = down, enabled = enabled && index < size - 1, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("↓ Bajar $label", style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun ExpressionManagement(vm: ContentViewModel, onEdit: (Expression?) -> Unit, onPlay: (String) -> Unit, onBack: () -> Unit) {
    val catalog = vm.catalog ?: return
    var categoryId by rememberSaveable { mutableStateOf(catalog.categories.firstOrNull()?.id ?: "") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = catalog.expressions.filter { it.category == categoryId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Expresiones", style = MaterialTheme.typography.headlineLarge) }
        item { CategoryChooser(catalog.categories, categoryId, !vm.busy) { categoryId = it } }
        item { LargeAction("Crear expresión", !vm.busy && catalog.categories.isNotEmpty()) { onEdit(null) } }
        if (catalog.categories.isEmpty()) item { Text("Crea primero una categoría.", style = MaterialTheme.typography.titleLarge) }
        else if (entries.isEmpty()) item { Text("Esta categoría todavía no tiene expresiones.", style = MaterialTheme.typography.titleLarge) }
        items(entries, key = { it.id }) { expression ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(expression.text, style = MaterialTheme.typography.headlineSmall)
                    Text(if (expression.id.startsWith("user_")) "Expresión personal" else "Expresión incorporada", style = MaterialTheme.typography.bodyLarge)
                    LargeAction("Editar", !vm.busy) { onEdit(expression) }
                    LargeAction("Reproducir", !vm.busy) { onPlay(expression.id) }
                    OrderButtons(entries.indexOf(expression), entries.size, !vm.busy, expression.text,
                        { vm.mutate { moveExpression(expression.id, -1) } }, { vm.mutate { moveExpression(expression.id, 1) } })
                    LargeAction("Eliminar", !vm.busy, destructive = true) { deleting = expression.id }
                }
            }
        }
        item { LargeAction("Volver", !vm.busy, onClick = onBack) }
    }
    val expression = catalog.expressions.find { it.id == deleting }
    if (expression != null) AlertDialog(onDismissRequest = { if (!vm.busy) deleting = null }, title = { Text("Eliminar expresión") },
        text = { Text("¿Quieres eliminar «${expression.text}» y su vídeo personal asociado? Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = { vm.mutate({ deleting = null }) { deleteExpression(expression.id) } }, enabled = !vm.busy, modifier = Modifier.heightIn(min = 56.dp)) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }, enabled = !vm.busy, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") } })
}

@Composable
fun ExpressionEditor(vm: ContentViewModel, onPreview: () -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    val draft = vm.draft ?: return
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.importVideo(uri) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success -> vm.finishCapture(success) }
    FormPage("Editar expresión", onCancel) {
        OutlinedTextField(value = draft.text, onValueChange = { vm.changeDraft(text = it) }, label = { Text("Texto obligatorio") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
        CategoryChooser(vm.catalog?.categories.orEmpty(), draft.category, !vm.busy) { vm.changeDraft(category = it) }
        Text("Vídeo", style = MaterialTheme.typography.titleLarge)
        Text(if (draft.video.isEmpty()) "Vídeo pendiente de incorporar" else "Vídeo asociado. Puedes comprobarlo antes de guardar.", style = MaterialTheme.typography.bodyLarge)
        LargeAction("Grabar vídeo", !vm.busy) {
            vm.prepareCapture()?.let { uri ->
                try { capture.launch(uri) }
                catch (_: ActivityNotFoundException) { vm.cancelCapture(); vm.reportError("No hay una aplicación de cámara disponible.") }
                catch (_: SecurityException) { vm.cancelCapture(); vm.reportError("La aplicación de cámara no permite iniciar la grabación.") }
            }
        }
        LargeAction("Elegir de la galería", !vm.busy) {
            try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
            catch (_: ActivityNotFoundException) { vm.reportError("No hay un selector de vídeos disponible.") }
            catch (_: SecurityException) { vm.reportError("No se permite abrir el selector de vídeos en este dispositivo.") }
        }
        if (draft.video.isNotEmpty()) {
            LargeAction("Vista previa del vídeo", !vm.busy, onClick = onPreview)
            LargeAction("Quitar asociación del vídeo", !vm.busy) { vm.removeDraftVideo() }
        }
        Text("La sustitución o retirada del vídeo solo se guarda al pulsar Guardar. Usa grabaciones autorizadas y revisadas en LSE. Máximo 500 MB por vídeo.", style = MaterialTheme.typography.bodyLarge)
        LargeAction(if (vm.busy) "Procesando…" else "Guardar", !vm.busy && draft.text.isNotBlank() && draft.category.isNotEmpty()) { vm.saveDraft(onDone) }
        LargeAction("Cancelar", !vm.busy, onClick = onCancel)
    }
}
