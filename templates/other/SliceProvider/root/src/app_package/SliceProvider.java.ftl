package ${packageName};

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import androidx.slice.Slice;
import androidx.slice.SliceProvider;
import androidx.slice.builders.ListBuilder;
import androidx.slice.builders.ListBuilder.RowBuilder;


public class ${className} extends SliceProvider {
    /**
     * Instantiate any required objects. Return true if the provider was successfully created,
     * false otherwise.
     */
    @Override
    public boolean onCreateSliceProvider() {
        return true;
    }

    /**
     * Converts URL to content URI (i.e. content://${packageName}...)
     */
    @Override
    @NonNull
    public Uri onMapIntentToUri(@Nullable Intent intent) {
        // Note: implementing this is only required if you plan on catching URL requests.
        // This is an example solution.
        Uri.Builder uriBuilder = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT);
        if (intent == null) return uriBuilder.build();
        Uri data = intent.getData();
        if (data != null && data.getPath() != null) {
            String path = data.getPath().replace("${pathPrefix}", "");
            uriBuilder = uriBuilder.path(path);
        }
        Context context = getContext();
        if (context != null) {
            uriBuilder = uriBuilder.authority(context.getPackageName());
        }
        return uriBuilder.build();
    }

    /**
     * Construct the Slice and bind data if available.
     */
    public Slice onBindSlice(Uri sliceUri) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        if (sliceUri.getPath().equals("${pathPrefix}")) {
            // Path recognized. Customize the Slice using the androidx.slice.builders API.
            // Note: ANRs and strict mode is enforced here so don't do any heavy operations.
            // Only bind data that is currently available in memory.
            return new ListBuilder(getContext(), sliceUri)
                    .addRow(new RowBuilder(context, sliceUri).setTitle("URI found."))
                    .build();
        } else {
            // Error: Path not found.
            return new ListBuilder(context, sliceUri)
                    .addRow(new RowBuilder(context, sliceUri).setTitle("URI not found."))
                    .build();
        }
    }

    /**
     * Slice has been pinned to external process. Subscribe to data source if necessary.
     */
    @Override
    public void onSlicePinned(Uri sliceUri) {
     // When data is received, call context.contentResolver.notifyChange(sliceUri, null) to
     // trigger ${className}#onBindSlice(Uri) again.
    }

    /**
     * Unsubscribe from data source if necessary.
     */
    @Override
    public void onSliceUnpinned(Uri sliceUri) {
        // Remove any observers if necessary to avoid memory leaks.
    }
}
