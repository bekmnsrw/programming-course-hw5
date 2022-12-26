package ru.kpfu.itis.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.kpfu.itis.AwesomePacket;
import ru.kpfu.itis.models.Student;

import java.io.IOException;

public class Main {
    private static void initConnection(AwesomeClient client) throws IOException {
        AwesomePacket handshakePacket = AwesomePacket.create(AwesomePacket.TYPE_META, AwesomePacket.SUBTYPE_HANDSHAKE);
        client.sendMessage(handshakePacket);

        AwesomePacket secretKeyPacket = AwesomePacket.create(AwesomePacket.TYPE_STANDARD, AwesomePacket.SECRET_KEY);
        client.sendMessage(secretKeyPacket);
    }

    public static void main(String[] args) throws IOException {
        AwesomeClient client = AwesomeClient.initConnection("localhost", 5678);
        initConnection(client);

        AwesomePacket protectedPacket = AwesomePacket.create(AwesomePacket.TYPE_STANDARD, AwesomePacket.SUBTYPE_PROTECTED);
        protectedPacket.setValue(1, "Very secret data");
        client.sendMessage(protectedPacket);

        ObjectMapper objectMapper = new ObjectMapper();
        Student student = new Student("Ilya", "Bekmansurov", 19, new String[] {"Android", "Data Bases"});
        AwesomePacket jsonPacket = AwesomePacket.create(AwesomePacket.TYPE_STANDARD, AwesomePacket.SUBTYPE_JSON);
        jsonPacket.setValue(1, objectMapper.writeValueAsString(student));
        client.sendMessage(jsonPacket);

        AwesomePacket goodbyePacket = AwesomePacket.create(AwesomePacket.TYPE_META, AwesomePacket.SUBTYPE_GOODBYE);
        goodbyePacket.setValue(1, "Goodbye, server!");
        client.sendMessage(goodbyePacket);
    }
}
