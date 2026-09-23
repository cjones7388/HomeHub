package com.example.homehub

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.input.KeyboardType
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max


private const val SPREADSHEET_ID =
    "1C-vyDVpJHEuQlkSGV3G2Pd_5LqyUKhns-fxHe_F-PeQ"

private const val TARGET_SHEET_ID =
    1903279729

private const val SHEETS_SCOPE =
    "https://www.googleapis.com/auth/spreadsheets.readonly"


/* -------------------------------------------------- */
/* HOMEHUB COLOURS                                    */
/* -------------------------------------------------- */

private val HOMEHUB_BACKGROUND =
    Color(0xFF355B97)

private val HOMEHUB_PRIMARY =
    Color(0xFF183D6B)

private val HOMEHUB_SECONDARY =
    Color(0xFFDCE6F5)

private val HOMEHUB_TEXT =
    Color(0xFF1C1C1C)

private val HOMEHUB_OUTLINE =
    Color(0xFFB8C7DD)

private val HOMEHUB_INCOME =
    Color(0xFF2E7D5B)

private val HOMEHUB_OUTGOING =
    Color(0xFFB94242)

private val HOMEHUB_SURFACE_VARIANT =
    Color(0xFFE8EEF7)


data class Bill(
    val name: String,
    val dueDay: Int,
    val amount: Double,
    val dueDate: LocalDate
)


data class AccountTransaction(
    val description: String,
    val amount: Double,
    val date: String = "",
    val source: String = "MANUAL",
    val bankTransactionId: String = "",
    val upstreamBankTransactionId: String = ""
)


data class BankTransaction(
    val id: String,
    val description: String,
    val amount: Double,
    val date: String,
    val upstreamTransactionId: String = "",
    val counterparty: String = "",
    val merchantName: String = ""
)


class MainActivity : ComponentActivity() {

    private var googleConnected by mutableStateOf(false)

    private var loading by mutableStateOf(false)

    private var sheetStatus by mutableStateOf<String?>(null)

    private var thisWeeksBills by mutableStateOf<List<Bill>>(emptyList())


    private val authorizationLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->

            if (
                result.resultCode != RESULT_OK ||
                result.data == null
            ) {

                loading = false

                sheetStatus =
                    "Google authorization cancelled."

                return@registerForActivityResult
            }


            try {

                val authorizationResult =
                    Identity
                        .getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(
                            result.data
                        )


                handleAuthorizationResult(
                    authorizationResult
                )

            } catch (e: Exception) {

                loading = false

                sheetStatus =
                    "Authorization error: ${
                        e.message ?: "Unknown error"
                    }"
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )


        setContent {

            HomeHubApp(
                googleConnected =
                    googleConnected,

                loading =
                    loading,

                sheetStatus =
                    sheetStatus,

                thisWeeksBills =
                    thisWeeksBills,

                onConnectGoogle = {
                    connectGoogleSheets()
                }
            )
        }
    }


    private fun connectGoogleSheets() {

        loading = true

        sheetStatus = null


        val request =
            AuthorizationRequest
                .builder()
                .setRequestedScopes(
                    listOf(
                        Scope(
                            SHEETS_SCOPE
                        )
                    )
                )
                .build()


        Identity
            .getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->

                if (
                    result.hasResolution()
                ) {

                    val pendingIntent =
                        result.pendingIntent


                    if (
                        pendingIntent == null
                    ) {

                        loading = false

                        sheetStatus =
                            "Google permission screen unavailable."

                        return@addOnSuccessListener
                    }


                    val intentSenderRequest =
                        IntentSenderRequest.Builder(
                            pendingIntent.intentSender
                        ).build()


                    authorizationLauncher.launch(
                        intentSenderRequest
                    )

                } else {

                    handleAuthorizationResult(
                        result
                    )
                }
            }
            .addOnFailureListener { exception ->

                loading = false

                sheetStatus =
                    "Couldn't connect to Google: ${
                        exception.message
                            ?: "Unknown error"
                    }"
            }
    }


    private fun handleAuthorizationResult(
        result: AuthorizationResult
    ) {

        val accessToken =
            result.accessToken


        if (
            accessToken.isNullOrBlank()
        ) {

            loading = false

            sheetStatus =
                "Google didn't return an access token."

            return
        }


        readSpreadsheet(
            accessToken
        )
    }


    private fun readSpreadsheet(
        accessToken: String
    ) {

        Executors
            .newSingleThreadExecutor()
            .execute {

                try {

                    val spreadsheetUrl =
                        "https://sheets.googleapis.com/v4/" +
                                "spreadsheets/$SPREADSHEET_ID" +
                                "?fields=sheets.properties"


                    val spreadsheetJson =
                        makeGoogleRequest(
                            spreadsheetUrl,
                            accessToken
                        )


                    val sheetsArray =
                        spreadsheetJson
                            .getJSONArray(
                                "sheets"
                            )


                    var sheetTitle: String? =
                        null


                    for (
                    i in 0 until sheetsArray.length()
                    ) {

                        val sheet =
                            sheetsArray
                                .getJSONObject(i)


                        val properties =
                            sheet.getJSONObject(
                                "properties"
                            )


                        val sheetId =
                            properties.getLong(
                                "sheetId"
                            )


                        if (
                            sheetId ==
                            TARGET_SHEET_ID.toLong()
                        ) {

                            sheetTitle =
                                properties.getString(
                                    "title"
                                )

                            break
                        }
                    }


                    if (
                        sheetTitle == null
                    ) {

                        throw Exception(
                            "Couldn't find the Bills sheet tab."
                        )
                    }


                    val range =
                        "'$sheetTitle'!A:E"


                    val encodedRange =
                        URLEncoder
                            .encode(
                                range,
                                "UTF-8"
                            )
                            .replace(
                                "+",
                                "%20"
                            )


                    val valuesUrl =
                        "https://sheets.googleapis.com/v4/" +
                                "spreadsheets/$SPREADSHEET_ID" +
                                "/values/$encodedRange"


                    val valuesJson =
                        makeGoogleRequest(
                            valuesUrl,
                            accessToken
                        )


                    val rows =
                        valuesJson.optJSONArray(
                            "values"
                        )


                    val bills =
                        mutableListOf<Bill>()


                    val excludedCategories =
                        setOf(
                            "food",
                            "going out",
                            "travelling"
                        )


                    if (
                        rows != null
                    ) {

                        for (
                        i in 1 until rows.length()
                        ) {

                            val row =
                                rows.getJSONArray(i)


                            if (
                                row.length() < 3
                            ) {
                                continue
                            }


                            val name =
                                row.optString(0)
                                    .trim()


                            val dueDayText =
                                row.optString(1)
                                    .trim()


                            if (
                                excludedCategories.contains(
                                    name.lowercase()
                                )
                            ) {

                                continue
                            }


                            val amountText =
                                row.optString(2)
                                    .trim()


                            if (
                                name.isBlank() ||
                                dueDayText.isBlank() ||
                                amountText.isBlank()
                            ) {

                                continue
                            }


                            val dueDay =
                                dueDayText
                                    .toDoubleOrNull()
                                    ?.toInt()


                            val amount =
                                amountText
                                    .replace(
                                        "£",
                                        ""
                                    )
                                    .replace(
                                        ",",
                                        ""
                                    )
                                    .toDoubleOrNull()


                            if (
                                dueDay == null ||
                                amount == null ||
                                dueDay !in 1..31
                            ) {

                                continue
                            }


                            val today =
                                LocalDate.now()


                            val lastDay =
                                today
                                    .withDayOfMonth(1)
                                    .lengthOfMonth()


                            if (
                                dueDay > lastDay
                            ) {

                                continue
                            }


                            val dueDate =
                                LocalDate.of(
                                    today.year,
                                    today.month,
                                    dueDay
                                )


                            bills.add(
                                Bill(
                                    name =
                                        name,

                                    dueDay =
                                        dueDay,

                                    amount =
                                        amount,

                                    dueDate =
                                        dueDate
                                )
                            )
                        }
                    }


                    val today =
                        LocalDate.now()


                    val monday =
                        today.with(
                            DayOfWeek.MONDAY
                        )


                    val sunday =
                        monday.plusDays(6)


                    val weeklyBills =
                        bills
                            .filter {

                                !it.dueDate.isBefore(
                                    monday
                                ) &&
                                        !it.dueDate.isAfter(
                                            sunday
                                        )
                            }
                            .sortedBy {
                                it.dueDate
                            }


                    val total =
                        weeklyBills.sumOf {
                            it.amount
                        }


                    runOnUiThread {

                        googleConnected = true

                        loading = false

                        thisWeeksBills =
                            weeklyBills


                        sheetStatus =
                            if (
                                weeklyBills.isEmpty()
                            ) {

                                "Google Sheet connected — no bills due this week."

                            } else {

                                "${weeklyBills.size} bill(s) due this week — " +
                                        formatMoney(total)
                            }
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        loading = false

                        sheetStatus =
                            "Couldn't read Google Sheet: ${
                                e.message
                                    ?: "Unknown error"
                            }"
                    }
                }
            }
    }


    private fun makeGoogleRequest(
        urlString: String,
        accessToken: String
    ): JSONObject {

        val connection =
            URL(urlString)
                .openConnection() as HttpURLConnection


        try {

            connection.requestMethod =
                "GET"


            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )


            connection.setRequestProperty(
                "Accept",
                "application/json"
            )


            connection.connectTimeout =
                15000


            connection.readTimeout =
                15000


            val responseCode =
                connection.responseCode


            if (
                responseCode !in 200..299
            ) {

                val errorText =
                    connection
                        .errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }


                throw Exception(
                    "Google returned HTTP $responseCode" +
                            if (
                                errorText.isNullOrBlank()
                            ) {

                                ""

                            } else {

                                ": $errorText"
                            }
                )
            }


            val responseText =
                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }


            return JSONObject(
                responseText
            )

        } finally {

            connection.disconnect()
        }
    }
}


/* -------------------------------------------------- */
/* HOME HUB                                           */
/* -------------------------------------------------- */

