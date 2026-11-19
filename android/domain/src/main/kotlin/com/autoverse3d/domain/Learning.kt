package com.autoverse3d.domain

import com.autoverse3d.domain.model.LearningModule

data class QuizResult(val correct: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else (correct * 100) / total
    val passed: Boolean get() = total > 0 && percent >= PASS_PERCENT

    companion object { const val PASS_PERCENT = 70 }
}

object QuizScoring {
    /** answers: questionId -> chosen index. Unanswered questions count as incorrect. */
    fun score(module: LearningModule, answers: Map<String, Int>): QuizResult =
        QuizResult(correct = module.quiz.count { q -> answers[q.id] == q.correctIndex }, total = module.quiz.size)
}

/** Persisted per-module progress; content version lets the app detect updated modules. */
data class ModuleProgress(
    val moduleId: String,
    val sectionsRead: Set<String> = emptySet(),
    val bestQuizPercent: Int? = null,
    val completed: Boolean = false,
    val contentVersion: String = "",
)

object LearningProgress {
    fun markSectionRead(progress: ModuleProgress, module: LearningModule, sectionId: String): ModuleProgress {
        val read = progress.sectionsRead + sectionId
        return progress.copy(sectionsRead = read, completed = isComplete(module, read, progress.bestQuizPercent))
    }

    fun recordQuiz(progress: ModuleProgress, module: LearningModule, result: QuizResult): ModuleProgress {
        val best = maxOf(progress.bestQuizPercent ?: 0, result.percent)
        return progress.copy(bestQuizPercent = best, completed = isComplete(module, progress.sectionsRead, best))
    }

    /** A module is complete when every section has been read and, if it has a quiz, the quiz was passed. */
    fun isComplete(module: LearningModule, read: Set<String>, bestPercent: Int?): Boolean {
        val allRead = module.sections.all { it.id in read }
        val quizOk = module.quiz.isEmpty() || (bestPercent ?: 0) >= QuizResult.PASS_PERCENT
        return allRead && quizOk
    }

    fun overallPercent(modules: List<LearningModule>, progress: Map<String, ModuleProgress>): Int {
        if (modules.isEmpty()) return 0
        return modules.count { progress[it.id]?.completed == true } * 100 / modules.size
    }
}
