package com.mobdeve.s13.estanol.miguelfrancis.mp.ui.task

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.drawable.GradientDrawable
import com.mobdeve.s13.estanol.miguelfrancis.mp.R
import com.mobdeve.s13.estanol.miguelfrancis.mp.objects.Task
import java.text.SimpleDateFormat
import java.util.Locale

class TaskAdapter(
    private var tasks: List<Task>,
    private val onTaskCompleted: (Int) -> Unit,
    private val onTaskEdited: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.taskTitle)
        val dueDate: TextView = itemView.findViewById(R.id.taskDueDate)
        val typeIndicator: TextView = itemView.findViewById(R.id.taskTypeIndicator)
        val completeButton: Button = itemView.findViewById(R.id.completeTaskButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.title.text = task.title
        holder.dueDate.text = task.dueDate?.let { "Due ${formatDueDate(it)}" } ?: "No Due Date"

        val (color, text) = when (task.type) {
            "School" -> Pair(R.color.red_500, "School")
            "Work" -> Pair(R.color.red_700, "Work")
            else -> Pair(R.color.gray, "None")
        }
        holder.typeIndicator.text = text
        val background = holder.typeIndicator.background
        if (background is GradientDrawable) {
            val resolvedColor = holder.itemView.context.getColor(color)
            background.setColor(resolvedColor)
        }

        holder.completeButton.setOnClickListener { onTaskCompleted(task.id) }

        holder.itemView.setOnClickListener {
            onTaskEdited(task)
        }
    }

    private fun formatDueDate(raw: String): String {
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val formatter = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            val date = parser.parse(raw)
            if (date != null) formatter.format(date) else raw
        }.getOrDefault(raw)
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}