@Composable
fun HomeHubApp(
    googleConnected: Boolean,
    loading: Boolean,
    sheetStatus: String?,
    thisWeeksBills: List<Bill>,
    onConnectGoogle: () -> Unit
) {

    var currentScreen by remember {
        mutableStateOf("home")
    }


    val homeHubColors =
        lightColorScheme(

            primary =
                HOMEHUB_PRIMARY,

            onPrimary =
                Color.White,

            secondary =
                HOMEHUB_SECONDARY,

            onSecondary =
                HOMEHUB_PRIMARY,

            tertiary =
                HOMEHUB_INCOME,

            onTertiary =
                Color.White,

            background =
                HOMEHUB_BACKGROUND,

            onBackground =
                Color.White,

            surface =
                Color.White,

            onSurface =
                HOMEHUB_TEXT,

            surfaceVariant =
                HOMEHUB_SURFACE_VARIANT,

            onSurfaceVariant =
                HOMEHUB_TEXT,

            outline =
                HOMEHUB_OUTLINE,

            error =
                HOMEHUB_OUTGOING,

            onError =
                Color.White
        )


    BackHandler(
        enabled =
            currentScreen != "home"
    ) {

        currentScreen =
            "home"
    }


    MaterialTheme(
        colorScheme =
            homeHubColors
    ) {

        Surface(
            modifier =
                Modifier.fillMaxSize(),

            color =
                HOMEHUB_BACKGROUND,

            contentColor =
                Color.White
        ) {

            when (currentScreen) {

                "home" -> {

                    HomeScreen(
                        onBillsClick = {
                            currentScreen =
                                "bills"
                        },

                        onNotesClick = {
                            currentScreen =
                                "notes"
                        },

                        onReceiptsClick = {
                            currentScreen =
                                "receipts"
                        }
                    )
                }


                "bills" -> {

                    BillsScreen(
                        onBack = {
                            currentScreen =
                                "home"
                        },

                        googleConnected =
                            googleConnected,

                        loading =
                            loading,

                        sheetStatus =
                            sheetStatus,

                        thisWeeksBills =
                            thisWeeksBills,

                        onConnectGoogle =
                            onConnectGoogle
                    )
                }


                "notes" -> {

                    NotesScreen(
                        onBack = {
                            currentScreen =
                                "home"
                        }
                    )
                }


                "receipts" -> {

                    ReceiptsScreen(
                        onBack = {
                            currentScreen =
                                "home"
                        },

                        onBankSync = {
                            currentScreen =
                                "bankSync"
                        }
                    )
                }


                "bankSync" -> {

                    BankSyncScreen(
                        onBack = {
                            currentScreen =
                                "receipts"
                        }
                    )
                }
            }
        }
    }
}


/* -------------------------------------------------- */
/* HOME SCREEN                                        */
/* -------------------------------------------------- */

@Composable
fun HomeScreen(
    onBillsClick: () -> Unit,
    onNotesClick: () -> Unit,
    onReceiptsClick: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),

            contentAlignment =
                Alignment.Center
        ) {

            Image(
                painter =
                    painterResource(
                        id = R.drawable.carlhomehub
                    ),

                contentDescription =
                    "HomeHub",

                modifier =
                    Modifier.fillMaxSize(),

                contentScale =
                    ContentScale.Crop
            )
        }


        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 20.dp
                    ),

            verticalArrangement =
                Arrangement.Center
        ) {

            HomeCard(
                emoji =
                    "💷",

                title =
                    "Bills",

                description =
                    "Your household bills from Google Sheets",

                onClick =
                    onBillsClick
            )


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            HomeCard(
                emoji =
                    "📝",

                title =
                    "Notes",

                description =
                    "Your notes and lists",

                onClick =
                    onNotesClick
            )


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            HomeCard(
                emoji =
                    "🧾",

                title =
                    "Receipts",

                description =
                    "Track your current account balance",

                onClick =
                    onReceiptsClick
            )
        }
    }
}


/* -------------------------------------------------- */
/* HOME CARD                                          */
/* -------------------------------------------------- */

@Composable
fun HomeCard(
    emoji: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },

        shape =
            RoundedCornerShape(20.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White,

                contentColor =
                    HOMEHUB_TEXT
            ),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    emoji,

                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,

                modifier =
                    Modifier.size(50.dp)
            )


            Spacer(
                modifier =
                    Modifier.size(16.dp)
            )


            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        title,

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge
                )


                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )


                Text(
                    text =
                        description,

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }


            Text(
                text =
                    "→",

                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,

                fontWeight =
                    androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}


/* -------------------------------------------------- */
/* BACK BUTTON                                        */
/* -------------------------------------------------- */

@Composable
fun ScreenBackButton(
    onBack: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .size(90.dp)
                .clickable {
                    onBack()
                },

        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                "←",

            style =
                MaterialTheme
                    .typography
                    .displayLarge,

            color =
                Color.White
        )
    }
}


/* -------------------------------------------------- */
/* BILLS SCREEN                                       */
/* -------------------------------------------------- */

@Composable
fun BillsScreen(
    onBack: () -> Unit,
    googleConnected: Boolean,
    loading: Boolean,
    sheetStatus: String?,
    thisWeeksBills: List<Bill>,
    onConnectGoogle: () -> Unit
) {

    val context =
        LocalContext.current


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(20.dp)
    ) {

        ScreenBackButton(
            onBack =
                onBack
        )


        Spacer(
            modifier =
                Modifier.height(10.dp)
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    "💷",

                style =
                    MaterialTheme
                        .typography
                        .displayLarge
            )
        }


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    "Household Bills",

                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        Button(
            onClick = {

                openBillsSpreadsheet(
                    context
                )
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp)
        ) {

            Text(
                text =
                    "OPEN & EDIT GOOGLE SHEET"
            )
        }


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        Button(
            onClick =
                onConnectGoogle,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp),

            enabled =
                !loading
        ) {

            if (
                loading
            ) {

                CircularProgressIndicator(
                    modifier =
                        Modifier.size(22.dp)
                )

            } else {

                Text(
                    text =
                        if (
                            googleConnected
                        ) {

                            "REFRESH THIS WEEK'S BILLS"

                        } else {

                            "SHOW THIS WEEK'S BILLS"
                        }
                )
            }
        }


        if (
            sheetStatus != null
        ) {

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            Text(
                text =
                    sheetStatus,

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )
        }


        Spacer(
            modifier =
                Modifier.height(25.dp)
        )


        if (
            googleConnected &&
            !loading
        ) {

            ThisWeeksBillsSection(
                bills =
                    thisWeeksBills
            )
        }
    }
}


/* -------------------------------------------------- */
/* THIS WEEK'S BILLS                                  */
/* -------------------------------------------------- */

@Composable
fun ThisWeeksBillsSection(
    bills: List<Bill>
) {

    val total =
        bills.sumOf {
            it.amount
        }


    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            text =
                "This Week's Bills",

            style =
                MaterialTheme
                    .typography
                    .headlineSmall
        )


        Spacer(
            modifier =
                Modifier.height(5.dp)
        )


        if (
            bills.isEmpty()
        ) {

            Text(
                text =
                    "Nothing is due this week.",

                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )

        } else {

            Text(
                text =
                    "${bills.size} bill(s) • ${formatMoney(total)} total",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            (bills.size * 72)
                                .coerceAtMost(360)
                                .dp
                        )
            ) {

                items(
                    bills
                ) { bill ->

                    BillRow(
                        bill =
                            bill
                    )
                }
            }
        }
    }
}


/* -------------------------------------------------- */
/* BILL ROW                                           */
/* -------------------------------------------------- */

@Composable
fun BillRow(
    bill: Bill
) {

    val dateFormatter =
        DateTimeFormatter.ofPattern(
            "EEE d MMM"
        )


    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(14.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White,

                contentColor =
                    HOMEHUB_TEXT
            ),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        bill.dueDate.format(
                            dateFormatter
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )


                Text(
                    text =
                        bill.name,

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )
            }


            Text(
                text =
                    formatMoney(
                        bill.amount
                    ),

                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )
        }
    }
}


/* -------------------------------------------------- */
/* OPEN GOOGLE SHEET                                  */
/* -------------------------------------------------- */

fun openBillsSpreadsheet(
    context: Context
) {

    val spreadsheetUrl =
        "https://docs.google.com/spreadsheets/d/" +
                "1C-vyDVpJHEuQlkSGV3G2Pd_5LqyUKhns-fxHe_F-PeQ" +
                "/edit?gid=1903279729"


    val sheetsIntent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                spreadsheetUrl
            )
        ).apply {

            setPackage(
                "com.google.android.apps.docs.editors.sheets"
            )
        }


    try {

        context.startActivity(
            sheetsIntent
        )

    } catch (
        e: ActivityNotFoundException
    ) {

        val browserIntent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    spreadsheetUrl
                )
            )


        try {

            context.startActivity(
                browserIntent
            )

        } catch (
            e2: ActivityNotFoundException
        ) {

            Toast.makeText(
                context,

                "No browser or Google Sheets app was found.",

                Toast.LENGTH_LONG
            ).show()
        }
    }
}


/* -------------------------------------------------- */
/* MONEY                                              */
/* -------------------------------------------------- */

fun formatMoney(
    amount: Double
): String {

    return "£" +
            String.format(
                java.util.Locale.UK,
                "%.2f",
                amount
            )
}


/* -------------------------------------------------- */
/* RECEIPTS STORAGE                                   */
/* -------------------------------------------------- */

private const val RECEIPTS_PREFS =
    "homehub_receipts"

private const val TRANSACTIONS_KEY =
    "transactions"


fun loadTransactions(
    context: Context
): MutableList<AccountTransaction> {

    val preferences =
        context.getSharedPreferences(
            RECEIPTS_PREFS,
            Context.MODE_PRIVATE
        )

    val jsonText =
        preferences.getString(
            TRANSACTIONS_KEY,
            "[]"
        ) ?: "[]"

    val transactions =
        mutableListOf<AccountTransaction>()

    try {

        val array =
            JSONArray(
                jsonText
            )

        for (
        i in 0 until array.length()
        ) {

            val item =
                array.getJSONObject(i)


            transactions.add(
                AccountTransaction(
                    description =
                        item.optString(
                            "description",
                            ""
                        ),

                    amount =
                        item.optDouble(
                            "amount",
                            0.0
                        ),

                    date =
                        item.optString(
                            "date",
                            ""
                        ),

                    source =
                        item.optString(
                            "source",
                            "MANUAL"
                        ),

                    bankTransactionId =
                        item.optString(
                            "bankTransactionId",
                            ""
                        ),

                    upstreamBankTransactionId =
                        item.optString(
                            "upstreamBankTransactionId",
                            ""
                        )
                )
            )
        }

    } catch (e: Exception) {

        return mutableListOf()
    }

    return transactions
}


