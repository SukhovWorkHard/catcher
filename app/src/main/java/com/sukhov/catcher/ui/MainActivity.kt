package com.sukhov.catcher.ui

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.sukhov.catcher.R
import com.sukhov.catcher.databinding.ActivityMainBinding

private const val CATCHABLE_SIZE = 100

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameViewModel by viewModels()
    private val catchables = mutableListOf<ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initScreenSize()
        initBinding()
        initObservers()
        initClickers()
        startGameAfterCatcherPositions()
    }

    override fun onPause() {
        super.onPause()
        viewModel.pauseGame(
            binding.ivCatcher.x,
            binding.ivCatcher.y,
            catchables
        )
    }

    private fun initBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun initObservers() {
        viewModel.isRunningLiveData.observe(this) { updateResumeButtonUI(it) }
        viewModel.scoreLiveData.observe(this) { updateScoreUI(it) }
    }

    private fun updateResumeButtonUI(isRunning: Boolean) = with(binding) {
        btnResume.visibility = if (isRunning) View.GONE else View.VISIBLE
    }

    private fun updateScoreUI(score: Int) = with(binding) {
        tvScore.text = getString(R.string.score, score)
    }

    private fun initClickers() = with(binding) {
        btnResume.setOnClickListener {
            viewModel.resumeGame(restoreWith = { catcherPositions, catchablePositions ->
                // clear old catchables
                catchables.forEach { catchable ->
                    gameLayout.removeView(catchable)
                }
                catchables.clear()
                // restore catcher positions
                ivCatcher.x = catcherPositions.catcherPositionX
                ivCatcher.y = catcherPositions.catcherPositionY
                // restoring catchables
                catchablePositions.forEach { position ->
                    createAndDropCatchableView(position.first, position.second)
                }
            }, createAndDropBlock = { createAndDropCatchableView() })
        }
    }

    private fun initScreenSize() {
        resources.displayMetrics.let {
            viewModel.setScreenSize(it.widthPixels, it.heightPixels)
        }
    }

    private fun startGameAfterCatcherPositions() = with(binding) {
        ivCatcher.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // set catcher start positions
                ivCatcher.apply {
                    x = viewModel.catcherStartWidth(this.width)
                    y = viewModel.catcherStartHeight()
                }
                // start game
                viewModel.startGame(createAndDropBlock = { createAndDropCatchableView() })
                // remove listener
                ivCatcher.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun createAndDropCatchableView(
        startX: Float = viewModel.defaultCatchableStartX(),
        startY: Float = viewModel.defaultCatchableStartY()
    ) {
        val catchable = fetchCatchable(startX, startY)
        binding.gameLayout.addView(catchable)
        catchables.add(catchable)
        viewModel.dropCatchable(binding.ivCatcher, catchable, catchableUIHandle = {
            // remove catchable from layout and list
            binding.gameLayout.removeView(catchable)
            catchables.remove(catchable)
        })
    }

    private fun fetchCatchable(startX: Float, startY: Float) = ImageView(applicationContext).apply {
        setImageResource(R.drawable.catchable)
        layoutParams = FrameLayout.LayoutParams(CATCHABLE_SIZE, CATCHABLE_SIZE)
        x = startX
        y = startY
    }

    private fun moveCatcher(x: Float, screenWidth: Int) = with(binding) {
        val wolfWidth = ivCatcher.width
        ivCatcher.x = (x - wolfWidth / 2).coerceIn(0f, (screenWidth - wolfWidth).toFloat())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        viewModel.touchEvent(event) { eventX, screenWidth ->
            moveCatcher(eventX, screenWidth)
        }
        return true
    }
}