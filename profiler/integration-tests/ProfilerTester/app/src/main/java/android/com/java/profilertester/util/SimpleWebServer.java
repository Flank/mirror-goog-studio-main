package android.com.java.profilertester.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public final class SimpleWebServer implements Runnable {
    public static class QueryParam {

        private String key;
        private String value;

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setKey(String value) {
            key = value;
        }

        public String getKey() {
            return key;
        }

        public QueryParam(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    /** The port number we listen to */
    private int port;

    /** True if the server is running. */
    private boolean isRunning;

    /** The {@link java.net.ServerSocket} that we listen to. */
    private ServerSocket serverSocket;

    private RequestHandler handler;

    private Thread myThread;

    public interface RequestHandler {

        String onRequest(List<QueryParam> queryParams);
    }

    /** WebServer constructor. */
    public SimpleWebServer(RequestHandler handler) {
        this.port = getAvailablePort();
        this.handler = handler;
    }

    private static int getAvailablePort() {
        int port = -1;
        try {
            ServerSocket socket = new ServerSocket(0);
            port = socket.getLocalPort();
            socket.close();
        } catch (IOException ex) {
            System.out.println("Unable to find available port: " + ex);
        }
        return port;
    }

    public int getPort() {
        return port;
    }

    /** This method starts the web server listening to the specified port. */
    public void start() {
        isRunning = true;
        myThread = new Thread(this);
        myThread.start();
    }

    public void join() {
        try {
            myThread.join();
        } catch (InterruptedException ex) {
            // do nothing.
        }
    }

    /** This method stops the web server */
    public void stop() {
        try {
            isRunning = false;
            if (null != serverSocket) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            System.err.println("Error closing the server socket." + e);
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Test Framework Server Listening");
            while (isRunning) {
                Socket socket = serverSocket.accept();
                handle(socket);
                socket.close();
            }
        } catch (SocketException e) {
            // The server was stopped; ignore.
            System.err.println("Socket Exception." + e);
        } catch (IOException e) {
            System.err.println("Web server error." + e);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Respond to a request from a client.
     *
     * @param socket The client socket.
     */
    private void handle(Socket socket) throws IOException {
        BufferedReader reader = null;
        PrintStream output = null;
        try {
            List<QueryParam> argPair = new ArrayList<>();

            // Read HTTP request headers and parse out the route.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("GET /") || line.startsWith("POST /")) {
                    int argStart = line.indexOf('?');
                    int argEnd = line.indexOf(' ', argStart);
                    if (argStart == -1) {
                        break;
                    }
                    String[] argList = line.substring(argStart + 1, argEnd).split("&");
                    for (String arg : argList) {
                        int keyIndex = arg.indexOf("=");
                        argPair.add(
                                new QueryParam(
                                        arg.substring(0, keyIndex), arg.substring(keyIndex + 1)));
                    }
                    break;
                }
            }

            // Output stream that we send the response to
            output = new PrintStream(socket.getOutputStream());
            String response = handler.onRequest(argPair);

            // Send out the content.
            output.println("HTTP/1.0 200 OK");
            output.println("Content-Type: text/text");
            output.println("Content-Length: " + response.length());
            output.println();
            output.println(response);
            output.flush();
        } finally {
            if (null != output) {
                output.close();
            }
            if (null != reader) {
                reader.close();
            }
        }
    }
}
