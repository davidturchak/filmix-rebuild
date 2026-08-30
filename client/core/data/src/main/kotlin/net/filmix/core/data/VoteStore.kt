package net.filmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import net.filmix.core.model.Vote

private val Context.votesDataStore: DataStore<Preferences> by preferencesDataStore(name = "filmix_votes")

/**
 * Which way this device voted on a title.
 *
 * Purely local, because nothing else can answer it: the API carries no
 * per-user vote field on any endpoint, and the reference app simply re-sends
 * on every tap and never highlights a thumb. Kept in its own DataStore rather
 * than a Room table — the rows are disposable, so a schema and a migration
 * would buy nothing. A reinstall forgets them.
 */
class VoteStore(private val context: Context) {

    suspend fun vote(postId: Int): Vote? =
        Vote.fromApiValue(context.votesDataStore.data.first()[key(postId)])

    suspend fun setVote(postId: Int, vote: Vote) {
        context.votesDataStore.edit { it[key(postId)] = vote.apiValue }
    }

    private fun key(postId: Int) = stringPreferencesKey("vote_$postId")
}
