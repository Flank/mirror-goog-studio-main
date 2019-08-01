package ${packageName}.ui.${navFragmentPrefix}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)}
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs

import ${packageName}.R

class ${secondFragmentClass} : Fragment() {

    private val args: ${secondFragmentClass}Args by navArgs()

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.${secondFragmentLayoutName}, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.textview_${navFragmentPrefix}_second).text =
            getString(R.string.hello_${navFragmentPrefix}_second, args.myArg)

        view.findViewById<Button>(R.id.button_${navFragmentPrefix}_second).setOnClickListener {
            findNavController().navigate(R.id.action_${secondFragmentClass}_to_${firstFragmentClass})
        }
    }
}