fun saveTransactions(
    context: Context,
    transactions: List<AccountTransaction>
) {

    val array =
        JSONArray()


    transactions.forEach { transaction ->

        array.put(
            JSONObject().apply {

                put(
                    "description",
                    transaction.description
                )

                put(
                    "amount",
                    transaction.amount
                )

                put(
                    "date",
                    transaction.date
                )

                put(
                    "source",
                    transaction.source
                )

                put(
                    "bankTransactionId",
                    transaction.bankTransactionId
                )

                put(
                    "upstreamBankTransactionId",
                    transaction.upstreamBankTransactionId
                )
            }
        )
    }


    context
        .getSharedPreferences(
            RECEIPTS_PREFS,
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            TRANSACTIONS_KEY,
            array.toString()
        )
        .apply()
}


/* -------------------------------------------------- */
/* TRANSACTION MATCHING                               */
/* -------------------------------------------------- */

fun normaliseTransactionText(
    text: String
): String {

    return text
        .lowercase()
        .replace(
            Regex("[^a-z0-9]+"),
            ""
        )
}


private fun transactionTextTokens(
    text: String
): Set<String> {

    val ignoredTokens =
        setOf(
            "payment",
            "card",
            "transfer",
            "direct",
            "debit",
            "purchase",
            "online",
            "uk",
            "gb",
            "pos",
            "credit",
            "transaction",
            "cash",
            "withdrawal"
        )


    return text
        .lowercase()
        .split(
            Regex("[^a-z0-9]+")
        )
        .map {
            it.trim()
        }
        .filter {
            it.length >= 3 &&
                    it !in ignoredTokens
        }
        .toSet()
}


private fun merchantTextMatches(
    manualDescription: String,
    bankDescription: String,
    counterparty: String,
    merchantName: String
): Boolean {

    val manualNormalised =
        normaliseTransactionText(
            manualDescription
        )


    if (
        manualNormalised.length < 4
    ) {

        return false
    }


    val manualTokens =
        transactionTextTokens(
            manualDescription
        )


    val candidates =
        listOf(
            bankDescription,
            counterparty,
            merchantName
        )
            .filter {
                it.isNotBlank()
            }


    for (
    candidate in candidates
    ) {

        val candidateNormalised =
            normaliseTransactionText(
                candidate
            )


        if (
            candidateNormalised.length < 4
        ) {

            continue
        }


        if (
            candidateNormalised ==
            manualNormalised
        ) {

            return true
        }


        if (
            candidateNormalised.contains(
                manualNormalised
            ) ||
            manualNormalised.contains(
                candidateNormalised
            )
        ) {

            return true
        }


        val candidateTokens =
            transactionTextTokens(
                candidate
            )


        if (
            manualTokens.isNotEmpty() &&
            manualTokens
                .intersect(
                    candidateTokens
                )
                .isNotEmpty()
        ) {

            return true
        }
    }


    return false
}


private fun transactionMatchScore(
    manual: AccountTransaction,
    bank: BankTransaction
): Int {

    if (
        abs(
            manual.amount -
                    bank.amount
        ) >= 0.005
    ) {

        return 0
    }


    if (
        manual.bankTransactionId.isNotBlank() ||
        manual.upstreamBankTransactionId.isNotBlank()
    ) {

        return 0
    }


    val manualNormalised =
        normaliseTransactionText(
            manual.description
        )


    val manualTokens =
        transactionTextTokens(
            manual.description
        )


    if (
        manualNormalised.isBlank()
    ) {

        return 0
    }


    val candidates =
        listOf(
            bank.merchantName,
            bank.counterparty,
            bank.description
        )
            .filter {
                it.isNotBlank()
            }


    var bestScore =
        0


    for (
    candidate in candidates
    ) {

        val candidateNormalised =
            normaliseTransactionText(
                candidate
            )


        if (
            candidateNormalised.isBlank()
        ) {

            continue
        }


        if (
            candidateNormalised ==
            manualNormalised
        ) {

            bestScore =
                max(
                    bestScore,
                    100
                )
        }


        if (
            manualNormalised.length >= 4 &&
            (
                    candidateNormalised.contains(
                        manualNormalised
                    ) ||
                            manualNormalised.contains(
                                candidateNormalised
                            )
                    )
        ) {

            bestScore =
                max(
                    bestScore,
                    80
                )
        }


        val candidateTokens =
            transactionTextTokens(
                candidate
            )


        val overlap =
            manualTokens
                .intersect(
                    candidateTokens
                )


        if (
            overlap.isNotEmpty()
        ) {

            bestScore =
                max(
                    bestScore,
                    60
                )
        }
    }


    return bestScore
}


fun findMatchingTransactionIndex(
    transactions: List<AccountTransaction>,
    bankTransaction: BankTransaction
): Int? {

    val bankId =
        bankTransaction.id

    val upstreamId =
        bankTransaction.upstreamTransactionId


    if (
        bankId.isNotBlank()
    ) {

        val idIndex =
            transactions.indexOfFirst {

                it.bankTransactionId ==
                        bankId
            }


        if (
            idIndex >= 0
        ) {

            return idIndex
        }
    }


    if (
        upstreamId.isNotBlank()
    ) {

        val upstreamIndex =
            transactions.indexOfFirst {

                it.upstreamBankTransactionId ==
                        upstreamId
            }


        if (
            upstreamIndex >= 0
        ) {

            return upstreamIndex
        }
    }


    val scoredCandidates =
        transactions
            .mapIndexedNotNull { index, transaction ->

                val score =
                    transactionMatchScore(
                        transaction,
                        bankTransaction
                    )


                if (
                    score > 0
                ) {

                    Pair(
                        index,
                        score
                    )

                } else {

                    null
                }
            }
            .sortedByDescending {
                it.second
            }


    if (
        scoredCandidates.isEmpty()
    ) {

        return null
    }


    val highestScore =
        scoredCandidates.first().second


    if (
        highestScore < 60
    ) {

        return null
    }


    val highestMatches =
        scoredCandidates.count {
            it.second == highestScore
        }


    if (
        highestMatches != 1
    ) {

        return null
    }


    return scoredCandidates.first().first
}


/* -------------------------------------------------- */
/* ENDUTE                                            */
/* -------------------------------------------------- */

private const val ENDUTE_API_BASE_URL =
    "https://api.endute.com/v1"

private const val ENDUTE_ACCOUNT_ID =
    "86b25e34-4065-42c8-99f4-64ef55388dc0"

private const val ENDUTE_SECURE_PREFS =
    "homehub_endute_secure"

private const val ENDUTE_API_KEY_PREF =
    "endute_api_key_encrypted"

private const val ENDUTE_KEYSTORE_ALIAS =
    "homehub_endute_api_key"


private fun getEnduteSecretKey(): SecretKey {

    val keyStore =
        KeyStore
            .getInstance(
                "AndroidKeyStore"
            )
            .apply {
                load(null)
            }


    val existing =
        keyStore.getKey(
            ENDUTE_KEYSTORE_ALIAS,
            null
        ) as? SecretKey


    if (
        existing != null
    ) {

        return existing
    }


    val generator =
        KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )


    generator.init(

        KeyGenParameterSpec.Builder(

            ENDUTE_KEYSTORE_ALIAS,

            KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT

        )

            .setKeySize(256)

            .setBlockModes(
                KeyProperties.BLOCK_MODE_GCM
            )

            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )

            .build()
    )


    return generator.generateKey()
}


private fun hasStoredEnduteApiKey(
    context: Context
): Boolean =

    !context
        .getSharedPreferences(
            ENDUTE_SECURE_PREFS,
            Context.MODE_PRIVATE
        )
        .getString(
            ENDUTE_API_KEY_PREF,
            null
        )
        .isNullOrBlank()


private fun saveEnduteApiKey(
    context: Context,
    apiKey: String
) {

    val cipher =
        Cipher.getInstance(
            "AES/GCM/NoPadding"
        )


    cipher.init(
        Cipher.ENCRYPT_MODE,
        getEnduteSecretKey()
    )


    val encrypted =
        cipher.doFinal(
            apiKey.toByteArray(
                Charsets.UTF_8
            )
        )


    val combined =
        ByteArray(
            cipher.iv.size +
                    encrypted.size
        )


    System.arraycopy(
        cipher.iv,
        0,
        combined,
        0,
        cipher.iv.size
    )


    System.arraycopy(
        encrypted,
        0,
        combined,
        cipher.iv.size,
        encrypted.size
    )


    context
        .getSharedPreferences(
            ENDUTE_SECURE_PREFS,
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            ENDUTE_API_KEY_PREF,
            Base64.encodeToString(
                combined,
                Base64.NO_WRAP
            )
        )
        .apply()
}


private fun loadEnduteApiKey(
    context: Context
): String? {

    val encoded =
        context
            .getSharedPreferences(
                ENDUTE_SECURE_PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                ENDUTE_API_KEY_PREF,
                null
            )
            ?: return null


    val combined =
        Base64.decode(
            encoded,
            Base64.NO_WRAP
        )


    val ivLength =
        12


    if (
        combined.size <= ivLength
    ) {

        throw Exception(
            "Stored Endute API key is invalid. Please replace it."
        )
    }


    val iv =
        combined.copyOfRange(
            0,
            ivLength
        )


    val encrypted =
        combined.copyOfRange(
            ivLength,
            combined.size
        )


    val cipher =
        Cipher.getInstance(
            "AES/GCM/NoPadding"
        )


    cipher.init(
        Cipher.DECRYPT_MODE,
        getEnduteSecretKey(),
        GCMParameterSpec(
            128,
            iv
        )
    )


    return String(
        cipher.doFinal(
            encrypted
        ),
        Charsets.UTF_8
    )
}


private fun clearEnduteApiKey(
    context: Context
) {

    context
        .getSharedPreferences(
            ENDUTE_SECURE_PREFS,
            Context.MODE_PRIVATE
        )
        .edit()
        .remove(
            ENDUTE_API_KEY_PREF
        )
        .apply()


    val keyStore =
        KeyStore
            .getInstance(
                "AndroidKeyStore"
            )
            .apply {
                load(null)
            }


    if (
        keyStore.containsAlias(
            ENDUTE_KEYSTORE_ALIAS
        )
    ) {

        keyStore.deleteEntry(
            ENDUTE_KEYSTORE_ALIAS
        )
    }
}


