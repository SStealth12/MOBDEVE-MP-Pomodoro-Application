package com.mobdeve.s13.estanol.miguelfrancis.mp.ui.account

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.mobdeve.s13.estanol.miguelfrancis.mp.R

class AccountFragment : Fragment() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)

        val rootView = inflater.inflate(R.layout.fragment_account, container, false)
        setupSpinners(rootView)
        return rootView
    }

    private fun setupSpinners(rootView: View) {
        val workDurationSpinner = rootView.findViewById<Spinner>(R.id.pomodoroLengthDropdown)
        val breakDurationSpinner = rootView.findViewById<Spinner>(R.id.shortBreakLengthDropdown)
        val longBreakSpinner = rootView.findViewById<Spinner>(R.id.longBreakDropdown)
        val workAlarmSpinner = rootView.findViewById<Spinner>(R.id.pomodoroAlarmDropdown)
        val breakAlarmSpinner = rootView.findViewById<Spinner>(R.id.breakAlarmDropdown)

        workDurationSpinner.setSelection(sharedPreferences.getInt("work_duration_index", 5))
        breakDurationSpinner.setSelection(sharedPreferences.getInt("break_duration_index", 1))
        longBreakSpinner.setSelection(sharedPreferences.getInt("long_break_duration_index", 3))
        workAlarmSpinner.setSelection(sharedPreferences.getInt("work_alarm", 0))
        breakAlarmSpinner.setSelection(sharedPreferences.getInt("break_alarm", 0))

        workDurationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sharedPreferences.edit().putInt("work_duration_index", position).putInt("work_duration", positionToMinutes(position)).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        breakDurationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sharedPreferences.edit().putInt("break_duration_index", position).putInt("break_duration", positionToMinutes(position)).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        longBreakSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sharedPreferences.edit().putInt("long_break_duration_index", position).putInt("long_break_duration", positionToMinutes(position)).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        workAlarmSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sharedPreferences.edit().putInt("work_alarm", position).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        breakAlarmSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sharedPreferences.edit().putInt("break_alarm", position).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun positionToMinutes(position: Int): Int {
        val options = arrayOf(1, 5, 10, 15, 20, 25, 30) // Matches spinner entries
        return options.getOrElse(position) { 25 } // Default to 25 minutes
    }
}
