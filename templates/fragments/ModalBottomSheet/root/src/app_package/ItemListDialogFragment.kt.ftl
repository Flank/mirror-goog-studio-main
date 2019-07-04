package ${escapeKotlinIdentifiers(packageName)}

import android.os.Bundle
import ${getMaterialComponentName('android.support.design.widget.BottomSheetDialog', useMaterial2)}Fragment
<#if columnCount == "1">
import ${getMaterialComponentName('android.support.v7.widget.LinearLayoutManager', useAndroidX)}
<#else>
import ${getMaterialComponentName('android.support.v7.widget.GridLayoutManager', useAndroidX)}
</#if>
import ${getMaterialComponentName('android.support.v7.widget.RecyclerView', useAndroidX)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
<#if applicationPackage??>
import ${applicationPackage}.R
</#if>
import kotlinx.android.synthetic.main.${listLayout}.*
import kotlinx.android.synthetic.main.${itemLayout}.view.*

// TODO: Customize parameter argument names
const val ARG_ITEM_COUNT = "item_count"

/**
 *
 * A fragment that shows a list of items as a modal bottom sheet.
 *
 * You can show this modal bottom sheet from your activity like this:
 * <pre>
 *    ${className}.newInstance(30).show(supportFragmentManager, "dialog")
 * </pre>
 */
class ${className} : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.${listLayout}, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
<#if columnCount == "1">
        list.layoutManager = LinearLayoutManager(context)
<#else>
        list.layoutManager = GridLayoutManager(context, ${columnCount})
</#if>
        list.adapter = arguments?.getInt(ARG_ITEM_COUNT)?.let { ItemAdapter(it) }
    }

    private inner class ViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup)
        : RecyclerView.ViewHolder(inflater.inflate(R.layout.${itemLayout}, parent, false)) {

        internal val text: TextView = itemView.text
    }

    private inner class ${objectKind}Adapter internal constructor(private val mItemCount: Int) : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context), parent)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.text.text = position.toString()
        }

        override fun getItemCount(): Int {
            return mItemCount
        }
    }

    companion object {

        // TODO: Customize parameters
        fun newInstance(itemCount: Int): ${className} =
                ${className}().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_ITEM_COUNT, itemCount)
                    }
                }

    }
}
