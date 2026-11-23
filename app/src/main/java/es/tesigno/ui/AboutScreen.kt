package es.tesigno.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.tesigno.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var browserUnavailable by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Acerca de TeSigno", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Image(painterResource(R.drawable.logo_tesigno), contentDescription = "Logo de TeSigno", modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
        Text("Una forma sencilla de entendernos", style = MaterialTheme.typography.titleLarge)
        Text(
            "TeSigno facilita la comunicación cotidiana mediante expresiones y vídeos previamente grabados en lengua de signos española (LSE). Elige una expresión y muestra su vídeo a la otra persona.\n\nFunciona sin conexión y no es un traductor automático de lengua de signos.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/entreunosyceros/tesigno")))
                browserUnavailable = false
            } catch (_: ActivityNotFoundException) {
                browserUnavailable = true
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text("Ver proyecto en GitHub", style = MaterialTheme.typography.titleLarge)
        }
        Text("Se abre en el navegador y requiere conexión a Internet.", style = MaterialTheme.typography.bodyLarge)
        if (browserUnavailable) Text("No hay un navegador disponible para abrir el enlace.", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text("Volver a Ajustes", style = MaterialTheme.typography.titleLarge)
        }
    }
}
