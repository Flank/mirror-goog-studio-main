package androidx.compose.runtime.internal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface LiveLiteralInfo {
    String key();

    int offset();
}
