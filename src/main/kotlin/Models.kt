package com.florintiron.localizegpt

data class GptRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<Message>,
    val temperature: Double = 1.0,
    val top_p: Double = 1.0,
    val n: Int = 1,
    val stream: Boolean = false,
    val max_tokens: Int = 1000,
    val presence_penalty: Double = 0.0,
    val frequency_penalty: Double = 0.0
)

data class Message(
    val role: String, val content: String
)

data class GptResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val index: Int,
    val message: Message,
    val finish_reason: String
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)