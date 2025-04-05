import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ����ͻ���Ӧ�ó���
 * 
 * @author Lx
 * @version 1.0.0
 * @since 2025-04-05
 */


public class Server {
    private static final int PORT = 12345;
    // �洢�ͻ��˵��û����������
    private static final Map<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    // �洢�ͻ��˵�Socket���ӣ������ļ�����
    private static final Map<String, Socket> clientSockets = new ConcurrentHashMap<>();
    // �洢Ⱥ����Ϣ��Ⱥ�����ƺͳ�Ա�б�
    private static final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("�������������ȴ��ͻ�������...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();  // ���ܿͻ������Ӳ�����
            }
        }
    }

    // ÿ���ͻ��˶�Ӧһ���߳�
    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;
        private ObjectOutputStream objectOut;
        private ObjectInputStream objectIn;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                // �������������
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                objectOut = new ObjectOutputStream(socket.getOutputStream());
                objectIn = new ObjectInputStream(socket.getInputStream());

                // �����û���¼
                handleLogin();

                // ���͵�ǰ�����û��б��Ⱥ���б�
                sendUserList();
                sendGroupList();

                // �㲥��ӭ��Ϣ
                broadcastMessage("SYSTEM:" + clientName + " �Ѽ������죡");

                // ����ͻ�����Ϣ
                processClientMessages();

            } catch (IOException e) {
                System.out.println("�ͻ��������쳣: " + e.getMessage());
            } finally {
                handleClientDisconnect();
            }
        }

        // �����û���¼
        private void handleLogin() throws IOException {
            while (true) {
                out.println("LOGIN_REQUEST");
                clientName = in.readLine();

                synchronized (clientWriters) {
                    if (clientWriters.containsKey(clientName)) {
                        out.println("LOGIN_FAILED:�û����ѱ�ʹ�ã�������ѡ��һ���û�����");
                    } else {
                        out.println("LOGIN_SUCCESS");
                        clientWriters.put(clientName, out);
                        clientSockets.put(clientName, socket);
                        return;
                    }
                }
            }
        }

        // ����ͻ�����Ϣ
        private void processClientMessages() throws IOException {
            String message;
            while ((message = in.readLine()) != null) {
                String[] parts = message.split(":", -1);

                if (parts.length < 1) continue;

                String command = parts[0];

                switch (command) {
                    case "PRIVATE":
                        if (parts.length >= 3) {
                            sendPrivateMessage(parts[1], parts[2]);
                        }
                        break;
                    case "GROUP":
                        if (parts.length >= 3) {
                            sendGroupMessage(parts[1], parts[2]);
                        }
                        break;
                    case "CREATE_GROUP":
                        if (parts.length >= 3) {
                            createGroup(parts[1], parts[2]);
                        }
                        break;
                    case "FILE_REQUEST":
                        if (parts.length >= 4) {
                            handleFileRequest(parts[1], parts[2], parts[3]);
                        }
                        break;
                    case "FILE_ACCEPT":
                        if (parts.length >= 3) {
                            handleFileAccept(parts[1], parts[2]);
                        }
                        break;
                    case "FILE_REJECT":
                        if (parts.length >= 3) {
                            handleFileReject(parts[1], parts[2]);
                        }
                        break;
                    case "FILE_READY":
                        if (parts.length >= 4) {
                            handleFileReady(parts[1], parts[2], parts[3]);
                        }
                        break;
                    default:
                        // ��ͨ�㲥��Ϣ
                        broadcastMessage("BROADCAST:" + clientName + ":" + message);
                }
            }
        }

        // ����ͻ��˶Ͽ�����
        private void handleClientDisconnect() {
            if (clientName != null) {
                synchronized (clientWriters) {
                    clientWriters.remove(clientName);
                    clientSockets.remove(clientName);
                }

                // ������Ⱥ�����Ƴ����û�
                for (Set<String> members : groups.values()) {
                    members.remove(clientName);
                }

                // ���͸��º���û��б�
                sendUserList();

                // �㲥�û��뿪��Ϣ
                broadcastMessage("SYSTEM:" + clientName + " ���뿪���죡");
            }

            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("�ر�socket�쳣: " + e.getMessage());
            }
        }

        // �㲥��Ϣ�����пͻ���
        private void broadcastMessage(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters.values()) {
                    writer.println(message);
                }
            }
        }

        // ����˽����Ϣ��ָ���û�
        private void sendPrivateMessage(String recipient, String message) {
            PrintWriter recipientWriter = clientWriters.get(recipient);
            if (recipientWriter != null) {
                recipientWriter.println("PRIVATE:" + clientName + ":" + message);
                out.println("PRIVATE_SENT:" + recipient + ":" + message); // ��֪���ͷ�˽���ѷ���
            } else {
                out.println("SYSTEM:�û� " + recipient + " �����ߣ�");
            }
        }

        // ����Ⱥ����Ϣ
        private void sendGroupMessage(String groupName, String message) {
            Set<String> members = groups.get(groupName);
            if (members == null) {
                out.println("SYSTEM:Ⱥ�� " + groupName + " �����ڣ�");
                return;
            }

            if (!members.contains(clientName)) {
                out.println("SYSTEM:������Ⱥ�� " + groupName + " �ĳ�Ա��");
                return;
            }

            String groupMessage = "GROUP:" + groupName + ":" + clientName + ":" + message;
            for (String member : members) {
                PrintWriter memberWriter = clientWriters.get(member);
                if (memberWriter != null) {
                    memberWriter.println(groupMessage);
                }
            }
        }

        // ������Ⱥ��
        private void createGroup(String groupName, String memberList) {
            if (groups.containsKey(groupName)) {
                out.println("SYSTEM:Ⱥ���� " + groupName + " �Ѵ��ڣ�");
                return;
            }

            Set<String> members = new HashSet<>();
            members.add(clientName); // �������Զ�����Ⱥ��

            for (String member : memberList.split(",")) {
                if (!member.isEmpty() && clientWriters.containsKey(member)) {
                    members.add(member);
                }
            }

            groups.put(groupName, members);

            // ֪ͨ����Ⱥ���Ա
            String notification = "SYSTEM:���ѱ���ӵ�Ⱥ�� " + groupName;
            for (String member : members) {
                PrintWriter memberWriter = clientWriters.get(member);
                if (memberWriter != null) {
                    memberWriter.println(notification);
                }
            }

            // �������пͻ��˵�Ⱥ���б�
            sendGroupList();
        }

        // ���������û��б�
        private void sendUserList() {
            StringBuilder userList = new StringBuilder("USER_LIST:");
            synchronized (clientWriters) {
                for (String user : clientWriters.keySet()) {
                    userList.append(user).append(",");
                }
            }
            broadcastMessage(userList.toString());
        }

        // ����Ⱥ���б�
        private void sendGroupList() {
            synchronized (clientWriters) {
                for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
                    String user = entry.getKey();
                    PrintWriter writer = entry.getValue();

                    StringBuilder groupList = new StringBuilder("GROUP_LIST:");
                    for (Map.Entry<String, Set<String>> groupEntry : groups.entrySet()) {
                        String groupName = groupEntry.getKey();
                        Set<String> members = groupEntry.getValue();

                        // ֻ�е��û���Ⱥ���Աʱ������Ӹ�Ⱥ�鵽�б�
                        if (members.contains(user)) {
                            groupList.append(groupName).append(":");
                            for (String member : members) {
                                groupList.append(member).append(",");
                            }
                            groupList.append(";");
                        }
                    }

                    // ���͸��Ի���Ⱥ���б��ÿ���û�
                    writer.println(groupList.toString());
                }
            }
        }

        // �����ļ���������
        private void handleFileRequest(String receiver, String filename, String filesize) {
            PrintWriter receiverWriter = clientWriters.get(receiver);
            if (receiverWriter != null) {
                receiverWriter.println("FILE_REQUEST:" + clientName + ":" + filename + ":" + filesize);
            } else {
                out.println("SYSTEM:�û� " + receiver + " �����ߣ��޷������ļ���");
            }
        }

        // ��������ļ�����
        private void handleFileAccept(String sender, String filename) {
            PrintWriter senderWriter = clientWriters.get(sender);
            if (senderWriter == null) return;

            senderWriter.println("FILE_ACCEPT:" + clientName + ":" + filename);

            // �����ļ������߳�
            new Thread(() -> {
                try {
                    Socket senderSocket = clientSockets.get(sender);
                    Socket receiverSocket = clientSockets.get(clientName);

                    if (senderSocket != null && receiverSocket != null) {
                        // Ϊ�ļ����䴴��һ����ʱ�˿�
                        ServerSocket fileTransferServer = new ServerSocket(0);
                        int filePort = fileTransferServer.getLocalPort();

                        // ֪ͨ���ͷ���ʼ���䣬���ṩ���շ���������Ϣ
                        senderWriter.println("FILE_TRANSFER_START:" + clientName + ":" + filename +
                                ":" + receiverSocket.getInetAddress().getHostAddress() +
                                ":" + filePort);

                        // �ȴ��ļ��������
                        fileTransferServer.close();
                    }
                } catch (Exception e) {
                    System.out.println("�ļ������쳣: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }

        // ����ܾ��ļ�����
        private void handleFileReject(String sender, String filename) {
            PrintWriter senderWriter = clientWriters.get(sender);
            if (senderWriter != null) {
                senderWriter.println("FILE_REJECT:" + clientName + ":" + filename);
            }
        }

        // ������շ�׼���ý����ļ�
        private void handleFileReady(String sender, String filename, String port) {
            PrintWriter senderWriter = clientWriters.get(sender);
            if (senderWriter != null) {
                // ��ȡ���շ���IP��ַ
                String receiverIP = socket.getInetAddress().getHostAddress();

                // ֪ͨ���ͷ���ʼ����
                senderWriter.println("FILE_TRANSFER_START:" + clientName + ":" + filename + ":" + receiverIP + ":" + port);
                System.out.println("�ļ�����׼������: " + sender + " -> " + clientName + ", �ļ�: " + filename);
            }
        }
    }
}