package chat;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
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

    private class Connection {
        InetAddress address;
        int port;
        public Connection(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
    }

    private class PendingMessage {
        String msg;
        String uuid;
        public PendingMessage(String msg, String uuid) {
            this.msg = msg;
            this.uuid = uuid;
        }
    }

    //<editor-fold desc="private fields">
    private static final int TIME_OUT_MILLIS = 1000;
    private static final int MAX_MSG_LENGTH = 256;

    // Node characteristics
    private String name;
    private DatagramSocket socket;
    private float lossChance;
    private int port;

    // Node connection
    private boolean hasParent;
    private int parentPort;
    private InetAddress parentAddress;
    private int alternativeParentPort;
    private InetAddress alternativeParentAddress;
    private ArrayList<Connection> connections;

    // Other
    ArrayList<PendingMessage> pendingMessages;
    private byte[] buf = new byte[MAX_MSG_LENGTH];
    private Scanner scanner = new Scanner(System.in);
    private boolean isStopped = false;
    private Random random = new Random();
    //</editor-fold>

    //<editor-fold desc="constructor">
    public ChatNode(String name, int port, int lossChance, byte[] parentIP, int parentPort) throws IOException {

        hasParent = true;

        this.port = port;
        this.parentPort = parentPort;
        this.name = name;
        parentAddress = InetAddress.getByAddress(parentIP);
        connections = new ArrayList<Connection>();
        pendingMessages = new ArrayList<PendingMessage>();

        initSocket(port);
        initLossChance(lossChance);
        SendParentConnectionRequest();

    }

    private void SendParentConnectionRequest() throws IOException {
        String connectionRequest = MessageType.CONN + ":" + port + "::";

        DatagramPacket packet = new DatagramPacket(connectionRequest.getBytes(), connectionRequest.length(), parentAddress, parentPort);

        socket.send(packet);
    }

    public ChatNode(String name, int port, int lossChance) throws SocketException, UnknownHostException {
        hasParent = false;
        this.name = name;
        connections = new ArrayList<Connection>();
        pendingMessages = new ArrayList<PendingMessage>();
        initSocket(port);
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

            if(!receivedMessage.contains("::")) return;

            //int lastOccur = receivedMessage.lastIndexOf("::");

            //receivedMessage = receivedMessage.substring(0, lastOccur);

            receivedMessage = receivedMessage.split("\\::")[0];

            String[] split = receivedMessage.split("\\:", 2);

            MessageType messageType = MessageType.valueOf(split[0]);

            receivedMessage = split[1];

            switch (messageType) {
                case CONF:
                    System.out.println("CONF: " + receivedMessage);
                    HandleConfirmationMessage(receivedMessage);
                    break;
                case TXT:
                    Double rand = random.nextDouble();
                    //System.out.println(rand + "<" + lossChance);
                    if(rand < lossChance) break;
                    HandleMessage(receivedMessage, packet.getAddress(), packet.getPort());
                    break;
                case CONN:
                    System.out.println("CONN: " + packet.getAddress() + " " + Integer.parseInt(receivedMessage));
                    connections.add(new Connection(packet.getAddress(), Integer.parseInt(receivedMessage)));
                    break;
                case ALT:
                    fillAlternativeNodeAddress(receivedMessage);
                    break;
                default:
                    System.out.println(receivedMessage + " " + messageType);
                    break;
            }


            /*
            packet.getData();

            UUID uuid = UUID.randomUUID();
            String confirmationMessage = name + " " + uuid.toString() + " " + split[1];

            System.out.println(packet.getData());
            */
        } catch (IOException e) { /*IGNORE*/ }

        try {

            SendPendingMessages();

            String msg = readStdin();

            if(msg.isEmpty()) return;

            if(msg.equals("stop")) {
                System.out.println("Stopping...");
                stop();
                return;
            }

            SendMessageToAllNeighbors(MessageType.TXT, msg);

        } catch (IOException e) {
            System.out.println("Failed to read from stdin!");
        }
    }

    private void HandleConfirmationMessage(String receivedMessage) {
        int uuidIndex = receivedMessage.lastIndexOf(":");
        String uuid = receivedMessage.substring(uuidIndex + 1);
        for (PendingMessage msg: pendingMessages) {
            if(msg.uuid.equals(uuid)) {
                pendingMessages.remove(msg);
                break;
            }
        }
    }

    private void HandleMessage(String receivedMessage, InetAddress address, int port) throws IOException {

        int uuidIndex = receivedMessage.lastIndexOf(":");

        String uuid = receivedMessage.substring(uuidIndex + 1);
        String msg = receivedMessage.substring(0, uuidIndex);

        String confMessage = "CONF:" + uuid + "::";

        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        packet.setData(confMessage.getBytes());
        socket.send(packet);

        System.out.println("MSG: " + msg);
    }

    private void fillAlternativeNodeAddress(String receivedMessage) throws UnknownHostException {
        String[] split;
        split = receivedMessage.split("\\:");
        alternativeParentPort = Integer.parseInt(split[1]);
        byte[] addr = new byte[4];
        String[] strAddr = split[0].split("\\.");
        for (int i = 0; i < 4; i++) {
            addr[i] = Byte.parseByte(strAddr[i]);
        }
        alternativeParentAddress = InetAddress.getByAddress(addr);
    }

    private void SendMessageToAllNeighbors(MessageType messageType, String msg) throws IOException {

        msg = msg.replaceAll("\\:\\:", "[UNKNOWN_SYMBOL]");

        msg = messageType + ":" + msg;

        if(messageType == MessageType.TXT) {
            UUID uuid = UUID.randomUUID();
            msg +=  ":" + uuid.toString();
            pendingMessages.add(new PendingMessage(msg, uuid.toString()));
        }

        DatagramPacket packet;
        msg += "::";

        SendMessageToAllNeighbors(msg);
/*
        if(hasParent){
            packet = new DatagramPacket(buf, buf.length, parentAddress, parentPort);

            System.out.println("Sending \"" + msg + "\" to" + parentAddress + ":" + parentPort);

            packet.setData(msg.getBytes());
            socket.send(packet);
        }

        if(connections.isEmpty()) return;

        for (Connection c : connections) {
            packet = new DatagramPacket(buf, buf.length, c.address, c.port);
            System.out.println("Sending \"" + msg + "\" to" + c.address + ":" + c.port);
            packet.setData(msg.getBytes());
            socket.send(packet);
        }
        */
    }

    private void SendMessageToAllNeighbors(String msg) throws IOException {
/*
        if(messageType == MessageType.TXT) {
            UUID uuid = UUID.randomUUID();
            pendingMessages.add(new PendingMessage(msg, uuid.toString()));
            msg += ":" + uuid.toString();
        }

        msg = messageType + ":" + msg + "::";
*/
        DatagramPacket packet;
        if(hasParent){
            packet = new DatagramPacket(buf, buf.length, parentAddress, parentPort);

            System.out.println("Sending \"" + msg + "\" to " + parentAddress + ":" + parentPort);

            packet.setData(msg.getBytes());
            socket.send(packet);
        }

        if(connections.isEmpty()) return;

        for (Connection c : connections) {
            packet = new DatagramPacket(buf, buf.length, c.address, c.port);
            System.out.println("Sending \"" + msg + "\" to" + c.address + ":" + c.port);
            packet.setData(msg.getBytes());
            socket.send(packet);
        }
    }

    private void SendPendingMessages() throws IOException {
        if(pendingMessages.isEmpty()) return;

        for (PendingMessage msg : pendingMessages) {
            SendMessageToAllNeighbors(msg.msg + "::");
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
