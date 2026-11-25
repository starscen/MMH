package com.mmh.app.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.mmh.app.data.remote.dto.AssetDistributionItemDto
import com.mmh.app.data.remote.dto.StatisticsCategoryDto
import com.mmh.app.data.remote.dto.StatisticsMonthDto
import com.mmh.app.domain.model.Resource
import com.mmh.app.data.repository.OverviewRepository
import com.mmh.app.data.repository.StatisticsRepository
import com.mmh.app.ui.theme.LocalDisplayPreferences
import com.mmh.app.ui.util.formatAmount
import com.mmh.app.ui.util.formatPnl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/**
 * 报表页：原生 Compose 图表，跟随 App 明暗主题。
 * - 净资产 / 本月收支汇总卡
 * - 资产分布环形图
 * - 月度收支柱状图
 * - 累计结余折线图
 * - 收入 / 支出分类环形图
 */
data class ReportsUiState(
    val isLoading: Boolean = false,
    val netWorth: Double = 0.0,
    val floatingPnL: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthExpense: Double = 0.0,
    val assetDistribution: List<AssetDistributionItemDto> = emptyList(),
    val monthData: List<StatisticsMonthDto> = emptyList(),
    val incomeCategories: List<StatisticsCategoryDto> = emptyList(),
    val expenseCategories: List<StatisticsCategoryDto> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val overviewRepository: OverviewRepository,
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            var summary: com.mmh.app.data.remote.dto.OverviewSummaryDto? = null
            var stats: com.mmh.app.data.remote.dto.StatisticsData? = null
            var firstError: String? = null

            when (val res = overviewRepository.getSummary()) {
                is Resource.Success -> summary = res.data
                is Resource.Error -> firstError = res.message
            }
            when (val res = statisticsRepository.getStatistics()) {
                is Resource.Success -> stats = res.data
                is Resource.Error -> if (firstError == null) firstError = res.message
            }

            _uiState.value = ReportsUiState(
                isLoading = false,
                netWorth = summary?.netWorth ?: 0.0,
                floatingPnL = summary?.floatingPnL ?: 0.0,
                monthIncome = summary?.monthIncome ?: 0.0,
                monthExpense = summary?.monthExpense ?: 0.0,
                assetDistribution = summary?.assetDistribution.orEmpty(),
                monthData = stats?.monthData.orEmpty(),
                incomeCategories = stats?.incomeCategories.orEmpty(),
                expenseCategories = stats?.expenseCategories.orEmpty(),
                error = firstError?.takeIf { summary == null && stats == null }
            )
        }
    }

    fun retry() = load()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val hasData = uiState.assetDistribution.isNotEmpty() ||
        uiState.monthData.isNotEmpty() ||
        uiState.incomeCategories.isNotEmpty() ||
        uiState.expenseCategories.isNotEmpty()

    if (uiState.isLoading && !hasData) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.retry() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { SummaryCard(uiState) }

                if (uiState.assetDistribution.isNotEmpty()) {
                    item { SectionHeader("资产分布") }
                    item {
                        DonutChartCard(
                            items = uiState.assetDistribution.map { Triple(it.label, it.value, it.pct) },
                            centerLabel = "净资产",
                            centerValue = uiState.netWorth
                        )
                    }
                }

                if (uiState.monthData.isNotEmpty()) {
                    item { SectionHeader("月度收支") }
                    item { MonthlyBarCard(uiState.monthData) }

                    item { SectionHeader("累计结余") }
                    item { CumulativeLineCard(uiState.monthData) }
                }

                if (uiState.expenseCategories.isNotEmpty()) {
                    item { SectionHeader("支出分类") }
                    item {
                        DonutChartCard(
                            items = uiState.expenseCategories.map { Triple(it.name, it.value, it.pct) },
                            centerLabel = "总支出",
                            centerValue = uiState.monthExpense
                        )
                    }
                }

                if (uiState.incomeCategories.isNotEmpty()) {
                    item { SectionHeader("收入分类") }
                    item {
                        DonutChartCard(
                            items = uiState.incomeCategories.map { Triple(it.name, it.value, it.pct) },
                            centerLabel = "总收入",
                            centerValue = uiState.monthIncome
                        )
                    }
                }

                uiState.error?.let { msg ->
                    item { ErrorBanner(msg) { viewModel.retry() } }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 汇总卡
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(state: ReportsUiState) {
    val prefs = LocalDisplayPreferences.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "净资产",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatAmount(state.netWorth),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryColumn("本月收入", formatAmount(state.monthIncome), prefs.upColor)
                SummaryColumn("本月支出", formatAmount(state.monthExpense), prefs.downColor)
                SummaryColumn(
                    "浮动盈亏",
                    formatPnl(state.floatingPnL),
                    if (state.floatingPnL >= 0) prefs.upColor else prefs.downColor
                )
            }
        }
    }
}

