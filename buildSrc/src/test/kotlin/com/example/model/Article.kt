package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String
import kotlin.collections.List

internal data class Article(
    val title: String,
    val tags: List<String>? = null,
    val authors: List<String>
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("title", title)
        if (tags != null) {
            val tagsArray = JsonArray(tags.size)
            tags.forEach { tagsArray.add(it) }
            json.add("tags", tagsArray)
        }
        val authorsArray = JsonArray(authors.size)
        authors.forEach { authorsArray.add(it) }
        json.add("authors", authorsArray)
        return json
    }
}
