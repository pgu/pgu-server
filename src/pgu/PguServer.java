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
import java.util.concurrent.CopyOnWriteArrayList;

import pgu.RequestContext.ContentType;
import pgu.RequestContext.HttpMethod;

public class PguServer {

    private static final String                              HEADER_ACCEPT         = "Accept:";
    private static final String                              HEADER_CONTENT_LENGTH = "Content-Length:";
    private static final int                                 PORT                  = 8081;
    private static Thread                                    threadForResponses    = null;
    private static ConcurrentHashMap<Socket, RequestContext> socket2response       = new ConcurrentHashMap<Socket, RequestContext>();
    private static CopyOnWriteArrayList<String>              bodies                = new CopyOnWriteArrayList<String>();

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

    private static void initThreadForResponses() {
        threadForResponses = new Thread(new Runnable() {

            @Override
            public void run() {
                for (final Entry<Socket, RequestContext> e : socket2response.entrySet()) {

                    final Socket socket = e.getKey();
                    final RequestContext rqContext = e.getValue();

                    try {
                        final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        final String codeAndMessage = getResponseCodeAndMessage(rqContext);
                        bw.write("HTTP/1.0 " + codeAndMessage + "\n");
                        bw.write("Server: PguServer/1.0\n");
                        bw.write("Content-Type: " + rqContext.contentTypeValue + "\n");
                        bw.write("Content-Length: " + rqContext.response.getBytes().length + "\n");
                        bw.write("\n");
                        if (!codeAndMessage.contains(Integer.toString(HttpURLConnection.HTTP_NO_CONTENT))) {
                            bw.write(rqContext.response);
                        }

                        bw.flush();

                        socket.close();
                        socket2response.remove(socket);

                    } catch (final IOException ioe) {
                        ioe.printStackTrace();
                        throw new RuntimeException();
                    }
                }
            }

            private String getResponseCodeAndMessage(final RequestContext rqContext) {
                if (rqContext.method == HttpMethod.GET) {
                    return HttpURLConnection.HTTP_OK + " OK";

                } else if (rqContext.method == HttpMethod.POST) {
                    return HttpURLConnection.HTTP_CREATED + " CREATED";

                } else if (rqContext.method == HttpMethod.PUT) {
                    return HttpURLConnection.HTTP_NO_CONTENT + " NO CONTENT";

                } else {
                    return HttpURLConnection.HTTP_NOT_FOUND + " NOT FOUND";
                }
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

    private static void processClientRequest(final Socket socket) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                final RequestContext rqContext = readRequest(socket);

                if (rqContext.isRequestForListingBodies) {
                    final StringBuilder sb = new StringBuilder();
                    for (final String msg : bodies) {
                        sb.append(msg);
                        sb.append("\n\n");
                    }
                    rqContext.response = sb.toString();
                } else {
                    giveResponseForContentType(socket, rqContext);
                }

                socket2response.put(socket, rqContext);
                threadForResponses.run();
            }

            private void giveResponseForContentType(final Socket socket, final RequestContext rqContext) {
                final String clientIP = socket.getInetAddress().toString();
                final String clientPort = Integer.toString(socket.getPort());
                final String threadName = Thread.currentThread().getName();
                final String threadActives = Integer.toString(Thread.activeCount());
                final String rqMethod = rqContext.method.toString();

                if (ContentType.ANY == rqContext.contentType) {
                    rqContext.response = "Hello World!";

                } else if (ContentType.HTML == rqContext.contentType) {
                    rqContext.response = "" + //
                            "<html>" + //
                            "  <head>" + //
                            "    <title>" + threadName + "</title>" + //
                            "  </head>" + //
                            "  <body>" + //
                            "    <div>Your ip: " + clientIP + "</div>" + //
                            "    <div>Your port: " + clientPort + "</div>" + //
                            "    <div>Request method: " + rqMethod + "</div>" + //
                            "  </body>" + //
                            "</html>" //
                    ;
                } else if (ContentType.JSON == rqContext.contentType) {
                    rqContext.response = String.format("" + //
                            "{\"thread\": \"%s\"," + //
                            " \"ip\"    : \"%s\"," + //
                            " \"port\"  : \"%s\"," + //
                            " \"method\": \"%s\"}", //
                            threadName //
                            , clientIP //
                            , clientPort //
                            , rqMethod //
                            );

                } else if (ContentType.TEXT == rqContext.contentType) {
                    rqContext.response = String.format( //
                            "thread: %s\nyour IP: %s\nyour port: %s\nthe request method: %s" //
                            , threadName //
                            , clientIP //
                            , clientPort //
                            , rqMethod //
                            );

                } else if (ContentType.XML == rqContext.contentType) {
                    rqContext.response = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" + //
                            "<info>\n" + //
                            "  <thread>\n" + //
                            "    <name>" + threadName + "</name>\n" + //
                            "    <actives>" + threadActives + "</actives>\n" + //
                            "  </thread>\n" + //
                            "  <request>\n" + //
                            "    <inet>" + clientIP + "</inet>\n" + //
                            "    <port>" + clientPort + "</port>\n" + //
                            "    <method>" + rqMethod + "</method>\n" + //
                            "  </request>\n" + //
                            "</info>\n" //
                    ;
                } else {
                    rqContext.response = "Sorry, I do not deal with this content-type for now..";
                }
            }

            //            public long copyLarge(final InputStream input, final OutputStream output) throws IOException {
            //                final byte[] buffer = new byte[1024 * 4];
            //                long count = 0;
            //                int n = 0;
            //                while (-1 != (n = input.read(buffer))) {
            //                    output.write(buffer, 0, n);
            //                    count += n;
            //                }
            //                return count;
            //            }

            private RequestContext readRequest(final Socket socket) {
                try {

                    //                    final ByteArrayOutputStream output = new ByteArrayOutputStream();
                    //                    copyLarge(socket.getInputStream(), output);
                    //                    final byte[] in = output.toByteArray();
                    //                    final InputStream is = new ByteArrayInputStream(in);

                    final BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String line = br.readLine();

                    final RequestContext rqContext = new RequestContext();
                    rqContext.method = extractHttpMethod(line);
                    rqContext.isRequestForListingBodies = isRequestForListingBodies(line);

                    //                    final StringBuilder sbForBody = new StringBuilder();
                    //                    boolean isBody = false;

                    while (line != null) {
                        System.out.println(line);
                        if ("".equals(line)) {
                            break;
                        }

                        if (line.startsWith(HEADER_ACCEPT)) {
                            ContentType.setContentTypeFromHeader(line, rqContext);
                        } else if (line.startsWith(HEADER_CONTENT_LENGTH)) {
                            rqContext.contentLength = extractContentLength(line);
                        }

                        //                        if (isBody) {
                        //                            sbForBody.append(line);
                        //                            //                            line = null;
                        //                        }
                        //
                        //                        isBody = "".equals(line);

                        line = br.readLine();
                    }

                    if (HttpMethod.POST == rqContext.method //
                            || HttpMethod.PUT == rqContext.method) {

                        //                        if (sbForBody.length() != 0) {
                        //                            bodies.add(sbForBody.toString());
                        //                        }
                    }

                    return rqContext;

                } catch (final IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException();
                }
            }

        }).start();
    }

    private static int extractContentLength(final String line) {
        return Integer.parseInt(line.split(HEADER_CONTENT_LENGTH)[1].trim());
    }

    private static boolean isRequestForListingBodies(final String line) {
        return line.contains("/list");
    }

    private static HttpMethod extractHttpMethod(final String line) {

        if (line.startsWith(HttpMethod.GET.toString())) {
            return HttpMethod.GET;

        } else if (line.startsWith(HttpMethod.POST.toString())) {
            return HttpMethod.POST;

        } else if (line.startsWith(HttpMethod.PUT.toString())) {
            return HttpMethod.PUT;

        } else {
            return HttpMethod.GET; // do not deal with other methods for now
        }
    }
}
