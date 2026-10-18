package app.olauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.databinding.ItemWidgetChoiceBinding
import app.olauncher.helper.WidgetChoice
import app.olauncher.helper.widgetSizeLabel

/** Widget list for the weather widget picker, with each widget's name, size and preview. */
class WidgetPickerAdapter(
    private val choices: List<WidgetChoice>,
    private val onPick: (WidgetChoice) -> Unit,
) : RecyclerView.Adapter<WidgetPickerAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemWidgetChoiceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemWidgetChoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = choices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.binding) {
        val choice = choices[position]
        val context = root.context
        widgetLabel.text = choice.label
        widgetSize.text = context.widgetSizeLabel(choice.info)
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            runCatching { choice.info.loadDescription(context) }.getOrNull()
        else null
        widgetDescription.text = description
        widgetDescription.isVisible = !description.isNullOrBlank()
        bindPreview(this, choice.info)
        root.setOnClickListener { onPick(choice) }
    }

    // Previews are built only for rows on screen; many apps ship large ones
    private fun bindPreview(binding: ItemWidgetChoiceBinding, info: AppWidgetProviderInfo) = with(binding) {
        val context = root.context
        widgetPreview.removeAllViews()
        val image = runCatching { info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi) }.getOrNull()
        val preview: View? = when {
            image != null -> ImageView(context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_START
                setImageDrawable(image)
            }
            // Newer widgets ship a layout instead of an image, drawn the way a home screen does
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.previewLayout != 0 -> runCatching {
                // The app context: AppCompat's inflater would swap in views RemoteViews can't drive
                RemoteViews(info.provider.packageName, info.previewLayout).apply(context.applicationContext, widgetPreview)
            }.getOrNull()

            else -> null
        }
        if (preview != null) widgetPreview.addView(preview)
        widgetPreview.isVisible = preview != null
    }
}
