import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * @author Lx
 * @version 1.1
 * @since 2025-04-05
 */

public class Client {
    // 网络相关
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ObjectOutputStream objectOut;
    private ObjectInputStream objectIn;
    private String serverAddress = "127.0.0.1";
    private int port = 12345;
    private String username;

    // 界面组件
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField messageField;
    private JList<String> userList;
    private JList<String> groupList;
    private DefaultListModel<String> userListModel;
    private DefaultListModel<String> groupListModel;
    private JTabbedPane rightPanel;
    private JButton sendButton;
    private JButton fileButton;

    // 当前聊天模式
    private String currentChatMode = "PUBLIC"; // PUBLIC, PRIVATE, GROUP
    private String currentChatTarget = ""; // 私聊对象或群组名称

    // 文件传输相关
    private Map<String, FileTransferInfo> pendingFileTransfers = new HashMap<>(); 

    // 主方法
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client().initUI());
    }

    // 初始化界面
    private void initUI() {
        // 创建主窗口
        frame = new JFrame("聊天客户端");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // 创建聊天区域
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // 创建用户列表和群组列表
        userListModel = new DefaultListModel<>();
        groupListModel = new DefaultListModel<>();

        // 组装主界面
        frame.add(chatScrollPane, BorderLayout.CENTER);
        frame.add(MainPanel("right"), BorderLayout.EAST);
        frame.add(MainPanel("bottom"), BorderLayout.SOUTH);

        // 显示登录对话框
        LoginPanel();
    }

    // 登录对话框
    private void LoginPanel() {
        JDialog loginDialog = new JDialog(frame, "登录", true);
        loginDialog.setSize(200, 160);
        
        loginDialog.setLayout(new BorderLayout(10, 10));
        
        // 创建输入面板
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
            
        JTextField serverField = new JTextField(serverAddress);
        JTextField usernameField = new JTextField();
        JButton loginButton = new JButton("登录");
            
        loginButton.addActionListener(e -> {
            serverAddress = serverField.getText().trim();
            if (serverAddress.isEmpty()) serverAddress = "127.0.0.1";
            
            username = usernameField.getText().trim();
            if (!username.isEmpty()) {
                loginDialog.dispose();
                connectToServer();
            } else {
                JOptionPane.showMessageDialog(loginDialog, "用户名不能为空！");
            }
        });
        
        // 添加输入字段到输入面板
        inputPanel.add(new JLabel("服务器IP地址:"));
        inputPanel.add(serverField);
        inputPanel.add(new JLabel("用户名ID:"));
        inputPanel.add(usernameField);
     
        // 创建按钮面板并居中
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(loginButton);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        
        // 将面板添加到对话框
        loginDialog.add(inputPanel, BorderLayout.CENTER);
        loginDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        loginDialog.setLocationRelativeTo(null);
        loginDialog.setVisible(true);
    }

    // 创建主聊天面板
    private JPanel MainPanel(String panelType) {
        if (panelType.equals("right")) {
            // 创建右侧面板
            JPanel rightContainer = new JPanel(new BorderLayout());
            
            // 创建用户列表
            userList = new JList<>(userListModel);
            userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            userList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 1) {
                        String selectedUser = userList.getSelectedValue();
                        if (selectedUser != null && !selectedUser.equals(username)) {
                            switchChatMode("PRIVATE", selectedUser);
                        }
                    }
                }
            });
            JScrollPane userScrollPane = new JScrollPane(userList);
            userScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // 创建群组列表
            groupList = new JList<>(groupListModel);
            groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            groupList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 1 && groupList.getSelectedValue() != null) {
                        switchChatMode("GROUP", groupList.getSelectedValue());
                    }
                }
            });
            JScrollPane groupScrollPane = new JScrollPane(groupList);
            groupScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // 创建创建群组按钮
            JButton createGroupButton = new JButton("创建群组");
            createGroupButton.addActionListener(e -> createGroupPanel());
            JPanel createGroupPanel = new JPanel(new BorderLayout());
            createGroupPanel.add(groupScrollPane, BorderLayout.CENTER);
            createGroupPanel.add(createGroupButton, BorderLayout.SOUTH);
            createGroupPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // 创建公共聊天按钮面板
            JPanel publicChatPanel = new JPanel(new BorderLayout());
            JButton publicChatButton = new JButton("公共广播");
            publicChatButton.addActionListener(e -> switchChatMode("PUBLIC", ""));
            publicChatPanel.add(publicChatButton, BorderLayout.CENTER);
            publicChatPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // 创建右侧选项卡面板
            rightPanel = new JTabbedPane();
            rightPanel.addTab("在线用户", userScrollPane);
            rightPanel.addTab("公共聊天", publicChatPanel);
            rightPanel.addTab("群组列表", createGroupPanel);

            // 设置选项卡切换事件
            rightPanel.addChangeListener(e -> {
                if (rightPanel.getSelectedIndex() == 1) { // 公共聊天选项卡
                    switchChatMode("PUBLIC", "");
                }
            });

            // 设置右侧面板大小
            rightContainer.add(rightPanel, BorderLayout.CENTER);
            rightContainer.setPreferredSize(new Dimension(250, frame.getHeight()));
            rightContainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            return rightContainer;
            
        } else if (panelType.equals("bottom")) {
            // 创建底部面板
            JPanel bottomPanel = new JPanel(new BorderLayout());

            // 创建消息输入框
            messageField = new JTextField();
            messageField.addActionListener(e -> sendMessage());

            // 创建发送按钮和文件按钮
            sendButton = new JButton("发送");
            sendButton.addActionListener(e -> sendMessage());
            fileButton = new JButton("发送文件");
            fileButton.addActionListener(e -> sendFile("REQUEST"));
            fileButton.setEnabled(false); // 初始禁用，只有私聊时才启用

            // 组装按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(fileButton);
            buttonPanel.add(sendButton);

            // 组装底部面板
            bottomPanel.add(messageField, BorderLayout.CENTER);
            bottomPanel.add(buttonPanel, BorderLayout.EAST);

            return bottomPanel;
        }
        return new JPanel(); // 默认返回空面板
    }

    // 显示弹出创建群组窗口
    private void createGroupPanel() {
        JDialog dialog = new JDialog(frame, "创建群组", true);
        dialog.setSize(200, 300);
        dialog.setLayout(new BorderLayout());

        JTextField nameField = new JTextField();
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JLabel("群组名称:"), BorderLayout.WEST);
        topPanel.add(nameField, BorderLayout.CENTER);

        // 创建用户选择列表
        JList<String> memberList = new JList<>(userListModel);
        memberList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel buttonPanel = getJPanel(nameField, dialog, memberList);

        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(new JLabel("选择成员:"), BorderLayout.WEST);
        dialog.add(new JScrollPane(memberList), BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private JPanel getJPanel(JTextField nameField, JDialog dialog, JList<String> memberList) {
        JButton createButton = new JButton("创建");
        createButton.addActionListener(e -> {
            String groupName = nameField.getText().trim();
            if (groupName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "群组名称不能为空！");
                return;
            }

            List<String> selectedMembers = memberList.getSelectedValuesList();
            if (selectedMembers.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请至少选择一个成员！");
                return;
            }

            // 构建成员列表字符串
            StringBuilder memberStr = new StringBuilder();
            for (String member : selectedMembers) {
                if (!member.equals(username)) { // 排除自己
                    memberStr.append(member).append(",");
                }
            }

            // 发送创建群组请求
            out.println("CREATE_GROUP:" + groupName + ":" + memberStr.toString());
            dialog.dispose();
        });

        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(createButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }

    // 连接到服务器
    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()), 8192);
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()), 8192), true);
            objectOut = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream(), 8192));
            objectOut.flush(); // 确保对象头信息被写入
            objectIn = new ObjectInputStream(new BufferedInputStream(socket.getInputStream(), 8192));

            // 主动发送用户名到服务器
            out.println("LOGIN:" + username);

            // 启动接收消息的线程
            new Thread(new IncomingReader()).start();

            // 设置窗口标题
            frame.setTitle("聊天客户端 - " + username + " (" + serverAddress + ":" + port + ")");
            frame.setVisible(true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "无法连接到服务器: " + e.getMessage());
            System.exit(1);
        }
    }

    // 发送消息
    private void sendMessage() {
        String message = messageField.getText();
        if (message.isEmpty()) return;

        switch (currentChatMode) {
            case "PUBLIC":
                out.println(message);
                break;
            case "PRIVATE":
                out.println("PRIVATE:" + currentChatTarget + ":" + message);
                break;
            case "GROUP":
                out.println("GROUP:" + currentChatTarget + ":" + message);
                break;
        }
        messageField.setText("");
    }

    // 切换聊天模式
    private void switchChatMode(String mode, String target) {
        currentChatMode = mode;
        currentChatTarget = target;
        boolean enableFileButton = mode.equals("PRIVATE");

        // 更新文件按钮状态
        fileButton.setEnabled(enableFileButton);

        // 提示当前聊天模式
        String modeText = mode.equals("PUBLIC") ? "公共聊天" :
                mode.equals("PRIVATE") ? "与 " + target + " 的私聊" :
                        "群组 " + target + " 聊天";
        chatArea.append("系统: 已切换到" + modeText + "\n");
    }

    // 接收服务器消息的线程
    private class IncomingReader implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    processServerMessage(message);
                }
            } catch (IOException e) {
                chatArea.append("系统: 与服务器的连接已断开: " + e.getMessage() + "\n");
            }
        }

        // 处理服务器消息
        private void processServerMessage(String message) throws IOException {
            if (message.startsWith("LOGIN_REQUEST")) {
                out.println("LOGIN:" + username);  // 修改为统一的登录消息格式
            } else if (message.startsWith("LOGIN_FAILED:")) {
                String errorMsg = message.substring("LOGIN_FAILED:".length());
                JOptionPane.showMessageDialog(frame, errorMsg);
                socket.close();
                System.exit(1);
            } else if (message.startsWith("LOGIN_SUCCESS")) {
                chatArea.append("系统: 登录成功！\n");
            } else if (message.startsWith("USER_LIST:")) {
                updateList("USER", message.substring("USER_LIST:".length()));
            } else if (message.startsWith("GROUP_LIST:")) {
                updateList("GROUP", message.substring("GROUP_LIST:".length()));
            } else if (message.startsWith("BROADCAST:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("[公共] " + parts[1] + ": " + parts[2] + "\n");
                }
            } else if (message.startsWith("PRIVATE:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("[私聊] " + parts[1] + ": " + parts[2] + "\n");
                }
            } else if (message.startsWith("PRIVATE_SENT:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("[私聊] 我 -> " + parts[1] + ": " + parts[2] + "\n");
                }
            } else if (message.startsWith("GROUP:")) {
                String[] parts = message.split(":", 4);
                if (parts.length == 4) {
                    chatArea.append("[群聊:" + parts[1] + "] " + parts[2] + ": " + parts[3] + "\n");
                }
            } else if (message.startsWith("SYSTEM:")) {
                chatArea.append(message.substring("SYSTEM:".length()) + "\n");
            } else if (message.startsWith("FILE_REQUEST:")) {
                String[] parts = message.split(":", 4);
                if (parts.length == 4) {
                    handleFileRequest(parts[1], parts[2], Long.parseLong(parts[3]));
                }
            } else if (message.startsWith("FILE_ACCEPT:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("系统: " + parts[1] + " 已接受文件传输: " + parts[2] + "\n");
                }
            } else if (message.startsWith("FILE_REJECT:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("系统: " + parts[1] + " 已拒绝文件传输: " + parts[2] + "\n");
                    pendingFileTransfers.remove(parts[2]);
                }
            } else if (message.startsWith("FILE_TRANSFER_START:")) {
                String[] parts = message.split(":", 5);
                if (parts.length == 5) {
                    sendFile("SEND", parts[1], parts[2], parts[3], Integer.parseInt(parts[4]));
                }
            } else if (message.startsWith("FILE_READY:")) {
                String[] parts = message.split(":", 4);
                if (parts.length == 4) {
                    // 通知服务器开始传输
                    out.println("FILE_TRANSFER_START:" + parts[1] + ":" + parts[2] + ":" + parts[3]);
                }
            }
        }
    }

    // 更新用户和群列表
    private void updateList(String listType, String listStr) {
        if (listType.equals("USER")) {
            // 更新用户列表
            userListModel.clear();
            String[] users = listStr.split(",");
            for (String user : users) {
                if (!user.isEmpty() && !user.equals(username)) {
                    userListModel.addElement(user);
                }
            }
        } else if (listType.equals("GROUP")) {
            // 更新群组列表
            groupListModel.clear();
            if (listStr.isEmpty()) return;

            String[] groups = listStr.split(";");
            for (String group : groups) {
                if (group.isEmpty()) continue;

                String[] parts = group.split(":", 2);
                if (parts.length <= 0 || parts[0].isEmpty()) continue;

                // 检查群组成员信息
                if (parts.length > 1) {
                    String[] members = parts[1].split(",");
                    // 只添加当前用户是成员的群组
                    for (String member : members) {
                        if (member.trim().equals(username)) {
                            groupListModel.addElement(parts[0]);
                            break;
                        }
                    }
                }
            }
        }
    }

    // 文件传输信息类
    private class FileTransferInfo {
        File file;
        String receiver;

        public FileTransferInfo(File file, String receiver) {
            this.file = file;
            this.receiver = receiver;
        }
    }

    // 处理文件传输
    private void sendFile(String action, Object... params) {
        if (action.equals("REQUEST")) {
            // 发送文件请求
            if (!currentChatMode.equals("PRIVATE")) {
                JOptionPane.showMessageDialog(frame, "只能在私聊中发送文件！");
                return;
            }

            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;

            File selectedFile = fileChooser.getSelectedFile();
            String filename = selectedFile.getName();
            long filesize = selectedFile.length();

            // 发送文件传输请求
            out.println("FILE_REQUEST:" + currentChatTarget + ":" + filename + ":" + filesize);

            // 保存文件信息，等待对方接受
            pendingFileTransfers.put(filename, new FileTransferInfo(selectedFile, currentChatTarget));

            chatArea.append("系统: 已向 " + currentChatTarget + " 发送文件传输请求: " + filename + " (" + filesize + " 字节)\n");
        } else if (action.equals("SEND")) {
            // 发送文件数据
            String receiver = (String) params[0];
            String filename = (String) params[1];
            String receiverIP = (String) params[2];
            int receiverPort = (Integer) params[3];
            
            FileTransferInfo fileInfo = pendingFileTransfers.get(filename);
            if (fileInfo == null) return;

            try {
                File file = fileInfo.file;
                chatArea.append("系统: 正在发送文件: " + filename + " 给 " + receiver + "\n");

                // 创建专用于文件传输的Socket连接
                Socket fileSocket = new Socket(receiverIP, receiverPort);
                BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));
                DataOutputStream dataOut = new DataOutputStream(new BufferedOutputStream(fileSocket.getOutputStream()));

                // 发送文件大小
                dataOut.writeLong(file.length());

                // 发送文件内容
                byte[] buffer = new byte[8192]; // 增大缓冲区大小
                int bytesRead;

                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }

                dataOut.flush();
                fileIn.close();
                dataOut.close();
                fileSocket.close();

                chatArea.append("系统: 文件发送完成: " + filename + "\n");
                pendingFileTransfers.remove(filename);

            } catch (Exception e) {
                chatArea.append("系统: 文件发送失败: " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        }
    }

    // 处理文件传输请求
    private void handleFileRequest(String sender, String filename, long filesize) {
        int option = JOptionPane.showConfirmDialog(
                frame,
                sender + " 想要发送文件: " + filename + " (" + filesize + " 字节)\n是否接收？",
                "文件传输请求",
                JOptionPane.YES_NO_OPTION
        );

        if (option != JOptionPane.YES_OPTION) {
            // 拒绝文件传输
            out.println("FILE_REJECT:" + sender + ":" + filename);
            return;
        }

        // 选择保存位置
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(filename));
        if (fileChooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            // 用户取消了保存对话框
            out.println("FILE_REJECT:" + sender + ":" + filename);
            return;
        }

        // 接受文件传输
        out.println("FILE_ACCEPT:" + sender + ":" + filename);

        // 启动文件接收线程
        new Thread(() -> receiveFile(sender, filename, fileChooser.getSelectedFile())).start();
    }

    // 接收文件
    private void receiveFile(String sender, String filename, File saveFile) {
        try {
            chatArea.append("系统: 正在接收来自 " + sender + " 的文件: " + filename + "\n");

            // 创建文件服务器Socket等待发送方连接
            ServerSocket fileServerSocket = new ServerSocket(0);
            int filePort = fileServerSocket.getLocalPort();

            // 告知服务器我们准备好接收文件，并提供端口信息
            out.println("FILE_READY:" + sender + ":" + filename + ":" + filePort);

            // 等待发送方连接
            Socket fileSocket = fileServerSocket.accept();
            DataInputStream dataIn = new DataInputStream(new BufferedInputStream(fileSocket.getInputStream()));
            BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(saveFile));

            // 读取文件大小
            long fileSize = dataIn.readLong();

            // 读取文件内容
            byte[] buffer = new byte[8192]; // 增大缓冲区大小
            int bytesRead;
            long totalBytesRead = 0;

            while (totalBytesRead < fileSize &&
                    (bytesRead = dataIn.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            fileOut.flush(); // 确保所有数据都写入文件
            fileOut.close();
            dataIn.close();
            fileSocket.close();
            fileServerSocket.close();

            chatArea.append("系统: 文件接收完成: " + saveFile.getAbsolutePath() + "\n");

        } catch (Exception e) {
            chatArea.append("系统: 文件接收失败: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}