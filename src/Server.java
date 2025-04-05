import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 聊天客户端应用程序
 * 
 * @author Lx
 * @version 1.0.0
 * @since 2025-04-05
 */


public class Server {
    private static final int PORT = 12345;
    // 存储客户端的用户名和输出流
    private static final Map<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    // 存储客户端的Socket连接，用于文件传输
    private static final Map<String, Socket> clientSockets = new ConcurrentHashMap<>();
    // 存储群组信息，群组名称和成员列表
    private static final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("服务器启动，等待客户端连接...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();  // 接受客户端连接并处理
            }
        }
    }

    // 每个客户端对应一个线程
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
                // 创建输入输出流
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                objectOut = new ObjectOutputStream(socket.getOutputStream());
                objectIn = new ObjectInputStream(socket.getInputStream());

                // 处理用户登录
                handleLogin();

                // 发送当前在线用户列表和群组列表
                sendUserList();
                sendGroupList();

                // 广播欢迎消息
                broadcastMessage("SYSTEM:" + clientName + " 已加入聊天！");

                // 处理客户端消息
                processClientMessages();

            } catch (IOException e) {
                System.out.println("客户端连接异常: " + e.getMessage());
            } finally {
                handleClientDisconnect();
            }
        }

        // 处理用户登录
        private void handleLogin() throws IOException {
            while (true) {
                out.println("LOGIN_REQUEST");
                clientName = in.readLine();

                synchronized (clientWriters) {
                    if (clientWriters.containsKey(clientName)) {
                        out.println("LOGIN_FAILED:用户名已被使用，请重新选择一个用户名！");
                    } else {
                        out.println("LOGIN_SUCCESS");
                        clientWriters.put(clientName, out);
                        clientSockets.put(clientName, socket);
                        return;
                    }
                }
            }
        }

        // 处理客户端消息
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
                        // 普通广播消息
                        broadcastMessage("BROADCAST:" + clientName + ":" + message);
                }
            }
        }

        // 处理客户端断开连接
        private void handleClientDisconnect() {
            if (clientName != null) {
                synchronized (clientWriters) {
                    clientWriters.remove(clientName);
                    clientSockets.remove(clientName);
                }

                // 从所有群组中移除该用户
                for (Set<String> members : groups.values()) {
                    members.remove(clientName);
                }

                // 发送更新后的用户列表
                sendUserList();

                // 广播用户离开消息
                broadcastMessage("SYSTEM:" + clientName + " 已离开聊天！");
            }

            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("关闭socket异常: " + e.getMessage());
            }
        }

        // 广播消息给所有客户端
        private void broadcastMessage(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters.values()) {
                    writer.println(message);
                }
            }
        }

        // 发送私聊消息给指定用户
        private void sendPrivateMessage(String recipient, String message) {
            PrintWriter recipientWriter = clientWriters.get(recipient);
            if (recipientWriter != null) {
                recipientWriter.println("PRIVATE:" + clientName + ":" + message);
                out.println("PRIVATE_SENT:" + recipient + ":" + message); // 告知发送方私聊已发送
            } else {
                out.println("SYSTEM:用户 " + recipient + " 不在线！");
            }
        }

        // 发送群聊消息
        private void sendGroupMessage(String groupName, String message) {
            Set<String> members = groups.get(groupName);
            if (members == null) {
                out.println("SYSTEM:群组 " + groupName + " 不存在！");
                return;
            }

            if (!members.contains(clientName)) {
                out.println("SYSTEM:您不是群组 " + groupName + " 的成员！");
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

        // 创建新群组
        private void createGroup(String groupName, String memberList) {
            if (groups.containsKey(groupName)) {
                out.println("SYSTEM:群组名 " + groupName + " 已存在！");
                return;
            }

            Set<String> members = new HashSet<>();
            members.add(clientName); // 创建者自动加入群组

            for (String member : memberList.split(",")) {
                if (!member.isEmpty() && clientWriters.containsKey(member)) {
                    members.add(member);
                }
            }

            groups.put(groupName, members);

            // 通知所有群组成员
            String notification = "SYSTEM:您已被添加到群组 " + groupName;
            for (String member : members) {
                PrintWriter memberWriter = clientWriters.get(member);
                if (memberWriter != null) {
                    memberWriter.println(notification);
                }
            }

            // 更新所有客户端的群组列表
            sendGroupList();
        }

        // 发送在线用户列表
        private void sendUserList() {
            StringBuilder userList = new StringBuilder("USER_LIST:");
            synchronized (clientWriters) {
                for (String user : clientWriters.keySet()) {
                    userList.append(user).append(",");
                }
            }
            broadcastMessage(userList.toString());
        }

        // 发送群组列表
        private void sendGroupList() {
            synchronized (clientWriters) {
                for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
                    String user = entry.getKey();
                    PrintWriter writer = entry.getValue();

                    StringBuilder groupList = new StringBuilder("GROUP_LIST:");
                    for (Map.Entry<String, Set<String>> groupEntry : groups.entrySet()) {
                        String groupName = groupEntry.getKey();
                        Set<String> members = groupEntry.getValue();

                        // 只有当用户是群组成员时，才添加该群组到列表
                        if (members.contains(user)) {
                            groupList.append(groupName).append(":");
                            for (String member : members) {
                                groupList.append(member).append(",");
                            }
                            groupList.append(";");
                        }
                    }

                    // 发送个性化的群组列表给每个用户
                    writer.println(groupList.toString());
                }
            }
        }

        // 处理文件传输请求
        private void handleFileRequest(String receiver, String filename, String filesize) {
            PrintWriter receiverWriter = clientWriters.get(receiver);
            if (receiverWriter != null) {
                receiverWriter.println("FILE_REQUEST:" + clientName + ":" + filename + ":" + filesize);
            } else {
                out.println("SYSTEM:用户 " + receiver + " 不在线，无法发送文件！");
            }
        }

        // 处理接受文件传输
        private void handleFileAccept(String sender, String filename) {
            PrintWriter senderWriter = clientWriters.get(sender);
            if (senderWriter == null) return;

            senderWriter.println("FILE_ACCEPT:" + clientName + ":" + filename);

            // 启动文件传输线程
            new Thread(() -> {
                try {
                    Socket senderSocket = clientSockets.get(sender);
                    Socket receiverSocket = clientSockets.get(clientName);

                    if (senderSocket != null && receiverSocket != null) {
                        // 为文件传输创建一个临时端口
                        ServerSocket fileTransferServer = new ServerSocket(0);
                        int filePort = fileTransferServer.getLocalPort();

                        // 通知发送方开始传输，并提供接收方的连接信息
                        senderWriter.println("FILE_TRANSFER_START:" + clientName + ":" + filename +
                                ":" + receiverSocket.getInetAddress().getHostAddress() +
                                ":" + filePort);

                        // 等待文件传输完成
                        fileTransferServer.close();
                    }
                } catch (Exception e) {
                    System.out.println("文件传输异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }

        // 处理拒绝文件传输
        private void handleFileReject(String sender, String filename) {
            PrintWriter senderWriter = clientWriters.get(sender);
            if (senderWriter != null) {
                senderWriter.println("FILE_REJECT:" + clientName + ":" + filename);
            }
        }

        // 处理接收方准备好接收文件
        private void handleFileReady(String sender, String filename, String port) {
            PrintWriter senderWriter = clientWriters.get(sender);
            if (senderWriter != null) {
                // 获取接收方的IP地址
                String receiverIP = socket.getInetAddress().getHostAddress();

                // 通知发送方开始传输
                senderWriter.println("FILE_TRANSFER_START:" + clientName + ":" + filename + ":" + receiverIP + ":" + port);
                System.out.println("文件传输准备就绪: " + sender + " -> " + clientName + ", 文件: " + filename);
            }
        }
    }
}