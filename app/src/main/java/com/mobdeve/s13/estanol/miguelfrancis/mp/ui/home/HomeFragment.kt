package com.mobdeve.s13.estanol.miguelfrancis.mp.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.mobdeve.s13.estanol.miguelfrancis.mp.PomodoroService
import com.mobdeve.s13.estanol.miguelfrancis.mp.R
import com.mobdeve.s13.estanol.miguelfrancis.mp.databinding.FragmentHomeBinding
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val longBreakInterval = 4
    private var currentPhaseDuration: Long = 0L

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val timeLeft = intent?.getLongExtra(PomodoroService.EXTRA_TIME_LEFT, 0L) ?: 0L
            val isRunning = intent?.getBooleanExtra(PomodoroService.EXTRA_IS_RUNNING, false) ?: false
            val phaseName = intent?.getStringExtra(PomodoroService.EXTRA_PHASE) ?: "WORK"
            val totalDuration = intent?.getLongExtra(PomodoroService.EXTRA_TOTAL_DURATION, 0L) ?: 0L
            val completedCount = intent?.getIntExtra(PomodoroService.EXTRA_TOTAL_COMPLETED, 0) ?: 0
            val sessionCount = intent?.getIntExtra(PomodoroService.EXTRA_SESSION_COUNT, 0) ?: 0
            updateUI(timeLeft, isRunning, phaseName, totalDuration, completedCount, sessionCount)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.btnStart.setOnClickListener { startTimer() }
        binding.btnPause.setOnClickListener { pauseTimer() }
        binding.btnStop.setOnClickListener { stopTimer() }
        binding.btnReset.setOnClickListener { resetTimer() }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(PomodoroService.BROADCAST_UPDATE)
        ContextCompat.registerReceiver(
            requireContext(),
            timerReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        requestStatus()
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(timerReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was not registered; ignore
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun requestStatus() {
        val intent = Intent(requireContext(), PomodoroService::class.java).apply {
            action = PomodoroService.ACTION_STATUS
        }
        requireContext().startService(intent)
    }

    private fun startTimer() {
        val intent = Intent(requireContext(), PomodoroService::class.java)
            .apply { action = PomodoroService.ACTION_START }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun pauseTimer() {
        val intent = Intent(requireContext(), PomodoroService::class.java)
            .apply { action = PomodoroService.ACTION_PAUSE }
        requireContext().startService(intent)
    }

    private fun stopTimer() {
        val intent = Intent(requireContext(), PomodoroService::class.java)
            .apply { action = PomodoroService.ACTION_STOP }
        requireContext().startService(intent)
    }

    private fun resetTimer() {
        val intent = Intent(requireContext(), PomodoroService::class.java)
            .apply { action = PomodoroService.ACTION_RESET }
        requireContext().startService(intent)
    }

    private fun updateUI(
        timeLeft: Long,
        isRunning: Boolean,
        phaseName: String,
        totalDuration: Long,
        totalCompleted: Int,
        sessionCount: Int
    ) {
        val duration = if (totalDuration > 0) totalDuration else durationForPhase(phaseName)
        currentPhaseDuration = duration
        val minutes = (timeLeft / 1000) / 60
        val seconds = (timeLeft / 1000) % 60
        binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)
        binding.sessionCounter.text = getString(R.string.completed_sessions_label, totalCompleted)

        val phaseLabel = when (phaseName) {
            "LONG_BREAK" -> getString(R.string.long_break_label)
            "SHORT_BREAK" -> getString(R.string.short_break_label)
            else -> getString(R.string.work_phase_label)
        }
        binding.phaseLabel.text = phaseLabel

        val cycleText = if (phaseName == "LONG_BREAK") {
            getString(R.string.in_long_break_label)
        } else {
            getString(R.string.long_break_progress_label, sessionCount, longBreakInterval)
        }
        binding.cycleCounter.text = cycleText

        val progressPercent = if (duration > 0) {
            ((timeLeft.toDouble() / duration.toDouble()) * 100).roundToInt().coerceIn(0, 100)
        } else 0
        binding.progressCircular.progress = progressPercent

        binding.btnStart.isEnabled = !isRunning
        binding.btnPause.isEnabled = isRunning
        binding.btnReset.isEnabled = !isRunning
        binding.btnStop.isEnabled = isRunning || timeLeft < duration
    }

    private fun durationForPhase(phaseName: String): Long {
        val prefs = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        return when (phaseName) {
            "LONG_BREAK" -> prefs.getInt("long_break_duration", 15) * 60 * 1000L
            "SHORT_BREAK" -> prefs.getInt("break_duration", 5) * 60 * 1000L
            else -> prefs.getInt("work_duration", 25) * 60 * 1000L
        }
    }
}
