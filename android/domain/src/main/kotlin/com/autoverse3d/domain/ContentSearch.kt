package com.autoverse3d.domain

import com.autoverse3d.domain.model.ComponentCatalog
import com.autoverse3d.domain.model.EvLabContent
import com.autoverse3d.domain.model.HotspotCatalog
import com.autoverse3d.domain.model.LearningContent

data class SearchDocument(val id: String, val kind: String, val title: String, val text: String, val vehicleId: String? = null)

data class SearchHit(val document: SearchDocument, val score: Int)

/**
 * Offline keyword search over app-owned content. Identical algorithm to backend/src/content.js and the
 * iOS ContentSearch so offline fallback results match the grounding used by the online assistant.
 */
object ContentSearch {
    private val STOPWORDS = (
        "a an and are as at be by can do does for from how i in is it its me my of on or the this to what when " +
            "where which who why with you your"
        ).split(" ").toSet()
    private val TOKEN = Regex("[a-z0-9]+")

    fun tokenize(text: String): List<String> =
        TOKEN.findAll(text.lowercase()).map { it.value }.filter { it.length > 1 && it !in STOPWORDS }.toList()

    fun score(queryTerms: List<String>, doc: SearchDocument): Int {
        val title = tokenize(doc.title).toSet()
        val body = tokenize(doc.text)
        return queryTerms.toSet().sumOf { term -> (if (term in title) 3 else 0) + body.count { it == term } }
    }

    fun search(docs: List<SearchDocument>, query: String, limit: Int = 4): List<SearchHit> {
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()
        return docs.map { SearchHit(it, score(terms, it)) }
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.document.id })
            .take(limit)
    }

    fun buildIndex(learning: LearningContent, components: ComponentCatalog, hotspots: HotspotCatalog, ev: EvLabContent): List<SearchDocument> {
        val docs = mutableListOf<SearchDocument>()
        learning.modules.forEach { m ->
            m.sections.forEach { s -> docs += SearchDocument("learning:${m.id}:${s.id}", "learning", "${m.title}: ${s.heading}", s.body) }
        }
        components.components.forEach { c ->
            docs += SearchDocument("component:${c.id}", "component", c.name, listOf(c.description, c.purpose, c.detail).filter { it.isNotBlank() }.joinToString(" "), c.vehicleId)
        }
        hotspots.hotspots.forEach { h -> docs += SearchDocument("hotspot:${h.id}", "hotspot", h.title, "${h.summary} ${h.detail}", hotspots.vehicleId) }
        ev.nodes.forEach { n -> docs += SearchDocument("ev:${n.id}", "ev", n.title, n.summary) }
        return docs
    }
}
