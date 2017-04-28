package android.com.java.profilertester.network;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class NetworkAsyncTask extends AsyncTask<Integer, Void , Void> {
    static private final String URL_STRING = "https://dl.google.com/dl/android/studio/ide-zips/2.4.0.3/android-studio-ide-171.3870562-windows.zip";
    static private final int HTTP_TASK_NUMBER = 0;
    static private final int OK_HTTP_TASK_NUMBER = 1;
    static private final int FILE_SIZE_BASE = (1 << 18);
    static private final int ITERATION_NUMBER = 5;
    static private final int PERIOD_TIME = 3;

    private void testHttpUrlConnectionDownload() throws IOException, InterruptedException {
        int fileSize = FILE_SIZE_BASE;
        for (int k = 0; k < ITERATION_NUMBER; ++k, fileSize *= 2) {
            final URL url = new URL(URL_STRING);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Range", "bytes=0-" + fileSize);
            InputStream in = urlConnection.getInputStream();
            while (in.read() != -1);
            if (in != null) {
                in.close();
            }
            TimeUnit.SECONDS.sleep(PERIOD_TIME);
        }
    }

    private void testOkHttpDownload() throws IOException, InterruptedException {
        int fileSize = FILE_SIZE_BASE;
        for (int k = 0; k < ITERATION_NUMBER; ++k, fileSize *= 2) {
            OkHttpClient client = new OkHttpClient();
            Response response = client.newCall(new Request.Builder().url(URL_STRING)
                    .addHeader("Range", "bytes=0-" + fileSize).build()).execute();
            InputStream in = response.body().byteStream();
            while (in.read() != -1);
            if (in != null) {
                in.close();
            }
            TimeUnit.SECONDS.sleep(PERIOD_TIME);
        }
    }

    @Override
    protected Void doInBackground(Integer... integers) {
        int actionNumber = integers[0];
        try {
            if (actionNumber == HTTP_TASK_NUMBER) {
                testHttpUrlConnectionDownload();
            }
            if (actionNumber == OK_HTTP_TASK_NUMBER){
                testOkHttpDownload();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
