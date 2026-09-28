package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.data.Medal
import io.gh.kdbrian.ttac.data.MedalKind
import io.gh.kdbrian.ttac.data.Medals
import io.gh.kdbrian.ttac.data.Profile
import io.gh.kdbrian.ttac.data.StatsData
import io.gh.kdbrian.ttac.ui.components.MedalTile
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.SectionLabel
import io.gh.kdbrian.ttac.ui.components.StreakMeter
import io.gh.kdbrian.ttac.ui.components.TopBar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.MedalArt
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type

/**
 * Streaks and the medal cabinet. "You" collects from solo play; each profile collects from
 * Pass & Play and LAN games.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AwardsScreen(vm: AppViewModel) {
    val palette = LocalPalette.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var ownerId by rememberSaveable { mutableStateOf(StatsData.SOLO_OWNER) }

    val you = Profile(StatsData.SOLO_OWNER, "You", settings.xColor)
    val owners = listOf(you) + stats.profiles
    val owner = owners.firstOrNull { it.id == ownerId } ?: you
    val record = stats.streakRecord(owner.id)
    val (nextWinMedal, winTarget) = Medals.nextWin(record.streak)
    val (nextGrit, gritTarget) = Medals.nextGrit(record.lossStreak)
    val total = Medal.entries.sumOf { stats.medalCount(owner.id, it) }

    ScreenColumn {
        TopBar("Awards", { vm.back() })
        Txt(
            "Every ${Medals.GAP} in a row earns a medal. Losing streaks count too.",
            Type.body, palette.textDim, Modifier.fillMaxWidth(), TextAlign.Center,
        )
        SectionLabel(if (owner.id == StatsData.SOLO_OWNER) "Collector · solo games" else "Collector · pass & play and LAN")
        ProfileChips(owners, owner.id, { ownerId = it.id; vm.tapSound() })

        Gap(14)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StreakMeter("Win streak", record.streak, record.bestStreak, winTarget, nextWinMedal, cold = false, modifier = Modifier.weight(1f).popIn(40))
            StreakMeter("Losing streak", record.lossStreak, record.worstLossStreak, gritTarget, nextGrit, cold = true, modifier = Modifier.weight(1f).popIn(90))
        }

        SectionLabel("Cabinet · $total earned")
        for ((i, kind) in MedalKind.entries.withIndex()) {
            Panel(Modifier.fillMaxWidth().padding(bottom = 12.dp).popIn(140 + i * 60), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                Txt(
                    when (kind) {
                        MedalKind.WIN -> "WIN STREAKS"
                        MedalKind.LOSS -> "GRIT (LOSING STREAKS)"
                        MedalKind.COMEBACK -> "COMEBACKS"
                    },
                    Type.label, palette.textDim, Modifier.padding(start = 10.dp, bottom = 4.dp),
                )
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, maxItemsInEachRow = 3) {
                    Medal.entries.filter { it.kind == kind }.forEach { m ->
                        MedalTile(m, stats.medalCount(owner.id, m), Modifier.width(104.dp))
                    }
                }
            }
        }

        SectionLabel("Recent awards")
        val recent = stats.awards.filter { it.ownerId == owner.id }.take(20)
        if (recent.isEmpty()) {
            Txt("Nothing yet — string ${Medals.GAP} results together.", Type.body, palette.textDim, Modifier.fillMaxWidth().padding(vertical = 12.dp), TextAlign.Center)
        }
        recent.forEachIndexed { i, a ->
            Panel(Modifier.fillMaxWidth().padding(bottom = 8.dp).popIn((i * 40).coerceAtMost(300)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MedalArt(a.medal, true, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(a.medal.title, Type.body.copy(fontWeight = FontWeight.Bold))
                        Txt(
                            when (a.medal.kind) {
                                MedalKind.WIN -> "${a.streak} wins in a row"
                                MedalKind.LOSS -> "${a.streak} losses in a row"
                                MedalKind.COMEBACK -> "Broke a ${a.streak}-game slide"
                            },
                            Type.label.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), palette.textDim,
                        )
                    }
                    Txt(ago(a.timestamp), Type.label, palette.textDim)
                }
            }
        }
        Gap(24)
    }
}