private fun makeEnduteRequest(
    urlString: String,
    apiKey: String
): String {

    val connection =
        URL(urlString)
            .openConnection() as HttpURLConnection


    try {

        connection.requestMethod =
            "GET"


        connection.setRequestProperty(
            "Authorization",
            "Bearer $apiKey"
        )


        connection.setRequestProperty(
            "Accept",
            "application/json"
        )


        connection.connectTimeout =
            15000


        connection.readTimeout =
            30000


        val responseCode =
            connection.responseCode


        val responseText =
            if (
                responseCode in 200..299
            ) {

                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            } else {

                connection
                    .errorStream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    ?: ""
            }


        if (
            responseCode !in 200..299
        ) {

            val apiMessage =
                try {

                    val json =
                        JSONObject(
                            responseText
                        )


                    json
                        .optJSONObject(
                            "error"
                        )
                        ?.optString(
                            "message",
                            json.optString(
                                "message",
                                ""
                            )
                        )
                        ?: ""

                } catch (
                    _: Exception
                ) {

                    ""
                }


            val message =
                when (
                    responseCode
                ) {

                    401 ->
                        "Endute API key is invalid or revoked. Replace it."

                    403 ->
                        "Endute access is not currently active."

                    429 ->
                        "Endute is temporarily rate-limiting requests. Try again shortly."

                    else ->
                        apiMessage.ifBlank {
                            "Endute returned HTTP $responseCode."
                        }
                }


            throw Exception(
                message
            )
        }


        return responseText

    } finally {

        connection.disconnect()
    }
}


private fun parseEnduteTransactions(
    responseText: String
): Pair<JSONArray, String?> {

    val trimmed =
        responseText.trim()


    if (
        trimmed.startsWith("[")
    ) {

        return Pair(
            JSONArray(trimmed),
            null
        )
    }


    val json =
        JSONObject(
            trimmed
        )


    val transactions =
        json.optJSONArray(
            "transactions"
        )
            ?: json.optJSONArray(
                "results"
            )
            ?: throw Exception(
                "Endute transactions response did not contain transactions."
            )


    val next =
        if (
            json.isNull("next")
        ) {

            null

        } else {

            json
                .optString(
                    "next",
                    ""
                )
                .takeIf {
                    it.isNotBlank()
                }
        }


    return Pair(
        transactions,
        next
    )
}


private fun fetchEnduteTransactions(
    context: Context,
    requestedCount: Int
): List<BankTransaction> {

    val apiKey =
        loadEnduteApiKey(context)
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?: throw Exception(
                "Save your Endute API key first."
            )


    val safeCount =
        requestedCount.coerceAtLeast(
            1
        )


    val transactions =
        mutableListOf<BankTransaction>()


    val seenIds =
        mutableSetOf<String>()


    var nextUrl: String? =
        "$ENDUTE_API_BASE_URL/accounts/$ENDUTE_ACCOUNT_ID/transactions"


    while (
        nextUrl != null &&
        transactions.size < safeCount
    ) {

        val (
            page,
            pageNext
        ) =
            parseEnduteTransactions(
                makeEnduteRequest(
                    nextUrl!!,
                    apiKey
                )
            )


        for (
        i in 0 until page.length()
        ) {

            if (
                transactions.size >= safeCount
            ) {

                break
            }


            val item =
                page.getJSONObject(i)


            val id =
                item.optString(
                    "id",
                    ""
                )


            if (
                id.isBlank() ||
                !seenIds.add(id)
            ) {

                continue
            }


            val amountText =
                item.optString(
                    "amount",
                    ""
                )


            val amount =
                amountText.toDoubleOrNull()
                    ?: item.optDouble(
                        "amount",
                        Double.NaN
                    )


            if (
                amount.isNaN()
            ) {

                continue
            }


            val description =
                item
                    .optString(
                        "description",
                        ""
                    )
                    .trim()
                    .ifBlank {
                        "Bank transaction"
                    }


            val date =
                item
                    .optString(
                        "booking_date",
                        ""
                    )
                    .ifBlank {
                        item.optString(
                            "value_date",
                            ""
                        )
                    }


            val counterparty =
                item
                    .optString(
                        "counterparty",
                        ""
                    )
                    .trim()


            val enrichment =
                item.optJSONObject(
                    "enrichment"
                )


            val merchantName =
                enrichment
                    ?.optString(
                        "merchant_name",
                        ""
                    )
                    ?.trim()
                    .orEmpty()


            val upstreamTransactionId =
                item
                    .optString(
                        "upstream_transaction_id",
                        ""
                    )
                    .trim()


            transactions.add(

                BankTransaction(

                    id =
                        id,

                    description =
                        description,

                    amount =
                        amount,

                    date =
                        date,

                    upstreamTransactionId =
                        upstreamTransactionId,

                    counterparty =
                        counterparty,

                    merchantName =
                        merchantName
                )
            )
        }


        nextUrl =
            pageNext
    }


    return transactions
}


/* -------------------------------------------------- */
/* RECEIPTS SCREEN                                    */
/* -------------------------------------------------- */

