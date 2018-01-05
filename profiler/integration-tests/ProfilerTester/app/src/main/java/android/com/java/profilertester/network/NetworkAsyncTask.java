package android.com.java.profilertester.network;

import android.os.AsyncTask;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NetworkAsyncTask extends AsyncTask<Integer, Void, Void> {
    private static final String URL_STRING =
            "https://dl.google.com/dl/android/studio/ide-zips/2.4.0.3/android-studio-ide-171.3870562-windows.zip";
    private static final String FORM_DATA =
            "function=validate&args[urlEntryUser]=https://www.test.com&args[audioBitrate]=0"
                    + "&args[channel]=stereo&args[advSettings]=false&args[aspectRatio]=-1";
    private static final String LONG_VALUE =
            "loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooogField";

    // GET requests.
    private static final int HTTP_TASK_NUMBER = 0;
    private static final int OK_HTTP_TASK_NUMBER = 1;
    private static final int OK_HTTP2_TASK_NUMBER = 2;
    // POST requests.
    private static final int HTTP_POST_JSON_TASK_NUMBER = 3;
    private static final int OK_HTTP_POST_JSON_TASK_NUMBER = 4;
    private static final int HTTP_POST_FORM_DATA_TASK_NUMBER = 5;
    private static final int OK_HTTP_POST_FORM_DATA_TASK_NUMBER = 6;

    private static final int FILE_SIZE_BASE = (1 << 18);
    private static final int ITERATION_NUMBER = 5;
    private static final int PERIOD_TIME = 3;

    private static void testHttpUrlConnectionDownload() throws IOException, InterruptedException {
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

    private static void testOkHttpDownload() throws IOException, InterruptedException {
        int fileSize = FILE_SIZE_BASE;
        for (int k = 0; k < ITERATION_NUMBER; ++k, fileSize *= 2) {
            okhttp3.Request request =
                    new okhttp3.Request.Builder()
                            .url(URL_STRING)
                            .addHeader("Range", "bytes=0-" + fileSize)
                            .build();
            okhttp3.Response response = new okhttp3.OkHttpClient().newCall(request).execute();
            InputStream in = response.body().byteStream();
            while (in.read() != -1);
            if (in != null) {
                in.close();
            }
            TimeUnit.SECONDS.sleep(PERIOD_TIME);
        }
    }

    private static void testOkHttp2Download() throws IOException, InterruptedException {
        int fileSize = FILE_SIZE_BASE;
        for (int k = 0; k < ITERATION_NUMBER; ++k, fileSize *= 2) {
            Request request =
                    new Request.Builder()
                            .url(URL_STRING)
                            .addHeader("Range", "bytes=0-" + fileSize)
                            .build();
            Response response = new OkHttpClient().newCall(request).execute();
            InputStream in = response.body().byteStream();
            while (in.read() != -1) ;
            if (in != null) {
                in.close();
            }
            TimeUnit.SECONDS.sleep(PERIOD_TIME);
        }
    }

    private static void testHttpPostJson() throws IOException, InterruptedException {
        ServerTest postJsonTest =
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws IOException {
                        URL url = new URL(getUrl(server.getPort()));
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/json;");
                        connection.setRequestProperty("field", LONG_VALUE);
                        OutputStream out = connection.getOutputStream();
                        out.write(createJSONString().getBytes());
                        out.close();
                        InputStream in = connection.getInputStream();
                        while (in.read() != -1) ;
                        if (in != null) {
                            in.close();
                        }
                    }
                };
        for (int k = 0; k < ITERATION_NUMBER; ++k) {
            runWithServer(postJsonTest);
            TimeUnit.SECONDS.sleep(PERIOD_TIME);
        }
    }

    private static void testOkHttpPostJson() throws IOException, InterruptedException {
        ServerTest postJsonTest =
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws IOException {
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        okhttp3.RequestBody requestBody =
                                okhttp3.RequestBody.create(
                                        okhttp3.MediaType.parse("application/json"),
                                        createJSONString());
                        okhttp3.Request request =
                                new okhttp3.Request.Builder()
                                        .url(getUrl(server.getPort()))
                                        .addHeader("field", LONG_VALUE)
                                        .post(requestBody)
                                        .build();
                        InputStream in = client.newCall(request).execute().body().byteStream();
                        while (in.read() != -1) ;
                        if (in != null) {
                            in.close();
                        }
                    }
                };
        for (int k = 0; k < ITERATION_NUMBER; ++k) {
            runWithServer(postJsonTest);
            TimeUnit.SECONDS.sleep(PERIOD_TIME);
        }
    }

    private static void testHttpPostFormData() throws IOException, InterruptedException {
        ServerTest postFormDataTest =
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws IOException {
                        URL url = new URL(getUrl(server.getPort()));
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setDoOutput(true);
                        connection.setRequestProperty(
                                "Content-Type", "application/x-www-form-urlencoded;");
                        connection.setRequestProperty("field", LONG_VALUE);
                        OutputStream out = connection.getOutputStream();
                        out.write(FORM_DATA.getBytes());
                        out.close();
                        InputStream in = connection.getInputStream();
                        while (in.read() != -1) ;
                        if (in != null) {
                            in.close();
                        }
                    }
                };
        for (int k = 0; k < ITERATION_NUMBER; ++k) {
            runWithServer(postFormDataTest);
            TimeUnit.SECONDS.sleep(PERIOD_TIME);
        }
    }

    private static void testOkHttpPostFormData() throws IOException, InterruptedException {
        ServerTest postFormDataTest =
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws IOException {
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        okhttp3.RequestBody requestBody =
                                okhttp3.RequestBody.create(
                                        okhttp3.MediaType.parse(
                                                "application/x-www-form-urlencoded;"),
                                        FORM_DATA);
                        okhttp3.Request request =
                                new okhttp3.Request.Builder()
                                        .url(getUrl(server.getPort()))
                                        .addHeader("field", LONG_VALUE)
                                        .post(requestBody)
                                        .build();
                        InputStream in = client.newCall(request).execute().body().byteStream();
                        while (in.read() != -1) ;
                        if (in != null) {
                            in.close();
                        }
                    }
                };
        for (int k = 0; k < ITERATION_NUMBER; ++k) {
            runWithServer(postFormDataTest);
            TimeUnit.SECONDS.sleep(PERIOD_TIME);
        }
    }

    @Override
    protected Void doInBackground(Integer... integers) {
        int actionNumber = integers[0];
        try {
            switch (actionNumber) {
                case HTTP_TASK_NUMBER:
                    testHttpUrlConnectionDownload();
                    break;
                case OK_HTTP_TASK_NUMBER:
                    testOkHttpDownload();
                    break;
                case OK_HTTP2_TASK_NUMBER:
                    testOkHttp2Download();
                    break;
                case HTTP_POST_JSON_TASK_NUMBER:
                    testHttpPostJson();
                    break;
                case OK_HTTP_POST_JSON_TASK_NUMBER:
                    testOkHttpPostJson();
                    break;
                case HTTP_POST_FORM_DATA_TASK_NUMBER:
                    testHttpPostFormData();
                    break;
                case OK_HTTP_POST_FORM_DATA_TASK_NUMBER:
                    testOkHttpPostFormData();
                    break;
                default:
                    break;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    interface ServerTest {
        void runWith(SimpleWebServer server) throws IOException;
    }

    private static String getUrl(int port) {
        return String.format("http://127.0.0.1:%d", port);
    }

    /**
     * Start a new server to run a single test against. The test can then send requests to it.
     *
     * <p>The server will automatically be started before the test begins and stopped on its
     * completion.
     */
    private static void runWithServer(ServerTest serverTest) throws IOException {
        SimpleWebServer server =
                new SimpleWebServer(
                        new SimpleWebServer.RequestHandler() {
                            @Override
                            public String onRequest(List<SimpleWebServer.QueryParam> queryParams) {
                                return "SUCCESS";
                            }
                        });
        server.start();
        try {
            serverTest.runWith(server);
        } finally {
            server.stop();
        }
    }

    private static String createJSONString() {
        try {
            JSONObject json = new JSONObject().put("name", "student");
            JSONObject arrayItem = new JSONObject().put("id", 3).put("name", "course1");
            JSONArray array = new JSONArray().put(arrayItem);
            json.put("course", array);
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace(System.out);
        }
        return "";
    }
}
