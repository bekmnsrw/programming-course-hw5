package ru.kpfu.itis.client;

import ru.kpfu.itis.AwesomePacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class AwesomeClient {
    private String host;
    private Integer port;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private AwesomeClient() {

    }

    public static AwesomeClient initConnection(String host, Integer port) throws IOException {
        AwesomeClient client = new AwesomeClient();
        client.host = host;
        client.port = port;
        client.socket = new Socket(host, port);
        client.in = client.socket.getInputStream();
        client.out = client.socket.getOutputStream();

        return client;
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

    private void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }

    public void sendMessage(AwesomePacket packet) throws IOException {
        out.write(packet.toByteArray());
        out.flush();

        byte[] data = readInput(in);

        AwesomePacket responsePacket = AwesomePacket.parse(data);

        if (packet.getType() == AwesomePacket.TYPE_META && packet.getSubtype() == AwesomePacket.SUBTYPE_HANDSHAKE) {
            String response = responsePacket.getValue(1).toString();
            System.out.println("SERVER: HANDSHAKE STATUS: " + response);
            return;
        }

        String response = responsePacket.getValue(1);
        System.out.println("SERVER: " + response);

        if (packet.getType() == AwesomePacket.TYPE_META && packet.getSubtype() == AwesomePacket.SUBTYPE_GOODBYE) {
            close();
        }
    }
}
