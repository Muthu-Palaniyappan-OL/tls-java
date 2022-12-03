import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Function;

public class TlsState extends X25519 {
    private byte[] sessionId = new byte[32];
    private byte[] random = new byte[32];
    ByteBuffer buffer = ByteBuffer.allocate(16000);
    int bufferLength = 0;
    private int state = 0;
    private int side = 0;
    /*
     * state = 0 => Not Helloed
     * state = 1 => can send Application Data
     * side = 0 => client
     * side = 1 => server
     */

    TlsState() throws NoSuchAlgorithmException {
        super();
        new Random().nextBytes(sessionId);
        new Random().nextBytes(random);
    }

    public byte[] getSessionId() {
        return this.sessionId;
    }

    public String getSide() {
        return (this.side == 0 ? "Client" : "Server");
    }

    public void setSide(String str) {
        if (str == "Server")
            this.side = 1;
        else
            this.side = 0;
    }

    public void readResponseBuffer() throws Exception {
        System.out.println(Arrays.toString(getPublicKey()));
        System.out.println(Arrays.toString(getPrivateKey()));
        System.out.println(buffer);

        if (this.getSide() == "Server")
            this.buffer.position(101);
        else
            this.buffer.position(95);

        System.out.println(this.buffer);
        byte[] pub = new byte[46];
        this.buffer.get(pub);
        setOthersPublicKey(pub);
        deriveSharedKey();
        System.out.println("Derived : " + Arrays.toString(getDerivedKey()));
        this.buffer.rewind();
    }

    public int constructResponseBuffer() {
        this.buffer.clear();
        int len = 0;

        // Server Hello
        if (this.state == 0 && this.side == 1) {
            len = this.serverHandshakeRecordHeader(this.buffer, b2 -> {
                return this.serverHandshakeHeader(b2, b1 -> {
                    return this.extensions(b1, b -> {
                        return this.serverTls13version(b);
                    }, b -> {
                        return this.serverKeyShare(b);
                    });
                });
            });
            this.state = 1;
            this.bufferLength = len;
        }

        // Client Hello
        else if (this.state == 0 && this.side == 0) {
            len = this.clientHandshakeRecordHeader(this.buffer, b2 -> {
                return this.clientHandshakeHeader(b2, b1 -> {
                    return this.extensions(b1, b -> {
                        return this.clientTls13version(b);
                    }, b -> {
                        return this.clientKeyShare(b);
                    });
                });
            });
            this.state = 1;
            this.bufferLength = len;
        }

        this.buffer.rewind();
        return len;
    }

    public void printBuffer(int size) {
        for (int i = 0; i < size; i++) {
            System.out.printf("0x%x ", this.buffer.get(i));
        }
    }

    public int clientHandshakeRecordHeader(ByteBuffer buf, Function<ByteBuffer, Integer> header) {
        int len = 0;
        buf.put((byte) 0x16);
        len += 1;
        buf.putShort((short) 0x0301);
        len += 2;
        int pos = buf.position();
        buf.putShort((short) 0x0000);
        len += 2;
        int headerLen = header.apply(buf);
        buf.putShort(pos, (short) (headerLen));
        return len + headerLen;
    }

    public int serverHandshakeRecordHeader(ByteBuffer buf, Function<ByteBuffer, Integer> header) {
        int len = 0;
        buf.put((byte) 0x16);
        len += 1;
        buf.putShort((short) 0x0303);
        len += 2;
        int pos = buf.position();
        buf.putShort((short) 0x0000);
        len += 2;
        int headerLen = header.apply(buf);
        buf.putShort(pos, (short) (headerLen));
        return len + headerLen;
    }

    public int clientTls13version(ByteBuffer buf) {
        int len = 0;
        buf.putShort((short) 0x002b);
        len += 2;
        buf.putShort((short) 0x0003);
        len += 2;
        buf.put((byte) 0x02);
        len += 1;
        buf.putShort((short) 0x0304);
        len += 2;
        return len;
    }

    public int serverTls13version(ByteBuffer buf) {
        int len = 0;
        buf.putShort((short) 0x002b);
        len += 2;
        buf.putShort((short) 0x0002);
        len += 2;
        buf.putShort((short) 0x0304);
        len += 2;
        return len;
    }

    public int clientHandshakeHeader(ByteBuffer buf, Function<ByteBuffer, Integer> header) {
        int len = 0;
        buf.putShort((short) 0x0100);
        len += 2;
        int pos = buf.position();
        buf.putShort((short) 0x0000);
        len += 2;
        buf.putShort((short) 0x0303);
        len += 2;
        buf.put(random);
        len += 32;
        buf.put((byte) sessionId.length);
        len += 1;
        buf.put(sessionId);
        len += sessionId.length;
        buf.putShort((short) 0x0002);
        len += 2;
        buf.putShort((short) 0x1302);
        len += 2;
        buf.putShort((short) 0x0100);
        len += 2;

        int headerLength = header.apply(buf);
        buf.putShort(pos, (short) (len + headerLength - 4));
        return len + headerLength;
    }

    public int serverHandshakeHeader(ByteBuffer buf, Function<ByteBuffer, Integer> header) {
        int len = 0;
        buf.putShort((short) 0x0200);
        len += 2;
        int pos = buf.position();
        buf.putShort((short) 0x0000);
        len += 2;
        buf.putShort((short) 0x0303);
        len += 2;
        buf.put(random);
        len += 32;
        buf.put((byte) sessionId.length);
        len += 1;
        buf.put(sessionId);
        len += sessionId.length;
        buf.putShort((short) 0x1302);
        len += 2;
        buf.put((byte) 0x00);
        len += 1;

        int headerLength = header.apply(buf);
        buf.putShort(pos, (short) (len + headerLength - 4));
        return len + headerLength;
    }

    public int clientKeyShare(ByteBuffer buf) {
        int publicKeyLen = getPublicKey().length;
        int len = 0;
        buf.putShort((short) 0x0033);
        len += 2;
        buf.putShort((short) (publicKeyLen + 6));
        len += 2;
        buf.putShort((short) (publicKeyLen + 4));
        len += 2;
        buf.putShort((short) 0x001d);
        len += 2;
        buf.putShort((short) (publicKeyLen));
        len += 2;
        buf.put(getPublicKey());
        return len + publicKeyLen;
    }

    public int serverKeyShare(ByteBuffer buf) {
        int publicKeyLen = getPublicKey().length;
        int len = 0;
        buf.putShort((short) 0x0033);
        len += 2;
        buf.putShort((short) (publicKeyLen + 4));
        len += 2;
        buf.putShort((short) 0x001d);
        len += 2;
        buf.putShort((short) (publicKeyLen));
        len += 2;
        buf.put(getPublicKey());
        return len + publicKeyLen;
    }

    @SafeVarargs
    public final int extensions(ByteBuffer buf, Function<ByteBuffer, Integer>... ex) {
        int pos = buf.position();
        buf.putShort((short) 0x0000);
        int extensionLen = 0;
        for (int i = 0; i < ex.length; i++) {
            extensionLen += ex[i].apply(buf);
        }
        buf.putShort(pos, (short) extensionLen);
        return extensionLen + 2;
    }
}
