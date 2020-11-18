package workflow.tutorial.views

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import workflow.tutorial.views.TodoListAdapter.TodoViewHolder

/**
 * [RecyclerView.Adapter] for a list of TODO items.
 */
class TodoListAdapter : RecyclerView.Adapter<TodoViewHolder>() {

  class TodoViewHolder(
    container: View,
    val textView: TextView
  ) : ViewHolder(container)

  var todoList: List<String> = emptyList()
  var onTodoSelected: (Int) -> Unit = {}

  override fun getItemCount(): Int = todoList.size

  override fun onCreateViewHolder(
    parent: ViewGroup,
    position: Int
  ): TodoViewHolder {
    val view = LayoutInflater.from(parent.context)
        .inflate(R.layout.todo_list_item_view, parent, false)
    val textView = view.findViewById<TextView>(R.id.todo_list_item)
    return TodoViewHolder(view, textView)
  }

  override fun onBindViewHolder(
    viewHolder: TodoViewHolder,
    position: Int
  ) {
    viewHolder.textView.text = todoList[position]
    viewHolder.itemView.setOnClickListener {
      onTodoSelected(position)
    }
  }
}
