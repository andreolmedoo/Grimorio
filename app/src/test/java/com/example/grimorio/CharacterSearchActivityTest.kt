package com.example.grimorio

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CharacterSearchActivityTest {
    class QueuedExecutor : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
        fun finish() { tasks.removeFirst().run(); shadowOf(Looper.getMainLooper()).idle() }
    }

    class TestActivity : CharacterSearchActivity() {
        val executor = QueuedExecutor()
        var result: HogwartsCharacter? = null
        var failure = false
        var calls = 0
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return CharacterSearchViewModel({
                        calls++
                        if (failure) throw IOException("offline")
                        result
                    }, executor) as T
                }
            }
    }

    @Test fun validationLoadingSuccessEmptyFailureAndRetryAreRendered() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val input = activity.findViewById<EditText>(R.id.character_query)
            val button = activity.findViewById<Button>(R.id.search_character_button)
            val progress = activity.findViewById<View>(R.id.character_progress)
            val details = activity.findViewById<View>(R.id.character_details)
            val message = activity.findViewById<TextView>(R.id.character_message)

            input.setText("   ")
            button.performClick()
            assertEquals(activity.getString(R.string.character_query_required), input.error.toString())
            input.setText(" ab ")
            button.performClick()
            assertEquals(activity.getString(R.string.character_query_too_short), input.error.toString())
            assertEquals(0, activity.calls)
            assertEquals(View.GONE, progress.visibility)

            activity.result = HogwartsCharacter("Harry Potter", null, null)
            input.setText(" Harry ")
            button.performClick()
            assertEquals(View.VISIBLE, progress.visibility)
            assertEquals(View.GONE, details.visibility)
            assertFalse(button.isEnabled)
            activity.executor.finish()
            assertEquals(View.GONE, progress.visibility)
            assertTrue(button.isEnabled)
            assertEquals(View.VISIBLE, details.visibility)
            assertEquals("Nome: Harry Potter", activity.findViewById<TextView>(R.id.character_name).text.toString())
            assertEquals("Espécie: Não informado", activity.findViewById<TextView>(R.id.character_species).text.toString())
            assertEquals("Casa: Não informado", activity.findViewById<TextView>(R.id.character_house).text.toString())

            activity.result = null
            button.performClick()
            assertEquals(View.GONE, details.visibility)
            activity.executor.finish()
            assertEquals("Nenhum personagem encontrado", message.text.toString())
            assertEquals(View.GONE, progress.visibility)

            activity.failure = true
            button.performClick()
            activity.executor.finish()
            assertEquals(activity.getString(R.string.character_search_error), message.text.toString())
            assertEquals(View.GONE, progress.visibility)
            assertTrue(button.isEnabled)
            activity.failure = false
            activity.result = HogwartsCharacter("Dobby", "House-elf", null)
            button.performClick()
            activity.executor.finish()
            assertEquals(View.VISIBLE, details.visibility)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun rotationDuringRequestKeepsViewModelAndFinishesOnNewActivity() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val original = controller.get()
            original.result = HogwartsCharacter("Harry", "Human", "Gryffindor")
            original.findViewById<EditText>(R.id.character_query).setText("Harry")
            original.findViewById<Button>(R.id.search_character_button).performClick()
            val before = ViewModelProvider(original)[CharacterSearchViewModel::class.java]
            controller.recreate()
            val recreated = controller.get()
            assertNotSame(original, recreated)
            assertSame(before, ViewModelProvider(recreated)[CharacterSearchViewModel::class.java])
            assertEquals(View.VISIBLE, recreated.findViewById<View>(R.id.character_progress).visibility)
            original.executor.finish()
            assertEquals(View.GONE, recreated.findViewById<View>(R.id.character_progress).visibility)
            assertEquals("Nome: Harry", recreated.findViewById<TextView>(R.id.character_name).text.toString())
            assertEquals(1, original.calls)
        } finally { controller.pause().stop().destroy() }
    }
}
