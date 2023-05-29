package com.doubtless.doubtless.screens.doubt.create

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.doubtless.doubtless.DoubtlessApp
import com.doubtless.doubtless.analytics.AnalyticsTracker
import com.doubtless.doubtless.databinding.FragmentCreateDoubtBinding
import com.doubtless.doubtless.screens.auth.User
import com.doubtless.doubtless.screens.auth.usecases.UserManager
import com.doubtless.doubtless.screens.doubt.usecases.DoubtDataSharedPrefUseCase
import com.doubtless.doubtless.screens.doubt.usecases.PostDoubtUseCase
import com.doubtless.doubtless.screens.onboarding.OnBoardingAttributes
import com.doubtless.doubtless.screens.onboarding.usecases.FetchOnBoardingDataUseCase
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class CreateDoubtFragment : Fragment() {
    private var _binding: FragmentCreateDoubtBinding? = null

    private val binding get() = _binding!!
    private var isButtonClicked = false
    private lateinit var db: FirebaseFirestore
    private lateinit var userManager: UserManager
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var postDoubtUseCase: PostDoubtUseCase
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private var maxHeadingCharLimit by Delegates.notNull<Int>()
    private var maxDescriptionCharLimit by Delegates.notNull<Int>()
    private var maxKeywordsLimit by Delegates.notNull<Int>()

    private val keywordsEntered = mutableListOf<String>()

    private lateinit var doubtDataSharedPrefUseCase: DoubtDataSharedPrefUseCase
    private lateinit var onBoardingDataUseCase: FetchOnBoardingDataUseCase

    private var onBoardingAttributes: OnBoardingAttributes? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Firebase.firestore
        userManager = DoubtlessApp.getInstance().getAppCompRoot().getUserManager()
        analyticsTracker = DoubtlessApp.getInstance().getAppCompRoot().getAnalyticsTracker()
        doubtDataSharedPrefUseCase =
            DoubtlessApp.getInstance().getAppCompRoot().getDoubtDataSharedPrefUseCase()
        onBoardingDataUseCase = DoubtlessApp.getInstance().getAppCompRoot()
            .getFetchOnBoardingDataUseCase(userManager.getCachedUserData()!!)
        remoteConfig = DoubtlessApp.getInstance().getAppCompRoot().getRemoteConfig()
        postDoubtUseCase = DoubtlessApp.getInstance().getAppCompRoot().getPostDoubtUseCase()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateDoubtBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.doubtHeading.setRawInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        binding.doubtHeading.requestFocus()
        val mgr = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        mgr.showSoftInput(binding.doubtHeading, InputMethodManager.SHOW_FORCED)

        getMaxCharacterLimit()

        if (onBoardingAttributes == null) {
            CoroutineScope(Dispatchers.Main).launch {

                val result = onBoardingDataUseCase.getData()
                if (!isAdded || result is FetchOnBoardingDataUseCase.Result.Error) return@launch

                onBoardingAttributes = (result as FetchOnBoardingDataUseCase.Result.Success).data

                binding.chipgroupTags.removeAllViews()

                repeat(onBoardingAttributes!!.tags!!.size) {
                    val chip = Chip(requireContext()).apply {
                        text = onBoardingAttributes!!.tags!![it]
                        isCheckable = true
                    }

                    binding.chipgroupTags.addView(chip)
                }
            }
        } else {
            binding.chipgroupTags.removeAllViews()

            repeat(onBoardingAttributes!!.tags!!.size) {
                val chip = Chip(requireContext()).apply {
                    text = onBoardingAttributes!!.tags!![it]
                    isCheckable = true
                }

                binding.chipgroupTags.addView(chip)
            }
        }

        // post button
        binding.postButton.setOnClickListener {
            if (!isButtonClicked) {
                checkText()
            }
        }

        // tags

        // keywords
        binding.doubtKeywords.addTextChangedListener {
            val words = it.toString().split("/")
            keywordsEntered.clear()
            keywordsEntered.addAll(words)
            binding.doubtKeywordsPreview.text =
                "Preview [${words.size}/${maxKeywordsLimit}]: $words"
        }

        // restore drafted text
        val (savedHeadingText, savedDescriptionText) = doubtDataSharedPrefUseCase.getSavedDoubtData()

        savedHeadingText?.let {
            if (it.isNotEmpty()) {
                binding.doubtHeading.setText(savedHeadingText)
            }
        }

        savedDescriptionText?.let {
            if (it.isNotEmpty()) {
                binding.doubtDescription.setText(savedDescriptionText)
            }
        }

    }

    override fun onPause() {
        super.onPause()
        doubtDataSharedPrefUseCase.saveDoubtData(
            binding.doubtHeading.text.toString(), binding.doubtDescription.text.toString()
        )
    }


    private fun checkText() {
        val errorMessage = isEverythingValid()

        if (errorMessage != null) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
            return
        }

        analyticsTracker.trackPostDoubtButtonClicked()

        isButtonClicked = true
        binding.progress.isVisible = !binding.progress.isVisible
        binding.postButton.isClickable = false
        binding.postButton.alpha = 0.8f

        showConfirmationDialog(getSelectedTags(), keywordsEntered)

    }

    private fun isEverythingValid(): String? {
        if (binding.doubtHeading.text.toString().isEmpty()) {
            return "Heading required"
        }

        if (binding.doubtDescription.text.toString().isEmpty()) {
            return "Description required"
        }

        if (keywordsEntered.size > maxKeywordsLimit) {
            return "keyword size is more than $maxKeywordsLimit"
        }

        if (keywordsEntered.size == 0) {
            return "Please enter a keyword!"
        }

        val size = getSelectedTags().size

        if (size > 3)
            return "More than 3 Tags are not allowed!"

        if (size == 0)
            return "Please select a tag!"

        return null
    }

    private fun getSelectedTags(): List<String> {
        val checkedTags = mutableListOf<String>()

        binding.chipgroupTags.checkedChipIds.forEach {
            checkedTags.add(binding.chipgroupTags.findViewById<Chip>(it).text.toString())
        }

        return checkedTags
    }

    private fun showConfirmationDialog(tags: List<String>, keywords: List<String>) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Confirmation").setMessage("Are you sure you want to post?")
            .setPositiveButton("Post") { dialogInterface: DialogInterface, _: Int ->
                createDoubt(
                    binding.doubtHeading.text.toString(),
                    binding.doubtDescription.text.toString(),
                    tags, keywords,
                    userManager.getCachedUserData()!!
                )
                dialogInterface.dismiss()
            }.setNegativeButton("Cancel") { dialogInterface: DialogInterface, _: Int ->
                isButtonClicked = false
                binding.postButton.alpha = 1f
                dialogInterface.dismiss()
            }.show()
    }

    private fun getMaxCharacterLimit() {
        val maxCharLimit = remoteConfig.getString("max_character_limit")

        try {
            val jsonObject = Gson().fromJson(maxCharLimit, Map::class.java) as Map<*, *>

            maxHeadingCharLimit = (jsonObject["max_heading_char_limit"] as Double).toInt()
            maxDescriptionCharLimit =
                (jsonObject["max_description_char_limit"] as Double).toInt()
            maxKeywordsLimit = (jsonObject["keywords_limit"] as Double).toInt()

            setMaxCharacterLimit(maxHeadingCharLimit, maxDescriptionCharLimit, maxKeywordsLimit)

        } catch (e: Exception) {
            Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setMaxCharacterLimit(
        maxHeadingCharLimit: Int,
        maxDescriptionCharLimit: Int,
        maxKeywordsLimit: Int
    ) {
        binding.doubtHeading.filters =
            arrayOf<InputFilter>(InputFilter.LengthFilter(maxHeadingCharLimit))
        binding.doubtDescription.filters =
            arrayOf<InputFilter>(InputFilter.LengthFilter(maxDescriptionCharLimit))
    }

    private fun createDoubt(
        heading: String,
        description: String,
        tags: List<String>,
        keywords: List<String>,
        user: User
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = postDoubtUseCase.post(
                PostDoubtUseCase.PostDoubtRequest(
                    author_id = user.id!!,
                    author_name = user.name!!,
                    author_photo_url = user.photoUrl!!,
                    author_college = user.local_user_attr!!.college!!,
                    heading = heading,
                    description = description,
                    net_votes = 0f,
                    tags = tags,
                    keywords = keywords
                )
            )

            if (!isAdded) return@launch

            if (result is PostDoubtUseCase.Result.Success) {
                isButtonClicked = false
                binding.postButton.alpha = 1f
                binding.doubtHeading.setText("")
                binding.doubtDescription.setText("")
                Toast.makeText(context, "Posted Successfully!", Toast.LENGTH_SHORT).show()
            } else {
                isButtonClicked = false
                binding.postButton.alpha = 1f
                Toast.makeText(
                    /* context = */ context,
                    /* text = */ "Failed to Post ${(result as PostDoubtUseCase.Result.Error).message}",
                    /* duration = */ Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}