@Composable
fun ReceiptsScreen(
    onBack: () -> Unit,
    onBankSync: () -> Unit
) {

    val context =
        LocalContext.current


    val keyboardController =
        LocalSoftwareKeyboardController.current


    var transactions by remember {

        mutableStateOf(
            loadTransactions(
                context
            ).toList()
        )
    }


    var descriptionText by remember {
        mutableStateOf("")
    }


    var amountText by remember {
        mutableStateOf("")
    }


    var moneyOutSelected by remember {
        mutableStateOf(true)
    }


    var editingTransactionIndex by remember {
        mutableStateOf<Int?>(null)
    }


    var showResetDialog by remember {
        mutableStateOf(false)
    }


    var checkResult by remember {
        mutableStateOf<String?>(null)
    }


    val runningBalance =
        transactions.sumOf {
            it.amount
        }


    val recentTransactionIndices =
        transactions
            .indices
            .toList()
            .takeLast(30)


    val historyListState =
        rememberLazyListState()


    LaunchedEffect(
        transactions.size
    ) {

        if (
            recentTransactionIndices.isNotEmpty()
        ) {

            historyListState.animateScrollToItem(
                recentTransactionIndices.lastIndex
            )
        }
    }


    val textFieldColors =
        OutlinedTextFieldDefaults.colors(

            focusedContainerColor =
                Color.White,

            unfocusedContainerColor =
                Color.White,

            disabledContainerColor =
                Color.White,

            focusedTextColor =
                HOMEHUB_TEXT,

            unfocusedTextColor =
                HOMEHUB_TEXT,

            focusedBorderColor =
                HOMEHUB_PRIMARY,

            unfocusedBorderColor =
                HOMEHUB_OUTLINE,

            focusedLabelColor =
                HOMEHUB_PRIMARY,

            unfocusedLabelColor =
                HOMEHUB_PRIMARY,

            cursorColor =
                HOMEHUB_PRIMARY
        )


    fun checkLast30Amount() {

        val enteredAmount =
            amountText
                .replace(
                    "£",
                    ""
                )
                .replace(
                    ",",
                    ""
                )
                .trim()
                .toDoubleOrNull()


        if (
            enteredAmount == null ||
            enteredAmount <= 0
        ) {

            checkResult =
                "Enter an amount to check."

            return
        }


        val targetAmount =
            abs(
                enteredAmount
            )


        val recentTransactions =
            transactions
                .takeLast(30)
                .asReversed()


        val matches =
            recentTransactions.filter {

                abs(
                    abs(it.amount) -
                            targetAmount
                ) < 0.005
            }


        checkResult =
            if (
                matches.isEmpty()
            ) {

                "£%.2f has NOT been entered in the last 30 records."
                    .format(
                        targetAmount
                    )

            } else {

                val matchText =
                    matches.joinToString(
                        "\n"
                    ) { transaction ->

                        "${transaction.description} — " +
                                "${formatSignedMoney(transaction.amount)}"
                    }


                "£%.2f has already been entered %d time%s in the last 30 records:\n\n%s"
                    .format(
                        targetAmount,

                        matches.size,

                        if (
                            matches.size == 1
                        ) {
                            ""
                        } else {
                            "s"
                        },

                        matchText
                    )
            }
    }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(20.dp)
    ) {

        ScreenBackButton(
            onBack =
                onBack
        )


        Spacer(
            modifier =
                Modifier.height(2.dp)
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    "Receipts",

                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )
        }


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        /* ---------------- BALANCE ---------------- */

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(14.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color.White,

                    contentColor =
                        HOMEHUB_TEXT
                ),

            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        "Balance",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    modifier =
                        Modifier.weight(1f)
                )


                Text(
                    text =
                        formatMoney(
                            runningBalance
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Button(
            onClick =
                onBankSync,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp),

            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        HOMEHUB_SECONDARY,

                    contentColor =
                        HOMEHUB_PRIMARY
                )
        ) {

            Text(
                text =
                    "SYNC VIRGIN MONEY"
            )
        }


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        /* ---------------- DESCRIPTION ---------------- */

        OutlinedTextField(
            value =
                descriptionText,

            onValueChange = {
                descriptionText = it
            },

            modifier =
                Modifier.fillMaxWidth(),

            placeholder = {
                Text(
                    "Description"
                )
            },

            singleLine =
                true,

            keyboardOptions =
                KeyboardOptions(
                    capitalization =
                        KeyboardCapitalization.Sentences
                ),

            colors =
                textFieldColors
        )


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        /* ---------------- AMOUNT ---------------- */

        OutlinedTextField(
            value =
                amountText,

            onValueChange = {

                amountText =
                    it
                        .replace(
                            "£",
                            ""
                        )
                        .replace(
                            "+",
                            ""
                        )
                        .replace(
                            "-",
                            "")

                checkResult =
                    null
            },

            modifier =
                Modifier.fillMaxWidth(),

            placeholder = {
                Text(
                    "Amount"
                )
            },

            singleLine =
                true,

            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Number
                ),

            colors =
                textFieldColors
        )


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        /* ---------------- CHECK LAST 30 ---------------- */

        Button(
            onClick = {
                checkLast30Amount()
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(45.dp),

            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        HOMEHUB_SECONDARY,

                    contentColor =
                        HOMEHUB_PRIMARY
                )
        ) {

            Text(
                text =
                    "CHECK LAST 30"
            )
        }


        if (
            checkResult != null
        ) {

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )


            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(12.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White,

                        contentColor =
                            HOMEHUB_TEXT
                    ),

                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
            ) {

                Text(
                    text =
                        checkResult!!,

                    modifier =
                        Modifier.padding(12.dp),

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        /* ---------------- MONEY IN / OUT ---------------- */

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = {
                    moneyOutSelected = true
                },

                modifier =
                    Modifier
                        .weight(1f)
                        .height(50.dp),

                colors =
                    ButtonDefaults.buttonColors(

                        containerColor =
                            if (
                                moneyOutSelected
                            ) {

                                HOMEHUB_OUTGOING

                            } else {

                                HOMEHUB_SECONDARY
                            },

                        contentColor =
                            if (
                                moneyOutSelected
                            ) {

                                Color.White

                            } else {

                                HOMEHUB_PRIMARY
                            }
                    )
            ) {

                Text(
                    text =
                        if (
                            moneyOutSelected
                        ) {

                            "✓ MONEY OUT"

                        } else {

                            "MONEY OUT"
                        }
                )
            }


            Button(
                onClick = {
                    moneyOutSelected = false
                },

                modifier =
                    Modifier
                        .weight(1f)
                        .height(50.dp),

                colors =
                    ButtonDefaults.buttonColors(

                        containerColor =
                            if (
                                !moneyOutSelected
                            ) {

                                HOMEHUB_INCOME

                            } else {

                                HOMEHUB_SECONDARY
                            },

                        contentColor =
                            if (
                                !moneyOutSelected
                            ) {

                                Color.White

                            } else {

                                HOMEHUB_PRIMARY
                            }
                    )
            ) {

                Text(
                    text =
                        if (
                            !moneyOutSelected
                        ) {

                            "✓ MONEY IN"

                        } else {

                            "MONEY IN"
                        }
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        /* ---------------- SUBMIT / UPDATE ---------------- */

        Button(
            onClick = {

                val enteredAmount =
                    amountText
                        .replace(
                            "£",
                            ""
                        )
                        .replace(
                            ",",
                            ""
                        )
                        .trim()
                        .toDoubleOrNull()


                if (
                    descriptionText.isNotBlank() &&
                    enteredAmount != null &&
                    enteredAmount > 0
                ) {

                    val signedAmount =
                        if (
                            moneyOutSelected
                        ) {

                            -enteredAmount

                        } else {

                            enteredAmount
                        }


                    val updatedTransactions =
                        transactions.toMutableList()


                    val editingIndex =
                        editingTransactionIndex


                    if (
                        editingIndex != null &&
                        editingIndex in
                        updatedTransactions.indices
                    ) {

                        val existingTransaction =
                            updatedTransactions[
                                editingIndex
                            ]


                        updatedTransactions[
                            editingIndex
                        ] =
                            existingTransaction.copy(

                                description =
                                    descriptionText.trim(),

                                amount =
                                    signedAmount,

                                date =
                                    if (
                                        existingTransaction
                                            .date
                                            .isBlank()
                                    ) {

                                        LocalDate
                                            .now()
                                            .toString()

                                    } else {

                                        existingTransaction.date
                                    }
                            )

                    } else {

                        updatedTransactions.add(

                            AccountTransaction(

                                description =
                                    descriptionText.trim(),

                                amount =
                                    signedAmount,

                                date =
                                    LocalDate
                                        .now()
                                        .toString(),

                                source =
                                    "MANUAL"
                            )
                        )
                    }


                    saveTransactions(
                        context,
                        updatedTransactions
                    )


                    transactions =
                        updatedTransactions


                    descriptionText =
                        ""

                    amountText =
                        ""

                    editingTransactionIndex =
                        null

                    checkResult =
                        null


                    keyboardController?.hide()
                }
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
        ) {

            Text(
                text =
                    if (
                        editingTransactionIndex != null
                    ) {

                        "UPDATE"

                    } else {

                        "SUBMIT"
                    }
            )
        }


        /* ---------------- CANCEL EDIT ---------------- */

        if (
            editingTransactionIndex != null
        ) {

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )


            TextButton(
                onClick = {

                    descriptionText =
                        ""

                    amountText =
                        ""

                    editingTransactionIndex =
                        null

                    checkResult =
                        null

                    keyboardController?.hide()
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "CANCEL EDIT"
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(
                    if (
                        editingTransactionIndex != null
                    ) {

                        2.dp

                    } else {

                        8.dp
                    }
                )
        )


        /* ---------------- HISTORY TITLE ---------------- */

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    "Last 30 Entries",

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall
            )
        }


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        /* ---------------- HISTORY ---------------- */

        if (
            recentTransactionIndices.isEmpty()
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(2f),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Top
            ) {

                Text(
                    text =
                        "No transactions yet.",

                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )
            }

        } else {

            LazyColumn(
                state =
                    historyListState,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(2f),

                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                items(
                    recentTransactionIndices
                ) { transactionIndex ->

                    val transaction =
                        transactions[
                            transactionIndex
                        ]


                    val balanceAfter =
                        transactions
                            .take(
                                transactionIndex + 1
                            )
                            .sumOf {
                                it.amount
                            }


                    AccountTransactionRow(
                        transaction =
                            transaction,

                        balanceAfter =
                            balanceAfter,

                        onEdit = {

                            descriptionText =
                                transaction.description


                            amountText =
                                abs(
                                    transaction.amount
                                ).toString()


                            moneyOutSelected =
                                transaction.amount < 0


                            editingTransactionIndex =
                                transactionIndex

                            checkResult =
                                null
                        },

                        onDelete = {

                            val updatedTransactions =
                                transactions.toMutableList()


                            if (
                                transactionIndex in
                                updatedTransactions.indices
                            ) {

                                updatedTransactions.removeAt(
                                    transactionIndex
                                )


                                saveTransactions(
                                    context,
                                    updatedTransactions
                                )


                                transactions =
                                    updatedTransactions


                                if (
                                    editingTransactionIndex ==
                                    transactionIndex
                                ) {

                                    descriptionText =
                                        ""

                                    amountText =
                                        ""

                                    editingTransactionIndex =
                                        null

                                    checkResult =
                                        null
                                }
                            }
                        }
                    )
                }
            }
        }


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        /* ---------------- RESET ---------------- */

        Button(
            onClick = {
                showResetDialog = true
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
        ) {

            Text(
                text =
                    "RESET ACCOUNT"
            )
        }
    }


    /* ---------------- RESET DIALOG ---------------- */

    if (
        showResetDialog
    ) {

        AlertDialog(

            onDismissRequest = {
                showResetDialog = false
            },

            title = {

                Text(
                    text =
                        "Reset account?"
                )
            },

            text = {

                Text(
                    text =
                        "This will delete all transactions and reset the balance to £0.00."
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        context
                            .getSharedPreferences(
                                RECEIPTS_PREFS,
                                Context.MODE_PRIVATE
                            )
                            .edit()
                            .clear()
                            .apply()


                        transactions =
                            emptyList()

                        descriptionText =
                            ""

                        amountText =
                            ""

                        editingTransactionIndex =
                            null

                        checkResult =
                            null

                        showResetDialog =
                            false
                    }
                ) {

                    Text(
                        text =
                            "RESET"
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showResetDialog = false
                    }
                ) {

                    Text(
                        text =
                            "CANCEL"
                    )
                }
            }
        )
    }
}


/* -------------------------------------------------- */
/* BANK SYNC SCREEN                                   */
/* -------------------------------------------------- */

data class BankSyncPreview(
    val transaction: BankTransaction,
    val status: String,
    val statusDetail: String,
    val existingIndex: Int? = null
)


fun buildBankSyncPreview(
    transactions: List<AccountTransaction>,
    bankTransactions: List<BankTransaction>
): List<BankSyncPreview> {

    val reservedIndices =
        mutableSetOf<Int>()


    return bankTransactions.map { bankTransaction ->

        /* ------------------------------------------ */
        /* FIRST: ENDUTE ID                           */
        /* ------------------------------------------ */

        val idIndex =
            transactions
                .mapIndexedNotNull { index, transaction ->

                    if (
                        reservedIndices.contains(
                            index
                        )
                    ) {

                        return@mapIndexedNotNull null
                    }


                    if (
                        bankTransaction.id.isNotBlank() &&
                        transaction.bankTransactionId ==
                        bankTransaction.id
                    ) {

                        index

                    } else {

                        null
                    }
                }
                .firstOrNull()


        if (
            idIndex != null
        ) {

            return@map BankSyncPreview(

                transaction =
                    bankTransaction,

                status =
                    "ALREADY IMPORTED",

                statusDetail =
                    "This Endute transaction ID is already linked to this HomeHub entry.",

                existingIndex =
                    idIndex
            )
        }


        /* ------------------------------------------ */
        /* SECOND: VIRGIN MONEY UPSTREAM ID           */
        /* ------------------------------------------ */

        val upstreamIndex =
            transactions
                .mapIndexedNotNull { index, transaction ->

                    if (
                        reservedIndices.contains(
                            index
                        )
                    ) {

                        return@mapIndexedNotNull null
                    }


                    if (
                        bankTransaction
                            .upstreamTransactionId
                            .isNotBlank() &&

                        transaction
                            .upstreamBankTransactionId ==
                        bankTransaction
                            .upstreamTransactionId
                    ) {

                        index

                    } else {

                        null
                    }
                }
                .firstOrNull()


        if (
            upstreamIndex != null
        ) {

            return@map BankSyncPreview(

                transaction =
                    bankTransaction,

                status =
                    "ALREADY IMPORTED",

                statusDetail =
                    "This Virgin Money transaction ID is already linked to this HomeHub entry.",

                existingIndex =
                    upstreamIndex
            )
        }


        /* ------------------------------------------ */
        /* FIND SAFE MANUAL MATCH                     */
        /* ------------------------------------------ */

        val eligibleIndices =
            transactions
                .mapIndexedNotNull { index, transaction ->

                    if (
                        reservedIndices.contains(
                            index
                        )
                    ) {

                        return@mapIndexedNotNull null
                    }


                    if (
                        transaction.bankTransactionId.isNotBlank() ||
                        transaction.upstreamBankTransactionId.isNotBlank()
                    ) {

                        return@mapIndexedNotNull null
                    }


                    if (
                        abs(
                            transaction.amount -
                                    bankTransaction.amount
                        ) >= 0.005
                    ) {

                        return@mapIndexedNotNull null
                    }


                    index
                }


        val scored =
            eligibleIndices
                .map { index ->

                    Pair(
                        index,

                        transactionMatchScore(
                            transactions[index],
                            bankTransaction
                        )
                    )
                }
                .filter {
                    it.second > 0
                }
                .sortedByDescending {
                    it.second
                }


        val matchIndex: Int?
        val detail: String


        if (
            scored.isNotEmpty() &&
            scored.first().second >= 60
        ) {

            val highestScore =
                scored.first().second


            val highestMatches =
                scored.count {
                    it.second == highestScore
                }


            if (
                highestMatches == 1
            ) {

                matchIndex =
                    scored.first().first


                detail =
                    "A unique existing entry matches the amount and merchant/description. It will be linked and its HomeHub date will be changed to the bank transaction date."

            } else {

                matchIndex =
                    null


                detail =
                    "More than one existing entry matches this transaction equally well. HomeHub will not guess which one to link."
            }

        } else {

            matchIndex =
                null


            detail =
                if (
                    eligibleIndices.isEmpty()
                ) {

                    "No unlinked HomeHub entry matches this transaction."

                } else {

                    "No sufficiently strong unique match was found. This will be treated as a new transaction unless you deselect it."
                }
        }


        if (
            matchIndex != null
        ) {

            reservedIndices.add(
                matchIndex
            )


            BankSyncPreview(

                transaction =
                    bankTransaction,

                status =
                    "WILL LINK",

                statusDetail =
                    detail,

                existingIndex =
                    matchIndex
            )

        } else {

            BankSyncPreview(

                transaction =
                    bankTransaction,

                status =
                    "NEW",

                statusDetail =
                    "No unique existing entry can be safely linked. A new bank transaction will be added if you select it for import.",

                existingIndex =
                    null
            )
        }
    }
}


