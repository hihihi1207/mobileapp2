package com.example.busarrival

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ============================================
// 데이터 모델
// ============================================
data class PoiSearchResponse(
    @SerializedName("searchPoiInfo") val searchPoiInfo: SearchPoiInfo?
)
data class SearchPoiInfo(@SerializedName("pois") val pois: Pois?)
data class Pois(@SerializedName("poi") val poi: List<Poi>?)
data class Poi(
    @SerializedName("noorLon") val noorLon: String?,
    @SerializedName("noorLat") val noorLat: String?
)

data class TransitRouteRequest(
    @SerializedName("startX") val startX: String,
    @SerializedName("startY") val startY: String,
    @SerializedName("endX") val endX: String,
    @SerializedName("endY") val endY: String,
    @SerializedName("lang") val lang: Int = 0,
    @SerializedName("format") val format: String = "json",
    @SerializedName("count") val count: Int = 10
)

data class TransitRouteResponse(
    @SerializedName("metaData") val metaData: MetaData?,
    @SerializedName("itineraries") val itineraries: List<Itinerary>?
)
data class MetaData(@SerializedName("plan") val plan: Plan?)
data class Plan(@SerializedName("itineraries") val itineraries: List<Itinerary>?)
data class Itinerary(
    @SerializedName("fare") val fare: Fare?,
    @SerializedName("totalTime") val totalTime: Int?,
    @SerializedName("totalDistance") val totalDistance: Int?,
    @SerializedName("transferCount") val transferCount: Int?,
    @SerializedName("pathType") val pathType: Int?,
    @SerializedName("legs") val legs: List<Leg>?
)
data class Fare(@SerializedName("regular") val regular: FareDetail?)
data class FareDetail(@SerializedName("totalFare") val totalFare: Int?)
data class Leg(
    @SerializedName("mode") val mode: String?,
    @SerializedName("sectionTime") val sectionTime: Int?,
    @SerializedName("distance") val distance: Int?,
    @SerializedName("start") val start: LegPoint?,
    @SerializedName("end") val end: LegPoint?,
    @SerializedName("route") val route: String?,
    @SerializedName("routeColor") val routeColor: String?
)
data class LegPoint(@SerializedName("name") val name: String?)

data class RouteInfo(
    val totalTime: String,
    val totalDistance: String,
    val totalFare: String,
    val transferCount: Int,
    val pathType: String,
    val legs: List<LegInfo>
)

data class LegInfo(
    val mode: String,
    val modeName: String,
    val route: String,
    val startName: String,
    val endName: String,
    val sectionTime: String,
    val distance: String
)

// ============================================
// API 인터페이스
// ============================================
interface TransitApiService {
    @GET("tmap/pois")
    suspend fun searchPlace(
        @Query("version") version: Int = 1,
        @Query("searchKeyword") keyword: String,
        @Query("appKey") appKey: String
    ): PoiSearchResponse

    @POST("transit/routes")
    suspend fun searchTransitRoute(
        @Header("appKey") appKey: String,
        @Body request: TransitRouteRequest
    ): TransitRouteResponse

    companion object {
        fun create(): TransitApiService {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .connectTimeout(30, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("https://apis.openapi.sk.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TransitApiService::class.java)
        }
    }
}

// ============================================
// ViewModel
// ============================================
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val routes: List<RouteInfo>) : UiState()
    data class Error(val message: String) : UiState()
}

