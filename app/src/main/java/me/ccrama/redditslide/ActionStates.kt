package me.ccrama.redditslide

import ltd.ucode.network.lemmy.data.type.CommentView
import ltd.ucode.network.reddit.VoteDirectionExtensions.asVoteDirection
import ltd.ucode.network.data.IPost
import ltd.ucode.network.trait.IVotable
import net.dean.jraw.models.Comment
import net.dean.jraw.models.PublicContribution
import net.dean.jraw.models.VoteDirection

object ActionStates {
    val upVotedFullnames = ArrayList<String>()
    val downVotedFullnames = ArrayList<String>()
    val unvotedFullnames = ArrayList<String>()
    val savedFullnames = ArrayList<String>()
    val unSavedFullnames = ArrayList<String>()

    @JvmStatic
    fun getVoteDirection(s: PublicContribution): VoteDirection {
        return if (upVotedFullnames.contains(s.fullName)) {
            VoteDirection.UPVOTE
        } else if (downVotedFullnames.contains(s.fullName)) {
            VoteDirection.DOWNVOTE
        } else if (unvotedFullnames.contains(s.fullName)) {
            VoteDirection.NO_VOTE
        } else {
            s.vote
        }
    }

    @JvmStatic
    fun getVoteDirection(s: IVotable): VoteDirection {
        return if (upVotedFullnames.contains(s.uri)) {
            VoteDirection.UPVOTE
        } else if (downVotedFullnames.contains(s.uri)) {
            VoteDirection.DOWNVOTE
        } else if (unvotedFullnames.contains(s.uri)) {
            VoteDirection.NO_VOTE
        } else {
            s.myVote.asVoteDirection()
        }
    }

    @JvmStatic
    fun getVoteDirection(s: CommentView): VoteDirection {
        return if (upVotedFullnames.contains(s.permalink)) {
            VoteDirection.UPVOTE
        } else if (downVotedFullnames.contains(s.permalink)) {
            VoteDirection.DOWNVOTE
        } else if (unvotedFullnames.contains(s.permalink)) {
            VoteDirection.NO_VOTE
        } else {
            when (s.myVote?.let { it > 0 }) {
                null -> { VoteDirection.NO_VOTE }
                true -> { VoteDirection.UPVOTE }
                false -> { VoteDirection.DOWNVOTE }
            }
        }
    }

    @JvmStatic
    fun setVoteDirection(s: PublicContribution, direction: VoteDirection) {
        val fullname = s.fullName
        upVotedFullnames.remove(fullname)
        downVotedFullnames.remove(fullname)
        unvotedFullnames.remove(fullname)
        when (direction) {
            VoteDirection.UPVOTE -> upVotedFullnames.add(fullname)
            VoteDirection.DOWNVOTE -> downVotedFullnames.add(fullname)
            VoteDirection.NO_VOTE -> unvotedFullnames.add(fullname)
        }
    }

    @JvmStatic
    fun setVoteDirection(s: IVotable, direction: VoteDirection) {
        val fullname = s.uri
        upVotedFullnames.remove(fullname)
        downVotedFullnames.remove(fullname)
        unvotedFullnames.remove(fullname)
        when (direction) {
            VoteDirection.UPVOTE -> upVotedFullnames.add(fullname)
            VoteDirection.DOWNVOTE -> downVotedFullnames.add(fullname)
            VoteDirection.NO_VOTE -> unvotedFullnames.add(fullname)
        }
    }

    @JvmStatic
    fun setVoteDirection(s: CommentView, direction: VoteDirection) {
        val fullname = s.permalink
        upVotedFullnames.remove(fullname)
        downVotedFullnames.remove(fullname)
        unvotedFullnames.remove(fullname)
        when (direction) {
            VoteDirection.UPVOTE -> upVotedFullnames.add(fullname)
            VoteDirection.DOWNVOTE -> downVotedFullnames.add(fullname)
            VoteDirection.NO_VOTE -> unvotedFullnames.add(fullname)
        }
    }

    @JvmStatic
    fun isSaved(s: IPost): Boolean {
        return if (savedFullnames.contains(s.uri)) {
            true
        } else if (unSavedFullnames.contains(s.uri)) {
            false
        } else {
            s.isSaved
        }
    }

    @JvmStatic
    fun isSaved(s: Comment): Boolean {
        return if (savedFullnames.contains(s.fullName)) {
            true
        } else if (unSavedFullnames.contains(s.fullName)) {
            false
        } else {
            s.isSaved
        }
    }

    @JvmStatic
    fun isSaved(s: CommentView): Boolean {
        return if (savedFullnames.contains(s.permalink)) {
            true
        } else if (unSavedFullnames.contains(s.permalink)) {
            false
        } else {
            s.isSaved
        }
    }

    fun setSaved(s: IPost, b: Boolean) {
        val fullname: String = s.uri
        savedFullnames.remove(fullname)
        if (b) {
            savedFullnames.add(fullname)
        } else {
            unSavedFullnames.add(fullname)
        }
    }

    @JvmStatic
    fun setSaved(s: Comment, b: Boolean) {
        val fullname = s.fullName
        savedFullnames.remove(fullname)
        if (b) {
            savedFullnames.add(fullname)
        } else {
            unSavedFullnames.add(fullname)
        }
    }

    fun setSaved(s: CommentView, b: Boolean) {
        val fullname: String = s.permalink
        savedFullnames.remove(fullname)
        if (b) {
            savedFullnames.add(fullname)
        } else {
            unSavedFullnames.add(fullname)
        }
    }
}
