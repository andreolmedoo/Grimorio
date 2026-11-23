package com.example.grimorio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class SearchInputError { EMPTY, TOO_SHORT }

fun validateCharacterQuery(query: String): SearchInputError? = when {
    query.trim().isEmpty() -> SearchInputError.EMPTY
    query.trim().length < 3 -> SearchInputError.TOO_SHORT
    else -> null
}

sealed interface CharacterSearchState {
    data object Idle : CharacterSearchState
    data object Loading : CharacterSearchState
    data class Invalid(val error: SearchInputError) : CharacterSearchState
    data class Found(val character: HogwartsCharacter) : CharacterSearchState
    data object Empty : CharacterSearchState
    data object Failed : CharacterSearchState
}

class CharacterSearchViewModel(
    private val source: CharacterSource = CharacterRepository(),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<CharacterSearchState>(CharacterSearchState.Idle)
    val state: LiveData<CharacterSearchState> = mutableState

    fun search(rawQuery: String) {
        if (mutableState.value == CharacterSearchState.Loading) return
        val query = rawQuery.trim()
        val error = validateCharacterQuery(query)
        if (error != null) {
            mutableState.value = CharacterSearchState.Invalid(error)
            return
        }
        mutableState.value = CharacterSearchState.Loading
        executor.execute {
            val result = try {
                source.search(query)?.let { CharacterSearchState.Found(it) }
                    ?: CharacterSearchState.Empty
            } catch (_: Exception) {
                CharacterSearchState.Failed
            }
            mutableState.postValue(result)
        }
    }

    override fun onCleared() {
        executor.shutdownNow()
    }
}
