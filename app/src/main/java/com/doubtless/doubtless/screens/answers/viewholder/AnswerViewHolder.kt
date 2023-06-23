package com.doubtless.doubtless.screens.answers.viewholder

import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.doubtless.doubtless.DoubtlessApp
import com.doubtless.doubtless.R
import com.doubtless.doubtless.screens.answers.AnswerData
import com.doubtless.doubtless.screens.doubt.usecases.VotingUseCase
import com.doubtless.doubtless.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.floor


class AnswerViewHolder(itemView: View, private val interactionListener: InteractionListener) :
    RecyclerView.ViewHolder(itemView) {

    interface InteractionListener {
        fun onAnswerClicked(answerData: AnswerData, position: Int)
    }

    private val authorName: TextView
    private val time: TextView
    private val description: TextView
    private val ivDp: ImageView
    private val tvYear: TextView
    private val tvVotes: TextView
    private val upVote: CheckBox
    private val downVote: CheckBox

    init {
        authorName = itemView.findViewById(R.id.tv_author_name)
        time = itemView.findViewById(R.id.author_doubt_timestamp_2)
        description = itemView.findViewById(R.id.author_answer_description_2)
        ivDp = itemView.findViewById(R.id.iv_dp_author)
        tvYear = itemView.findViewById(R.id.user_year)
        tvVotes = itemView.findViewById(R.id.tv_votes)
        upVote = itemView.findViewById(R.id.cb_upvote)
        downVote = itemView.findViewById(R.id.cb_downvote)
    }

    fun setData(answerData: AnswerData) {

        itemView.setOnClickListener {
            interactionListener.onAnswerClicked(answerData, adapterPosition)
        }

        authorName.text = answerData.authorName

        try {
            time.text = Utils.getTimeAgo(Date(answerData.date.toString()))
        } catch (e: Exception) {
            time.isVisible = false
        }

        description.text = answerData.description
        tvYear.text = "| ${answerData.authorYear} Year |"

        val votingUseCase = DoubtlessApp.getInstance().getAppCompRoot().getAnswerVotingDoubtCase(answerData.copy())
        setVotesUi(answerData, votingUseCase)

        upVote.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {

                val result = votingUseCase.upvoteDoubt()

                if (result is VotingUseCase.Result.UpVoted) {
                    answerData.netVotes += 1
                } else if (result is VotingUseCase.Result.UndoneUpVote) {
                    answerData.netVotes -= 1
                }

                setVotesUi(answerData, votingUseCase)
            }
        }

        downVote.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {

                val result = votingUseCase.downVoteDoubt()

                if (result is VotingUseCase.Result.DownVoted) {
                    answerData.netVotes -= 1
                } else if (result is VotingUseCase.Result.UndoneDownVote) {
                    answerData.netVotes += 1
                }

                setVotesUi(answerData, votingUseCase)
            }
        }

        Glide.with(ivDp).load(answerData.authorPhotoUrl).circleCrop()
            .into(ivDp)

    }

    private fun setVotesUi(answerData: AnswerData, votingUseCase: VotingUseCase) {
        tvVotes.text = floor(answerData.netVotes).toInt().toString()
        CoroutineScope(Dispatchers.Main).launch {
            when (votingUseCase.getUserCurrentState()) {
                VotingUseCase.UPVOTED -> downVote.isClickable = false
                VotingUseCase.DOWNVOTED -> upVote.isClickable = false
                else -> {
                    downVote.isClickable = true
                    upVote.isClickable = true
                }
            }
        }
    }
}
