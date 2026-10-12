package pl.koksownia.harmonogram

import android.graphics.Paint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import pl.koksownia.harmonogram.databinding.ItemChamberBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChamberAdapter(
    private val onClick: (index: Int) -> Unit,
) : RecyclerView.Adapter<ChamberAdapter.Holder>() {

    private var schedule: Schedule? = null
    private var planned: List<Long?> = emptyList()
    private val timeFormat = SimpleDateFormat("H:mm", Locale.getDefault())

    class Holder(val b: ItemChamberBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(s: Schedule) {
        schedule = s
        planned = s.chambers.indices.map { s.plannedMillis(it) }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = schedule?.chambers?.size ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemChamberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val s = schedule ?: return
        val c = s.chambers[position]
        val ctx = holder.b.root.context
        val isNext = s.nextIndex == position
        val late = !c.done && (planned.getOrNull(position) ?: Long.MAX_VALUE) < System.currentTimeMillis()

        holder.b.txtLp.text = (position + 1).toString()
        holder.b.txtChamber.text = c.number
        holder.b.txtPlan.text = c.planTime
        holder.b.txtDone.text = c.doneAt?.let { "✓ " + timeFormat.format(Date(it)) } ?: "—"

        val normal = ContextCompat.getColor(ctx, android.R.color.black)
        val grey = ContextCompat.getColor(ctx, R.color.done_text)
        val red = ContextCompat.getColor(ctx, R.color.late_text)
        holder.b.txtLp.setTextColor(if (c.done) grey else normal)
        holder.b.txtChamber.setTextColor(if (c.done) grey else normal)
        holder.b.txtPlan.setTextColor(if (c.done) grey else if (late) red else normal)
        holder.b.txtDone.setTextColor(if (c.done) grey else normal)

        val strike = if (c.done) Paint.STRIKE_THRU_TEXT_FLAG else 0
        holder.b.txtChamber.paintFlags = (holder.b.txtChamber.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()) or strike
        holder.b.txtPlan.setTypeface(null, if (isNext) Typeface.BOLD else Typeface.NORMAL)

        holder.b.root.setBackgroundColor(
            if (isNext) ContextCompat.getColor(ctx, R.color.next_bg) else 0
        )
        holder.b.root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }
}
