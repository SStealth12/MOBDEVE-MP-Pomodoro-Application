package com.mobdeve.s13.estanol.miguelfrancis.mp.ui.status

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.mobdeve.s13.estanol.miguelfrancis.mp.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mobdeve.s13.estanol.miguelfrancis.mp.PomodoroService
import com.mobdeve.s13.estanol.miguelfrancis.mp.databinding.FragmentStatusBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StatusFragment : Fragment() {

    private lateinit var binding: FragmentStatusBinding
    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)

        updateStatsUI()
        setupDailyGraph()
        setupWeeklyGraph()
    }

    private fun updateStatsUI() {
        val dailyCount = sharedPreferences.getInt("daily_pomodoro_count", 0)
        val weeklyCount = sharedPreferences.getInt("weekly_pomodoro_count", 0)
        val streak = sharedPreferences.getInt("daily_streak", 0)

        binding.dailyPomodoroCountTv.text = dailyCount.toString()
        binding.weeklyPomodoroCountTv.text = weeklyCount.toString()
        binding.streakCountTv.text = streak.toString()
    }

    @SuppressLint("NewApi")
    private fun setupDailyGraph() {
        val today = LocalDate.now()
        val history = loadHistory()

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            labels.add(date.dayOfWeek.name.take(3))
            val count = history[date] ?: 0
            entries.add(BarEntry((6 - i).toFloat(), count.toFloat()))
        }

        val dataSet = BarDataSet(entries, "Last 7 days").apply {
            color = ContextCompat.getColor(requireContext(), R.color.red_500)
            valueTextSize = 10f
            setDrawValues(true)
        }
        binding.dailyChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                granularity = 1f
                setDrawGridLines(false)
                valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)
                textSize = 10f
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            }
            axisLeft.granularity = 1f
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            setTouchEnabled(false)
            invalidate()
        }
    }

    private fun setupWeeklyGraph() {
        val today = LocalDate.now()
        val history = loadHistory()

        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        for (weekIndex in 3 downTo 0) {
            val start = today.minusDays(((weekIndex + 1) * 7 - 1).toLong())
            val end = today.minusDays(weekIndex * 7L)

            val total = history.filterKeys { date ->
                !date.isBefore(start) && !date.isAfter(end)
            }.values.sum()

            labels.add(if (weekIndex == 0) "This week" else "Week -$weekIndex")
            entries.add(Entry((3 - weekIndex).toFloat(), total.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Weekly total").apply {
            color = ContextCompat.getColor(requireContext(), R.color.red_700)
            circleRadius = 4f
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.red_500))
            lineWidth = 2f
            valueTextSize = 10f
        }

        binding.weeklyChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                granularity = 1f
                valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)
                textSize = 10f
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
            }
            axisLeft.axisMinimum = 0f
            axisLeft.granularity = 1f
            axisRight.isEnabled = false
            setTouchEnabled(false)
            invalidate()
        }
    }

    @SuppressLint("NewApi")
    private fun loadHistory(): Map<LocalDate, Int> {
        val mapJson = sharedPreferences.getString("session_history_map", "{}")
        val gson = Gson()
        val rawMap: Map<String, Int> = gson.fromJson(
            mapJson,
            object : TypeToken<Map<String, Int>>() {}.type
        )
        val parsed = mutableMapOf<LocalDate, Int>()
        rawMap.forEach { (key, value) ->
            runCatching { LocalDate.parse(key) }.getOrNull()?.let { parsed[it] = value }
        }
        return parsed
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateStatsUI() // Refresh stats
            setupDailyGraph() // Refresh daily graph
            setupWeeklyGraph() // Refresh weekly graph
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PomodoroService.BROADCAST_UPDATE)
        ContextCompat.registerReceiver(
            requireContext(),
            updateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(updateReceiver)
    }

    override fun onResume() {
        super.onResume()
        updateStreak()
        updateStatsUI() // Ensure the updated streak is displayed
    }

    @SuppressLint("NewApi")
    private fun updateStreak() {
        val currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val lastOpenedDate = sharedPreferences.getString("last_opened_date", null)
        var streak = sharedPreferences.getInt("daily_streak", 0)

        val editor = sharedPreferences.edit()

        if (lastOpenedDate == null || lastOpenedDate != currentDate) {
            if (lastOpenedDate != null && LocalDate.parse(lastOpenedDate).isBefore(LocalDate.now().minusDays(1))) {
                // Missed a day, reset streak to 1
                streak = 1
            } else {
                // Increment streak
                streak += 1
            }

            // Update SharedPreferences with the new streak and current date
            editor.putString("last_opened_date", currentDate)
            editor.putInt("daily_streak", streak)
            editor.apply()
        }
    }
}
