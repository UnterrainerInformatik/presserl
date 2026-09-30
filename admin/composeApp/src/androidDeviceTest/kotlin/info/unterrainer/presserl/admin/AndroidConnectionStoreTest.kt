package info.unterrainer.presserl.admin

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import info.unterrainer.presserl.admin.ui.account.SlipCredentials
import info.unterrainer.presserl.admin.ui.connect.AndroidConnectionStore
import info.unterrainer.presserl.admin.ui.connect.StoredConnection
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AndroidConnectionStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val anna = SlipCredentials("https://zeitung.example.org", "anna", "tiger-wolke-apfel-leiter")

    @Before
    @After
    fun clear() = AndroidConnectionStore(context).clear()

    @Test
    fun storesLoadsAndClears() {
        AndroidConnectionStore(context).save(StoredConnection(anna.base, anna))

        // a new instance reads what the previous app start wrote
        assertEquals(StoredConnection(anna.base, anna), AndroidConnectionStore(context).load())

        AndroidConnectionStore(context).clear()

        assertNull(AndroidConnectionStore(context).load())
    }

    @Test
    fun addressWithoutCredentials() {
        val store = AndroidConnectionStore(context)
        store.save(StoredConnection(anna.base, anna))

        store.save(StoredConnection(anna.base))

        assertEquals(StoredConnection(anna.base), store.load())
    }

    @Test
    fun preferencesHoldNoPlainPassPhraseOrUsername() {
        AndroidConnectionStore(context).save(StoredConnection(anna.base, anna))

        val stored = context.getSharedPreferences("presserl.connection", Context.MODE_PRIVATE).all.values.joinToString(" ")

        assertFalse("tiger" in stored, stored)
        assertFalse("anna" in stored, stored)
    }

    @Test
    fun credentialsOfALostKeyAreDropped() {
        AndroidConnectionStore(context).save(StoredConnection(anna.base, anna))
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("presserl.slip-credentials")

        assertEquals(StoredConnection(anna.base), AndroidConnectionStore(context).load())
    }
}
