package com.autoverse3d.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.autoverse3d.core.designsystem.Disclaimer
import com.autoverse3d.core.designsystem.SectionHeader
import com.autoverse3d.data.ContentRepository
import com.autoverse3d.data.ProgressRepository
import com.autoverse3d.domain.LearningProgress
import com.autoverse3d.domain.ModuleProgress
import com.autoverse3d.domain.QuizResult
import com.autoverse3d.domain.QuizScoring
import com.autoverse3d.domain.model.LearningContent
import com.autoverse3d.domain.model.LearningModule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LearnViewModel @Inject constructor(content: ContentRepository, progress: ProgressRepository) : ViewModel() {
    val state: StateFlow<Pair<LearningContent?, Map<String, ModuleProgress>>> =
        combine(flow { emit(content.content().learning) }, progress.observe()) { c, p -> c to p }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to emptyMap())
}

@HiltViewModel
class ModuleViewModel @Inject constructor(savedState: SavedStateHandle, private val content: ContentRepository, private val progressRepo: ProgressRepository) : ViewModel() {
    private val moduleId: String = savedState["moduleId"] ?: ""
    val module: StateFlow<LearningModule?> = flow { emit(content.content().learning.modules.firstOrNull { it.id == moduleId }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val progress = progressRepo.observe().let { f -> combine(f, module) { p, _ -> p[moduleId] ?: ModuleProgress(moduleId) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModuleProgress(moduleId))

    fun markRead(sectionId: String) = viewModelScope.launch {
        val m = module.value ?: return@launch
        val version = content.content().learning.contentVersion
        progressRepo.save(LearningProgress.markSectionRead(progressRepo.get(m.id), m, sectionId).copy(contentVersion = version))
    }

    fun submitQuiz(answers: Map<String, Int>): QuizResult? {
        val m = module.value ?: return null
        val result = QuizScoring.score(m, answers)
        viewModelScope.launch { progressRepo.save(LearningProgress.recordQuiz(progressRepo.get(m.id), m, result)) }
        return result
    }
}

@Composable
fun LearnScreen(onOpenModule: (String) -> Unit, vm: LearnViewModel = hiltViewModel()) {
    val (content, progress) = vm.state.collectAsStateWithLifecycle().value
    val learning = content ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Learn", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val pct = LearningProgress.overallPercent(learning.modules, progress)
            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("$pct% complete · works offline", style = MaterialTheme.typography.bodySmall)
        }
        items(learning.modules, key = { it.id }) { m ->
            val p = progress[m.id]
            Card(onClick = { onOpenModule(m.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (p?.completed == true) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (p?.completed == true) "Completed" else "Not completed", tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(m.title, fontWeight = FontWeight.SemiBold)
                        Text(m.summary, style = MaterialTheme.typography.bodySmall)
                        Text("${m.estimatedMinutes} min · ${m.sections.size} sections · ${m.quiz.size} quiz questions" +
                            (p?.bestQuizPercent?.let { " · best $it%" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Disclaimer(learning.disclaimer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(onBack: () -> Unit, vm: ModuleViewModel = hiltViewModel()) {
    val module by vm.module.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val m = module ?: return
    var quizMode by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<String, Int>() }
    var result by remember { mutableStateOf<QuizResult?>(null) }

    Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), topBar = {
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), title = { Text(m.title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!quizMode) {
                m.sections.forEach { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(s.body, style = MaterialTheme.typography.bodyMedium)
                            if (s.id in progress.sectionsRead) Text("✓ Read", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            else OutlinedButton(onClick = { vm.markRead(s.id) }) { Text("Mark as read") }
                        }
                    }
                }
                if (m.quiz.isNotEmpty()) Button(onClick = { quizMode = true; result = null; answers.clear() }) { Text("Take the quiz (optional)") }
            } else {
                SectionHeader("Quiz")
                m.quiz.forEachIndexed { qi, q ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${qi + 1}. ${q.prompt}", fontWeight = FontWeight.SemiBold)
                            q.choices.forEachIndexed { ci, choice ->
                                Row(
                                    Modifier.fillMaxWidth().selectable(selected = answers[q.id] == ci, enabled = result == null, role = Role.RadioButton) { answers[q.id] = ci },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = answers[q.id] == ci, onClick = null, enabled = result == null)
                                    Text(choice, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            if (result != null) {
                                val correct = answers[q.id] == q.correctIndex
                                Text((if (correct) "Correct. " else "Not quite — answer: ${q.choices[q.correctIndex]}. ") + q.explanation,
                                    color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val r = result
                if (r == null) Button(onClick = { result = vm.submitQuiz(answers.toMap()) }, enabled = answers.size == m.quiz.size) { Text("Check answers") }
                else {
                    Text("You scored ${r.correct}/${r.total} (${r.percent}%) — ${if (r.passed) "passed!" else "try again to pass (70%)."}", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { result = null; answers.clear() }) { Text("Retry") }
                        Button(onClick = { quizMode = false }) { Text("Back to lesson") }
                    }
                }
            }
        }
    }
}
