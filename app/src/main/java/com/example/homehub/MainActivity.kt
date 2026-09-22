
package com.example.homehub
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.input.KeyboardType
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.style.TextAlign
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

private const val SPREADSHEET_ID =
    "1C-vyDVpJHEuQlkSGV3G2Pd_5LqyUKhns-fxHe_F-PeQ"

private const val TARGET_SHEET_ID =
    1903279729

private const val SHEETS_SCOPE =
    "https://www.googleapis.com/auth/spreadsheets.readonly"


/* -------------------------------------------------- */
/* HOMEHUB COLOURS                                   */
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
    val amount: Double
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
                painter = painterResource(id = R.drawable.carlhomehub),
                contentDescription = "HomeHub",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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
                Modifier
                    .height(20.dp)
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
                        item.getString(
                            "description"
                        ),

                    amount =
                        item.getDouble(
                            "amount"
                        )
                )
            )
        }

    } catch (
        e: Exception
    ) {

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
/* RECEIPTS SCREEN                                    */
/* -------------------------------------------------- */

@Composable
fun ReceiptsScreen(
    onBack: () -> Unit
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


    var showResetDialog by remember {
        mutableStateOf(false)
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

    val historyListState = rememberLazyListState()

    LaunchedEffect(transactions.size) {
        if (recentTransactionIndices.isNotEmpty()) {
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


        OutlinedTextField(
            value = descriptionText,
            onValueChange = { newText ->
                descriptionText = newText
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Description")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            colors = textFieldColors
        )


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        OutlinedTextField(
            value = amountText,
            onValueChange = {
                amountText = it
                    .replace("£", "")
                    .replace("+", "")
                    .replace("-", "")
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Amount")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            colors = textFieldColors
        )


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


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
                        transactions
                            .toMutableList()


                    updatedTransactions.add(
                        AccountTransaction(
                            description =
                                descriptionText
                                    .trim(),

                            amount =
                                signedAmount
                        )
                    )


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
                    "SUBMIT"
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
                state = historyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                        onDelete = {

                            val updatedTransactions =
                                transactions
                                    .toMutableList()


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
/* ACCOUNT TRANSACTION ROW                            */
/* -------------------------------------------------- */

@Composable
fun AccountTransactionRow(
    transaction: AccountTransaction,
    balanceAfter: Double,
    onDelete: () -> Unit
) {

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


            TextButton(
                onClick =
                    onDelete,

                modifier =
                    Modifier.fillMaxWidth(),

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
                    kotlin.math.abs(
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
            verticalArrangement =
                Arrangement.spacedBy(6.dp),

            modifier =
                Modifier.weight(1f)
        ) {

            item {

                NoteEntry(
                    label =
                        "AdHoc",

                    text =
                        adhocText,

                    onTextChange = {
                        adhocText = it

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
                        mondayText = it

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
                        tuesdayText = it

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
                        wednesdayText = it

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
                        thursdayText = it

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
                        fridayText = it

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
                        saturdayText = it

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
                        sundayText = it

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
                showResetDialog = true
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
                showResetDialog = false
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

                        adhocText = ""

                        mondayText = ""

                        tuesdayText = ""

                        wednesdayText = ""

                        thursdayText = ""

                        fridayText = ""

                        saturdayText = ""

                        sundayText = ""


                        preferences
                            .edit()
                            .clear()
                            .apply()


                        showResetDialog = false
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