# Contributing to Android Developer Tools
For general information about contributing to the Android Open Source Project,
read the following documentation:
* [Life Of a Patch](http://source.android.com/source/life-of-a-patch.html)
* [Submitting Patches](http://source.android.com/source/submit-patches.html)
* [AOSP Java Code Style for Contributors](http://source.android.com/source/code-style.html)

The branch strategy for Developer Tools projects is different from
[the platform strategy](http://source.android.com/source/code-lines.html).
For example, Developer Tools doesn’t have the typical platform release branches
(such as, cupcake, donut, eclair, froyo, etc.).
Instead, the project uses branches matching its release cycles.

If you are interested in contributing to the project as an external contributor,
you need to follow these steps:
1. Make sure you’re able to [download](source.md) and [build the Android Studio from source](studio.md).
   Use the `studio-master-dev` branch.
2. Email adt-dev@google.com or post a message on
   [the adt-dev Google group](http://groups.google.com/group/adt-dev) to make sure that:
   * You receive early feedback from the team whether the change is likely to be merged
   * Other team member or external contributor are not already working on the bug or feature
3. Create a repo, commit your changes, and upload your change to
   [gerrit](https://android-review.googlesource.com/).
   To learn more, read [Submitting Patches](http://source.android.com/source/submit-patches.html).
   When writing your commit message, follow these guidelines:
   * The first line must be a short summary of the feature or fix
     (keep it to 60 characters or less).
     For example, "Fix for bug 2134" is _not_ a valid summary but something like "Add lint check for
     insecure WebView ssl handling" is.
   * The summary line must be followed by a blank line.
   * The rest of the commit message should provide an full overview of how the patch works, and
     explain how new classes interact together or are used by the underlying platform.
   * Make sure the entire message is hard wrapped at 76 characters.
   * If the contribution includes UI change, include link to a screenshot of the new UI and
     highlight the changes (required).

After you upload your change, the internal Developer Tools team will review it.
If they accept your change, they will merge it into `studio-master-dev` for you.
Your change should then appear in the first or second external release of Android Studio after the
merge.

If you are looking for ideas on what to contribute, please look at our
[official bug tracker](https://issuetracker.google.com/issues?q=componentid:192708%20status:open).
