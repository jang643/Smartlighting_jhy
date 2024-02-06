import android.content.Context
import com.example.airquality.AppDatabase
import com.example.airquality.Light
import com.example.airquality.LightDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object LightRepository {
    private lateinit var lightDao: LightDao

    fun initialize(context: Context) {
        val database = AppDatabase.getDatabase(context)
        lightDao = database.lightDao()
    }

    val allLights: List<Light>
        get() = runBlocking { fetchAllLights() }

    private suspend fun fetchAllLights(): List<Light> = withContext(Dispatchers.IO) {
        return@withContext lightDao.allLights
    }

    fun addLight(): Int {
        val newLight = Light(name = "조명 ${allLights.size + 1}")
        val id = runBlocking { insertLight(newLight) }
        return id.toInt()
    }


    private suspend fun insertLight(light: Light) = withContext(Dispatchers.IO) {
        lightDao.insert(light)
    }
}