/* -------------------------------------------------- */
/* BANK SYNC SCREEN                                   */
/* -------------------------------------------------- */

@Composable
fun BankSyncScreen(
    onBack: () -> Unit
) {

    val context =
        LocalContext.current


    val keyboardController =
        LocalSoftwareKeyboardController.current


    var bankTransactions by remember {
        mutableStateOf<List<BankTransaction>>(
            emptyList()
        )
    }


    var previewItems by remember {
        mutableStateOf<List<BankSyncPreview>>(
            emptyList()
        )
    }


    var selectedBankIds by remember {
        mutableStateOf<Set<String>>(
            emptySet()
        )
    }


    var syncMessage by remember {
        mutableStateOf<String?>(null)
    }


    var transactionCount by remember {
        mutableStateOf("")
    }


    var apiKeyInput by remember {
        mutableStateOf("")
    }


    var apiKeySaved by remember {
        mutableStateOf(
            hasStoredEnduteApiKey(
                context
            )
        )
    }


    var showApiKeyEntry by remember {
        mutableStateOf(
            !apiKeySaved
        )
    }


    LazyColumn(

        modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        /* ------------------------------------------ */
        /* BACK                                     */
        /* ------------------------------------------ */

        item {

            ScreenBackButton(
                onBack =
                    onBack
            )
        }


        /* ------------------------------------------ */
        /* TITLE                                    */
        /* ------------------------------------------ */

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.Center
            ) {

                Text(
                    text =
                        "Virgin Money Sync",

                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium
                )
            }
        }


        /* ------------------------------------------ */
        /* CONNECTION INFO                           */
        /* ------------------------------------------ */

        item {

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(14.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White,

                        contentColor =
                            HOMEHUB_TEXT
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp)
                ) {

                    Text(
                        text =
                            "Endute connection",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(
                        text =
                            "HomeHub connects directly to Endute from this phone. Fetching does not change any HomeHub data. Review and select the transactions you want to import or link.",

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )
                }
            }
        }


        /* ------------------------------------------ */
        /* API KEY                                   */
        /* ------------------------------------------ */

        item {

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(14.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White,

                        contentColor =
                            HOMEHUB_TEXT
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp)
                ) {

                    Text(
                        text =
                            "ENDUTE API KEY",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    if (
                        apiKeySaved &&
                        !showApiKeyEntry
                    ) {

                        Text(
                            text =
                                "API key is saved securely on this device.",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )


                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )


                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {

                            Button(

                                onClick = {

                                    apiKeyInput =
                                        ""

                                    showApiKeyEntry =
                                        true
                                },

                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),

                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            HOMEHUB_SECONDARY,

                                        contentColor =
                                            HOMEHUB_PRIMARY
                                    )
                            ) {

                                Text(
                                    "REPLACE API KEY"
                                )
                            }


                            Button(

                                onClick = {

                                    clearEnduteApiKey(
                                        context
                                    )

                                    apiKeySaved =
                                        false

                                    apiKeyInput =
                                        ""

                                    showApiKeyEntry =
                                        true

                                    syncMessage =
                                        "Endute API key cleared."
                                },

                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),

                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            HOMEHUB_SECONDARY,

                                        contentColor =
                                            HOMEHUB_PRIMARY
                                    )
                            ) {

                                Text(
                                    "CLEAR API KEY"
                                )
                            }
                        }

                    } else {

                        OutlinedTextField(

                            value =
                                apiKeyInput,

                            onValueChange = {
                                apiKeyInput =
                                    it
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            placeholder = {
                                Text(
                                    "Enter Endute API key"
                                )
                            },

                            singleLine =
                                true,

                            visualTransformation =
                                PasswordVisualTransformation(),

                            colors =
                                OutlinedTextFieldDefaults.colors(

                                    focusedContainerColor =
                                        Color.White,

                                    unfocusedContainerColor =
                                        Color.White,

                                    focusedBorderColor =
                                        HOMEHUB_TEXT,

                                    unfocusedBorderColor =
                                        HOMEHUB_TEXT,

                                    focusedTextColor =
                                        HOMEHUB_TEXT,

                                    unfocusedTextColor =
                                        HOMEHUB_TEXT,

                                    cursorColor =
                                        HOMEHUB_TEXT
                                )
                        )


                        Spacer(
                            modifier =
                                Modifier.height(6.dp)
                        )


                        Row(

                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {

                            Button(

                                onClick = {

                                    val enteredKey =
                                        apiKeyInput
                                            .trim()


                                    if (
                                        !enteredKey
                                            .startsWith(
                                                "edk_"
                                            )
                                    ) {

                                        syncMessage =
                                            "Enter a valid Endute API key."

                                        return@Button
                                    }


                                    try {

                                        saveEnduteApiKey(
                                            context,
                                            enteredKey
                                        )


                                        apiKeyInput =
                                            ""

                                        apiKeySaved =
                                            true

                                        showApiKeyEntry =
                                            false

                                        syncMessage =
                                            "Endute API key saved securely on this device."

                                    } catch (
                                        e: Exception
                                    ) {

                                        syncMessage =
                                            "Couldn't save the Endute API key: ${e.message ?: "Unknown error"}"
                                    }
                                },

                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp)
                            ) {

                                Text(
                                    "SAVE API KEY"
                                )
                            }


                            if (
                                apiKeySaved
                            ) {

                                TextButton(

                                    onClick = {

                                        apiKeyInput =
                                            ""

                                        showApiKeyEntry =
                                            false
                                    },

                                    modifier =
                                        Modifier.height(
                                            48.dp
                                        )
                                ) {

                                    Text(
                                        "CANCEL"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        /* ------------------------------------------ */
        /* COUNT                                     */
        /* ------------------------------------------ */

        item {

            OutlinedTextField(

                value =
                    transactionCount,

                onValueChange = { value ->

                    transactionCount =
                        value.filter {
                            it.isDigit()
                        }
                },

                modifier =
                    Modifier.fillMaxWidth(),

                placeholder = {
                    Text(
                        "Number of transactions to review"
                    )
                },

                singleLine =
                    true,

                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Number
                    ),

                colors =
                    OutlinedTextFieldDefaults.colors(

                        focusedContainerColor =
                            Color.White,

                        unfocusedContainerColor =
                            Color.White,

                        focusedBorderColor =
                            HOMEHUB_TEXT,

                        unfocusedBorderColor =
                            HOMEHUB_TEXT,

                        focusedTextColor =
                            HOMEHUB_TEXT,

                        unfocusedTextColor =
                            HOMEHUB_TEXT,

                        cursorColor =
                            HOMEHUB_TEXT
                    )
            )
        }


        /* ------------------------------------------ */
        /* FETCH                                     */
        /* ------------------------------------------ */

        item {

            Button(

                onClick = {

                    keyboardController?.hide()


                    val count =
                        transactionCount
                            .toIntOrNull()
                            ?.coerceAtLeast(
                                1
                            )


                    if (
                        count == null
                    ) {

                        syncMessage =
                            "Enter a valid number of transactions to review."

                        return@Button
                    }


                    if (
                        !apiKeySaved
                    ) {

                        syncMessage =
                            "Save your Endute API key first."

                        return@Button
                    }


                    syncMessage =
                        "Connecting to Endute..."


                    Executors
                        .newSingleThreadExecutor()
                        .execute {

                            try {

                                val fetched =
                                    fetchEnduteTransactions(
                                        context,
                                        count
                                    )


                                val existing =
                                    loadTransactions(
                                        context
                                    )


                                val preview =
                                    buildBankSyncPreview(
                                        existing,
                                        fetched
                                    )


                                val defaultSelection =
                                    preview
                                        .filter {
                                            it.status !=
                                                    "ALREADY IMPORTED"
                                        }
                                        .map {
                                            it.transaction.id
                                        }
                                        .toSet()


                                context
                                    .mainExecutor
                                    .execute {

                                        bankTransactions =
                                            fetched

                                        previewItems =
                                            preview

                                        selectedBankIds =
                                            defaultSelection

                                        syncMessage =
                                            "Fetched ${fetched.size} transaction(s) from Endute. Review the selections below before importing."
                                    }

                            } catch (
                                e: Exception
                            ) {

                                context
                                    .mainExecutor
                                    .execute {

                                        bankTransactions =
                                            emptyList()

                                        previewItems =
                                            emptyList()

                                        selectedBankIds =
                                            emptySet()

                                        syncMessage =
                                            e.message
                                                ?: "Endute error: Unknown error"
                                    }
                            }
                        }
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
            ) {

                Text(
                    "FETCH TRANSACTIONS"
                )
            }
        }


        /* ------------------------------------------ */
        /* MESSAGE                                    */
        /* ------------------------------------------ */

        if (
            syncMessage != null
        ) {

            item {

                Card(

                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(12.dp),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Color.White,

                            contentColor =
                                HOMEHUB_TEXT
                        )
                ) {

                    Text(

                        text =
                            syncMessage!!,

                        modifier =
                            Modifier.padding(
                                12.dp
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )
                }
            }
        }


        /* ------------------------------------------ */
        /* PREVIEW                                   */
        /* ------------------------------------------ */

        if (
            previewItems.isNotEmpty()
        ) {

            val alreadyImported =
                previewItems.count {
                    it.status ==
                            "ALREADY IMPORTED"
                }


            val willLink =
                previewItems.count {
                    it.status ==
                            "WILL LINK"
                }


            val newCount =
                previewItems.count {
                    it.status ==
                            "NEW"
                }


            val selectedCount =
                previewItems.count {
                    it.transaction.id in
                            selectedBankIds
                }


            item {

                Card(

                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(12.dp),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Color.White,

                            contentColor =
                                HOMEHUB_TEXT
                        )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                12.dp
                            )
                    ) {

                        Text(

                            "IMPORT PREVIEW",

                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )


                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )


                        Text(

                            "$newCount NEW  •  " +
                                    "$willLink WILL LINK  •  " +
                                    "$alreadyImported ALREADY IMPORTED",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )


                        Spacer(
                            modifier =
                                Modifier.height(6.dp)
                        )


                        Text(

                            "$selectedCount selected",

                            style =
                                MaterialTheme
                                    .typography
                                    .titleSmall
                        )


                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )


                        Text(

                            "WILL LINK keeps your manual description and amount, but updates its HomeHub date to the actual bank transaction date.",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }


            /* -------------------------------------- */
            /* SELECT ALL / NONE                      */
            /* -------------------------------------- */

            item {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Button(

                        onClick = {

                            selectedBankIds =
                                previewItems
                                    .filter {
                                        it.status !=
                                                "ALREADY IMPORTED"
                                    }
                                    .map {
                                        it.transaction.id
                                    }
                                    .toSet()
                        },

                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    HOMEHUB_SECONDARY,

                                contentColor =
                                    HOMEHUB_PRIMARY
                            )
                    ) {

                        Text(
                            "SELECT ALL"
                        )
                    }


                    Button(

                        onClick = {

                            selectedBankIds =
                                emptySet()
                        },

                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    HOMEHUB_SECONDARY,

                                contentColor =
                                    HOMEHUB_PRIMARY
                            )
                    ) {

                        Text(
                            "DESELECT ALL"
                        )
                    }
                }
            }


            /* -------------------------------------- */
            /* IMPORT SELECTED                        */
            /* -------------------------------------- */

            item {

                Button(

                    onClick = {

                        if (
                            selectedBankIds.isEmpty()
                        ) {

                            syncMessage =
                                "Nothing is selected to import."

                            return@Button
                        }


                        val existing =
                            loadTransactions(
                                context
                            ).toMutableList()


                        var imported =
                            0


                        var matched =
                            0


                        var skipped =
                            0


                        previewItems.forEach { preview ->

                            val bankTransaction =
                                preview.transaction


                            if (
                                bankTransaction.id
                                !in selectedBankIds
                            ) {

                                return@forEach
                            }


                            when (
                                preview.status
                            ) {

                                "ALREADY IMPORTED" -> {

                                    skipped++
                                }


                                "WILL LINK" -> {

                                    val matchIndex =
                                        preview.existingIndex


                                    if (
                                        matchIndex != null &&
                                        matchIndex in
                                        existing.indices
                                    ) {

                                        val existingTransaction =
                                            existing[
                                                matchIndex
                                            ]


                                        existing[
                                            matchIndex
                                        ] =
                                            existingTransaction.copy(

                                                /* Keep manual description */

                                                description =
                                                    existingTransaction
                                                        .description,

                                                /* Keep manual amount */

                                                amount =
                                                    existingTransaction
                                                        .amount,

                                                /* BACKDATE TO BANK DATE */

                                                date =
                                                    bankTransaction
                                                        .date
                                                        .ifBlank {
                                                            existingTransaction
                                                                .date
                                                        },

                                                source =
                                                    if (
                                                        existingTransaction
                                                            .source ==
                                                        "MANUAL"
                                                    ) {

                                                        "MATCHED"

                                                    } else {

                                                        existingTransaction
                                                            .source
                                                    },

                                                bankTransactionId =
                                                    bankTransaction.id,

                                                upstreamBankTransactionId =
                                                    bankTransaction
                                                        .upstreamTransactionId
                                            )


                                        matched++

                                    } else {

                                        existing.add(

                                            AccountTransaction(

                                                description =
                                                    bankTransaction
                                                        .description,

                                                amount =
                                                    bankTransaction
                                                        .amount,

                                                date =
                                                    bankTransaction
                                                        .date,

                                                source =
                                                    "BANK",

                                                bankTransactionId =
                                                    bankTransaction
                                                        .id,

                                                upstreamBankTransactionId =
                                                    bankTransaction
                                                        .upstreamTransactionId
                                            )
                                        )


                                        imported++
                                    }
                                }


                                "NEW" -> {

                                    existing.add(

                                        AccountTransaction(

                                            description =
                                                bankTransaction
                                                    .description,

                                            amount =
                                                bankTransaction
                                                    .amount,

                                            date =
                                                bankTransaction
                                                    .date,

                                            source =
                                                "BANK",

                                            bankTransactionId =
                                                bankTransaction.id,

                                            upstreamBankTransactionId =
                                                bankTransaction
                                                    .upstreamTransactionId
                                        )
                                    )


                                    imported++
                                }
                            }
                        }


                        saveTransactions(
                            context,
                            existing
                        )


                        val refreshedPreview =
                            buildBankSyncPreview(
                                existing,
                                bankTransactions
                            )


                        previewItems =
                            refreshedPreview


                        selectedBankIds =
                            refreshedPreview
                                .filter {
                                    it.status !=
                                            "ALREADY IMPORTED"
                                }
                                .map {
                                    it.transaction.id
                                }
                                .toSet()


                        syncMessage =
                            "Import complete: $imported new, $matched linked/backdated, $skipped already imported and skipped."
                    },

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp),

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                HOMEHUB_SECONDARY,

                            contentColor =
                                HOMEHUB_PRIMARY
                        )
                ) {

                    Text(
                        "IMPORT / UPSERT SELECTED"
                    )
                }
            }


            /* -------------------------------------- */
            /* TRANSACTION LIST                       */
            /* -------------------------------------- */

            item {

                Text(

                    text =
                        "What will happen to each transaction",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )
            }


            items(
                previewItems
            ) { preview ->

                val transaction =
                    preview.transaction


                val isSelected =
                    transaction.id in
                            selectedBankIds


                Card(

                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(12.dp),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Color.White,

                            contentColor =
                                HOMEHUB_TEXT
                        )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                10.dp
                            )
                    ) {

                        /* ------------------------ */
                        /* SELECT + TRANSACTION     */
                        /* ------------------------ */

                        Row(

                            modifier =
                                Modifier.fillMaxWidth(),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Checkbox(

                                checked =
                                    isSelected,

                                onCheckedChange = { checked ->

                                    if (
                                        preview.status ==
                                        "ALREADY IMPORTED"
                                    ) {

                                        return@Checkbox
                                    }


                                    selectedBankIds =
                                        if (
                                            checked
                                        ) {

                                            selectedBankIds +
                                                    transaction.id

                                        } else {

                                            selectedBankIds -
                                                    transaction.id
                                        }
                                },

                                enabled =
                                    preview.status !=
                                            "ALREADY IMPORTED"
                            )


                            Column(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {

                                Text(

                                    text =
                                        transaction
                                            .description,

                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleSmall
                                )


                                Text(

                                    text =
                                        transaction.date,

                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall
                                )
                            }


                            Text(

                                text =
                                    formatSignedMoney(
                                        transaction.amount
                                    ),

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyLarge,

                                color =
                                    if (
                                        transaction.amount >= 0
                                    ) {

                                        HOMEHUB_INCOME

                                    } else {

                                        HOMEHUB_OUTGOING
                                    }
                            )
                        }


                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )


                        /* ------------------------ */
                        /* STATUS                   */
                        /* ------------------------ */

                        Text(

                            text =
                                preview.status,

                            style =
                                MaterialTheme
                                    .typography
                                    .labelLarge,

                            color =
                                when (
                                    preview.status
                                ) {

                                    "NEW" ->
                                        HOMEHUB_PRIMARY

                                    "ALREADY IMPORTED",
                                    "WILL LINK" ->
                                        HOMEHUB_INCOME

                                    "WILL UPDATE" ->
                                        HOMEHUB_PRIMARY

                                    else ->
                                        HOMEHUB_TEXT
                                }
                        )


                        Text(

                            text =
                                preview.statusDetail,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }

        } else {

            item {

                Column(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 8.dp
                            ),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(

                        text =
                            "Nothing fetched yet.",

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )
                }
            }
        }
    }
}


