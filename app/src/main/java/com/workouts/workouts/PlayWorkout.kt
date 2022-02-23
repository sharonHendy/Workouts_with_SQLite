package com.workouts.workouts

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.gson.Gson
import me.zhanghai.android.materialprogressbar.MaterialProgressBar
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlinx.android.synthetic.main.play_workout.*

import java.util.*
import kotlin.collections.HashSet

open class PlayWorkout : AppCompatActivity()  {

    enum class TimerState{
        Stopped, Paused, Running
    }

    var timerState = TimerState.Stopped

    var player : MediaPlayer? = null //for sounds
    var playerEnd : MediaPlayer? = null

    lateinit var workout: Workout
     var indexOfWorkout: Int = 0
    lateinit var btnStop : Button
    lateinit var btnStart : Button
    lateinit var btnPause : Button
    lateinit var btnBack  : Button
    var exerciseWhenPaused : Int = -1
    var timeRemaining : Long = 0
    var timePassed : Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.play_workout)

        this.window.decorView.setBackgroundColor(resources.getColor(R.color.black)) //grey

        setWorkoutAndExercises()

        btnStop = findViewById(R.id.btnStop)
        btnStart = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnBack = findViewById(R.id.fab_backToMyWorkouts)

        findViewById<MaterialProgressBar>(R.id.progressCountdown).progress = 100

        btnStart.isEnabled = workout.exercisesAndTimes.size != 0
        btnStop.isEnabled = false
        btnPause.isEnabled = false


        //displays total time
        findViewById<TextView>(R.id.timeCountdown).text = workout.totalTime

        //open listener for play button
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            startTimeCounter()
        }

        //open listener for stop button
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            showStopDialog()
        }

        //open listener for pause button
        findViewById<Button>(R.id.btnPause).setOnClickListener {
            pauseTimeCounter()
        }

        btnBack.setOnClickListener{
            backToMyWorkouts()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        backToMyWorkouts()
    }

    open fun setWorkoutAndExercises(){
        val listOfWorkouts : HashSet<Workout> = getListOfWorkouts(this)
        indexOfWorkout = intent.getIntExtra("WorkoutIndex",0)
        workout  = listOfWorkouts.elementAt(indexOfWorkout)

        playedWorkoutName.text = workout.name

        val ll_exercisesNext : LinearLayout = findViewById(R.id.ll_exercisesNext)
        for (exercise in workout.exercisesAndTimes){
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val rowView: View = inflater.inflate(R.layout.exercise_details, null)
            rowView.findViewById<TextView>(R.id.MW_exerciseName).text = exercise.name
            rowView.findViewById<TextView>(R.id.MW_exerciseTime).text = exercise.time
            ll_exercisesNext.addView(rowView)
        }
    }

    fun backToMyWorkouts(){
        if(timerState != TimerState.Stopped){
            stopTimeCounterClicked()
        }
        finish()
    }


    fun pauseTimeCounter(){
        timerState = TimerState.Paused
        btnStop.isEnabled = true
        btnStart.isEnabled = true
        btnPause.isEnabled = false
    }

    fun resumeTimeCounter(){
        timerState = TimerState.Running
        btnPause.isEnabled = true
        btnStop.isEnabled = true
        btnStart.isEnabled = false

        startTimeCounterForExercise(timeRemaining, exerciseWhenPaused)

    }

    open fun stopTimeCounterClicked(){
        if (timerState == TimerState.Paused){ //when user clicks pause or stop dialog is presented
            timerState = TimerState.Stopped
            stopTimeCounter()
        }
        timerState = TimerState.Stopped
        btnPause.isEnabled = false
        btnStop.isEnabled = false
        btnStart.isEnabled = true

    }

    open fun stopTimeCounter(){ //resets layout and timer
        stopTimeCounterClicked()
        findViewById<MaterialProgressBar>(R.id.progressCountdown).progress = 100
        ll_exercisesNext.removeAllViews()
        setWorkoutAndExercises()
        initializeColor()
        findViewById<TextView>(R.id.timeCountdown).text = workout.totalTime

        saveTotalTimeOfWorkout()
        releasePlayer()
    }

    //opens stop dialog, pauses while user chooses what to do next
    fun showStopDialog(){
        timerState = TimerState.Paused
        val dialog = MaterialDialog(this)
            .customView(R.layout.stop_dialog)
        dialog.findViewById<Button>(R.id.btnStopYes).setOnClickListener { //clicks yes -> stops time counter
            dialog.cancel()
            stopTimeCounterClicked()
        }
        dialog.findViewById<Button>(R.id.btnStopNo).setOnClickListener { //clicks no -> resumes
            dialog.cancel()
            resumeTimeCounter()
        }
        dialog.show()
    }

    fun startTimeCounter(){ //starts timer for the first exercise
        if (timerState == TimerState.Paused){
            resumeTimeCounter()
            return
        }

        timerState = TimerState.Running
        btnPause.isEnabled = true
        btnStop.isEnabled = true
        btnStart.isEnabled = false

        val exercise = workout.exercisesAndTimes[0]
        startTimeCounterForExercise(exercise.timeInMillis, 0)
    }

    fun startTimeCounterForExercise(timeInMillis : Long, i : Int) {
        val countTime: TextView = findViewById(R.id.timeCountdown)
        player = MediaPlayer.create(this@PlayWorkout,R.raw.countdown)
        playerEnd = MediaPlayer.create(this@PlayWorkout,R.raw.sound1) //plays finish sound

        val countDownTimer = object : CountDownTimer(timeInMillis, 1000) {

            override fun onTick(millisUntilFinished: Long) {

                if (timerState != TimerState.Running){ //if user clicked pause or stop
                    player!!.stop()
                    cancel()
                    if(timerState == TimerState.Paused){ //saves state for resume later
                        exerciseWhenPaused = i
                        timeRemaining = millisUntilFinished
                    }
                    if(timerState == TimerState.Stopped){
                        stopTimeCounter()
                    }
                }
                else{
                    //sets the time remaining in the textview, and the progress of the progressbar
                    val format : NumberFormat = DecimalFormat("00")
                    val hour : Long = (millisUntilFinished / 3600000) % 24
                    val min : Long = (millisUntilFinished / 60000) % 60
                    val sec : Long = (millisUntilFinished / 1000) % 60
                    countTime.text = format.format(hour) + ":" + format.format(min) + ":" + format.format(sec)
                    val total :Float= (workout.exercisesAndTimes[i].timeInMillis/1000).toFloat()
                    val secondsRemaining :Float = (millisUntilFinished / 1000).toFloat()
                    val progress = ((secondsRemaining / total) * 100).toInt()
                    findViewById<MaterialProgressBar>(R.id.progressCountdown).progress = progress

                    if(secondsRemaining.equals(2f)){ //plays countdown sound
                        player!!.start()
                    }
                }


            }
            override fun onFinish() {
                countTime.text = "00:00:00"
                ll_exercisesNext.removeViewAt(0)
                timePassed += timeInMillis //for the charts
                if(i+1 < workout.exercisesAndTimes.size){ //starts timer for the next exercise
                    startTimeCounterForExercise(workout.exercisesAndTimes[i + 1].timeInMillis, i + 1)
                }
                else{ //workout finished, stops the timer and resets
                    playerEnd?.start()
                    addToTimePlayedOfWorkout()
                    stopTimeCounter()
                }
            }
        }
        countDownTimer.start()

        nestedScrollView.scrollTo(0,0) //scrolls to top
        //sets the current running exercises view
        val exerciseView = ll_exercisesNext.getChildAt(0)
        exerciseView.findViewById<TextView>(R.id.MW_exerciseName).apply {
            textSize = 30f
            setTextColor(resources.getColor(R.color.purple_exercisePlayed))
        }
        exerciseView.findViewById<TextView>(R.id.MW_exerciseTime).apply {
            textSize = 30f
            setTextColor(resources.getColor(R.color.purple_exercisePlayed))
        }

    }

    //save the total time of workout that has been done
    fun saveTotalTimeOfWorkout(){
        val sharedPreferences: SharedPreferences = getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        val weekPerformance1 = getWeekPerformance1(this)

        val cal : Calendar = Calendar.getInstance()
        val day : Int = cal.get(Calendar.DAY_OF_WEEK)

        when (day){ //saves time in minutes
            Calendar.SUNDAY -> weekPerformance1[0] += (timePassed/1000).toFloat()/60
            Calendar.MONDAY -> weekPerformance1[1] += (timePassed/1000).toFloat()/60
            Calendar.TUESDAY -> weekPerformance1[2] += (timePassed/1000).toFloat()/60
            Calendar.WEDNESDAY -> weekPerformance1[3] += (timePassed/1000).toFloat()/60
            Calendar.THURSDAY -> weekPerformance1[4] += (timePassed/1000).toFloat()/60
            Calendar.FRIDAY -> weekPerformance1[5] += (timePassed/1000).toFloat()/60
            Calendar.SATURDAY -> weekPerformance1[6] += (timePassed/1000).toFloat()/60
        }

        val jsonString = Gson().toJson(weekPerformance1)
        editor.putString("weekPerformance1",jsonString)
        editor.apply()
        timePassed = 0
    }

    //adds to number of times the workout was played
    fun addToTimePlayedOfWorkout(){
        val sharedPreferences: SharedPreferences = getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        val listOfWorkouts  = getListOfWorkouts(this)
        workout.addToTimePlayed()
        listOfWorkouts.remove(workout)
        listOfWorkouts.add(workout)
        val jsonString = Gson().toJson(listOfWorkouts)
        editor.putString("ListOfWorkouts",jsonString)
        editor.apply()
    }

    //releases media player when completes sound
    fun releasePlayer(){
        player?.setOnCompletionListener {
            player?.release()
            player = null
        }
        playerEnd?.setOnCompletionListener {
            playerEnd?.release()
            playerEnd = null
        }

    }

    //colors the exercises in light grey
    open fun initializeColor(){
        for (j in 0 until workout.exercisesAndTimes.size ){
            setExerciseColor(j,"#BCBEBE")  //light grey
        }
    }

    //colors the exercise with the index with the color
    fun setExerciseColor(exerciseIndex: Int, color: String){
        val exercise = findViewById<LinearLayout>(R.id.ll_exercisesNext).getChildAt(exerciseIndex)
        exercise.findViewById<TextView>(R.id.MW_exerciseName).setTextColor(Color.parseColor(color))
        exercise.findViewById<TextView>(R.id.MW_exerciseTime).setTextColor(Color.parseColor(color))
    }

}
