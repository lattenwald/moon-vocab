package org.lattenwald.moonvocab.domain.settings

import org.lattenwald.moonvocab.data.settings.LlmService

data class ServiceTemplate(val key: String, val name: String, val baseUrl: String)

val SERVICE_TEMPLATES: List<ServiceTemplate> = listOf(
    ServiceTemplate("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
    ServiceTemplate("openai", "OpenAI", "https://api.openai.com/v1"),
    ServiceTemplate("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    ServiceTemplate("mistral", "Mistral", "https://api.mistral.ai/v1"),
    ServiceTemplate("deepseek", "DeepSeek", "https://api.deepseek.com"),
    ServiceTemplate("groq", "Groq", "https://api.groq.com/openai/v1"),
    ServiceTemplate("custom", "", ""),
)

data class ServiceList(val services: List<LlmService>, val activeId: String)

fun ServiceList.add(template: ServiceTemplate, name: String, newId: String): ServiceList =
    ServiceList(services + LlmService(id = newId, name = name, baseUrl = template.baseUrl), activeId = newId)

fun ServiceList.duplicate(id: String, name: String, newId: String): ServiceList {
    val index = services.indexOfFirst { it.id == id }
    if (index < 0) return this
    val copy = services[index].copy(id = newId, name = name)
    return ServiceList(services.toMutableList().apply { add(index + 1, copy) }, activeId = newId)
}

fun ServiceList.delete(id: String): ServiceList {
    val index = services.indexOfFirst { it.id == id }
    if (index < 0 || services.size == 1) return this
    val rest = services.filterIndexed { i, _ -> i != index }
    val active = if (id == activeId) rest[minOf(index, rest.lastIndex)].id else activeId
    return ServiceList(rest, active)
}

fun ServiceList.select(id: String): ServiceList = if (services.any { it.id == id }) copy(activeId = id) else this
