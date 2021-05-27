/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun contentListFragmentJava(
  collectionName: String,
  detailName: String,
  applicationPackage: String?,
  detailNameLayout: String,
  itemListContentLayout: String,
  itemListLayout: String,
  objectKindPlural: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {
  val layoutName = "fragment_${itemListLayout}"
  val onCreateViewBlock = if (isViewBindingSupported) """
      binding = ${layoutToViewBindingClass(layoutName)}.inflate(inflater, container, false);
      return binding.getRoot();
  """ else "return inflater.inflate(R.layout.$layoutName, container, false);"

  val onCreateViewHolderBlock = if (isViewBindingSupported) """
    ${layoutToViewBindingClass(itemListContentLayout)} binding =
      ${layoutToViewBindingClass(itemListContentLayout)}.inflate(LayoutInflater.from(parent.getContext()), parent, false);
    return new ViewHolder(binding);
  """ else """
    View view = LayoutInflater.from(parent.getContext())
      .inflate(R.layout.${itemListContentLayout}, parent, false);
    return new ViewHolder(view);
  """

  val viewHolderBlock = if (isViewBindingSupported) """
    ViewHolder(${layoutToViewBindingClass(itemListContentLayout)} binding) {
      super(binding.getRoot());
      mIdView = binding.idText;
      mContentView = binding.content;
    }
  """ else """
    ViewHolder(View view) {
      super(view);
      mIdView = view.findViewById(R.id.id_text);
      mContentView = view.findViewById(R.id.content);
    }
  """

  return """
package ${packageName};

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Build;
import android.os.Bundle;

import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import ${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)};

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R;" }}
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}
${importViewBindingClass(isViewBindingSupported, packageName, itemListContentLayout, Language.Java)}

import ${packageName}.placeholder.PlaceholderContent;

import java.util.List;

/**
 * A fragment representing a list of ${objectKindPlural}. This fragment
 * has different presentations for handset and larger screen devices. On
 * handsets, the fragment presents a list of items, which when touched,
 * lead to a {@link ${detailName}Fragment} representing
 * item details. On larger screens, the Navigation controller presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class ${collectionName}Fragment extends Fragment {

    /**
     * Method to intercept global key events in the
     * item list fragment to trigger keyboard shortcuts
     * Currently provides a toast when Ctrl + Z and Ctrl + F
     * are triggered
     */
    ViewCompat.OnUnhandledKeyEventListenerCompat unhandledKeyEventListenerCompat = (v, event) -> {
        if (event.getKeyCode() == KeyEvent.KEYCODE_Z && event.isCtrlPressed()) {
            Toast.makeText(
                    v.getContext(),
                    "Undo (Ctrl + Z) shortcut triggered",
                    Toast.LENGTH_LONG
            ).show();
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_F && event.isCtrlPressed()) {
            Toast.makeText(
                    v.getContext(),
                    "Find (Ctrl + F) shortcut triggered",
                    Toast.LENGTH_LONG
            ).show();
            return true;
        }
        return false;
    };

${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} binding;
"""}}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        $onCreateViewBlock
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.addOnUnhandledKeyEventListener(view, unhandledKeyEventListenerCompat);

        RecyclerView recyclerView = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = itemListLayout,
          parentView = "view")};

        // Leaving this not using view binding as it relies on if the view is visible the current
        // layout configuration (layout, layout-sw600dp)
        View itemDetailFragmentContainer = view.findViewById(R.id.${detailNameLayout}_nav_container);

        setupRecyclerView(recyclerView, itemDetailFragmentContainer);
    }

    private void setupRecyclerView(
            RecyclerView recyclerView,
            View itemDetailFragmentContainer
    ) {

        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(
                PlaceholderContent.ITEMS,
                itemDetailFragmentContainer
        ));
    }

    public static class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final List<PlaceholderContent.PlaceholderItem> mValues;
        private final View mItemDetailFragmentContainer;

        SimpleItemRecyclerViewAdapter(List<PlaceholderContent.PlaceholderItem> items,
                                      View itemDetailFragmentContainer) {
            mValues = items;
            mItemDetailFragmentContainer = itemDetailFragmentContainer;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            $onCreateViewHolderBlock
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mIdView.setText(mValues.get(position).id);
            holder.mContentView.setText(mValues.get(position).content);

            holder.itemView.setTag(mValues.get(position));
            holder.itemView.setOnClickListener(itemView -> {
                PlaceholderContent.PlaceholderItem item =
                        (PlaceholderContent.PlaceholderItem) itemView.getTag();
                Bundle arguments = new Bundle();
                arguments.putString(${detailName}Fragment.ARG_ITEM_ID, item.id);
                if (mItemDetailFragmentContainer != null) {
                    Navigation.findNavController(mItemDetailFragmentContainer)
                        .navigate(R.id.fragment_${detailNameLayout}, arguments);
                } else {
                    Navigation.findNavController(itemView).navigate(R.id.show_${detailNameLayout}, arguments);
                }
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                /*
                 * Context click listener to handle Right click events
                 * from mice and trackpad input to provide a more native
                 * experience on larger screen devices
                 */
                holder.itemView.setOnContextClickListener(v -> {
                    PlaceholderContent.PlaceholderItem item =
                            (PlaceholderContent.PlaceholderItem) holder.itemView.getTag();
                    Toast.makeText(
                            holder.itemView.getContext(),
                            "Context click of item " + item.id,
                            Toast.LENGTH_LONG
                    ).show();
                    return true;
                });
            }
            holder.itemView.setOnLongClickListener(v -> {
                // Setting the item id as the clip data so that the drop target is able to
                // identify the id of the content
                ClipData.Item clipItem = new ClipData.Item(mValues.get(position).id);
                ClipData dragData = new ClipData(
                        ((PlaceholderContent.PlaceholderItem) v.getTag()).content,
                        new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN},
                        clipItem
                );

                if (Build.VERSION.SDK_INT >= 24) {
                    v.startDragAndDrop(
                            dragData,
                            new View.DragShadowBuilder(v),
                            null,
                            0
                    );
                } else {
                    v.startDrag(
                            dragData,
                            new View.DragShadowBuilder(v),
                            null,
                            0
                    );
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView mIdView;
            final TextView mContentView;

            $viewHolderBlock
        }
    }

${renderIf(isViewBindingSupported) {"""
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
"""}}
}

"""
}
