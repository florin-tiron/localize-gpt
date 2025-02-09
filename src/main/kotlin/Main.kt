package com.florintiron.localizegpt

import PersistentLocalCache
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
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
    val cache = PersistentLocalCache(File("cache.dat"))

    println("Let's start!")
    println(
        "\nInput your project path, separated by ;. (Ex:/Users/johndoe/AndroidStudioProjects/MyAndroidProject/app)" +
                "\nLeave empty for current directory use" +
                "\nInput 'C' for usage of cached path: ${cache.get(KEY_CACHED_PROJECT_PATH)}"
    )

    projectPathList = readln().trim().split(";").toMutableList()

    when {
        projectPathList.isEmpty() -> projectPathList.add(System.getProperty("user.dir"))
        projectPathList[0].capitalize() == "C" -> projectPathList =
            cache.get(KEY_CACHED_PROJECT_PATH)?.split(";")?.toMutableList()
                ?: emptyList<String>().toMutableList()

        else -> {
            cache.put(KEY_CACHED_PROJECT_PATH, projectPathList.joinToString(";"))
        }
    }

    println(
        "\nSupported locales:  ${languageMap.keys}" +
                "\nFor what locales (Language code) do you want localization?" +
                "\nInput locale code separated by coma. Ex: ro, de, fr" +
                "\nLeave empty for default: $DEFAULT_LANGUAGE_CODES" +
                "\nInput 'C' for usage of cached languages: ${
                    cache.get(
                        KEY_CACHED_TRANSLATE_LANGUAGES
                    )
                }"
    )

    var languageCodeInput = readln().trim()

    languageCodeInput = when {
        languageCodeInput.isEmpty() -> DEFAULT_LANGUAGE_CODES
        languageCodeInput.capitalize() == "C" -> cache.get(KEY_CACHED_TRANSLATE_LANGUAGES) ?: ""
        else -> {
            cache.put(KEY_CACHED_TRANSLATE_LANGUAGES, languageCodeInput)
            languageCodeInput
        }
    }

    var languageCodeArray = languageCodeInput.split(',')

    languageCodeArray
        .map { it.trim() }
        .partition { it in languageMap.keys }
        .let {
            languageCodeArray = it.first
            if (it.second.isNotEmpty()) {
                println("The following input language codes are not valid: ${it.second}")
            }
        }

    if (languageCodeArray.isEmpty()) {
        println("Please input at least a valid language code")
        exitProcess(0)
    }

    var openAiAuthToken = System.getenv("OPEN_AI_AUTH_TOKEN")
    if (openAiAuthToken.isNullOrBlank()) {
        println("OpenAI Authentication token not found for environment variable OPEN_AI_AUTH_TOKEN. Please enter a valid token:")
        openAiAuthToken = readln().trim()
    }
    if (openAiAuthToken.isBlank()) {
        println("Please input a valid language code!")
        exitProcess(0)
    }

    println("\n------------------------------------")
    println("\n>>>START GENERATING TRANSLATIONS<<<")

    for (path in projectPathList) {
        val defaultStringsFilePath = "$path/src/main/res/values/strings.xml".also {
            println("Project default strings path: $it")
        }
        val uploadStringsFileContent: String
        try {
            uploadStringsFileContent = File(defaultStringsFilePath).readText()
        } catch (e: FileNotFoundException) {
            println("Please input a valid path. No strings.xml file found at $defaultStringsFilePath")
            exitProcess(0)
        }

        languageCodeArray.map { languageCode ->
            launch(Dispatchers.IO) {
                println("\n...")
                val translation =
                    requestTranslation(
                        uploadStringsFileContent,
                        languageMap[languageCode] ?: "",
                        openAiAuthToken
                    ).run {
                        this?.let {
                            parseTranslation(this)
                        }
                    }
                println("Translation in ${languageMap[languageCode]}:\n$translation")
                translation?.let { value ->
                    writeTranslationToFile(path, languageCode, value)
                }
            }
        }

    }
}

private fun requestTranslation(content: String, targetLanguage: String, apiKey: String): String? {
    val prompt =
        "Translate the following strings.xml file of an Android App into language: $targetLanguage. " +
                "\nKeep the original value for elements with attribute 'translatable=\"false\"'." +
                " \n$content"

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
        println("Response: $response")
        val configure = jacksonObjectMapper().configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )
        val responseMapped = configure.readValue(response, GptResponse::class.java)
        parsedResponse = responseMapped.choices[0].message.content
    } catch (e: Exception) {
        e.printStackTrace()
        println("An error occurred while parsing ai response")
    }
    return parsedResponse
}

private fun writeTranslationToFile(path:String, languageCode: String, translationText: String) {
    val filePath = "$path/src/main/res/values-$languageCode/strings.xml"
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

private lateinit var projectPathList: MutableList<String>
private const val DEFAULT_LANGUAGE_CODES = "de,fr,it,es,pt"
private const val KEY_CACHED_PROJECT_PATH = "PROJECT_PATH_KEY"
private const val KEY_CACHED_TRANSLATE_LANGUAGES = "TRANSLATE_LANGUAGES_KEY"