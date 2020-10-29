package com.android.test.lint.libmodel.mylibrary;

import java.time.format.DateTimeFormatter;
import java.util.*;

@SuppressWarnings("NewApi")
public class MyLibrary {
     private DateTimeFormatter PROFILE_FILE_NAME =
         DateTimeFormatter.ofPattern("'profile-'YYYY-MM-dd-HH-mm-ss-SSS'.rawproto'", Locale.US); // ERROR
}
