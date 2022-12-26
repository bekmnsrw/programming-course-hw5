package ru.kpfu.itis;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
public class AwesomePacket {
    private static final byte HEADER_1 = (byte) 0xe4;
    private static final byte HEADER_2 = (byte) 0x15;

    private static final byte FOOTER_1 = (byte) 0x00;
    private static final byte FOOTER_2 = (byte) 0x90;

    public static final byte TYPE_META = 1;
    public static final byte SUBTYPE_HANDSHAKE = 2;
    public static final byte SUBTYPE_GOODBYE = 3;

    public static final byte TYPE_STANDARD = 4;
    public static final byte SUBTYPE_JSON = 5;
    public static final byte SUBTYPE_PROTECTED = 6;
    public static final byte SECRET_KEY = 7;

    private byte type;
    private byte subtype;
    public static byte[] secretKey;
    private List<AwesomeField> fields = new ArrayList<>();

    private AwesomePacket() {

    }

    public static boolean compareEOP(byte[] array, int lastItem) {
        return array[lastItem - 1] == FOOTER_1 && array[lastItem] == FOOTER_2;
    }

    public byte[] toByteArray() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(
                    new byte[] {
                            HEADER_1,
                            HEADER_2,
                            type,
                            subtype
                    }
            );

            for (AwesomeField field : fields) {
                out.write(field.id);
                out.write(field.size);
                out.write(field.data);
            }

            out.write(
                    new byte[] {
                            FOOTER_1,
                            FOOTER_2
                    }
            );

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static AwesomePacket parse(byte[] data) {
        if (data[0] != HEADER_1 && data[1] != HEADER_2
                || data[data.length - 1] != FOOTER_2 && data[data.length - 2] != FOOTER_1) {
            throw new IllegalArgumentException("Unknown packet format");
        }

        byte type = data[2];
        byte subtype = data[3];
        AwesomePacket packet = AwesomePacket.create(type, subtype);

        int offset = 4;

        while (true) {
            if (data.length - 2 <= offset) {
                return packet;
            }

            byte fieldId = data[offset];
            byte fieldSize = data[offset + 1];
            byte[] content = new byte[Byte.toUnsignedInt(fieldSize)];

            if (fieldSize != 0) {
                System.arraycopy(data, offset + 2, content, 0, Byte.toUnsignedInt(fieldSize));
            }

            AwesomeField field = new AwesomeField(fieldId, fieldSize, content);
            packet.getFields().add(field);

            offset += 2 + fieldSize;
        }
    }

    public AwesomeField getField(int id) {
        Optional<AwesomeField> field = getFields()
                .stream()
                .filter(f -> f.getId() == (byte) id)
                .findFirst();

        if (field.isEmpty()) {
            throw new IllegalArgumentException("No field with " + id + " id");
        }

        return field.get();
    }

    public <T> T getValue(int id) {
        AwesomeField field = getField(id);
        byte[] data;

        if (type == TYPE_STANDARD && subtype == SUBTYPE_PROTECTED) {
            data = decodeBytes(secretKey, field.getData());
        } else {
            data = field.getData();
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void setValue(int id, Object value) {
        AwesomeField field;

        try {
            field = getField(id);
        } catch (IllegalArgumentException e) {
            field = new AwesomeField((byte) id);
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);

            byte[] data = bos.toByteArray();

            if (type == TYPE_STANDARD && subtype == SUBTYPE_PROTECTED) {
                data = encodeBytes(secretKey, data);
            }

            if (data.length > 255) {
                throw new IllegalArgumentException("Too much data sent");
            }

            field.setSize((byte) data.length);
            field.setData(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        getFields().add(field);
    }

    private void setSecretKey() {
        String key = "lgh125fg78ervb0d";
        secretKey = key.getBytes();
    }

    private byte[] encodeBytes(byte[] key, byte[] content) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

            SecretKey secretKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(content);
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException |
                 InvalidKeyException e) {
            throw new RuntimeException("Unable to encode");
        }
    }

    private byte[] decodeBytes(byte[] key, byte[] content){
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

            SecretKey secretKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(content);
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException |
                 InvalidKeyException e) {
            throw new RuntimeException("Unable to decode");
        }
    }

    private void setHandshakeContent() {
        setValue(0, "Hello!");
    }

    public boolean checkHandshakeRequest() {
        if (type != TYPE_META || subtype != SUBTYPE_HANDSHAKE) {
            return false;
        } else {
            if (getValue(0).equals("Hello!")) {
                setValue(1, "We're connected through AwesomeProtocol!");
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean checkHandshakeResponse() {
        if (type != TYPE_META || subtype != SUBTYPE_HANDSHAKE) {
            return false;
        } else {
            return getValue(1).equals("We're connected through AwesomeProtocol!");
        }
    }

    public static AwesomePacket create(int type, int subtype) {
        AwesomePacket packet = new AwesomePacket();
        packet.type = (byte) type;
        packet.subtype = (byte) subtype;

        if (type == TYPE_STANDARD && subtype == SECRET_KEY) {
            packet.setSecretKey();
        }

        if (type == TYPE_META && subtype == SUBTYPE_HANDSHAKE) {
            packet.setHandshakeContent();
        }

        return packet;
    }

    @Data
    @AllArgsConstructor
    private static class AwesomeField {
        private byte id;
        private byte size;
        private byte[] data;

        public AwesomeField(byte id) {
            this.id = id;
        }
    }
}
