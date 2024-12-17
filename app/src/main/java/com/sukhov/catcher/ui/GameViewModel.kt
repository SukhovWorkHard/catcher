package com.sukhov.catcher.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sukhov.catcher.data.CatcherPositions
import com.sukhov.catcher.data.ScreenSize
import kotlin.random.Random

private const val CATCHABLE_SPEED = 10f
private const val CATCHABLE_SPAWN_INTERVAL = 1000L
private const val LAST_CATCHABLE_SPAWN_TIME_DEFAULT = 0L
private const val DELAY_MILLIS_60_FPS = 16L

class GameViewModel : ViewModel() {

    // observe
    private val _isRunningLiveData = MutableLiveData<Boolean>()
    val isRunningLiveData: LiveData<Boolean> = _isRunningLiveData
    private val _scoreLiveData = MutableLiveData<Int>()
    val scoreLiveData: LiveData<Int> = _scoreLiveData

    // const
    private val catchableSpeed = CATCHABLE_SPEED
    private val catchableSpawnInterval = CATCHABLE_SPAWN_INTERVAL
    private var lastCatchableSpawnTime = LAST_CATCHABLE_SPAWN_TIME_DEFAULT
    private val screenSize = ScreenSize()

    // thread
    private val handler = Handler(Looper.getMainLooper())

    // save state
    private val catcherPositions = CatcherPositions()
    private var catchablePositions = mutableListOf<Pair<Float, Float>>()


    init {
        _isRunningLiveData.value = false
        _scoreLiveData.value = 0
    }

    fun catcherStartWidth(width: Int) = (screenSize.width / 2 - width / 2).toFloat()

    fun catcherStartHeight() = (screenSize.height - 50).toFloat()

    fun defaultCatchableStartX() = Random.nextFloat() * (screenSize.width - 100)

    fun defaultCatchableStartY() = -100f

    fun setScreenSize(x: Int, y: Int) {
        screenSize.apply {
            width = x
            height = y
        }
    }

    fun startGame(createAndDropBlock: (() -> Unit)) {
        _isRunningLiveData.value = true
        handler.post(spawnCatchablesTask(createAndDropBlock))
    }

    fun dropCatchable(ivCatcher: ImageView, catchable: ImageView, catchableUIHandle: (() -> Unit)) {
        if (!isRunning()) return

        val runnable = object : Runnable {
            override fun run() {
                if (!isRunning()) return
                // move the catchable down at a given speed
                catchable.y += catchableSpeed
                // check if the catchable collides with the catcher
                if (catchable.y + catchable.height >= ivCatcher.y &&
                    catchable.y + catchable.height <= ivCatcher.y + ivCatcher.height &&
                    catchable.x + catchable.width >= ivCatcher.x &&
                    catchable.x <= ivCatcher.x + ivCatcher.width
                ) {
                    // increment score
                    _scoreLiveData.value = (_scoreLiveData.value ?: 0) + 1
                    catchableUIHandle.invoke()
                    return
                }
                // if the catchable falls off the screen
                if (catchable.y > screenSize.height) {
                    catchableUIHandle.invoke()
                    return
                }
                // movement of the catchable
                handler.postDelayed(this, DELAY_MILLIS_60_FPS)
            }
        }
        handler.post(runnable)
    }

    fun pauseGame(catcherX: Float, catcherY: Float, catchables: List<ImageView>) {
        if (!isRunning()) return
        _isRunningLiveData.value = false
        // save catcher positions
        catcherPositions.apply {
            catcherPositionX = catcherX
            catcherPositionY = catcherY
        }
        // save catchable positions
        catchablePositions.clear()
        catchables.forEach { catchable ->
            catchablePositions.add(Pair(catchable.x, catchable.y))
        }
        // Stop all updates and catchable movement
        handler.removeCallbacksAndMessages(null)
    }

    fun resumeGame(
        restoreWith: ((catcherPositions: CatcherPositions, MutableList<Pair<Float, Float>>) -> Unit),
        createAndDropBlock: (() -> Unit)
    ) {
        if (isRunning()) return
        _isRunningLiveData.value = true
        // restoring
        restoreWith.invoke(catcherPositions, catchablePositions)
        // resuming catchables movement
        handler.postDelayed(spawnCatchablesTask(createAndDropBlock), DELAY_MILLIS_60_FPS)
    }

    fun touchEvent(event: MotionEvent, moveBlock: ((eventX: Float, screenWidth: Int) -> Unit)) {
        if (event.action == MotionEvent.ACTION_MOVE && isRunning()) {
            moveBlock.invoke(event.x, screenSize.width)
        }
    }

    private fun spawnCatchablesTask(createAndDropBlock: (() -> Unit)) = object : Runnable {
        override fun run() {
            if (!isRunning()) return
            // skip creation if short interval
            System.currentTimeMillis().takeIf { it - lastCatchableSpawnTime >= catchableSpawnInterval }
                ?.let { lastCatchableSpawnTime = it }
                ?: return
            createAndDropBlock.invoke()
            handler.postDelayed(this, catchableSpawnInterval)
        }
    }

    private fun isRunning() = isRunningLiveData.value == true
}