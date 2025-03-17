package com.example.ui_wi_fi

import android.util.Log
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.example.ui_wi_fi.ui.theme.Ui_wifiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Ui_wifiTheme {
                WiFiLoginScreen(context = this)
            }
        }
    }
}

data class UserCredentials(val username: String, val password: String)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiLoginScreen(context: Context) {
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var savedCredentials by remember { mutableStateOf(loadCredentials(context)) }
    var editingCredential by remember { mutableStateOf<UserCredentials?>(null) }
    var selectedCredential by remember { mutableStateOf<UserCredentials?>(null) }
    var showAddCredentialDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Login") },
                actions = {
                    IconButton(
                        onClick = { showAddCredentialDialog = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Credential")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // List of saved credentials
            Text("Saved Credentials:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(savedCredentials) { credentials ->
                    CredentialItem(
                        credentials = credentials,
                        isSelected = credentials == selectedCredential,
                        onEditClick = { editingCredential = credentials },
                        onDeleteClick = {
                            val updatedList = savedCredentials.toMutableList()
                            updatedList.removeIf { it.username == credentials.username }
                            saveAllCredentials(context, updatedList)
                            savedCredentials = updatedList
                            if (selectedCredential == credentials) {
                                selectedCredential = null
                            }
                        },
                        onSelect = { selectedCredential = credentials }
                    )
                }
            }

            // Fixed Connect Button at the bottom
            Button(
                onClick = {
                    selectedCredential?.let { credentials ->
                        scope.launch {
                            val result1 = executeCurlRequest(
                                "http://logout.ui.ac.ir/login?dst=&popup=true&username=${credentials.username}&password=${credentials.password}"
                            )
                            val result2 = executeCurlRequest(
                                "http://logout2.ui.ac.ir/login?dst=&popup=true&username=${credentials.username}&password=${credentials.password}"
                            )

                            // Show dialog based on result
                            Log.d("CurlRequestResult", "The result is: $result1 , $credentials")
                            Log.d("CurlRequestResult", "The result2 is: $result2, $credentials")
                            dialogMessage = if (result1 == "200" || result2 == "200") {
                                "Connected"
                            } else {
                                "Not Connected"
                            }
                            showDialog = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = selectedCredential != null
            ) {
                Text("Connect")
            }
        }
    }

    // Show AlertDialog based on the result
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Connection Status") },
            text = { Text(dialogMessage) },
            confirmButton = {
                Button(
                    onClick = { showDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Show Edit Dialog
    if (editingCredential != null) {
        EditCredentialDialog(
            credential = editingCredential!!,
            onSave = { updatedCredential ->
                val updatedList = savedCredentials.toMutableList()
                val index = updatedList.indexOfFirst { it.username == editingCredential?.username }
                if (index != -1) {
                    updatedList[index] = updatedCredential
                    saveAllCredentials(context, updatedList)
                    savedCredentials = updatedList
                }
                editingCredential = null
            },
            onDismiss = { editingCredential = null }
        )
    }

    // Show Add Credential Dialog
    if (showAddCredentialDialog) {
        AddCredentialDialog(
            onSave = { username, password ->
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    val newCredentials = UserCredentials(username, password)
                    saveCredentials(context, newCredentials)
                    savedCredentials = loadCredentials(context)
                }
                showAddCredentialDialog = false
            },
            onDismiss = { showAddCredentialDialog = false }
        )
    }
}

@Composable
fun AddCredentialDialog(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Credential") },
        text = {
            Column {
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(username, password)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CredentialItem(
    credentials: UserCredentials,
    isSelected: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSelect: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(8.dp) // Add padding around the Card
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFFC790EE) else Color.Transparent,
                shape = MaterialTheme.shapes.medium // Optional: Add rounded corners
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect() }
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text("Username: ${credentials.username}", style = MaterialTheme.typography.bodyLarge)
                }

                // Three-dot menu
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        onClick = {
                            onEditClick()
                            showMenu = false
                        },
                        text = { Text("Edit") }
                    )
                    DropdownMenuItem(
                        onClick = {
                            onDeleteClick()
                            showMenu = false
                        },
                        text = { Text("Delete") }
                    )
                }
            }
        }
    }
}

@Composable
fun EditCredentialDialog(
    credential: UserCredentials,
    onSave: (UserCredentials) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(credential.username) }
    var password by remember { mutableStateOf(credential.password) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Credential") },
        text = {
            Column {
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(UserCredentials(username, password))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

// Save all credentials to SharedPreferences
private fun saveAllCredentials(context: Context, credentials: List<UserCredentials>) {
    val sharedPreferences = context.getSharedPreferences("WiFiCredentials", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putStringSet("credentials", credentials.map { "${it.username}|${it.password}" }.toSet())
    editor.apply()
}

// Save credentials to SharedPreferences
private fun saveCredentials(context: Context, credentials: UserCredentials) {
    val sharedPreferences = context.getSharedPreferences("WiFiCredentials", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    val existingCredentials = loadCredentials(context).toMutableList()
    existingCredentials.add(credentials)
    editor.putStringSet("credentials", existingCredentials.map { "${it.username}|${it.password}" }.toSet())
    editor.apply()
}

// Load credentials from SharedPreferences
private fun loadCredentials(context: Context): List<UserCredentials> {
    val sharedPreferences = context.getSharedPreferences("WiFiCredentials", Context.MODE_PRIVATE)
    val credentialsSet = sharedPreferences.getStringSet("credentials", emptySet()) ?: emptySet()
    return credentialsSet.map {
        val parts = it.split("|")
        UserCredentials(parts[0], parts[1])
    }
}

// Execute the curl-like request
suspend fun executeCurlRequest(urlString: String): String {
    return withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                "200" // Return "200" for success
            } else {
                responseCode.toString() // Return the error code
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        } finally {
            connection.disconnect()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WiFiLoginScreenPreview() {
    Ui_wifiTheme {
        WiFiLoginScreen(context = androidx.compose.ui.platform.LocalContext.current)
    }
}