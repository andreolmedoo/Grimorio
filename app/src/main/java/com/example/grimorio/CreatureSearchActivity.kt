package com.example.grimorio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CreatureSearchActivity : AppCompatActivity() {

    private lateinit var editTextCreature: EditText
    private lateinit var buttonSearchCreature: Button
    private lateinit var progressBarCreature: ProgressBar
    private lateinit var textViewCreatureName: TextView
    private lateinit var imageViewCreature: ImageView
    private lateinit var textViewCreatureMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_creature_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.creature)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left + 48, systemBars.top + 48, systemBars.right + 48, systemBars.bottom + 48)
            insets
        }

        editTextCreature = findViewById<EditText>(R.id.editTextCreature)
        buttonSearchCreature = findViewById<Button>(R.id.buttonSearchCreature)
        progressBarCreature = findViewById<ProgressBar>(R.id.progressBarCreature)
        textViewCreatureName = findViewById<TextView>(R.id.textViewCreatureName)
        imageViewCreature = findViewById<ImageView>(R.id.imageViewCreature)
        textViewCreatureMessage = findViewById<TextView>(R.id.textViewCreatureMessage)
    }

    // Chamado pelo android:onClick do botão Buscar
    fun searchCreature(view: View) {
        // 1. Validar a entrada antes de qualquer consulta à API
        val name = editTextCreature.text.toString().trim()
        if (name.length == 0) {
            editTextCreature.error = "Digite o nome de uma criatura."
            editTextCreature.requestFocus()
            return
        }
        if (name.length < 3) {
            editTextCreature.error = "Digite pelo menos 3 caracteres."
            editTextCreature.requestFocus()
            return
        }

        // 2. Limpar o resultado anterior e mostrar o ProgressBar
        textViewCreatureName.visibility = View.GONE
        imageViewCreature.visibility = View.GONE
        textViewCreatureMessage.text = ""
        progressBarCreature.visibility = View.VISIBLE
        buttonSearchCreature.isEnabled = false

        // 3. Executar a consulta e baixar a imagem fora da thread principal
        Thread {
            var creature: Creature? = null
            var image: Bitmap? = null
            var error = ""
            try {
                creature = fetchCreature(name)
                if (creature != null && creature.imageUrl.length > 0) {
                    image = downloadImage(creature.imageUrl)
                }
            } catch (e: Exception) {
                error = "Não foi possível buscar a criatura. Verifique sua conexão e tente novamente."
            }

            // 4. Receber a resposta na thread principal e atualizar a tela
            runOnUiThread {
                showResult(creature, image, error)
            }
        }.start()
    }

    // Consulta a API e devolve a criatura cujo nome é igual ao digitado (sem diferenciar maiúsculas).
    // Devolve null quando nenhuma criatura tem esse nome. Lança exceção em erro de rede ou servidor.
    private fun fetchCreature(name: String): Creature? {
        val url = URL(
            "https://hogwarts-api.com/api/creatures?search=" +
                URLEncoder.encode(name, "UTF-8") + "&page_size=100"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        try {
            if (connection.responseCode != 200) {
                throw IOException("Erro do servidor: ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().readText()
            val data = JSONObject(body).getJSONArray("data")
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val itemName = item.optString("name")
                if (itemName.equals(name, ignoreCase = true)) {
                    val imageUrl = if (item.isNull("image")) "" else item.optString("image")
                    return Creature(itemName, imageUrl)
                }
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    // Baixa a imagem. Devolve null se ela não carregar (erro de rede, resposta que não é imagem etc.).
    private fun downloadImage(imageUrl: String): Bitmap? {
        try {
            val connection = URL(imageUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            try {
                if (connection.responseCode != 200) {
                    return null
                }
                val bytes = connection.inputStream.readBytes()

                // Lê só o tamanho da imagem e reduz imagens muito grandes,
                // para não estourar a memória nem a tela do celular
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                var sampleSize = 1
                while (options.outWidth / sampleSize > 1024 || options.outHeight / sampleSize > 1024) {
                    sampleSize *= 2
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun showResult(creature: Creature?, image: Bitmap?, error: String) {
        // Se o usuário saiu da tela durante a busca, não há o que atualizar
        if (isFinishing || isDestroyed) {
            return
        }

        // 5. Ocultar o ProgressBar e liberar uma nova busca
        progressBarCreature.visibility = View.GONE
        buttonSearchCreature.isEnabled = true

        if (error.length > 0) {
            textViewCreatureMessage.text = error
            return
        }
        if (creature == null) {
            textViewCreatureMessage.text = "Nenhuma criatura encontrada"
            return
        }

        textViewCreatureName.text = creature.name
        textViewCreatureName.visibility = View.VISIBLE
        imageViewCreature.visibility = View.VISIBLE
        if (image != null) {
            imageViewCreature.setImageBitmap(image)
        } else {
            imageViewCreature.setImageResource(R.drawable.creature_placeholder)
            textViewCreatureMessage.text = "Imagem indisponível"
        }
    }
}
