import java.io.*

class PersistentLocalCache(private val cacheFile: File) {

    private var cache: MutableMap<String, String> = if (cacheFile.exists()) {
        FileInputStream(cacheFile).use { fis ->
            ObjectInputStream(fis).use { ois ->
                @Suppress("UNCHECKED_CAST")
                ois.readObject() as MutableMap<String, String>
            }
        }
    } else {
        mutableMapOf()
    }

    fun put(key: String, value: String) {
        cache[key] = value
        saveToFile()
    }

    fun get(key: String): String? {
        return cache[key]
    }

    private fun saveToFile() {
        FileOutputStream(cacheFile).use { fos ->
            ObjectOutputStream(fos).use { oos ->
                oos.writeObject(cache)
            }
        }
    }

    fun clear() {
        cache.clear()
        saveToFile()
    }
}