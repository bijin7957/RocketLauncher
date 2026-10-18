package app.olauncher.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.databinding.ItemTimeZoneBinding
import app.olauncher.helper.TimeZoneChoice

class TimeZonePickerAdapter(
    private val choices: List<TimeZoneChoice>,
    private val onPick: (TimeZoneChoice) -> Unit,
) : RecyclerView.Adapter<TimeZonePickerAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTimeZoneBinding) : RecyclerView.ViewHolder(binding.root)

    private var shown = choices

    /** Keeps the zones whose city, region or offset contains [query]. */
    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        val text = query.trim()
        shown = if (text.isEmpty()) choices
        else choices.filter { it.city.contains(text, true) || it.detail.contains(text, true) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemTimeZoneBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = shown.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.binding) {
        val choice = shown[position]
        timeZoneCity.text = choice.city
        timeZoneDetail.text = choice.detail
        root.setOnClickListener { onPick(choice) }
    }
}
