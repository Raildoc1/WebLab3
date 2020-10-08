package chat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Scanner;
import java.util.UUID;

// "<node name> <message UUID> <message>"
//

public class ChatNode {

    //<editor-fold desc="private fields">
    private static final int TIME_OUT_MILLIS = 1000;
    private static final int MAX_MSG_LENGHT = 256;

    private DatagramSocket socket;
    private InetAddress address;
    private String name;
    private int portToListen;
    private int port;
    private byte[] buf = new byte[MAX_MSG_LENGHT];
    private Scanner scanner = new Scanner(System.in);
    //</editor-fold>

    //<editor-fold desc="constructor">
    public ChatNode(int port, String name, int portToListen) throws SocketException {
        initSocket(port);
        this.port = port;
        this.portToListen = portToListen;
        this.name = name;
    }

    public ChatNode(int port, String name) throws SocketException {
        initSocket(port);
        this.name = name;
    }

    private void initSocket(int port) throws SocketException {
        socket = new DatagramSocket(port);
        socket.setSoTimeout(TIME_OUT_MILLIS);
    }
    //</editor-fold>

    public void tick() {
        try {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            String receivedMessage = new String(packet.getData());

            String[] split = receivedMessage.split("\\s+");

            packet.getData();

            UUID uuid = UUID.randomUUID();
            String confirmationMessage = name + " " + uuid.toString() + " " + split[1];

        } catch (IOException e) { /*IGNORE*/ }

        try {
            String msg = readStdin();

            if(!msg.equals("")) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
                packet.setData(msg.getBytes());
                socket.send(packet);
            }
        } catch (IOException e) {
            System.out.println("Failed to read from stdin!");
        }
    }

    public void stop() {
        socket.close();
    }

    private String readStdin() throws IOException {
        if(System.in.available() != 0) {
            return scanner.nextLine();
        }
        return "";
    }
}
