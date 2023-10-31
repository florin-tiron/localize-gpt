package com.florintiron.localizegpt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Duration
import kotlin.system.exitProcess


fun main() = runBlocking<Unit> {

    println("Let's start!")
    println("Input your project path or leave empty for current directory; Ex:/Users/johndoe/AndroidStudioProjects/MyAndroidProject")
    projectPath = readln().trim()

    if (projectPath.isEmpty()) {
        projectPath = System.getProperty("user.dir")
    }

    val defaultStringsFilePath = "$projectPath/app/src/main/res/values/strings.xml".also {
        println("Project default strings path: $it")
    }
    val uploadStringsFileContent: String
    try {
        uploadStringsFileContent = File(defaultStringsFilePath).readText()
    } catch (e: FileNotFoundException) {
        println("Please input a valid path. No strings.xml file found at $defaultStringsFilePath")
        exitProcess(0)
    }

    println(
        "Supported locales:  ${languageMap.keys}" +
                "\nFor what locales (Language code) do you want localization?" +
                "\nInput locale code separated by coma. Ex: ro, de-DE, fr-CA"
    )
    val languageCodeInput = readln().trim()
    var languageCodes = languageCodeInput.split(',')

    languageCodes
        .map { it.trim() }
        .partition { it in languageMap.keys }
        .let {
            languageCodes = it.first
            if (it.second.isNotEmpty()) {
                println("The following input language codes are not valid: ${it.second}")
            }
        }

    if (languageCodes.isEmpty()) {
        println("Please input at least a valid language code")
        exitProcess(0)
    }

    var openAiAuthToken = System.getenv("OPEN_AI_AUTH_TOKEN")
    if (openAiAuthToken.isNullOrBlank()) {
        println("OpenAI Authentication token not found for environment variable OPEN_AI_AUTH_TOKEN. Please enter a valid token")
        openAiAuthToken = readln().trim()
    }
    if (openAiAuthToken.isBlank()) {
        println("Please input a valid language code!")
        exitProcess(0)
    }

    languageCodes.map { languageCode ->
        launch {
            val translation =
                requestTranslation(uploadStringsFileContent, languageMap[languageCode] ?: "", openAiAuthToken).run {
                    this?.let {
                        parseTranslation(this)
                    }
                }
            println("Translation in ${languageMap[languageCode]}:\n$translation")
            translation?.let { value ->
                writeTranslationToFile(languageCode, value)
            }
        }
    }
}

private fun requestTranslation(content: String, targetLanguage: String, apiKey: String): String? {
    val prompt = "Translate the following XML content into $targetLanguage:\n$content"

    val body = jacksonObjectMapper().writeValueAsString(
        GptRequest(
            messages = listOf(
                Message(
                    role = "user",
                    content = prompt
                )
            )
        )
    ).toRequestBody("application/json; charset=utf-8".toMediaType())

    val request = Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .post(body)
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json").build()

    try {
        httpClient.newCall(request).execute().use { response ->
            return response.body?.string()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

private fun parseTranslation(response: String): String? {
    var parsedResponse: String? = null
    try {
        val responseMapped = jacksonObjectMapper().readValue(response, GptResponse::class.java)
        parsedResponse = responseMapped.choices[0].message.content
    } catch (e: Exception) {
        e.printStackTrace()
        println("An error occurred while parsing ai response")
    }
    return parsedResponse
}

private fun writeTranslationToFile(languageCode: String, translationText: String) {
    val filePath = "$projectPath/app/src/main/res/values-$languageCode/strings.xml"
    val file = File(filePath)

    try {
        file.parentFile?.mkdirs()
        file.writeText(translationText)
        println("String written successfully at $filePath")
    } catch (e: IOException) {
        e.printStackTrace()
        println("An error occurred while writing the translation to the file")
    }
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(60))
    .writeTimeout(Duration.ofSeconds(120))
    .readTimeout(Duration.ofSeconds(60))
    .build()
private lateinit var projectPath: String
