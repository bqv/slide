package ltd.ucode.slide.ui.submissionView

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.MarginLayoutParamsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.itemanimators.AlphaInAnimator
import com.mikepenz.itemanimators.SlideUpAlphaAnimator
import dagger.hilt.android.AndroidEntryPoint
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ltd.ucode.network.data.IPost
import ltd.ucode.slide.App
import ltd.ucode.slide.R
import ltd.ucode.slide.SettingValues
import ltd.ucode.slide.SettingValues.alwaysShowFAB
import ltd.ucode.slide.SettingValues.colours
import ltd.ucode.slide.SettingValues.defaultCardView
import ltd.ucode.slide.SettingValues.fabType
import ltd.ucode.slide.SettingValues.single
import ltd.ucode.slide.SettingValues.subredditSearchMethod
import ltd.ucode.slide.databinding.FragmentVerticalcontentBinding
import ltd.ucode.slide.repository.CommentRepository
import ltd.ucode.slide.repository.PostRepository
import ltd.ucode.slide.ui.BaseActivity
import ltd.ucode.slide.ui.main.MainActivity
import me.ccrama.redditslide.Activities.Search
import me.ccrama.redditslide.Activities.Submit
import me.ccrama.redditslide.Activities.SubredditView
import me.ccrama.redditslide.Adapters.SubmissionDisplay
import me.ccrama.redditslide.Adapters.SubredditPosts
import me.ccrama.redditslide.Constants
import me.ccrama.redditslide.HasSeen
import me.ccrama.redditslide.Hidden
import me.ccrama.redditslide.OfflineSubreddit
import me.ccrama.redditslide.Visuals.ColorPreferences
import me.ccrama.redditslide.Visuals.Palette
import me.ccrama.redditslide.handler.ToolbarScrollHideHandler
import me.ccrama.redditslide.submission
import me.ccrama.redditslide.util.LayoutUtils
import me.ccrama.redditslide.views.CatchStaggeredGridLayoutManager
import me.ccrama.redditslide.views.CreateCardView
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class SubmissionsViewFragment : Fragment(), SubmissionDisplay {
    private val logger: KLogger = KotlinLogging.logger {}
    private val viewModel: SubmissionsViewViewModel by viewModels()

    val argument get() = viewModel.id

    private lateinit var binding: FragmentVerticalcontentBinding
    @Inject
    lateinit var postRepository: PostRepository
    @Inject
    lateinit var commentRepository: CommentRepository

    val rv: RecyclerView get() = binding.verticalContent
    lateinit var posts: SubredditPosts
    val adapter: SubmissionAdapter by lazy {
        SubmissionAdapter(requireActivity(), posts!!, rv, viewModel.id!!, this@SubmissionsViewFragment)
    }
    var forced = false
    var diff = 0
    private var fab: FloatingActionButton? = null
    private var visibleItemCount = 0
    private var pastVisiblesItems = 0
    private var totalItemCount = 0
    private var mSwipeRefreshLayout: SwipeRefreshLayout? = null

    var mLongPressRunnable: Runnable? = null
    var detector = GestureDetector(activity, SimpleOnGestureListener())
    var origY = 0f

    var header: View? = null
    var toolbarScroll: ToolbarScrollHideHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireArguments().apply {
            viewModel.id = getString("id")
            viewModel.main = getBoolean("main", false)
            viewModel.forceLoad = getBoolean("load", false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val contextThemeWrapper: Context = ContextThemeWrapper(
            activity,
            ColorPreferences(inflater.context).getThemeSubreddit(viewModel.id)
        )
        binding = FragmentVerticalcontentBinding.inflate(inflater, container, false)
        val v = binding.root
        if (activity is MainActivity) {
            binding.back.setBackgroundResource(0)
        }
        rv!!.setHasFixedSize(true)
        val mLayoutManager = createLayoutManager(
            LayoutUtils.getNumColumns(resources.configuration.orientation, requireActivity())
        )
        if (activity !is SubredditView) {
            binding.back.background = null
        }
        rv!!.layoutManager = mLayoutManager
        rv!!.itemAnimator = SlideUpAlphaAnimator().withInterpolator(LinearOutSlowInInterpolator())
        rv!!.layoutManager!!.scrollToPosition(0)
        mSwipeRefreshLayout = binding.activityMainSwipeRefreshLayout
        mSwipeRefreshLayout!!.setColorSchemeColors(*Palette.getColors(viewModel.id, context))
        /**
         * If using List view mode, we need to remove the start margin from the SwipeRefreshLayout.
         * The scrollbar style of "outsideInset" creates a 4dp padding around it. To counter this,
         * change the scrollbar style to "insideOverlay" when list view is enabled.
         * To recap: this removes the margins from the start/end so list view is full-width.
         */
        if (defaultCardView === CreateCardView.CardEnum.LIST) {
            val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            MarginLayoutParamsCompat.setMarginStart(params, 0)
            rv!!.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            mSwipeRefreshLayout!!.layoutParams = params
        }
        /**
         * If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
         * So, we estimate the height of the header in dp.
         * If the view type is "single" (and therefore "commentPager"), we need a different offset
         */
        val HEADER_OFFSET =
            if ((single || activity is SubredditView)) Constants.SINGLE_HEADER_VIEW_OFFSET else Constants.TAB_HEADER_VIEW_OFFSET
        mSwipeRefreshLayout!!.setProgressViewOffset(
            false, HEADER_OFFSET - Constants.PTR_OFFSET_TOP,
            HEADER_OFFSET + Constants.PTR_OFFSET_BOTTOM
        )
        if (SettingValues.fab) {
            fab = binding.fabLayout.postFloatingActionButton
            when (fabType) {
                Constants.FAB_POST -> {
                    fab!!.setImageResource(R.drawable.ic_add)
                    fab!!.contentDescription = getString(R.string.btn_fab_post)
                    fab!!.setOnClickListener {
                        val inte = Intent(activity, Submit::class.java)
                        inte.putExtra(Submit.EXTRA_SUBREDDIT, viewModel.id)
                        requireActivity().startActivity(inte)
                    }
                }
                Constants.FAB_SEARCH -> {
                    fab!!.setImageResource(R.drawable.ic_search)
                    fab!!.contentDescription = getString(R.string.btn_fab_search)
                    fab!!.setOnClickListener(object : View.OnClickListener {
                        var term: String? = null
                        override fun onClick(v: View) {
                            val builder = MaterialDialog(activity!!).show {
                                title(R.string.search_title)
                                input(hintRes = R.string.search_msg, waitForPositiveButton = false) { _, charSequence ->
                                    term = charSequence.toString()
                                }

                                //Add "search current sub" if it is not frontpage/all/random
                                if ((!viewModel.id.equals("frontpage", ignoreCase = true)
                                        && !viewModel.id.equals("all", ignoreCase = true)
                                        && !viewModel.id!!.contains(".")
                                        && !viewModel.id!!.contains("/m/")
                                        && !viewModel.id.equals("friends", ignoreCase = true)
                                        && !viewModel.id.equals("random", ignoreCase = true)
                                        && !viewModel.id.equals("popular", ignoreCase = true)
                                        && !viewModel.id.equals("myrandom", ignoreCase = true)
                                        && !viewModel.id.equals("randnsfw", ignoreCase = true))
                                ) {
                                    positiveButton(text = getString(R.string.search_subreddit, viewModel.id)) { _ ->
                                        val i = Intent(activity, Search::class.java)
                                        i.putExtra(Search.EXTRA_TERM, term)
                                        i.putExtra(Search.EXTRA_SUBREDDIT, viewModel.id)
                                        startActivity(i)
                                    }
                                    neutralButton(R.string.search_all) { _ ->
                                        val i = Intent(activity, Search::class.java)
                                        i.putExtra(Search.EXTRA_TERM, term)
                                        startActivity(i)
                                    }
                                } else {
                                    positiveButton(R.string.search_all) { _ ->
                                        val i = Intent(activity, Search::class.java)
                                        i.putExtra(Search.EXTRA_TERM, term)
                                        startActivity(i)
                                    }
                                }
                            }
                        }
                    })
                }
                else -> {
                    fab!!.setImageResource(R.drawable.ic_visibility_off)
                    fab!!.contentDescription = getString(R.string.btn_fab_hide)
                    fab!!.setOnClickListener {
                        if (!App.fabClear) {
                            AlertDialog.Builder(requireActivity())
                                .setTitle(R.string.settings_fabclear)
                                .setMessage(R.string.settings_fabclear_msg)
                                .setPositiveButton(R.string.btn_ok) { dialog: DialogInterface?, which: Int ->
                                    colours.edit()
                                        .putBoolean(SettingValues.PREF_FAB_CLEAR, true)
                                        .apply()
                                    App.fabClear = true
                                    clearSeenPosts(false)
                                }
                                .show()
                        } else {
                            clearSeenPosts(false)
                        }
                    }
                    val handler = Handler()
                    fab!!.setOnTouchListener { v, event ->
                        detector.onTouchEvent(event)
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            origY = event.y
                            handler.postDelayed(
                                (mLongPressRunnable)!!,
                                ViewConfiguration.getLongPressTimeout().toLong()
                            )
                        }
                        if (((event.action == MotionEvent.ACTION_MOVE) && abs(event.y - origY) > fab!!.height / 2.0f) || (event.action == MotionEvent.ACTION_UP)) {
                            handler.removeCallbacks((mLongPressRunnable)!!)
                        }
                        false
                    }
                    mLongPressRunnable = Runnable {
                        fab!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (!App.fabClear) {
                            AlertDialog.Builder(requireActivity())
                                .setTitle(R.string.settings_fabclear)
                                .setMessage(R.string.settings_fabclear_msg)
                                .setPositiveButton(R.string.btn_ok) { dialog: DialogInterface?, which: Int ->
                                    colours.edit()
                                        .putBoolean(SettingValues.PREF_FAB_CLEAR, true)
                                        .apply()
                                    App.fabClear = true
                                    clearSeenPosts(true)
                                }
                                .show()
                        } else {
                            clearSeenPosts(true)
                        }
                        val s = Snackbar.make(
                            rv!!,
                            resources.getString(R.string.posts_hidden_forever),
                            Snackbar.LENGTH_LONG
                        )
                        /*Todo a way to unhide
                        s.setAction(R.string.btn_undo, new View.OnClickListener() {

                            @Override
                            public void onClick(View v) {

                            }
                        });*/LayoutUtils.showSnackbar(s)
                    }
                }
            }
        } else {
            binding.fabLayout.postFloatingActionButton.visibility = View.GONE
        }
        if (fab != null) fab!!.show()
        header = requireActivity().findViewById(R.id.header)

        //TODO, have it so that if the user clicks anywhere in the rv to hide and cancel GoToSubreddit?
//        final TextInputEditText GO_TO_SUB_FIELD = (TextInputEditText) getActivity().findViewById(R.id.toolbar_search);
//        final Toolbar TOOLBAR = ((Toolbar) getActivity().findViewById(R.id.toolbar));
//        final String PREV_TITLE = TOOLBAR.getTitle().toString();
//        final ImageView CLOSE_BUTTON = (ImageView) getActivity().findViewById(R.id.close);
//
//        rv.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                System.out.println("touched");
//                KeyboardUtil.hideKeyboard(getActivity(), v.getWindowToken(), 0);
//
//                GO_TO_SUB_FIELD.setText("");
//                GO_TO_SUB_FIELD.setVisibility(View.GONE);
//                CLOSE_BUTTON.setVisibility(View.GONE);
//                TOOLBAR.setTitle(PREV_TITLE);
//
//                return false;
//            }
//        });
        resetScroll()

        requireActivity().lifecycleScope.launch {
            viewModel.flow.collectLatest { pagingData ->
                logger.trace("collected posts page")
                adapter.submitData(pagingData)
            }
        }

        App.isLoading = false
        if (MainActivity.shouldLoad == null || viewModel.id == null ||
            MainActivity.shouldLoad == viewModel.id || activity !is MainActivity) {
            doAdapter()
        }
        return v
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentOrientation = newConfig.orientation
        val mLayoutManager = rv!!.layoutManager as CatchStaggeredGridLayoutManager?
        mLayoutManager!!.spanCount = LayoutUtils.getNumColumns(currentOrientation, requireActivity())
    }

    fun doAdapter() {
        if (!MainActivity.isRestart) {
            mSwipeRefreshLayout!!.post { mSwipeRefreshLayout!!.isRefreshing = true }
        }
        posts = SubredditPosts(viewModel.id!!, requireContext(), postRepository)
        //adapter!!.setHasStableIds(true)
        rv!!.adapter = adapter
        posts!!.loadMore(requireActivity(), this, true)
        mSwipeRefreshLayout!!.setOnRefreshListener { refresh() }
    }

    fun doAdapter(force18: Boolean) {
        mSwipeRefreshLayout!!.post { mSwipeRefreshLayout!!.isRefreshing = true }
        posts = SubredditPosts(viewModel.id!!, requireContext(), postRepository, force18)
        //adapter!!.setHasStableIds(true)
        rv!!.adapter = adapter
        posts!!.loadMore(requireActivity(), this, true)
        mSwipeRefreshLayout!!.setOnRefreshListener { refresh() }
    }

    fun clearSeenPosts(forever: Boolean): List<IPost>? {
        if (adapter!!.dataSet.posts != null) {
            val originalDataSetPosts: List<IPost> = adapter!!.dataSet.posts
            val o = OfflineSubreddit.getSubreddit(viewModel.id!!.lowercase(), false, activity)
            for (i in adapter!!.dataSet.posts.size downTo -1 + 1) {
                try {
                    if (HasSeen.getSeen(adapter!!.dataSet.posts[i].submission!!)) {
                        if (forever) {
                            Hidden.setHidden(adapter!!.dataSet.posts[i])
                        }
                        o!!.clearPost(adapter!!.dataSet.posts[i])
                        adapter!!.dataSet.posts.removeAt(i)
                        if (adapter!!.dataSet.posts.isEmpty()) {
                            adapter!!.notifyDataSetChanged()
                        } else {
                            rv!!.itemAnimator = AlphaInAnimator()
                            adapter!!.notifyItemRemoved(i + 1)
                        }
                    }
                } catch (e: IndexOutOfBoundsException) {
                    //Let the loop reset itself
                }
            }
            adapter!!.notifyItemRangeChanged(0, adapter!!.dataSet.posts.size)
            o!!.writeToMemoryNoStorage()
            rv!!.itemAnimator =
                SlideUpAlphaAnimator().withInterpolator(LinearOutSlowInInterpolator())
            return originalDataSetPosts
        }
        return null
    }

    override fun onResume() {
        super.onResume()
        if (adapter != null && adapterPosition > 0 && currentPosition == adapterPosition) {
            if (adapter!!.dataSet.posts.size >= adapterPosition - 1
                && adapter!!.dataSet.posts[adapterPosition - 1] === currentSubmission
            ) {
                adapter!!.performClick(adapterPosition)
                adapterPosition = -1
            }
        }
    }

    private fun refresh() {
        posts!!.forced = true
        forced = true
        posts!!.loadMore(mSwipeRefreshLayout!!.context, this, true, viewModel.id!!)
    }

    fun forceRefresh() {
        toolbarScroll!!.toolbarShow()
        rv!!.layoutManager!!.scrollToPosition(0)
        mSwipeRefreshLayout!!.post {
            mSwipeRefreshLayout!!.isRefreshing = true
            refresh()
        }
        mSwipeRefreshLayout!!.isRefreshing = false
    }

    override fun updateSuccess(submissions: List<IPost>, startIndex: Int) {
        if (activity != null) {
            if (activity is MainActivity) {
                if ((requireActivity() as MainActivity).runAfterLoad != null) {
                    Handler().post((requireActivity() as MainActivity).runAfterLoad!!)
                }
            }
            requireActivity().runOnUiThread {
                if (mSwipeRefreshLayout != null) {
                    mSwipeRefreshLayout!!.isRefreshing = false
                }
                if (startIndex != -1 && !forced) {
                    adapter!!.notifyItemRangeInserted(startIndex + 1, posts!!.posts.size)
                } else {
                    forced = false
                    rv!!.scrollToPosition(0)
                }
                adapter!!.notifyDataSetChanged()
            }
            if (MainActivity.isRestart) {
                MainActivity.isRestart = false
                posts!!.offline = false
                rv!!.layoutManager!!.scrollToPosition(MainActivity.restartPage + 1)
            }
            if (startIndex < 10) resetScroll()
        }
    }

    override fun updateOffline(submissions: List<IPost>, cacheTime: Long) {
        if (activity is MainActivity) {
            if ((requireActivity() as MainActivity).runAfterLoad != null) {
                Handler().post((requireActivity() as MainActivity).runAfterLoad!!)
            }
        }
        if (this.isAdded) {
            if (mSwipeRefreshLayout != null) {
                mSwipeRefreshLayout!!.isRefreshing = false
            }
            adapter!!.notifyDataSetChanged()
        }
    }

    override fun updateOfflineError() {
        if (activity is MainActivity) {
            if ((requireActivity() as MainActivity).runAfterLoad != null) {
                Handler().post((requireActivity() as MainActivity).runAfterLoad!!)
            }
        }
        mSwipeRefreshLayout!!.isRefreshing = false
        adapter!!.setError(true)
    }

    override fun updateError() {
        if (activity is MainActivity) {
            if ((requireActivity() as MainActivity).runAfterLoad != null) {
                Handler().post((requireActivity() as MainActivity).runAfterLoad!!)
            }
        }
        mSwipeRefreshLayout!!.isRefreshing = false
    }

    override fun updateViews() {
        if (adapter!!.dataSet.posts != null) {
            for (i in adapter!!.dataSet.posts.size downTo -1 + 1) {
                try {
                    if (HasSeen.getSeen(adapter!!.dataSet.posts[i].submission!!)) {
                        adapter!!.notifyItemChanged(i + 1)
                    }
                } catch (e: IndexOutOfBoundsException) {
                    //Let the loop reset itself
                }
            }
        }
    }

    override fun onAdapterUpdated() {
        adapter!!.notifyDataSetChanged()
    }

    fun resetScroll() {
        if (toolbarScroll == null) {
            toolbarScroll =
                object : ToolbarScrollHideHandler((requireActivity() as BaseActivity).mToolbar, header) {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        if (!posts!!.loading && !posts!!.nomore && !posts!!.offline && !adapter!!.hasError) {
                            visibleItemCount = rv!!.layoutManager!!.childCount
                            totalItemCount = rv!!.layoutManager!!.itemCount
                            val firstVisibleItems =
                                (rv!!.layoutManager as CatchStaggeredGridLayoutManager?)!!.findFirstVisibleItemPositions(
                                    null
                                )
                            if (firstVisibleItems != null && firstVisibleItems.isNotEmpty()) {
                                for (firstVisibleItem: Int in firstVisibleItems) {
                                    pastVisiblesItems = firstVisibleItem
                                    if ((SettingValues.scrollSeen
                                                && (pastVisiblesItems > 0
                                                ) && SettingValues.storeHistory)
                                    ) {
                                        HasSeen.addSeenScrolling(
                                            posts!!.posts[pastVisiblesItems - 1].uri
                                        )
                                    }
                                }
                            }
                            if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount) {
                                posts!!.loading = true
                                posts!!.loadMore(
                                    mSwipeRefreshLayout!!.context,
                                    this@SubmissionsViewFragment, false, posts!!.subreddit
                                )
                            }
                        }

                        /*
                        if(dy <= 0 && !down){
                            (getActivity()).findViewById(R.id.header).animate().translationY(((BaseActivity)getActivity()).mToolbar.getTop()).setInterpolator(new AccelerateInterpolator()).start();
                            down = true;
                        } else if(down){
                            (getActivity()).findViewById(R.id.header).animate().translationY(((BaseActivity)getActivity()).mToolbar.getTop()).setInterpolator(new AccelerateInterpolator()).start();
                            down = false;
                        }*/
                        //todo For future implementation instead of scrollFlags
                        if ((recyclerView.scrollState
                                    == RecyclerView.SCROLL_STATE_DRAGGING)
                        ) {
                            diff += dy
                        } else {
                            diff = 0
                        }
                        if (fab != null) {
                            if ((dy <= 0) && (fab!!.id != 0) && SettingValues.fab) {
                                if ((recyclerView.scrollState
                                            != RecyclerView.SCROLL_STATE_DRAGGING
                                            || diff < -fab!!.height * 2)
                                ) {
                                    fab!!.show()
                                }
                            } else {
                                if (!alwaysShowFAB) {
                                    fab!!.hide()
                                }
                            }
                        }
                    }

                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
//                switch (newState) {
//                    case RecyclerView.SCROLL_STATE_IDLE:
//                        ((Reddit)getActivity().getApplicationContext()).getImageLoader().resume();
//                        break;
//                    case RecyclerView.SCROLL_STATE_DRAGGING:
//                        ((Reddit)getActivity().getApplicationContext()).getImageLoader().resume();
//                        break;
//                    case RecyclerView.SCROLL_STATE_SETTLING:
//                        ((Reddit)getActivity().getApplicationContext()).getImageLoader().pause();
//                        break;
//                }
                        super.onScrollStateChanged(recyclerView, newState)
                        //If the toolbar search is open, and the user scrolls in the Main view--close the search UI
                        if (activity is MainActivity
                                && (subredditSearchMethod == Constants.SUBREDDIT_SEARCH_METHOD_TOOLBAR
                                    || subredditSearchMethod == Constants.SUBREDDIT_SEARCH_METHOD_BOTH)
                                && (context!! as MainActivity).findViewById<View>(R.id.toolbar_search).visibility == View.VISIBLE
                        ) {
                            (context!! as MainActivity).findViewById<View>(R.id.close_search_toolbar).performClick()
                        }
                    }
                }
            rv!!.addOnScrollListener(toolbarScroll!!)
        } else {
            toolbarScroll!!.reset = true
        }
    }

    companion object { // wtf is happening here
        private var adapterPosition = 0
        private var currentPosition = 0
        private var currentSubmission: IPost? = null

        @JvmStatic
        fun createLayoutManager(numColumns: Int): RecyclerView.LayoutManager {
            return CatchStaggeredGridLayoutManager(
                numColumns,
                CatchStaggeredGridLayoutManager.VERTICAL
            )
        }

        @JvmStatic
        fun datachanged(adaptorPosition2: Int) {
            adapterPosition = adaptorPosition2
        }

        @JvmStatic
        fun currentPosition(adapterPosition: Int) {
            currentPosition = adapterPosition
        }

        @JvmStatic
        fun currentSubmission(current: IPost?) {
            currentSubmission = current
        }
    }
}