package pgu;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class PguServer {

    public static void main(final String[] args) {
        run();
    }

    private static void run() {
        final ServerSocket serverSocket = getServerSocket();
        System.out.println("*** Server listening at " + serverSocket.getLocalPort());

        initThreadForResponses();

        while (true) {
            final Socket socket = acceptSocket(serverSocket);
            processClientRequest(socket);
        }
    }

    private static Thread threadForResponses = null;

    private static void initThreadForResponses() {
        threadForResponses = new Thread(new Runnable() {

            @Override
            public void run() {
                for (final Entry<Socket, String> e : socket2response.entrySet()) {

                    final Socket socket = e.getKey();
                    final String response = e.getValue();

                    try {
                        final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        bw.write("HTTP/1.0 200 OK\n");
                        bw.write("Server: pguServer/1.0\n");
                        bw.write("Content-Type: application/xml\n");
                        bw.write("Content-Length: " + response.getBytes().length + "\n");
                        bw.write(response);
                        bw.flush();

                        socket.close();

                    } catch (final IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }
        });
    }

    private static ServerSocket getServerSocket() {
        try {
            return new ServerSocket(8081);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Socket acceptSocket(final ServerSocket serverSocket) {
        try {
            return serverSocket.accept();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static ConcurrentHashMap<Socket, String> socket2response = new ConcurrentHashMap<Socket, String>();

    private static void processClientRequest(final Socket socket) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                System.out.println("+++ client from " + socket.getInetAddress() + ":" + socket.getPort());

                readRequest(socket);

                final String response = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" + //
                        "<threads>\n" + //
                        "  <thread>\n" + //
                        "    <name>" + Thread.currentThread().getName() + "</name>\n" + //
                        "  <thread>\n" + //
                        "</threads>\n" //
                ;
                socket2response.put(socket, response);
                threadForResponses.run();
            }

            private void readRequest(final Socket socket) {
                try {
                    final BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String line = br.readLine();
                    while (line != null) {
                        if ("".equals(line)) {
                            break;
                        } else {
                            System.out.println(line);
                        }
                        line = br.readLine();
                    }
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
