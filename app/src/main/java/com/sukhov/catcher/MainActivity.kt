package com.sukhov.catcher

import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.sukhov.catcher.databinding.ActivityMainBinding
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRunning = true
    private var score = 0

    private var catcherPositionX = 0f
    private var catcherPositionY = 0f
    private var catchablePositions = mutableListOf<Pair<Float, Float>>()

    private val handler = Handler(Looper.getMainLooper())
    private val catchables = mutableListOf<ImageView>()
    private val catchableSpeed = 10f
    private val catchableSpawnInterval = 1000L
    private var lastCatchableSpawnTime = 0L

    private val screenWidth by lazy {
        val point = Point()
        windowManager.defaultDisplay.getSize(point)
        point.x
    }

    private val screenHeight by lazy {
        val point = Point()
        windowManager.defaultDisplay.getSize(point)
        point.y
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        initBinding()
        fetchCatcherDimensionsAndStartGame()

        binding.btnResume.setOnClickListener {
            if (!isRunning) {
                resumeGame()
            }
        }
    }

    private fun initBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun fetchCatcherDimensionsAndStartGame() = with(binding) {
        ivCatcher.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                ivCatcher.viewTreeObserver.removeOnGlobalLayoutListener(this)
                initCatcherStartPosition()
                startGame()
            }
        })
    }

    private fun initCatcherStartPosition() = with(binding.ivCatcher) {
        x = (screenWidth / 2).toFloat()
        y = (screenHeight - 200).toFloat()
    }

    private fun startGame() {
        isRunning = true
        handler.post(spawnCatchablesTask)
        updateResumeButtonUI(isRunning)
    }

    private val spawnCatchablesTask = object : Runnable {
        override fun run() {
            if (!isRunning) return
            createCatchable()
            planningNextCatchableToAppear()
        }
    }

    private fun createCatchable() {
        skipCreationIfShortInterval()

        val catchable = ImageView(this)
        catchable.setImageResource(R.drawable.catchable)
        catchable.layoutParams = ConstraintLayout.LayoutParams(100, 100)

//        var startX: Float
//        var isOverlapping: Boolean
//
//        // generate coordinates to avoid overlapping with other catchables
//        do {
//            startX = Random.nextFloat() * (screenWidth - 100)
//            isOverlapping = catchables.any { existingEgg ->
//                val existingEggX = existingEgg.x
//                val existingEggY = existingEgg.y
//                val distanceX = kotlin.math.abs(existingEggX - startX)
//                val distanceY = kotlin.math.abs(existingEggY - (-100f)) // start Y
//                distanceX < 90 && distanceY < 90 // threshold by X and Y (120)
//            }
//        } while (isOverlapping)

        catchable.x = Random.nextFloat() * (screenWidth - 100) //startX
        catchable.y = -100f // outside the screen
        binding.gameLayout.addView(catchable)
        catchables.add(catchable)

        dropCatchable(catchable)
    }

    private fun skipCreationIfShortInterval() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCatchableSpawnTime < catchableSpawnInterval) return
        lastCatchableSpawnTime = currentTime
    }

    private fun planningNextCatchableToAppear() {
        handler.postDelayed(spawnCatchablesTask, catchableSpawnInterval)
    }

    private fun dropCatchable(catchable: ImageView) {
        if (!isRunning) return

        val runnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                with(binding) {
                    catchable.y += catchableSpeed // move the catchable down at a given speed

                    // check if the catchable collides with the catcher
                    if (catchable.y + catchable.height >= ivCatcher.y &&
                        catchable.y + catchable.height <= ivCatcher.y + ivCatcher.height &&
                        catchable.x + catchable.width >= ivCatcher.x &&
                        catchable.x <= ivCatcher.x + ivCatcher.width) {

                        score++
                        updateScoreUI()

                        removeCatchableFromLayoutAndList(catchable)
                        return
                    }

                    // If the catchable falls off the screen
                    if (catchable.y > screenHeight) {
                        removeCatchableFromLayoutAndList(catchable)
                        return
                    }
                }
                // movement of the catchable
                handler.postDelayed(this, 16) // 60 FPS
            }
        }
        handler.post(runnable)
    }

    private fun removeCatchableFromLayoutAndList(catchable: ImageView) = with(binding) {
        gameLayout.removeView(catchable)
        catchables.remove(catchable)
    }

    private fun updateScoreUI() = with(binding) {
        tvScore.text = "Score: $score"
    }

    private fun moveWolf(x: Float) = with(binding) {
        val wolfWidth = ivCatcher.width
        ivCatcher.x = (x - wolfWidth / 2).coerceIn(0f, (screenWidth - wolfWidth).toFloat())
    }

    override fun onPause() {
        super.onPause()
        pauseGame()
    }

    private fun pauseGame() = with(binding) {
        if (!isRunning) return

        isRunning = false

        // save catcher positions
        catcherPositionX = ivCatcher.x
        catcherPositionY = ivCatcher.y

        // save catchable positions
        catchablePositions.clear()
        catchables.forEach { catchable ->
            catchablePositions.add(Pair(catchable.x, catchable.y))
        }

        // Stop all updates and catchable movement
        handler.removeCallbacksAndMessages(null)

        updateResumeButtonUI(isRunning)
    }

    private fun resumeGame() = with(binding) {
        if (isRunning) return

        isRunning = true

        // cleaning old catchables from the screen
        catchables.forEach { catchable ->
            gameLayout.removeView(catchable)
        }
        catchables.clear()

        // restore the catcher's position
        ivCatcher.x = catcherPositionX
        ivCatcher.y = catcherPositionY

        // restoring catchables
        catchablePositions.forEach { position ->
            val catchable = ImageView(applicationContext).apply {
                setImageResource(R.drawable.catchable)
                layoutParams = ConstraintLayout.LayoutParams(100, 100)
                x = position.first
                y = position.second
            }
            gameLayout.addView(catchable)
            catchables.add(catchable)
            dropCatchable(catchable)
        }

        // resuming catchables movement
        handler.postDelayed(spawnCatchablesTask, 16)

        updateResumeButtonUI(isRunning)
    }


    private fun updateResumeButtonUI(isRunning: Boolean) = with(binding) {
        btnResume.visibility = if (isRunning) View.GONE else View.VISIBLE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE && isRunning) {
            moveWolf(event.x)
        }
        return true
    }
}