package chat;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {

        // <string : Name> <int : port> <int : loss chance>
        // <string : Name> <int : port> <int : loss chance> <string : parent's ip> <int parent's port>

        if(args.length != 3 && args.length != 5) {
            System.out.println("Wrong arguments!");
            return;
        }

        ChatNode node;
        Scanner scanner = new Scanner(System.in);

        try {
            if(args.length == 3) {
                node = new ChatNode(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            } else {
                byte[] addr = new byte[4];
                String[] strAddr = args[3].split("\\.");

                for (int i = 0; i < 4; i++) {
                    addr[i] = Byte.parseByte(strAddr[i]);
                }

                node = new ChatNode(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), addr, Integer.parseInt(args[4]));
            }

            while (true) {
                if(!node.isStop()) node.tick();
                else break;
            }

        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
