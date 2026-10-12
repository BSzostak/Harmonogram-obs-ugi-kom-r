package pl.koksownia.harmonogram;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pl.koksownia.harmonogram.databinding.ItemChamberBinding;

public class ChamberAdapter extends RecyclerView.Adapter<ChamberAdapter.Holder> {

    public interface OnRowClick {
        void onClick(int index);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemChamberBinding b;

        Holder(ItemChamberBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private final OnRowClick onClick;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("H:mm", Locale.getDefault());
    private Schedule schedule;
    private List<Long> planned = Collections.emptyList();
    private int nextIndex = -1;

    public ChamberAdapter(OnRowClick onClick) {
        this.onClick = onClick;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void submit(Schedule s) {
        schedule = s;
        planned = s.plannedMillis();
        nextIndex = s.nextIndex();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return schedule == null ? 0 : schedule.chambers.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemChamberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Chamber c = schedule.chambers.get(position);
        Context ctx = holder.b.getRoot().getContext();
        boolean isNext = position == nextIndex;
        Long plan = planned.get(position);
        boolean late = !c.isDone() && plan != null && plan < System.currentTimeMillis();

        holder.b.txtLp.setText(String.valueOf(position + 1));
        holder.b.txtChamber.setText(c.number);
        holder.b.txtPlan.setText(c.planTime);
        holder.b.txtDone.setText(c.doneAt != null ? "✓ " + timeFormat.format(new Date(c.doneAt)) : "—");

        int normal = ContextCompat.getColor(ctx, android.R.color.black);
        int grey = ContextCompat.getColor(ctx, R.color.done_text);
        int red = ContextCompat.getColor(ctx, R.color.late_text);
        int base = c.isDone() ? grey : normal;
        holder.b.txtLp.setTextColor(base);
        holder.b.txtChamber.setTextColor(base);
        holder.b.txtDone.setTextColor(base);
        holder.b.txtPlan.setTextColor(late ? red : base);

        int flags = holder.b.txtChamber.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG;
        holder.b.txtChamber.setPaintFlags(c.isDone() ? flags | Paint.STRIKE_THRU_TEXT_FLAG : flags);
        holder.b.txtPlan.setTypeface(null, isNext ? Typeface.BOLD : Typeface.NORMAL);

        holder.b.getRoot().setBackgroundColor(isNext ? ContextCompat.getColor(ctx, R.color.next_bg) : 0);
        holder.b.getRoot().setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) onClick.onClick(pos);
        });
    }
}