class TransitViewModel : ViewModel() {
    private val apiService = TransitApiService.create()
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun searchRoute(appKey: String, startPlace: String, endPlace: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // 출발지 좌표
                val startResponse = apiService.searchPlace(keyword = startPlace, appKey = appKey)
                val startPoi = startResponse.searchPoiInfo?.pois?.poi?.firstOrNull()
                if (startPoi?.noorLon == null || startPoi.noorLat == null) {
                    _uiState.value = UiState.Error("출발지를 찾을 수 없습니다")
                    return@launch
                }

                // 도착지 좌표
                val endResponse = apiService.searchPlace(keyword = endPlace, appKey = appKey)
                val endPoi = endResponse.searchPoiInfo?.pois?.poi?.firstOrNull()
                if (endPoi?.noorLon == null || endPoi.noorLat == null) {
                    _uiState.value = UiState.Error("도착지를 찾을 수 없습니다")
                    return@launch
                }

                // 경로 검색
                val request = TransitRouteRequest(
                    startX = startPoi.noorLon,
                    startY = startPoi.noorLat,
                    endX = endPoi.noorLon,
                    endY = endPoi.noorLat
                )
                val routeResponse = apiService.searchTransitRoute(appKey, request)

                val itineraries = routeResponse.metaData?.plan?.itineraries
                    ?: routeResponse.itineraries ?: emptyList()

                val routes = itineraries.map { itinerary ->
                    RouteInfo(
                        totalTime = "${itinerary.totalTime ?: 0}분",
                        totalDistance = formatDistance(itinerary.totalDistance ?: 0),
                        totalFare = "${itinerary.fare?.regular?.totalFare ?: 0}원",
                        transferCount = itinerary.transferCount ?: 0,
                        pathType = when (itinerary.pathType) {
                            1 -> "최적"
                            2 -> "최단시간"
                            3 -> "최소환승"
                            else -> "추천"
                        },
                        legs = itinerary.legs?.map { leg ->
                            LegInfo(
                                mode = leg.mode ?: "WALK",
                                modeName = when (leg.mode) {
                                    "BUS" -> "버스"
                                    "SUBWAY" -> "지하철"
                                    "WALK" -> "도보"
                                    else -> "이동"
                                },
                                route = leg.route ?: "",
                                startName = leg.start?.name ?: "",
                                endName = leg.end?.name ?: "",
                                sectionTime = "${leg.sectionTime ?: 0}분",
                                distance = formatDistance(leg.distance ?: 0)
                            )
                        } ?: emptyList()
                    )
                }

                _uiState.value = if (routes.isEmpty()) {
                    UiState.Error("경로를 찾을 수 없습니다")
                } else {
                    UiState.Success(routes)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("오류: ${e.message}")
            }
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) {
            String.format("%.1fkm", meters / 1000.0)
        } else {
            "${meters}m"
        }
    }
}

// ============================================
// UI 컴포저블
// ============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitScreen(viewModel: TransitViewModel, appKey: String) {
    var startPlace by remember { mutableStateOf("") }
    var endPlace by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚌 대중교통 길찾기") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = startPlace,
                onValueChange = { startPlace = it },
                label = { Text("🔵 출발지") },
                placeholder = { Text("예) 강남역, 홍대입구") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = endPlace,
                onValueChange = { endPlace = it },
                label = { Text("🔴 도착지") },
                placeholder = { Text("예) 잠실역, 명동") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (startPlace.isNotBlank() && endPlace.isNotBlank()) {
                        viewModel.searchRoute(appKey, startPlace, endPlace)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = startPlace.isNotBlank() && endPlace.isNotBlank()
            ) {
                Text("🔍 경로 검색", style = MaterialTheme.typography.titleMedium)
            }

            when (val state = uiState) {
                is UiState.Idle -> {}
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("경로를 검색하는 중...")
                        }
                    }
                }
                is UiState.Success -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.routes) { route -> RouteCard(route) }
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = state.message,
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteCard(route: RouteInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = when (route.pathType) {
                            "최적" -> "✨ ${route.pathType}"
                            "최단시간" -> "⚡ ${route.pathType}"
                            "최소환승" -> "🔄 ${route.pathType}"
                            else -> route.pathType
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = route.totalTime,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("환승: ", fontSize = 14.sp)
                Text("${route.transferCount}회", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(" | ", fontSize = 14.sp)
                Text("요금: ", fontSize = 14.sp)
                Text(route.totalFare, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Text("총 거리: ${route.totalDistance}", fontSize = 14.sp)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                route.legs.forEach { leg -> LegItem(leg) }
            }
        }
    }
}

@Composable
fun LegItem(leg: LegInfo) {
    val borderColor = when (leg.mode) {
        "BUS" -> Color(0xFF4CAF50)
        "SUBWAY" -> Color(0xFF2196F3)
        "WALK" -> Color(0xFFFF9800)
        else -> Color.Gray
    }

    val icon = when (leg.mode) {
        "BUS" -> "🚌"
        "SUBWAY" -> "🚇"
        "WALK" -> "🚶"
        else -> "📍"
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(60.dp)
                .background(borderColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(leg.modeName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (leg.route.isNotEmpty()) {
                    Text(
                        leg.route,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                "${leg.startName} → ${leg.endName}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(leg.sectionTime, fontSize = 13.sp, color = Color.Gray)
                Text(" | ", fontSize = 13.sp, color = Color.Gray)
                Text(leg.distance, fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}

// ============================================
// MainActivity
// ============================================
class MainActivity : ComponentActivity() {
    // TODO: SK Open API 키를 여기에 입력하세요
    private val APP_KEY = "YOUR_APP_KEY_HERE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: TransitViewModel = viewModel()
                    TransitScreen(viewModel, APP_KEY)
                }
            }
        }
    }
}