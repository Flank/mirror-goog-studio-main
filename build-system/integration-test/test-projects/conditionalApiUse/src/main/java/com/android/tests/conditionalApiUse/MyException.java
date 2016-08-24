package com.android.tests.conditionalApiUse;

import android.hardware.camera2.CameraAccessException;
import android.os.Build;
import android.support.annotation.RequiresApi;

@RequiresApi(21)
public class MyException extends CameraAccessException {

    public MyException(int problem, String message, Throwable cause) {
        super(problem, message, cause);
    }

    public String toString() {
        return "Null string";
    }
}