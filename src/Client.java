import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class Client {
    // �������
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ObjectOutputStream objectOut;
    private ObjectInputStream objectIn;
    private String serverAddress = "127.0.0.1";
    private int port = 12345;
    private String username;

    // �������
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

    // ��ǰ����ģʽ
    private String currentChatMode = "PUBLIC"; // PUBLIC, PRIVATE, GROUP
    private String currentChatTarget = ""; // ˽�Ķ����Ⱥ������

    // �ļ��������
    private Map<String, FileTransferInfo> pendingFileTransfers = new HashMap<>();

    // ���캯��
    public Client() {
        initializeUI();
    }

    // ��ʼ������
    private void initializeUI() {
        // ����������
        frame = new JFrame("����ͻ���");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // ������������
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // �����û��б��Ⱥ���б�
        userListModel = new DefaultListModel<>();
        groupListModel = new DefaultListModel<>();

        // ��װ������
        frame.add(chatScrollPane, BorderLayout.CENTER);
        frame.add(createRightPanel(), BorderLayout.EAST);
        frame.add(createBottomPanel(), BorderLayout.SOUTH);

        // ��ʾ��¼�Ի���
        showLoginDialog();
    }

    // �����Ҳ����
    private JPanel createRightPanel() {
        // �����û��б�
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedUser = userList.getSelectedValue();
                    if (selectedUser != null && !selectedUser.equals(username)) {
                        switchToChatMode("PRIVATE", selectedUser);
                    }
                }
            }
        });
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ����Ⱥ���б�
        groupList = new JList<>(groupListModel);
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && groupList.getSelectedValue() != null) {
                    switchToChatMode("GROUP", groupList.getSelectedValue());
                }
            }
        });
        JScrollPane groupScrollPane = new JScrollPane(groupList);
        groupScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ��������Ⱥ�鰴ť
        JButton createGroupButton = new JButton("����Ⱥ��");
        createGroupButton.addActionListener(e -> showCreateGroupDialog());
        JPanel createGroupPanel = new JPanel(new BorderLayout());
        createGroupPanel.add(groupScrollPane, BorderLayout.CENTER);
        createGroupPanel.add(createGroupButton, BorderLayout.SOUTH);
        createGroupPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // �����������찴ť���
        JPanel publicChatPanel = new JPanel(new BorderLayout());
        JButton publicChatButton = new JButton("�����㲥");
        publicChatButton.addActionListener(e -> switchToChatMode("PUBLIC", ""));
        publicChatPanel.add(publicChatButton, BorderLayout.CENTER);
        publicChatPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // �����Ҳ�ѡ����
        rightPanel = new JTabbedPane();
        rightPanel.addTab("�����û�", userScrollPane);
        rightPanel.addTab("��������", publicChatPanel);
        rightPanel.addTab("Ⱥ���б�", createGroupPanel);

        // ����ѡ��л��¼�
        rightPanel.addChangeListener(e -> {
            if (rightPanel.getSelectedIndex() == 1) { // ��������ѡ�
                switchToChatMode("PUBLIC", "");
            }
        });

        // �����Ҳ�����С
        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.add(rightPanel, BorderLayout.CENTER);
        rightContainer.setPreferredSize(new Dimension(250, frame.getHeight()));
        rightContainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        return rightContainer;
    }

    // �����ײ����
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());

        // ������Ϣ�����
        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());

        // �������Ͱ�ť���ļ���ť
        sendButton = new JButton("����");
        sendButton.addActionListener(e -> sendMessage());
        fileButton = new JButton("�����ļ�");
        fileButton.addActionListener(e -> sendFile());
        fileButton.setEnabled(false); // ��ʼ���ã�ֻ��˽��ʱ������

        // ��װ��ť���
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(fileButton);
        buttonPanel.add(sendButton);

        // ��װ�ײ����
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        return bottomPanel;
    }

    // ��ʾ��¼�Ի���
    private void showLoginDialog() {
        JDialog loginDialog = new JDialog(frame, "��¼", true);
        loginDialog.setSize(300, 150);
        loginDialog.setLayout(new GridLayout(3, 2, 5, 5));

        JTextField serverField = new JTextField(serverAddress);
        JTextField usernameField = new JTextField();
        JButton loginButton = new JButton("��¼");
        JButton cancelButton = new JButton("ȡ��");

        loginButton.addActionListener(e -> {
            serverAddress = serverField.getText().trim();
            if (serverAddress.isEmpty()) serverAddress = "127.0.0.1";

            username = usernameField.getText().trim();
            if (!username.isEmpty()) {
                loginDialog.dispose();
                connectToServer();
            } else {
                JOptionPane.showMessageDialog(loginDialog, "�û�������Ϊ�գ�");
            }
        });

        cancelButton.addActionListener(e -> System.exit(0));

        loginDialog.add(new JLabel("��������ַ:"));
        loginDialog.add(serverField);
        loginDialog.add(new JLabel("�û���:"));
        loginDialog.add(usernameField);
        loginDialog.add(loginButton);
        loginDialog.add(cancelButton);

        loginDialog.setLocationRelativeTo(null);
        loginDialog.setVisible(true);
    }

    // ���ӵ�������
    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()), 8192);
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()), 8192), true);
            objectOut = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream(), 8192));
            objectOut.flush(); // ȷ������ͷ��Ϣ��д��
            objectIn = new ObjectInputStream(new BufferedInputStream(socket.getInputStream(), 8192));

            // ����������Ϣ���߳�
            new Thread(new IncomingReader()).start();

            // ���ô��ڱ���
            frame.setTitle("����ͻ��� - " + username + " (" + serverAddress + ":" + port + ")");
            frame.setVisible(true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "�޷����ӵ�������: " + e.getMessage());
            System.exit(1);
        }
    }

    // �л�����ģʽ
    private void switchToChatMode(String mode, String target) {
        currentChatMode = mode;
        currentChatTarget = target;

        // ���´��ڱ�����ļ���ť״̬
        String titleSuffix;
        boolean enableFileButton = false;

        if (mode.equals("PUBLIC")) {
            titleSuffix = " - ��������";
        } else if (mode.equals("PRIVATE")) {
            titleSuffix = " - ˽��: " + target;
            enableFileButton = true;
        } else { // GROUP
            titleSuffix = " - Ⱥ��: " + target;
        }

        frame.setTitle("����ͻ��� - " + username + " (" + serverAddress + ":" + port + ")" + titleSuffix);
        fileButton.setEnabled(enableFileButton);

        // ��ʾ��ǰ����ģʽ
        String modeText = mode.equals("PUBLIC") ? "��������" :
                mode.equals("PRIVATE") ? "�� " + target + " ��˽��" :
                        "Ⱥ�� " + target + " ����";
        chatArea.append("ϵͳ: ���л���" + modeText + "\n");
    }

    // ������Ϣ
    private void sendMessage() {
        String message = messageField.getText();
        if (message.isEmpty()) return;

        if (currentChatMode.equals("PUBLIC")) {
            out.println(message);
        } else if (currentChatMode.equals("PRIVATE")) {
            out.println("PRIVATE:" + currentChatTarget + ":" + message);
        } else if (currentChatMode.equals("GROUP")) {
            out.println("GROUP:" + currentChatTarget + ":" + message);
        }
        messageField.setText("");
    }

    // �����ļ�
    private void sendFile() {
        if (!currentChatMode.equals("PRIVATE")) {
            JOptionPane.showMessageDialog(frame, "ֻ����˽���з����ļ���");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;

        File selectedFile = fileChooser.getSelectedFile();
        String filename = selectedFile.getName();
        long filesize = selectedFile.length();

        // �����ļ���������
        out.println("FILE_REQUEST:" + currentChatTarget + ":" + filename + ":" + filesize);

        // �����ļ���Ϣ���ȴ��Է�����
        pendingFileTransfers.put(filename, new FileTransferInfo(selectedFile, currentChatTarget));

        chatArea.append("ϵͳ: ���� " + currentChatTarget + " �����ļ���������: " + filename + " (" + filesize + " �ֽ�)\n");
    }

    // ��ʾ����Ⱥ��Ի���
    private void showCreateGroupDialog() {
        JDialog dialog = new JDialog(frame, "����Ⱥ��", true);
        dialog.setSize(300, 400);
        dialog.setLayout(new BorderLayout());

        JTextField nameField = new JTextField();
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JLabel("Ⱥ������:"), BorderLayout.WEST);
        topPanel.add(nameField, BorderLayout.CENTER);

        JLabel membersLabel = new JLabel("ѡ���Ա:");

        // �����û�ѡ���б�
        JList<String> memberList = new JList<>(userListModel);
        memberList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JButton createButton = new JButton("����");
        createButton.addActionListener(e -> {
            String groupName = nameField.getText().trim();
            if (groupName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Ⱥ�����Ʋ���Ϊ�գ�");
                return;
            }

            List<String> selectedMembers = memberList.getSelectedValuesList();
            if (selectedMembers.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "������ѡ��һ����Ա��");
                return;
            }

            // ������Ա�б��ַ���
            StringBuilder memberStr = new StringBuilder();
            for (String member : selectedMembers) {
                if (!member.equals(username)) { // �ų��Լ�
                    memberStr.append(member).append(",");
                }
            }

            // ���ʹ���Ⱥ������
            out.println("CREATE_GROUP:" + groupName + ":" + memberStr.toString());
            dialog.dispose();
        });

        JButton cancelButton = new JButton("ȡ��");
        cancelButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(createButton);
        buttonPanel.add(cancelButton);

        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(new JLabel("ѡ���Ա:"), BorderLayout.WEST);
        dialog.add(new JScrollPane(memberList), BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // �����ļ���������
    private void handleFileRequest(String sender, String filename, long filesize) {
        int option = JOptionPane.showConfirmDialog(
                frame,
                sender + " ��Ҫ�����ļ�: " + filename + " (" + filesize + " �ֽ�)\n�Ƿ���գ�",
                "�ļ���������",
                JOptionPane.YES_NO_OPTION
        );

        if (option != JOptionPane.YES_OPTION) {
            // �ܾ��ļ�����
            out.println("FILE_REJECT:" + sender + ":" + filename);
            return;
        }

        // ѡ�񱣴�λ��
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(filename));
        if (fileChooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            // �û�ȡ���˱���Ի���
            out.println("FILE_REJECT:" + sender + ":" + filename);
            return;
        }

        // �����ļ�����
        out.println("FILE_ACCEPT:" + sender + ":" + filename);

        // �����ļ������߳�
        new Thread(() -> receiveFile(sender, filename, fileChooser.getSelectedFile())).start();
    }

    // �����ļ�
    private void receiveFile(String sender, String filename, File saveFile) {
        try {
            chatArea.append("ϵͳ: ���ڽ������� " + sender + " ���ļ�: " + filename + "\n");

            // �����ļ�������Socket�ȴ����ͷ�����
            ServerSocket fileServerSocket = new ServerSocket(0);
            int filePort = fileServerSocket.getLocalPort();

            // ��֪����������׼���ý����ļ������ṩ�˿���Ϣ
            out.println("FILE_READY:" + sender + ":" + filename + ":" + filePort);

            // �ȴ����ͷ�����
            Socket fileSocket = fileServerSocket.accept();
            DataInputStream dataIn = new DataInputStream(new BufferedInputStream(fileSocket.getInputStream()));
            BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(saveFile));

            // ��ȡ�ļ���С
            long fileSize = dataIn.readLong();

            // ��ȡ�ļ�����
            byte[] buffer = new byte[8192]; // ���󻺳�����С
            int bytesRead;
            long totalBytesRead = 0;

            while (totalBytesRead < fileSize &&
                    (bytesRead = dataIn.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            fileOut.flush(); // ȷ���������ݶ�д���ļ�
            fileOut.close();
            dataIn.close();
            fileSocket.close();
            fileServerSocket.close();

            chatArea.append("ϵͳ: �ļ��������: " + saveFile.getAbsolutePath() + "\n");

        } catch (Exception e) {
            chatArea.append("ϵͳ: �ļ�����ʧ��: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // �����ļ�
    private void sendFileData(String receiver, String filename, String receiverIP, int receiverPort) {
        FileTransferInfo fileInfo = pendingFileTransfers.get(filename);
        if (fileInfo == null) return;

        try {
            File file = fileInfo.file;
            chatArea.append("ϵͳ: ���ڷ����ļ�: " + filename + " �� " + receiver + "\n");

            // ����ר�����ļ������Socket����
            Socket fileSocket = new Socket(receiverIP, receiverPort);
            BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));
            DataOutputStream dataOut = new DataOutputStream(new BufferedOutputStream(fileSocket.getOutputStream()));

            // �����ļ���С
            dataOut.writeLong(file.length());

            // �����ļ�����
            byte[] buffer = new byte[8192]; // ���󻺳�����С
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }

            dataOut.flush();
            fileIn.close();
            dataOut.close();
            fileSocket.close();

            chatArea.append("ϵͳ: �ļ��������: " + filename + "\n");
            pendingFileTransfers.remove(filename);

        } catch (Exception e) {
            chatArea.append("ϵͳ: �ļ�����ʧ��: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ���շ�������Ϣ���߳�
    private class IncomingReader implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    processServerMessage(message);
                }
            } catch (IOException e) {
                chatArea.append("ϵͳ: ��������������ѶϿ�: " + e.getMessage() + "\n");
            }
        }

        // �����������Ϣ
        private void processServerMessage(String message) throws IOException {
            if (message.startsWith("LOGIN_REQUEST")) {
                out.println(username);
            } else if (message.startsWith("LOGIN_FAILED:")) {
                String errorMsg = message.substring("LOGIN_FAILED:".length());
                JOptionPane.showMessageDialog(frame, errorMsg);
                socket.close();
                System.exit(1);
            } else if (message.startsWith("LOGIN_SUCCESS")) {
                chatArea.append("ϵͳ: ��¼�ɹ���\n");
            } else if (message.startsWith("USER_LIST:")) {
                updateUserList(message.substring("USER_LIST:".length()));
            } else if (message.startsWith("GROUP_LIST:")) {
                updateGroupList(message.substring("GROUP_LIST:".length()));
            } else if (message.startsWith("BROADCAST:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("[����] " + parts[1] + ": " + parts[2] + "\n");
                }
            } else if (message.startsWith("PRIVATE:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("[˽��] " + parts[1] + ": " + parts[2] + "\n");
                }
            } else if (message.startsWith("PRIVATE_SENT:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("[˽��] �� -> " + parts[1] + ": " + parts[2] + "\n");
                }
            } else if (message.startsWith("GROUP:")) {
                String[] parts = message.split(":", 4);
                if (parts.length == 4) {
                    chatArea.append("[Ⱥ��:" + parts[1] + "] " + parts[2] + ": " + parts[3] + "\n");
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
                    chatArea.append("ϵͳ: " + parts[1] + " �ѽ����ļ�����: " + parts[2] + "\n");
                }
            } else if (message.startsWith("FILE_REJECT:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    chatArea.append("ϵͳ: " + parts[1] + " �Ѿܾ��ļ�����: " + parts[2] + "\n");
                    pendingFileTransfers.remove(parts[2]);
                }
            } else if (message.startsWith("FILE_TRANSFER_START:")) {
                String[] parts = message.split(":", 5);
                if (parts.length == 5) {
                    sendFileData(parts[1], parts[2], parts[3], Integer.parseInt(parts[4]));
                }
            } else if (message.startsWith("FILE_READY:")) {
                String[] parts = message.split(":", 4);
                if (parts.length == 4) {
                    // ֪ͨ��������ʼ����
                    out.println("FILE_TRANSFER_START:" + parts[1] + ":" + parts[2] + ":" + parts[3]);
                }
            }
        }
    }

    // �����û��б�
    private void updateUserList(String userListStr) {
        userListModel.clear();
        String[] users = userListStr.split(",");
        for (String user : users) {
            if (!user.isEmpty() && !user.equals(username)) {
                userListModel.addElement(user);
            }
        }
    }

    // ����Ⱥ���б�
    private void updateGroupList(String groupListStr) {
        groupListModel.clear();
        if (groupListStr.isEmpty()) return;

        String[] groups = groupListStr.split(";");
        for (String group : groups) {
            if (group.isEmpty()) continue;

            String[] parts = group.split(":", 2);
            if (parts.length <= 0 || parts[0].isEmpty()) continue;

            // ���Ⱥ���Ա��Ϣ
            if (parts.length > 1) {
                String[] members = parts[1].split(",");
                // ֻ��ӵ�ǰ�û��ǳ�Ա��Ⱥ��
                for (String member : members) {
                    if (member.trim().equals(username)) {
                        groupListModel.addElement(parts[0]);
                        break;
                    }
                }
            }
        }
    }

    // �ļ�������Ϣ��
    private class FileTransferInfo {
        File file;
        String receiver;

        public FileTransferInfo(File file, String receiver) {
            this.file = file;
            this.receiver = receiver;
        }
    }

    // ������
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client());
    }
}