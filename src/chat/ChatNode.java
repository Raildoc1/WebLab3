package chat;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;
import java.util.UUID;

// "<node name> <message UUID> <message>"
//

public class ChatNode {

    public enum MessageType {
        CONF, // confirmation
        TXT, // text message
        CONN, // connection request
        ALT // alternative node address
    }

    //<editor-fold desc="private fields">
    private static final int TIME_OUT_MILLIS = 1000;
    private static final int MAX_MSG_LENGTH = 256;

    private DatagramSocket socket;
    private InetAddress address;
    private String name;
    private int parentPort;
    private InetAddress parentAddress;
    private int port;
    private float lossChance;
    private byte[] buf = new byte[MAX_MSG_LENGTH];
    private Scanner scanner = new Scanner(System.in);
    private boolean isStopped = false;
    //</editor-fold>

    //<editor-fold desc="constructor">
    public ChatNode(String name, int port, int lossChance, byte[] parentIP, int parentPort) throws IOException {
        initSocket(port);
        this.port = port;
        this.parentPort = parentPort;
        this.name = name;
        parentAddress = InetAddress.getByAddress(parentIP);
        initLossChance(lossChance);

        //byte[] addr = socket.getInetAddress().getAddress();

        String connectionRequest = MessageType.CONN + ":127.0.0.1:" + port + "::";

        DatagramPacket packet = new DatagramPacket(connectionRequest.getBytes(), connectionRequest.length(), parentAddress, parentPort);

        socket.send(packet);
    }

    public ChatNode(String name, int port, int lossChance) throws SocketException, UnknownHostException {
        initSocket(port);
        this.name = name;
        initLossChance(lossChance);
    }

    private void initSocket(int port) throws SocketException, UnknownHostException {
        socket = new DatagramSocket(port);
        socket.setSoTimeout(TIME_OUT_MILLIS);
    }

    private void initLossChance (int lossChance) {
        this.lossChance = ((float)lossChance) / ((float)100);
    }
    //</editor-fold>

    public void tick() {

        if(isStopped) return;

        try {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            String receivedMessage = new String(packet.getData());

            System.out.println(receivedMessage.split("\\::")[0]);

            String[] split = receivedMessage.split("\\s+");

            packet.getData();

            UUID uuid = UUID.randomUUID();
            String confirmationMessage = name + " " + uuid.toString() + " " + split[1];

            System.out.println(packet.getData());

        } catch (IOException e) { /*IGNORE*/ }

        try {
            String msg = readStdin();

            if(msg.isEmpty()) return;

            if(msg.equals("stop")) {
                System.out.println("Stopping...");
                stop();
                return;
            }

            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
            packet.setData(msg.getBytes());
            socket.send(packet);

        } catch (IOException e) {
            System.out.println("Failed to read from stdin!");
        }
    }

    public void stop() {
        socket.close();
        isStopped = true;
    }

    public boolean isStop() {
        return isStopped;
    }

    private String readStdin() throws IOException {
        if(System.in.available() != 0) {
            return scanner.nextLine();
        }
        return "";
    }
}
