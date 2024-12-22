import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class Client {
    private static final String DEFAULT_CONFIG_PATH = "client.conf";
    private static String serverAddress;
    private static int serverPort;
    private static String downloadDirectory;

    public static void main(String[] args) {
        try {
            loadOrCreateConfig();
            boolean running = true;
            try (Socket socket = new Socket(serverAddress, serverPort);
                Scanner scanner = new Scanner(System.in)) {
                while (running) {

                    System.out.println("\nWhat do you want to do? (enter a number)");
                    System.out.println("1. Upload file from server");
                    System.out.println("2. Download file from server");
                    System.out.println("3. List of file in the server");
                    System.out.println("4. Remove file from server");
                    System.out.println("5. Logout");
                    String action = scanner.nextLine().trim().toLowerCase();

                    switch (action) {
                        case "1":
                            handleUpload(scanner);
                            break;
                        case "2":
                            handleDownload(scanner);
                            break;
                        case "3":
                            handleList();
                            break;
                        case "4":
                            handleRemove(scanner);
                            break;
                        case "5":
                            running = false;
                            break;
                        default:
                            System.out.println("Invalid action. Please choose upload, download, list, remove, or logout.");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
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
                    System.out.println("No files are currently stored.");
                } else {
                    System.out.println("Files available: ");
                    for (String fileName : response.split(",")) {
                        System.out.println("- " + fileName);
                    }
                }
            } else {
                System.err.println("Error retrieving file list.");
            }
        } catch (IOException e) {
            System.err.println("Error during list operation: " + e.getMessage());
        }
    }

    private static void handleRemove(Scanner scanner) {
        System.out.println("Enter the name of the file to remove:");
        String fileName = scanner.nextLine();
    
        try (Socket socket = new Socket(serverAddress, serverPort);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            dos.writeUTF("REMOVE");
            dos.writeUTF(fileName);
    
            String response = dis.readUTF();
            if ("REMOVE_COMPLETE".equals(response)) {
                System.out.println("File removed successfully: " + fileName);
            } else if ("ERROR: File not found".equals(response)) {
                System.err.println("File not found: " + fileName);
            } else {
                System.err.println("Error removing file.");
            }
        } catch (IOException e) {
            System.err.println("Error during remove operation: " + e.getMessage());
        }
    }
    

    private static void loadOrCreateConfig() {
        File configFile = new File(DEFAULT_CONFIG_PATH);
        if (!configFile.exists()) {
            System.out.println("Configuration file not found. Creating default configuration...");
            try {
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("server=127.0.0.1:1234\n");
                    writer.write("download_dir=downloaded\n");
                }
                System.out.println("Default configuration file created at " + configFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error creating default configuration file: " + e.getMessage());
                return;
            }
        }
    
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            Properties config = new Properties();
            config.load(reader);
    
            // Lecture du serveur (adresse et port) à partir du fichier de configuration
            String serverConfig = config.getProperty("server");
            if (serverConfig == null || serverConfig.isBlank()) {
                throw new IllegalArgumentException("Missing or invalid 'server' entry in configuration file.");
            }
    
            String[] serverParts = serverConfig.split(":");
            if (serverParts.length != 2) {
                throw new IllegalArgumentException("Invalid 'server' format in configuration file. Expected <ipServer>:<port>.");
            }
    
            serverAddress = serverParts[0].trim();
            serverPort = Integer.parseInt(serverParts[1].trim());
    
            // Lecture du répertoire de téléchargement à partir du fichier de configuration
            downloadDirectory = config.getProperty("download_dir");
            File downloadDir = new File(downloadDirectory);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
                System.out.println("Download directory created at: " + downloadDir.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Error reading configuration file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
        }
    }
    

    private static void handleUpload(Scanner scanner) {
        try (Socket socket = new Socket(serverAddress, serverPort);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            System.out.println("Enter the path to the file you want to upload:");
            String filePath = scanner.nextLine();
            File file = new File(filePath);

            if (!file.exists() || !file.isFile()) {
                System.out.println("Invalid file path. Please try again.");
                return;
            }

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

            System.out.println("File uploaded successfully.");
        } catch (IOException e) {
            System.err.println("Error during file upload: " + e.getMessage());
        }
    }

    private static void handleDownload(Scanner scanner) {
        try (Socket socket = new Socket(serverAddress, serverPort);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            System.out.println("Enter the name of the file you want to download:");
            String fileName = scanner.nextLine();

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

                System.out.println("File downloaded successfully to: " + outputFile.getAbsolutePath());
            } else {
                System.out.println("File not found on the server.");
            }
        } catch (IOException e) {
            System.err.println("Error during file download: " + e.getMessage());
        }
    }
}
