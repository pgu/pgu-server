package pgu;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import pgu.RequestContext.HttpMethod;

public class PguServer {

    private static final int PORT = 8081;

    public static void main(final String[] args) {
        run();
    }

    private static void run() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = getServerSocket();
            System.out.println("*** Server listening at " + serverSocket.getLocalPort());

            initThreadForResponses();

            while (true) {
                final Socket socket = acceptSocket(serverSocket);
                processClientRequest(socket);
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    System.out.println("*** Closing server");
                    serverSocket.close();
                } catch (final IOException e) {
                    // fail silently
                }
            }
        }
    }

    private static Thread threadForResponses = null;

    private static void initThreadForResponses() {
        threadForResponses = new Thread(new Runnable() {

            @Override
            public void run() {
                for (final Entry<Socket, RequestContext> e : socket2response.entrySet()) {

                    final Socket socket = e.getKey();
                    final RequestContext rqContext = e.getValue();

                    try {
                        final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        bw.write("HTTP/1.0 " + getResponseCode(rqContext) + "\n");
                        bw.write("Server: pguServer/1.0\n");
                        bw.write("Content-Type: application/xml\n");
                        bw.write("Content-Length: " + rqContext.response.getBytes().length + "\n\n");
                        bw.write(rqContext.response);
                        bw.flush();

                        socket.close();
                        socket2response.remove(socket);

                    } catch (final IOException ioe) {
                        ioe.printStackTrace();
                        throw new RuntimeException();
                    }
                }
            }

            private String getResponseCode(final RequestContext rqContext) {
                if (rqContext.method == HttpMethod.GET) {
                    return HttpURLConnection.HTTP_OK + " OK";

                } else if (rqContext.method == HttpMethod.POST) {
                    return HttpURLConnection.HTTP_CREATED + " CREATED";

                } else if (rqContext.method == HttpMethod.PUT) {
                    return HttpURLConnection.HTTP_NO_CONTENT + " NO CONTENT";

                }
                return HttpURLConnection.HTTP_NOT_FOUND + " NOT FOUND";
            }
        });
    }

    private static ServerSocket getServerSocket() {
        try {
            return new ServerSocket(PORT);
        } catch (final IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private static Socket acceptSocket(final ServerSocket serverSocket) {
        try {
            return serverSocket.accept();
        } catch (final IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private static ConcurrentHashMap<Socket, RequestContext> socket2response = new ConcurrentHashMap<Socket, RequestContext>();

    private static void processClientRequest(final Socket socket) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                final RequestContext rqContext = readRequest(socket);

                if (rqContext.askForXml) {

                    rqContext.response = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" + //
                            "<info>\n" + //
                            "  <thread>\n" + //
                            "    <name>" + Thread.currentThread().getName() + "</name>\n" + //
                            "    <actives>" + Thread.activeCount() + "</actives>\n" + //
                            "  </thread>\n" + //
                            "  <request>\n" + //
                            "    <inet>" + socket.getInetAddress() + "</inet>\n" + //
                            "    <port>" + socket.getPort() + "</port>\n" + //
                            "    <method>" + rqContext.method + "</method>\n" + //
                            "  </request>\n" + //
                            "</info>\n" //
                            // rqContext.response = "" + //
                            // "<html>" + //
                            // "  <body>" + //
                            // "    <div>" + Thread.currentThread().getName() + "</div>" + //
                            // "  </body>" + //
                            // "</html>" //
                    ;

                }
                socket2response.put(socket, rqContext);
                threadForResponses.run();
            }

            private RequestContext readRequest(final Socket socket) {
                try {
                    final BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String line = br.readLine();

                    final RequestContext rqContext = new RequestContext();
                    if (line.startsWith("GET")) {
                        rqContext.method = HttpMethod.GET;

                    } else if (line.startsWith("POST")) {
                        rqContext.method = HttpMethod.POST;

                    } else if (line.startsWith("PUT")) {
                        rqContext.method = HttpMethod.PUT;
                    }

                    while (line != null) {
                        if ("".equals(line)) {
                            break;
                        } else {
                            System.out.println(line);
                            if (line.startsWith("Accept:")) {
                                if (line.contains("application/xml")) {
                                    rqContext.askForXml = true;
                                }
                            }
                        }
                        line = br.readLine();
                    }
                    return rqContext;
                } catch (final IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException();
                }
            }
        }).start();
    }
}
