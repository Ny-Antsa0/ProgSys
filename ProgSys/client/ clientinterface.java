import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Client {
    private static final String DEFAULT_CONFIG_PATH = "client.conf";
    private static String serverAddress;
    private static int serverPort;
    private static String downloadDirectory;
    private static JTextArea textArea; // For displaying messages in GUI
    private static JFrame frame;

    public static void main(String[] args) {
        // Create the GUI
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Client Interface");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 400);
            frame.setLayout(new BorderLayout());

            textArea = new JTextArea();
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);
            frame.add(scrollPane, BorderLayout.CENTER);

            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(5, 1));

            // Buttons for actions
            JButton uploadButton = new JButton("Upload File");
            JButton downloadButton = new JButton("Download File");
            JButton listButton = new JButton("List Files");
            JButton removeButton = new JButton("Remove File");
            JButton logoutButton = new JButton("Logout");

            panel.add(uploadButton);
            panel.add(downloadButton);
            panel.add(listButton);
            panel.add(removeButton);
            panel.add(logoutButton);

            removeButton.setBackground(Color.RED);

            frame.add(panel, BorderLayout.WEST);
            frame.setVisible(true);

            // Load configuration and setup socket
            loadOrCreateConfig();

            uploadButton.addActionListener(e -> handleUpload());
            downloadButton.addActionListener(e -> handleDownload());
            listButton.addActionListener(e -> handleList());
            removeButton.addActionListener(e -> handleRemove());
            logoutButton.addActionListener(e -> logout());
        });
    }

    private static void loadOrCreateConfig() {
        File configFile = new File(DEFAULT_CONFIG_PATH);
        if (!configFile.exists()) {
            logToTerminalAndGUI("Configuration file not found. Creating default configuration...");
            try {
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("server=localhost:1234\n");
                    writer.write("download_dir=./\n");
                }
                logToTerminalAndGUI("Default configuration file created at " + configFile.getAbsolutePath());
            } catch (IOException e) {
                logToTerminalAndGUI("Error creating default configuration file: " + e.getMessage());
                return;
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            Properties config = new Properties();
            config.load(reader);

            String serverConfig = config.getProperty("server", "localhost:1234");
            String[] serverParts = serverConfig.split(":");
            serverAddress = serverParts[0];
            serverPort = Integer.parseInt(serverParts[1]);

            downloadDirectory = config.getProperty("download_dir", "./");
            File downloadDir = new File(downloadDirectory);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
                logToTerminalAndGUI("Download directory created at: " + downloadDir.getAbsolutePath());
            }
        } catch (IOException e) {
            logToTerminalAndGUI("Error reading configuration file: " + e.getMessage());
        }
    }

    private static void handleList() {
        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            dos.writeUTF("LIST");
            String status = dis.readUTF();

            if ("OK".equals(status)) {
                String response = dis.readUTF();
                if ("EMPTY".equals(response)) {
                    logToTerminalAndGUI("No files are currently stored.");
                } else {
                    logToTerminalAndGUI("Files available: ");
                    for (String fileName : response.split(",")) {
                        logToTerminalAndGUI("- " + fileName);
                    }
                }
            } else {
                logToTerminalAndGUI("Error retrieving file list.");
            }
        } catch (IOException e) {
            logToTerminalAndGUI("Error during list operation: " + e.getMessage());
        }
    }

    private static void handleRemove() {
        String fileName = JOptionPane.showInputDialog(frame, "Enter the name of the file to remove:");
        if (fileName == null || fileName.trim().isEmpty()) return;

        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            dos.writeUTF("REMOVE");
            dos.writeUTF(fileName);

            String response = dis.readUTF();
            if ("REMOVE_COMPLETE".equals(response)) {
                logToTerminalAndGUI("File removed successfully: " + fileName);
            } else if ("ERROR: File not found".equals(response)) {
                logToTerminalAndGUI("File not found: " + fileName);
            } else {
                logToTerminalAndGUI("Error removing file.");
            }
        } catch (IOException e) {
            logToTerminalAndGUI("Error during remove operation: " + e.getMessage());
        }
    }

    private static void handleUpload() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = fileChooser.getSelectedFile();
        if (file == null || !file.exists() || !file.isFile()) {
            logToTerminalAndGUI("Invalid file path. Please try again.");
            return;
        }

        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeUTF("UPLOAD");
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
            }

            logToTerminalAndGUI("File uploaded successfully.");
        } catch (IOException e) {
            logToTerminalAndGUI("Error during file upload: " + e.getMessage());
        }
    }

    private static void handleDownload() {
        String fileName = JOptionPane.showInputDialog(frame, "Enter the name of the file you want to download:");
        if (fileName == null || fileName.trim().isEmpty()) return;

        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeUTF("DOWNLOAD");
            dos.writeUTF(fileName);

            String response = dis.readUTF();
            if ("OK".equals(response)) {
                long fileSize = dis.readLong();
                File outputFile = new File(downloadDirectory, fileName);

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    long remaining = fileSize;
                    int bytesRead;

                    while (remaining > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                        fos.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }

                logToTerminalAndGUI("File downloaded successfully to: " + outputFile.getAbsolutePath());
            } else {
                logToTerminalAndGUI("File not found on the server.");
            }
        } catch (IOException e) {
            logToTerminalAndGUI("Error during file download: " + e.getMessage());
        }
    }

    private static void logout() {
        logToTerminalAndGUI("Logging out...");
        System.exit(0);
    }

    private static void logToTerminalAndGUI(String message) {
        System.out.println(message);
        textArea.append(message + "\n");
    }
}