/* -------------------------------------------------- */
/* ACCOUNT TRANSACTION ROW                            */
/* -------------------------------------------------- */

@Composable
fun AccountTransactionRow(
    transaction: AccountTransaction,
    balanceAfter: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {

    var showDeleteConfirmation by remember {
        mutableStateOf(false)
    }


    Card(

        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(14.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White,

                contentColor =
                    HOMEHUB_TEXT
            ),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
    ) {

        Column(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
        ) {

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(

                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(

                        text =
                            transaction.description,

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )


                    if (
                        transaction.date.isNotBlank()
                    ) {

                        Text(

                            text =
                                transaction.date,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }


                    Text(

                        text =
                            formatSignedMoney(
                                transaction.amount
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,

                        color =
                            if (
                                transaction.amount >= 0
                            ) {

                                HOMEHUB_INCOME

                            } else {

                                HOMEHUB_OUTGOING
                            }
                    )
                }


                Column(

                    horizontalAlignment =
                        Alignment.End
                ) {

                    Text(

                        text =
                            "Balance",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )


                    Text(

                        text =
                            formatMoney(
                                balanceAfter
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(2.dp)
            )


            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.End
            ) {

                TextButton(

                    onClick =
                        onEdit,

                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                HOMEHUB_PRIMARY
                        )
                ) {

                    Text(
                        text =
                            "EDIT"
                    )
                }


                TextButton(

                    onClick = {
                        showDeleteConfirmation =
                            true
                    },

                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                HOMEHUB_OUTGOING
                        )
                ) {

                    Text(
                        text =
                            "DELETE"
                    )
                }
            }
        }
    }


    /* ---------------- DELETE CONFIRMATION ---------------- */

    if (
        showDeleteConfirmation
    ) {

        AlertDialog(

            onDismissRequest = {
                showDeleteConfirmation =
                    false
            },

            title = {

                Text(
                    text =
                        "Delete this record?"
                )
            },

            text = {

                Text(
                    text =
                        "Are you sure you want to delete \"${transaction.description}\"? This can't be undone."
                )
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        showDeleteConfirmation =
                            false

                        onDelete()
                    },

                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                HOMEHUB_OUTGOING
                        )
                ) {

                    Text(
                        text =
                            "DELETE"
                    )
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {
                        showDeleteConfirmation =
                            false
                    }
                ) {

                    Text(
                        text =
                            "CANCEL"
                    )
                }
            }
        )
    }
}


