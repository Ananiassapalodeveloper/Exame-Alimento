package com.ananiastomas.food

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.ananiastomas.food.ui.theme.FoodTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit

// Classe principal da atividade
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodTheme {
                AppNavigator() // Gerencia a navegação entre telas
            }
        }
    }
}

// Composable responsável por gerenciar a navegação entre SplashScreen, Login e a tela principal
@Composable
fun AppNavigator() {
    var showSplash by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }

    when {
        showSplash -> SplashScreen { showSplash = false }
        !isLoggedIn -> LoginScreen(onLoginSuccess = { isLoggedIn = true })
        else -> FoodScreen()
    }
}

// Tela de splash que aparece ao iniciar o app
@Composable
fun SplashScreen(onNavigateToLogin: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000) // Aguarda 3 segundos antes de ir para a tela de login
        onNavigateToLogin()
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_animation))
    val progress by animateLottieCompositionAsState(composition)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Bem vindo a", style = MaterialTheme.typography.headlineSmall)
            Text("MASTIGOU", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(100.dp)
            )
        }
    }
}

// Tela de login com autenticação via API
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf(TextFieldValue()) }
    var password by remember { mutableStateOf(TextFieldValue()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val client = OkHttpClient()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Função para realizar o login via API
    fun login() {
        if (username.text.isBlank() || password.text.isBlank()) {
            errorMessage = "Usuário e senha são obrigatórios!"
            return
        }

        val json = JSONObject().apply {
            put("username", username.text)
            put("password", password.text)
        }.toString()
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://192.168.152.29:3000/login")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Erro ao conectar: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Login bem-sucedido!")
                    }
                    onLoginSuccess()
                } else {
                    errorMessage = "Credenciais inválidas!"
                }
            }
        })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuário") },
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { login() }),
                modifier = Modifier.fillMaxWidth()
            )
            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { login() }) {
                Text("Entrar")
            }
        }
    }
}

@Composable
fun FoodScreen() {
    var foodName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var foodList by remember { mutableStateOf(listOf<String>()) }
    var filteredList by remember { mutableStateOf(listOf<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val client = OkHttpClient()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun fetchFood() {
        val request = Request.Builder().url("http://192.168.152.29:3000/tasks").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Erro ao buscar as tuas reservas de comidas: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let {
                        val jsonArray = JSONArray(it)
                        val items = List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).getString("title") }
                        foodList = items
                    }
                } else {
                    errorMessage = "Erro ao buscar comidas: ${response.message}"
                }
            }
        })
    }

    fun addFood() {
        if (foodName.isBlank()) {
            errorMessage = "O nome da comida não pode estar vazio!"
            return
        }

        val json = JSONObject().put("title", foodName).toString()
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://192.168.152.29:3000/tasks")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Erro ao adicionar a tua reserva: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    foodName = "" // Limpa o campo
                    scope.launch {
                        snackbarHostState.showSnackbar("A tua reserva adicionada com sucesso, já será entregue!")
                    }
                    fetchFood() // Atualiza a lista
                } else {
                    errorMessage = "Erro ao adicionar Comida: ${response.message}"
                }
            }
        })
    }

    fun updateTask(oldTask: String, newTask: String) {
        val json = JSONObject().put("title", newTask).toString()
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://192.168.152.29:3000/tasks/$oldTask")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Erro ao atualizar a tua comida: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    fetchFood() // Atualiza a lista
                } else {
                    errorMessage = "Erro ao atualizar As tuas reservas: ${response.message}"
                }
            }
        })
    }

    fun deleteTask(task: String) {
        val request = Request.Builder()
            .url("http://192.168.152.29:3000/tasks/$task")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Erro ao excluir as tuas comidas: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    fetchFood() // Atualiza a lista
                } else {
                    errorMessage = "Erro ao excluir as comidas: ${response.message}"
                }
            }
        })
    }

    fun deleteAllTasks() {
        val request = Request.Builder()
            .url("http://192.168.152.29:3000/tasks")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Erro ao excluir todas as comidas: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    fetchFood() // Atualiza a lista
                } else {
                    errorMessage = "Erro ao excluir todas as comidas: ${response.message}"
                }
            }
        })
    }

    LaunchedEffect(searchQuery, foodList) {
        filteredList = if (searchQuery.isBlank()) {
            foodList
        } else {
            foodList.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(Unit) { fetchFood() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Pesquisar comida") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = foodName,
                onValueChange = { foodName = it },
                label = { Text("Nome da Comida") },
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addFood() }),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { addFood() }) {
                Text("Adicionar Comida")
            }

            Spacer(modifier = Modifier.height(16.dp))

            filteredList.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(task, modifier = Modifier.padding(4.dp))

                    Row {
                        Button(onClick = { updateTask(task, "Nova Comida") }) {
                            Text("Editar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { deleteTask(task) }) {
                            Text("Excluir")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { deleteAllTasks() }) {
                Text("Apagar Todas as Comida")
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun FoodScreenPreview() {
    FoodTheme {
        FoodScreen()
    }
}