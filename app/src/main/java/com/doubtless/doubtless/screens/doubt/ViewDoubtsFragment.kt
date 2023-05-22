package com.doubtless.doubtless.screens.doubt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.doubtless.doubtless.DoubtlessApp
import com.doubtless.doubtless.analytics.AnalyticsTracker
import com.doubtless.doubtless.databinding.FragmentViewDoubtsBinding
import com.doubtless.doubtless.screens.adapters.ViewDoubtsAdapter
import com.doubtless.doubtless.screens.auth.usecases.UserManager

class ViewDoubtsFragment : Fragment() {
    private var _binding: FragmentViewDoubtsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ViewDoubtsViewModel
    private lateinit var adapter: ViewDoubtsAdapter
    private lateinit var userManager: UserManager
    private lateinit var analyticsTracker: AnalyticsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userManager = DoubtlessApp.getInstance().getAppCompRoot().getUserManager()
        analyticsTracker = DoubtlessApp.getInstance().getAppCompRoot().getAnalyticsTracker()
        viewModel = getViewModel()
        viewModel.fetchDoubts()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewDoubtsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // for debouncing
        var lastRefreshed = System.currentTimeMillis()

        binding.layoutSwipe.setOnRefreshListener {

            if (System.currentTimeMillis() - lastRefreshed < 3 * 1000L) {
                binding.layoutSwipe.isRefreshing = false
                return@setOnRefreshListener
            }

            lastRefreshed = System.currentTimeMillis()

            analyticsTracker.trackFeedRefresh()

            binding.layoutSwipe.isRefreshing = true
            viewModel.refreshList()
            adapter.clearCurrentList()
        }

        adapter = ViewDoubtsAdapter(viewModel.allDoubts.toMutableList(), onLastItemReached = {
            viewModel.fetchDoubts()
        }, user = userManager.getCachedUserData()!!)

        // how is rv restoring its scroll pos when switching tabs?
        binding.doubtsRecyclerView.adapter = adapter
        binding.doubtsRecyclerView.layoutManager = LinearLayoutManager(context)

        viewModel.fetchedDoubts.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            adapter.appendDoubts(it)
            viewModel.notifyFetchedDoubtsConsumed()
            binding.layoutSwipe.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getViewModel(): ViewDoubtsViewModel {
        return ViewModelProvider(
            owner = this,
            factory = ViewDoubtsViewModel.Companion.Factory(
                fetchHomeFeedUseCase = DoubtlessApp.getInstance().getAppCompRoot()
                    .getFetchHomeFeedUseCase(),
                analyticsTracker = analyticsTracker,
                userManager = userManager
            )
        )[ViewDoubtsViewModel::class.java]
    }

}