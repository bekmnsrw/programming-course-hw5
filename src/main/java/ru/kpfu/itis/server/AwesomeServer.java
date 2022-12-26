package ru.kpfu.itis.server;

import ru.kpfu.itis.AwesomePacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadPoolExecutor;

public class AwesomeServer implements Runnable {
    private Integer port;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private InputStream in;
    private OutputStream out;
    private ThreadPoolExecutor serverPool;

    private AwesomeServer() {

    }

    public static AwesomeServer create(Integer port, ThreadPoolExecutor serverPool) throws IOException {
        AwesomeServer server = new AwesomeServer();
        server.port = port;
        server.serverSocket = new ServerSocket(port);
        server.serverPool = serverPool;

        return server;
    }

    private byte[] extendArray(byte[] oldArray) {
        int oldSize = oldArray.length;
        byte[] newArray = new byte[oldSize * 2];
        System.arraycopy(oldArray, 0, newArray, 0, oldSize);

        return newArray;
    }

    private byte[] readInput(InputStream stream) throws IOException {
        int b;
        byte[] buffer = new byte[10];
        int counter = 0;

        while ((b = stream.read()) > -1) {
            buffer[counter++] = (byte) b;

            if (counter >= buffer.length) {
                buffer = extendArray(buffer);
            }

            if (counter > 1 && AwesomePacket.compareEOP(buffer, counter - 1)) {
                break;
            }
        }

        byte[] data = new byte[counter];
        System.arraycopy(buffer, 0, data, 0, counter);

        return data;
    }

    public void run() {
        try {
            clientSocket = serverSocket.accept();
            out = clientSocket.getOutputStream();
            in = clientSocket.getInputStream();

            while (true) {
                byte[] data = readInput(in);
                AwesomePacket packet = AwesomePacket.parse(data);

                if (packet.getType() == AwesomePacket.TYPE_META && packet.getSubtype() == AwesomePacket.SUBTYPE_HANDSHAKE) {
                    if (packet.checkHandshakeRequest()) {
                        System.out.println("CLIENT: HANDSHAKE STATUS: " + packet.checkHandshakeRequest());
                        AwesomePacket handshakePacket = AwesomePacket.create(AwesomePacket.TYPE_META, AwesomePacket.SUBTYPE_HANDSHAKE);
                        handshakePacket.setValue(1, packet.checkHandshakeResponse());
                        out.write(handshakePacket.toByteArray());
                        out.flush();
                    }

                }
                if (packet.getType() == AwesomePacket.TYPE_META && packet.getSubtype() == AwesomePacket.SUBTYPE_GOODBYE) {
                    System.out.println("CLIENT: " + packet.getValue(1));
                    AwesomePacket goodbyePacket = AwesomePacket.create(AwesomePacket.TYPE_META, AwesomePacket.SUBTYPE_GOODBYE);
                    goodbyePacket.setValue(1, "Goodbye, client!");
                    out.write(goodbyePacket.toByteArray());
                    out.flush();

                    synchronized (serverPool) {
                        serverPool.notifyAll();
                    }

                    break;
                }

                if (packet.getType() == AwesomePacket.TYPE_STANDARD && packet.getSubtype() == AwesomePacket.SECRET_KEY) {
                    System.out.println("Client has sent a secret key");
                    AwesomePacket handshakePacket = AwesomePacket.create(AwesomePacket.TYPE_META, AwesomePacket.SUBTYPE_HANDSHAKE);
                    handshakePacket.setValue(1, "Secret key was successfully received!");
                    out.write(handshakePacket.toByteArray());
                    out.flush();
                }

                if (packet.getType() == AwesomePacket.TYPE_STANDARD && packet.getSubtype() == AwesomePacket.SUBTYPE_PROTECTED) {
                    System.out.println("PROTECTED CONNECTION: Client's message from protected packet: " + packet.getValue(1));
                    AwesomePacket protectedPacket = AwesomePacket.create(AwesomePacket.TYPE_STANDARD, AwesomePacket.SUBTYPE_PROTECTED);
                    protectedPacket.setValue(1, "PROTECTED CONNECTION. Message from client: " + packet.getValue(1));
                    out.write(protectedPacket.toByteArray());
                    out.flush();
                }

                if (packet.getType() == AwesomePacket.TYPE_STANDARD && packet.getSubtype() == AwesomePacket.SUBTYPE_JSON) {
                    System.out.println("JSON from client: " + packet.getValue(1));
                    AwesomePacket protectedPacket = AwesomePacket.create(AwesomePacket.TYPE_STANDARD, AwesomePacket.SUBTYPE_PROTECTED);
                    protectedPacket.setValue(1, "JSON was received successfully!");
                    out.write(protectedPacket.toByteArray());
                    out.flush();
                }

            }
        } catch (IOException e) {
            System.out.println("Something went wrong. Reason: " + e.getMessage());
        }
    }
}
