package com.example.grimorio

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider

open class CharacterSearchActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var button: Button
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var details: View
    private lateinit var model: CharacterSearchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_character_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.character_search_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        input = findViewById(R.id.character_query)
        button = findViewById(R.id.search_character_button)
        progress = findViewById(R.id.character_progress)
        message = findViewById(R.id.character_message)
        details = findViewById(R.id.character_details)
        model = ViewModelProvider(this)[CharacterSearchViewModel::class.java]
        button.setOnClickListener { model.search(input.text.toString()) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                model.search(input.text.toString())
                true
            } else false
        }
        model.state.observe(this, ::render)
    }

    private fun render(state: CharacterSearchState) {
        val loading = state == CharacterSearchState.Loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        button.isEnabled = !loading
        input.isEnabled = !loading
        input.error = null
        details.visibility = View.GONE
        message.visibility = View.VISIBLE
        when (state) {
            CharacterSearchState.Idle -> message.setText(R.string.character_search_hint)
            CharacterSearchState.Loading -> message.setText(R.string.character_search_loading)
            CharacterSearchState.Empty -> message.setText(R.string.character_search_empty)
            CharacterSearchState.Failed -> message.setText(R.string.character_search_error)
            is CharacterSearchState.Invalid -> {
                input.error = getString(when (state.error) {
                    SearchInputError.EMPTY -> R.string.character_query_required
                    SearchInputError.TOO_SHORT -> R.string.character_query_too_short
                })
                input.requestFocus()
                message.visibility = View.GONE
            }
            is CharacterSearchState.Found -> {
                message.visibility = View.GONE
                details.visibility = View.VISIBLE
                val fallback = getString(R.string.character_not_informed)
                findViewById<TextView>(R.id.character_name).text =
                    getString(R.string.character_name_value, state.character.name ?: fallback)
                findViewById<TextView>(R.id.character_species).text =
                    getString(R.string.character_species_value, state.character.species ?: fallback)
                findViewById<TextView>(R.id.character_house).text =
                    getString(R.string.character_house_value, state.character.house ?: fallback)
            }
        }
    }
}
