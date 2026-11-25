package com.mmh.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mmh.app.data.remote.dto.AccountListRowDto
import com.mmh.app.data.remote.dto.CreditAccountRowDto
import com.mmh.app.ui.util.formatAccountDisplayName
import com.mmh.app.ui.util.formatAmount
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onAccountClick: (accountId: String, accountName: String) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
    var hideZeroBalance by rememberSaveable { mutableStateOf(true) }

    val titles = listOf("资金账户", "信用卡账户")
    val pagerState = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> FundAccountsPage(
                    uiState = uiState,
                    expandedMap = expandedMap,
                    hideZeroBalance = hideZeroBalance,
                    onToggleHideZero = { hideZeroBalance = it },
                    onAccountClick = onAccountClick,
                    onRetry = { viewModel.loadAccounts() }
                )

                1 -> CreditAccountsPage(
                    uiState = uiState,
                    onAccountClick = onAccountClick,
                    onRetry = { viewModel.loadAccounts() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 资金账户页
// ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FundAccountsPage(
    uiState: AccountsUiState,
    expandedMap: MutableMap<String, Boolean>,
    hideZeroBalance: Boolean,
    onToggleHideZero: (Boolean) -> Unit,
    onAccountClick: (accountId: String, accountName: String) -> Unit,
    onRetry: () -> Unit
) {
    val visibleGroups = uiState.groups
        .map { group ->
            if (hideZeroBalance) {
                group.copy(accounts = group.accounts.filterNot { it.isZeroBalance() })
            } else {
                group
            }
        }
        .filter { it.accounts.isNotEmpty() }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading && uiState.groups.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.groups.isEmpty() -> {
                ErrorState(message = uiState.error.orEmpty(), onRetry = onRetry)
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        GrandTotalCard(
                            grandTotal = uiState.grandTotal,
                            groupCount = visibleGroups.size,
                            accountCount = visibleGroups.sumOf { it.accounts.size }
                        )
                    }

                    item {
                        ZeroBalanceToggleCard(
                            checked = hideZeroBalance,
                            onCheckedChange = onToggleHideZero
                        )
                    }

                    if (visibleGroups.isEmpty()) {
                        item {
                            EmptyAccountsCard(
                                hideZeroBalance = hideZeroBalance,
                                onShowZeroBalance = { onToggleHideZero(false) }
                            )
                        }
                    }

                    visibleGroups.forEach { group ->
                        item(key = "header_${group.kind}") {
                            GroupHeader(
                                group = group,
                                expanded = expandedMap[group.kind] ?: true,
                                onToggle = { expandedMap[group.kind] = !(expandedMap[group.kind] ?: true) }
                            )
                        }
                        if (expandedMap[group.kind] ?: true) {
                            items(group.accounts, key = { it.id }) { account ->
                                val displayName = account.displayName()
                                AccountCard(account) { onAccountClick(account.id, displayName) }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 信用卡账户页
// ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditAccountsPage(
    uiState: AccountsUiState,
    onAccountClick: (accountId: String, accountName: String) -> Unit,
    onRetry: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading && uiState.creditAccounts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.creditAccounts.isEmpty() -> {
                ErrorState(message = uiState.error.orEmpty(), onRetry = onRetry)
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CreditSummaryCard(
                            used = uiState.creditUsedTotal,
                            limit = uiState.creditLimitTotal,
                            currentBill = uiState.creditBillTotal
                        )
                    }

                    if (uiState.creditAccounts.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "暂无信用卡账户",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    items(uiState.creditAccounts, key = { it.id }) { credit ->
                        CreditAccountCard(credit) {
                            onAccountClick(credit.id, credit.displayName())
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CreditSummaryCard(used: Double, limit: Double, currentBill: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "信用卡合计",
                color = MaterialTheme.colorScheme.onPrimary.copy(0.85f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryMetric("已用", formatAmount(used))
                SummaryMetric("总额度", formatAmount(limit))
                SummaryMetric("本期账单", formatAmount(currentBill))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
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
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun CreditAccountCard(credit: CreditAccountRowDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(kindColor("bank_credit").copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = kindColor("bank_credit"),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        credit.displayName(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        creditSubtitle(credit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "本期账单",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatAmount(credit.currentBill),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CreditMetric("已用", formatAmount(credit.balance))
                CreditMetric("额度", formatAmount(credit.creditLimit))
                CreditMetric("可用", formatAmount(credit.availableLimit))
            }
        }
    }
}

@Composable
private fun CreditMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun creditSubtitle(credit: CreditAccountRowDto): String {
    val parts = mutableListOf<String>()
    if (!credit.institutionName.isNullOrBlank()) parts.add(credit.institutionName!!)
    val due = credit.dueDate?.trim().orEmpty()
    if (due.isNotBlank()) parts.add("还款日 $due")
    return parts.joinToString(" · ")
}

// ─────────────────────────────────────────────────────────────────
// 资金账户卡片（原有）
// ─────────────────────────────────────────────────────────────────

@Composable
private fun GrandTotalCard(grandTotal: Double, groupCount: Int, accountCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "\u8d44\u91d1\u5408\u8ba1",
                color = MaterialTheme.colorScheme.onPrimary.copy(0.85f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatAmount(grandTotal),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip("$groupCount \u4e2a\u5206\u7c7b")
                StatChip("$accountCount \u4e2a\u8d26\u6237")
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary.copy(0.8f)
    )
}

@Composable
private fun ZeroBalanceToggleCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "\u9690\u85cf\u96f6\u4f59\u989d\u8d26\u6237",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "\u4ec5\u5f71\u54cd\u5f53\u524d\u9875\u7684\u8be6\u60c5\u5217\u8868",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun EmptyAccountsCard(hideZeroBalance: Boolean, onShowZeroBalance: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (hideZeroBalance) "\u6682\u65e0\u975e\u96f6\u4f59\u989d\u8d26\u6237" else "\u6682\u65e0\u8d44\u91d1\u8d26\u6237",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (hideZeroBalance) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onShowZeroBalance) {
                    Text("\u663e\u793a\u96f6\u4f59\u989d")
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: AccountGroup, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = kindColor(group.kind).copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(kindColor(group.kind).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(kindIcon(group.kind), contentDescription = null, tint = kindColor(group.kind), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    group.kindLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${group.accounts.size} \u4e2a\u8d26\u6237",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                formatAmount(group.groupTotal),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = balanceColor(group.kind, group.groupTotal)
            )

            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountCard(account: AccountListRowDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(kindColor(account.kind).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(kindIcon(account.kind), contentDescription = null, tint = kindColor(account.kind), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    account.displayName(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    AccountsViewModel.kindLabel(account.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                formatAmount(account.balance),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = balanceColor(account.kind, account.balance)
            )

            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("\u91cd\u8bd5") }
    }
}

private fun AccountListRowDto.isZeroBalance(): Boolean = abs(balance) < 0.005

private fun AccountListRowDto.displayName(): String = formatAccountDisplayName(name, institutionName)

private fun CreditAccountRowDto.displayName(): String = formatAccountDisplayName(name, institutionName)

@Composable
private fun balanceColor(kind: String, amount: Double): Color = when {
    kind == "bank_credit" || kind == "loan" -> MaterialTheme.colorScheme.error
    amount < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "bank_debit" -> Icons.Default.CreditCard
    "bank_credit" -> Icons.Default.CreditCard
    "ewallet" -> Icons.Default.AccountBalanceWallet
    "cash" -> Icons.Default.Payments
    "loan" -> Icons.Default.RequestQuote
    else -> Icons.Default.Savings
}

private fun kindColor(kind: String): Color = when (kind) {
    "bank_debit" -> Color(0xFF2563EB)
    "bank_credit" -> Color(0xFFDC2626)
    "ewallet" -> Color(0xFF0891B2)
    "cash" -> Color(0xFF16A34A)
    "loan" -> Color(0xFFB91C1C)
    else -> Color(0xFF6B7280)
}