/* -------------------------------------------------- */
/* SIGNED MONEY                                       */
/* -------------------------------------------------- */

fun formatSignedMoney(
    amount: Double
): String {

    return if (
        amount >= 0
    ) {

        "+" +
                formatMoney(
                    amount
                )

    } else {

        "-" +
                formatMoney(
                    abs(
                        amount
                    )
                )
    }
}


/* -------------------------------------------------- */
/* NOTES STORAGE                                      */
/* -------------------------------------------------- */

private const val NOTES_PREFS =
    "homehub_notes"

private const val NOTE_ADHOC =
    "adhoc"

private const val NOTE_MONDAY =
    "monday"

private const val NOTE_TUESDAY =
    "tuesday"

private const val NOTE_WEDNESDAY =
    "wednesday"

private const val NOTE_THURSDAY =
    "thursday"

private const val NOTE_FRIDAY =
    "friday"

private const val NOTE_SATURDAY =
    "saturday"

private const val NOTE_SUNDAY =
    "sunday"


/* -------------------------------------------------- */
/* NOTES SCREEN                                       */
/* -------------------------------------------------- */

@Composable
fun NotesScreen(
    onBack: () -> Unit
) {

    val context =
        LocalContext.current


    val preferences =
        remember {

            context.getSharedPreferences(
                NOTES_PREFS,
                Context.MODE_PRIVATE
            )
        }


    var adhocText by remember {

        mutableStateOf(
            preferences.getString(
                NOTE_ADHOC,
                ""
            ) ?: ""
        )
    }


    var mondayText by remember {

        mutableStateOf(
            preferences.getString(
                NOTE_MONDAY,
                ""
            ) ?: ""
        )
    }


    var tuesdayText by remember {

        mutableStateOf(
            preferences.getString(
                NOTE_TUESDAY,
                ""
            ) ?: ""
        )
    }


    var wednesdayText by remember {

        mutableStateOf(
            preferences.getString(
                NOTE_WEDNESDAY,
                ""
            ) ?: ""
        )
    }


    var thursdayText by remember {

        mutableStateOf(
            preferences.getString(
                NOTE_THURSDAY,
                ""
            ) ?: ""
        )
    }


    var fridayText by remember {

        mutableStateOf(
            preferences.getString(
                NOTE_FRIDAY,
                ""
            ) ?: ""
        )
    }


    var saturdayText by remember {

        mutableStateOf(
            preferences.getString(
                NOTE_SATURDAY,
                ""
            ) ?: ""
        )
    }


    var sundayText by remember {

        mutableStateOf(
            preferences.getString(
                NOTE_SUNDAY,
                ""
            ) ?: ""
        )
    }


    var showResetDialog by remember {
        mutableStateOf(false)
    }


    val textFieldColors =
        OutlinedTextFieldDefaults.colors(

            focusedContainerColor =
                Color.White,

            unfocusedContainerColor =
                Color.White,

            disabledContainerColor =
                Color.White,

            focusedTextColor =
                HOMEHUB_TEXT,

            unfocusedTextColor =
                HOMEHUB_TEXT,

            focusedBorderColor =
                HOMEHUB_PRIMARY,

            unfocusedBorderColor =
                HOMEHUB_OUTLINE,

            focusedLabelColor =
                HOMEHUB_PRIMARY,

            unfocusedLabelColor =
                HOMEHUB_PRIMARY,

            cursorColor =
                HOMEHUB_PRIMARY
        )


    Column(

        modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(
                    start = 20.dp,
                    top = 10.dp,
                    end = 20.dp,
                    bottom = 10.dp
                )
    ) {

        ScreenBackButton(
            onBack =
                onBack
        )


        Spacer(
            modifier =
                Modifier.height(2.dp)
        )


        LazyColumn(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),

            verticalArrangement =
                Arrangement.spacedBy(
                    6.dp
                ),

            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    bottom = 30.dp
                )
        ) {

            item {

                NoteEntry(

                    label =
                        "AdHoc",

                    text =
                        adhocText,

                    onTextChange = {
                        adhocText =
                            it

                        preferences
                            .edit()
                            .putString(
                                NOTE_ADHOC,
                                it
                            )
                            .apply()
                    },

                    textFieldColors =
                        textFieldColors
                )
            }


            item {

                NoteEntry(

                    label =
                        "Mon",

                    text =
                        mondayText,

                    onTextChange = {
                        mondayText =
                            it

                        preferences
                            .edit()
                            .putString(
                                NOTE_MONDAY,
                                it
                            )
                            .apply()
                    },

                    textFieldColors =
                        textFieldColors
                )
            }


            item {

                NoteEntry(

                    label =
                        "Tue",

                    text =
                        tuesdayText,

                    onTextChange = {
                        tuesdayText =
                            it

                        preferences
                            .edit()
                            .putString(
                                NOTE_TUESDAY,
                                it
                            )
                            .apply()
                    },

                    textFieldColors =
                        textFieldColors
                )
            }


            item {

                NoteEntry(

                    label =
                        "Wed",

                    text =
                        wednesdayText,

                    onTextChange = {
                        wednesdayText =
                            it

                        preferences
                            .edit()
                            .putString(
                                NOTE_WEDNESDAY,
                                it
                            )
                            .apply()
                    },

                    textFieldColors =
                        textFieldColors
                )
            }


            item {

                NoteEntry(

                    label =
                        "Thu",

                    text =
                        thursdayText,

                    onTextChange = {
                        thursdayText =
                            it

                        preferences
                            .edit()
                            .putString(
                                NOTE_THURSDAY,
                                it
                            )
                            .apply()
                    },

                    textFieldColors =
                        textFieldColors
                )
            }


            item {

                NoteEntry(

                    label =
                        "Fri",

                    text =
                        fridayText,

                    onTextChange = {
                        fridayText =
                            it

                        preferences
                            .edit()
                            .putString(
                                NOTE_FRIDAY,
                                it
                            )
                            .apply()
                    },

                    textFieldColors =
                        textFieldColors
                )
            }


            item {

                NoteEntry(

                    label =
                        "Sat",

                    text =
                        saturdayText,

                    onTextChange = {
                        saturdayText =
                            it

                        preferences
                            .edit()
                            .putString(
                                NOTE_SATURDAY,
                                it
                            )
                            .apply()
                    },

                    textFieldColors =
                        textFieldColors
                )
            }


            item {

                NoteEntry(

                    label =
                        "Sun",

                    text =
                        sundayText,

                    onTextChange = {
                        sundayText =
                            it

                        preferences
                            .edit()
                            .putString(
                                NOTE_SUNDAY,
                                it
                            )
                            .apply()
                    },

                    textFieldColors =
                        textFieldColors
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        Button(

            onClick = {
                showResetDialog =
                    true
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(70.dp)
        ) {

            Text(
                text =
                    "RESET NOTES"
            )
        }
    }


    if (
        showResetDialog
    ) {

        AlertDialog(

            onDismissRequest = {
                showResetDialog =
                    false
            },

            title = {

                Text(
                    text =
                        "Are you sure?"
                )
            },

            text = {

                Text(
                    text =
                        "This will delete all of your notes. This cannot be undone."
                )
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        adhocText =
                            ""

                        mondayText =
                            ""

                        tuesdayText =
                            ""

                        wednesdayText =
                            ""

                        thursdayText =
                            ""

                        fridayText =
                            ""

                        saturdayText =
                            ""

                        sundayText =
                            ""


                        preferences
                            .edit()
                            .clear()
                            .apply()


                        showResetDialog =
                            false
                    }
                ) {

                    Text(
                        text =
                            "RESET"
                    )
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {
                        showResetDialog =
                            false
                    }
                ) {

                    Text(
                        text =
                            "CANCEL"
                    )
                }
            }
        )
    }
}


/* -------------------------------------------------- */
/* NOTE ENTRY                                         */
/* -------------------------------------------------- */

@Composable
fun NoteEntry(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    textFieldColors:
    androidx.compose.material3.TextFieldColors
) {

    Row(

        modifier =
            Modifier.fillMaxWidth(),

        verticalAlignment =
            Alignment.Top
    ) {

        Text(

            text =
                label,

            style =
                MaterialTheme
                    .typography
                    .titleMedium,

            color =
                Color.White,

            modifier =
                Modifier
                    .size(
                        width = 50.dp,
                        height = 70.dp
                    )
                    .padding(
                        top = 22.dp
                    )
        )


        Spacer(
            modifier =
                Modifier.size(8.dp)
        )


        OutlinedTextField(

            value =
                text,

            onValueChange = { newText ->

                val capitalisedText =
                    if (
                        newText.isNotEmpty()
                    ) {

                        newText
                            .replaceFirstChar {

                                if (
                                    it.isLowerCase()
                                ) {

                                    it.titlecase()

                                } else {

                                    it.toString()
                                }
                            }

                    } else {

                        newText
                    }


                onTextChange(
                    capitalisedText
                )
            },

            modifier =
                Modifier
                    .weight(1f)
                    .height(70.dp),

            singleLine =
                false,

            maxLines =
                3,

            colors =
                textFieldColors
        )
    }
}