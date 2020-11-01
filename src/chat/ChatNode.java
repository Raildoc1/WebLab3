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
        Connection connection;
        int triesAmount = 0;
        public PendingMessage(String msg, String uuid, Connection connection) {
            this.msg = msg;
            this.uuid = uuid;
            this.connection = connection;
        }
    }

    //<editor-fold desc="private fields">
    private static final int TIME_OUT_MILLIS = 1000;
    private static final int MAX_MSG_LENGTH = 256;
    private static final int MAX_TRIES_AMOUNT = 5;

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
    private boolean hasAlternative = false;
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

            receivedMessage = receivedMessage.split("\\::")[0];

            String[] split = receivedMessage.split("\\:", 2);

            MessageType messageType = MessageType.valueOf(split[0]);

            receivedMessage = split[1];

            switch (messageType) {
                case CONF:
                    System.out.println("CONF: " + receivedMessage);
                    HandleConfirmationMessage(receivedMessage, packet.getAddress(), packet.getPort());
                    break;
                case TXT:
                    Double rand = random.nextDouble();
                    //System.out.println(rand + "<" + lossChance);
                    if(rand < lossChance) break;
                    HandleMessage(receivedMessage, packet.getAddress(), packet.getPort());
                    break;
                case CONN:
                    HandleConnectionRequest(receivedMessage, packet.getAddress());
                    break;
                case ALT:
                    fillAlternativeNodeAddress(receivedMessage);
                    break;
                default:
                    System.out.println(receivedMessage + " " + messageType);
                    break;
            }

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

    private void HandleConnectionRequest(String receivedMessage, InetAddress address) throws IOException {

        int port = Integer.parseInt(receivedMessage);

        System.out.println("CONN: " + address + " " + port);
        connections.add(new Connection(address, port));

        String altMessage;

        if(!hasParent) {

            if(connections.size() < 2) return;

            if(connections.get(0).address.equals(address) && connections.get(0).port == port) return;

            altMessage = "ALT:" + connections.get(0).address.toString().substring(1) + ":" + connections.get(0).port + "::";

        } else {

            altMessage = "ALT:" + parentAddress.toString().substring(1) + ":" + parentPort + "::";

        }

        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        packet.setData(altMessage.getBytes());
        socket.send(packet);

    }

    private void HandleConfirmationMessage(String receivedMessage, InetAddress address, int port) {
        int uuidIndex = receivedMessage.lastIndexOf(":");
        String uuid = receivedMessage.substring(uuidIndex + 1);
        for (PendingMessage msg: pendingMessages) {
            if(msg.uuid.equals(uuid) && msg.connection.address.equals(address) && (msg.connection.port == port)) {
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

        System.out.println("SendMessageToAllNeighborsBut(" + port + ")");

        SendMessageToAllNeighborsBut(MessageType.TXT, msg, address, port);

        System.out.println("MSG: " + msg);
    }

    private void fillAlternativeNodeAddress(String receivedMessage) throws UnknownHostException {

        hasAlternative = true;

        System.out.println("ALT: " + receivedMessage);

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

            if(hasParent) {
                pendingMessages.add(new PendingMessage(msg, uuid.toString(), new Connection(parentAddress, parentPort)));
            }
            if(!connections.isEmpty()) {
                for (Connection c : connections) {
                    pendingMessages.add(new PendingMessage(msg, uuid.toString(), new Connection(c.address, c.port)));
                }
            }
        }

        DatagramPacket packet;
        msg += "::";

        SendMessageToAllNeighbors(msg);

    }

    private void SendMessageToAllNeighborsBut(MessageType messageType, String msg, InetAddress address, int port) throws IOException {

        msg = msg.replaceAll("\\:\\:", "[UNKNOWN_SYMBOL]");

        msg = messageType + ":" + msg;

        if(messageType == MessageType.TXT) {
            UUID uuid = UUID.randomUUID();
            msg +=  ":" + uuid.toString();

            if(hasParent && ((!parentAddress.equals(address)) || (parentPort != port))) {
                System.out.println(parentAddress + " != " + address);
                System.out.println(parentPort + " != " + port);
                pendingMessages.add(new PendingMessage(msg, uuid.toString(), new Connection(parentAddress, parentPort)));
            }
            if(!connections.isEmpty()) {
                for (Connection c : connections) {
                    if(c.address.equals(address) && (c.port == port)) continue;
                    System.out.println(parentAddress + " != " + address);
                    System.out.println(parentPort + " != " + port);
                    pendingMessages.add(new PendingMessage(msg, uuid.toString(), new Connection(c.address, c.port)));
                }
            }
        }

        DatagramPacket packet;
        msg += "::";

        SendMessageToAllNeighborsBut(msg, address, port);

    }

    private void SendMessageToAllNeighbors(String msg) throws IOException {

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
            System.out.println("Sending \"" + msg + "\" to " + c.address + ":" + c.port);
            packet.setData(msg.getBytes());
            socket.send(packet);
        }
    }

    private void SendMessageToAllNeighborsBut(String msg, InetAddress address, int port) throws IOException {

        DatagramPacket packet;
        if(hasParent && ((!parentAddress.equals(address)) || (parentPort != port))){
            System.out.println(parentAddress + " != " + address);
            System.out.println(parentPort + " != " + port);
            packet = new DatagramPacket(buf, buf.length, parentAddress, parentPort);

            System.out.println("Sending \"" + msg + "\" to " + parentAddress + ":" + parentPort);

            packet.setData(msg.getBytes());
            socket.send(packet);
        }

        if(connections.isEmpty()) return;

        for (Connection c : connections) {

            if(c.address.equals(address) && (c.port == port)) continue;

            System.out.println(parentAddress + " != " + address);
            System.out.println(parentPort + " != " + port);

            packet = new DatagramPacket(buf, buf.length, c.address, c.port);
            System.out.println("Sending \"" + msg + "\" to" + c.address + ":" + c.port);
            packet.setData(msg.getBytes());
            socket.send(packet);
        }
    }

    private void SendPendingMessages() throws IOException {
        if(pendingMessages.isEmpty()) return;

        for (PendingMessage msg : pendingMessages) {
            //SendMessageToAllNeighbors(msg.msg + "::");
            DatagramPacket packet = new DatagramPacket(buf, buf.length, msg.connection.address, msg.connection.port);
            packet.setData((msg.msg + "::").getBytes());
            socket.send(packet);
            System.out.println("Resending \"" + msg.msg + "::" + "\" to " + parentAddress + ":" + parentPort + " " + (msg.triesAmount + 1) + " of " + MAX_TRIES_AMOUNT);
            if(++(msg.triesAmount) > MAX_TRIES_AMOUNT) {
                if(msg.connection.port == parentPort && msg.connection.address.equals(parentAddress)) {
                    System.out.println("hasAlternative = " + hasAlternative);
                    if(hasAlternative) {
                        parentAddress = alternativeParentAddress;
                        parentPort = alternativeParentPort;
                        hasAlternative = false;
                        SendParentConnectionRequest();

                        msg.connection.port = parentPort;
                        msg.connection.address = parentAddress;

                        msg.triesAmount = 0;
                    } else {
                        hasParent = false;
                        pendingMessages.remove(msg);
                        break;
                    }
                } else {
                    System.out.println(msg.connection.port + " != " + parentPort);
                    System.out.println(msg.connection.address + " != " + parentAddress);
                    for (Connection c : connections ) {
                        if(c.address.equals(msg.connection.address) && (c.port == msg.connection.port)) {
                            connections.remove(c);
                            break;
                        }
                    }
                    pendingMessages.remove(msg);
                    break;
                }
            }
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
