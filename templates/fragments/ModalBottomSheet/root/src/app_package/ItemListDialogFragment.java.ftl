package ${packageName};

import android.os.Bundle;
import ${getMaterialComponentName('android.support.annotation.Nullable', useAndroidX)};
import ${getMaterialComponentName('android.support.annotation.NonNull', useAndroidX)};
import ${getMaterialComponentName('android.support.design.widget.BottomSheetDialog', useMaterial2)}Fragment;
<#if columnCount == "1">
import ${getMaterialComponentName('android.support.v7.widget.LinearLayoutManager', useAndroidX)};
<#else>
import ${getMaterialComponentName('android.support.v7.widget.GridLayoutManager', useAndroidX)};
</#if>
import ${getMaterialComponentName('android.support.v7.widget.RecyclerView', useAndroidX)};
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
<#if applicationPackage??>
import ${applicationPackage}.R;
</#if>

/**
 * <p>A fragment that shows a list of items as a modal bottom sheet.</p>
 * <p>You can show this modal bottom sheet from your activity like this:</p>
 * <pre>
 *     ${fragmentClass}.newInstance(30).show(getSupportFragmentManager(), "dialog");
 * </pre>
 */
public class ${fragmentClass} extends BottomSheetDialogFragment {

    // TODO: Customize parameter argument names
    private static final String ARG_ITEM_COUNT = "item_count";

    // TODO: Customize parameters
    public static ${fragmentClass} newInstance(int itemCount) {
        final ${fragmentClass} fragment = new ${fragmentClass}();
        final Bundle args = new Bundle();
        args.putInt(ARG_ITEM_COUNT, itemCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.${listLayout}, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        final RecyclerView recyclerView = (RecyclerView) view;
<#if columnCount == "1">
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
<#else>
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), ${columnCount}));
</#if>
        recyclerView.setAdapter(new ${objectKind}Adapter(getArguments().getInt(ARG_ITEM_COUNT)));
    }

    private class ViewHolder extends RecyclerView.ViewHolder {

        final TextView text;

        ViewHolder(LayoutInflater inflater, ViewGroup parent) {
            // TODO: Customize the item layout
            super(inflater.inflate(R.layout.${itemLayout}, parent, false));
            text = itemView.findViewById(R.id.text);
        }
    }

    private class ${objectKind}Adapter extends RecyclerView.Adapter<ViewHolder> {

        private final int mItemCount;

        ${objectKind}Adapter(int itemCount) {
            mItemCount = itemCount;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()), parent);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.text.setText(String.valueOf(position));
        }

        @Override
        public int getItemCount() {
            return mItemCount;
        }

    }

}
