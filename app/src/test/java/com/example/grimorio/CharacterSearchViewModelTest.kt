package com.example.grimorio

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class CharacterSearchViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private class QueuedExecutor : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { tasks.add(command) }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return mutableListOf() }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = stopped
        fun finishRequest() = tasks.removeFirst().run()
    }

    @Test fun invalidQueriesNeverCallApi() {
        var calls = 0
        val executor = QueuedExecutor()
        val model = CharacterSearchViewModel({ calls++; null }, executor)
        listOf("" to SearchInputError.EMPTY, "   " to SearchInputError.EMPTY,
            " a " to SearchInputError.TOO_SHORT, " ab " to SearchInputError.TOO_SHORT).forEach { (query, error) ->
            model.search(query)
            assertEquals(CharacterSearchState.Invalid(error), model.state.value)
        }
        assertEquals(0, calls)
        assertTrue(executor.tasks.isEmpty())
    }

    @Test fun validQueryIsTrimmedAndLoadingLastsUntilRequestFinishes() {
        val executor = QueuedExecutor()
        val character = HogwartsCharacter("Harry", "Human", "Gryffindor")
        var receivedQuery = ""
        val model = CharacterSearchViewModel({ receivedQuery = it; character }, executor)
        val states = mutableListOf<CharacterSearchState>()
        val observer = Observer<CharacterSearchState> { states.add(it) }
        model.state.observeForever(observer)
        try {
            model.search("  Harry  ")
            assertEquals(CharacterSearchState.Loading, model.state.value)
            assertEquals("", receivedQuery)
            model.search("Weasley")
            assertEquals(1, executor.tasks.size)
            executor.finishRequest()
            assertEquals("Harry", receivedQuery)
            assertEquals(listOf(CharacterSearchState.Idle, CharacterSearchState.Loading,
                CharacterSearchState.Found(character)), states)
        } finally { model.state.removeObserver(observer) }
    }

    @Test fun emptyResponseEndsLoading() {
        val executor = QueuedExecutor()
        val model = CharacterSearchViewModel({ null }, executor)
        model.search("xyz")
        executor.finishRequest()
        assertEquals(CharacterSearchState.Empty, model.state.value)
    }

    @Test fun connectionFailureEndsLoadingAndAllowsRetry() {
        val executor = QueuedExecutor()
        var fail = true
        val character = HogwartsCharacter("Dobby", null, null)
        val model = CharacterSearchViewModel({ if (fail) throw IOException("offline") else character }, executor)
        model.search("Dobby")
        executor.finishRequest()
        assertEquals(CharacterSearchState.Failed, model.state.value)
        fail = false
        model.search("Dobby")
        assertEquals(CharacterSearchState.Loading, model.state.value)
        executor.finishRequest()
        assertEquals(CharacterSearchState.Found(character), model.state.value)
    }

    @Test fun unexpectedResponseFailureAlsoEndsLoading() {
        val executor = QueuedExecutor()
        val model = CharacterSearchViewModel({ throw IllegalArgumentException("Invalid JSON") }, executor)
        model.search("Harry")
        executor.finishRequest()
        assertEquals(CharacterSearchState.Failed, model.state.value)
    }

    @Test fun newSearchClearsPreviousResultWithLoadingState() {
        val executor = QueuedExecutor()
        val model = CharacterSearchViewModel({ HogwartsCharacter("Harry", null, null) }, executor)
        model.search("Harry")
        executor.finishRequest()
        model.search("Weasley")
        assertEquals(CharacterSearchState.Loading, model.state.value)
        executor.finishRequest()
    }
}