@Composable
private fun SummaryColumn(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 环形图（饼图）
// ─────────────────────────────────────────────────────────────────

private val ChartPalette = listOf(
    Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFE53935),
    Color(0xFF6D4C41), Color(0xFF5C6BC0)
)

@Composable
private fun DonutChartCard(
    items: List<Triple<String, Double, Double>>,
    centerLabel: String,
    centerValue: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                val total = items.sumOf { it.second }.coerceAtLeast(1.0)
                val strokeWidth = 18f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    items.forEachIndexed { i, (_, value, _) ->
                        val sweep = (value / total * 360f).toFloat()
                        if (sweep > 0f) {
                            drawArc(
                                color = ChartPalette[i % ChartPalette.size],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        centerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatAmount(centerValue),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.size(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { i, (name, value, pct) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ChartPalette[i % ChartPalette.size])
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            text = "${String.format("%.1f", pct)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = formatAmount(value),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 月度收支柱状图
// ─────────────────────────────────────────────────────────────────

@Composable
private fun MonthlyBarCard(monthData: List<StatisticsMonthDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val prefs = LocalDisplayPreferences.current
            val maxVal = monthData.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0
            val safeMax = maxVal.coerceAtLeast(1.0)

            monthData.forEach { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = monthLabel(m.month),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.size(8.dp))
                    if (m.income > 0) {
                        Box(
                            modifier = Modifier
                                .height(14.dp)
                                .weight((m.income / safeMax).toFloat())
                                .clip(RoundedCornerShape(4.dp))
                                .background(prefs.upColor)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.size(4.dp))
                    if (m.expense > 0) {
                        Box(
                            modifier = Modifier
                                .height(14.dp)
                                .weight((m.expense / safeMax).toFloat())
                                .clip(RoundedCornerShape(4.dp))
                                .background(prefs.downColor)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = formatPnl(m.netTotal),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Medium,
                        color = if (m.netTotal >= 0) prefs.upColor else prefs.downColor,
                        modifier = Modifier.width(96.dp),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                LegendDot(prefs.upColor, "收入")
                Spacer(Modifier.size(16.dp))
                LegendDot(prefs.downColor, "支出")
                Spacer(Modifier.size(16.dp))
                Text(
                    "数值=当月净结余",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────────
// 累计结余折线图
// ─────────────────────────────────────────────────────────────────

@Composable
private fun CumulativeLineCard(monthData: List<StatisticsMonthDto>) {
    val points = monthData.map { monthLabel(it.month) to it.cumNet }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val lineColor = MaterialTheme.colorScheme.primary
            if (points.size < 2) {
                Text(
                    "数据不足，暂无法绘制累计曲线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val values = points.map { it.second }
            val minV = values.min().coerceAtMost(0.0)
            val maxV = values.max().coerceAtLeast(0.0)
            val range = (maxV - minV).coerceAtLeast(1.0)
            val n = points.size
            val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val padX = 16f
                val padTop = 16f
                val padBottom = 16f
                val chartW = size.width - padX * 2
                val chartH = size.height - padTop - padBottom

                fun xAt(i: Int) = padX + if (n == 1) chartW / 2f else chartW * i / (n - 1)
                fun yAt(v: Double) = padTop + chartH - ((v - minV) / range * chartH).toFloat()

                // 基线
                drawLine(
                    color = baselineColor,
                    start = Offset(padX, yAt(0.0)),
                    end = Offset(size.width - padX, yAt(0.0)),
                    strokeWidth = 1f
                )

                val linePath = Path()
                val areaPath = Path()
                points.forEachIndexed { i, (_, v) ->
                    val x = xAt(i)
                    val y = yAt(v)
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        areaPath.moveTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                }
                areaPath.lineTo(xAt(n - 1), yAt(0.0))
                areaPath.lineTo(xAt(0), yAt(0.0))
                areaPath.close()

                drawPath(areaPath, color = lineColor.copy(alpha = 0.12f))
                drawPath(linePath, color = lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

                points.forEachIndexed { i, (_, v) ->
                    drawCircle(
                        color = lineColor,
                        radius = 4f,
                        center = Offset(xAt(i), yAt(v))
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEach { (label, _) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 辅助组件
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

/** month 字段可能是 "YYYY-MM" 或 "M"，统一展示为短月份标签。 */
private fun monthLabel(month: String): String {
    val raw = month.trim()
    val last = if (raw.contains("-")) raw.substringAfterLast("-") else raw
    return "${last.trimStart('0').ifBlank { raw }}月"
}
