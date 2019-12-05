package ${kotlinEscapedPackageName}

import ${getMaterialComponentName('android.support.v7.widget.RecyclerView', useAndroidX)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
<#if applicationPackage??>
import ${applicationPackage}.R
</#if>


import ${kotlinEscapedPackageName}.dummy.DummyContent.DummyItem

import kotlinx.android.synthetic.main.${fragment_layout}.view.*

/**
 * [RecyclerView.Adapter] that can display a [DummyItem].
 * TODO: Replace the implementation with code for your data type.
 */
class ${adapterClassName}(
        private val values: List<DummyItem>)
    : RecyclerView.Adapter<${adapterClassName}.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.${fragment_layout}, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.idView.text = item.id
        holder.contentView.text = item.content
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val idView: TextView = view.item_number
        val contentView: TextView = view.content

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }
    }
}
