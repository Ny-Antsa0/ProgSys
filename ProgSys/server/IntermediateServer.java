import java.io.*;
import java.net.*;
import java.util.*;

public class IntermediateServer {
    private static int PORT;
    private static final String CONF_DIR = "conf";
    private static final String TF_CONF = "port.conf";
    private static final String STORAGE_FILE = "storage_addresses.txt";
    private static final String MAP_FILE = "map.ini";

    private static final List<String> STORAGE_ADDRESSES = new ArrayList<>();
    private static final Map<String, Map<String, List<String>>> fileMap = new HashMap<>();

    public static void main(String[] args) {
        setupConfiguration();

        // Démarrage du thread d'écoute pour les messages des serveurs de stockage
        new Thread(IntermediateServer::listenForStorageServers).start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Intermediate Server démarré sur le port : " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void setupConfiguration() {
        File confDir = new File(CONF_DIR);
        if (!confDir.exists()) confDir.mkdirs();

        File confFile = new File(CONF_DIR, TF_CONF);
        if (!confFile.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(confFile))) {
                writer.write("12345"); // Port par défaut
                System.out.println("Fichier de configuration par défaut créé : " + confFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Lire le port du serveur intermédiaire
        try (BufferedReader reader = new BufferedReader(new FileReader(confFile))) {
            PORT = Integer.parseInt(reader.readLine().trim());
            System.out.println("Port du serveur intermédiaire chargé : " + PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Charger les adresses de stockage depuis le fichier storage_addresses.txt
        loadStorageAddresses();
        loadFileMap();
    }

    private static void loadStorageAddresses() {
        File storageFile = new File(CONF_DIR, STORAGE_FILE);
        if (!storageFile.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(storageFile))) {
            String line;
            STORAGE_ADDRESSES.clear();
            while ((line = br.readLine()) != null) {
                STORAGE_ADDRESSES.add(line);
            }
            System.out.println("Adresses de stockage chargées : " + STORAGE_ADDRESSES);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadFileMap() {
        File mapFile = new File(CONF_DIR, MAP_FILE);
        if (!mapFile.exists()) return;
    
        try (BufferedReader br = new BufferedReader(new FileReader(mapFile))) {
            String line;
            String currentFile = null;
            Map<String, List<String>> parts = null;
    
            // On vide d'abord puis on ajoute
            fileMap.clear();
            while ((line = br.readLine()) != null) {
                if (line.startsWith("[")) {
                    if (currentFile != null) fileMap.put(currentFile, parts);
                    currentFile = line.substring(1, line.length() - 1);
                    parts = new HashMap<>();
                } else if (currentFile != null) {
                    String[] keyValue = line.split(" = ");
                    // Convertir la liste immuable en une liste modifiable
                    List<String> servers = new ArrayList<>(Arrays.asList(keyValue[1].split(", ")));
                    parts.put(keyValue[0], servers);
                }
            }
            if (currentFile != null) fileMap.put(currentFile, parts);
    
            System.out.println("Fichier map chargé.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    

    private static void listenForStorageServers() {
        try (ServerSocket serverSocket = new ServerSocket(PORT + 1)) {
            System.out.println("En écoute pour les serveurs de stockage sur le port : " + (PORT + 1));
    
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> {
                    try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
                        String message = dis.readUTF();
    
                        if ("REGISTER_STORAGE".equals(message)) {
                            String storageAddress = dis.readUTF();
                            int storagePort = dis.readInt();
    
                            String storageEntry = storageAddress + ":" + storagePort;
                            STORAGE_ADDRESSES.add(storageEntry);
    
                            File storageFile = new File(CONF_DIR, STORAGE_FILE);
                            try (BufferedWriter writer = new BufferedWriter(new FileWriter(storageFile, true))) {
                                writer.write(storageEntry);
                                writer.newLine();
                            }
                            loadStorageAddresses();
                            System.out.println("Serveur de stockage enregistré : " + storageEntry);
    
                        } else if ("UNREGISTER_STORAGE".equals(message)) {
                            String storageAddress = dis.readUTF();
                            int storagePort = dis.readInt();
    
                            String storageEntry = storageAddress + ":" + storagePort;
                            if (STORAGE_ADDRESSES.remove(storageEntry)) {
                                updateStorageFile();
                                loadStorageAddresses();
                                System.out.println("Serveur de stockage supprimé : " + storageEntry);
                            }
                        }
                        if ("GET_ACTIVE_SERVERS".equals(message)) {
                            // Renvoyer la liste des serveurs actifs
                            List<String> activeServers = new ArrayList<>();
                            for (String storage : STORAGE_ADDRESSES) {
                                String[] addressPort = storage.split(":");
                                String address = addressPort[0];
                                int port = Integer.parseInt(addressPort[1]);
                                if (isServerAlive(address, port)) {
                                    activeServers.add(storage);
                                }
                            }
                            dos.writeInt(activeServers.size());
                            for (String server : activeServers) {
                                dos.writeUTF(server);
                            }
                            System.out.println("Liste des serveurs actifs envoyée : " + activeServers);
                        }
                        
                        if ("DUPLICATION_COMPLETE".equals(message)) {
                            // Mise à jour de map.ini après duplication
                            String partName = dis.readUTF();
                            String fileName = dis.readUTF();
                            String newStorageServer = dis.readUTF();
                        
                            synchronized (fileMap) {
                                if (fileMap.containsKey(fileName)) {
                                    fileMap.get(fileName).get(partName).add(newStorageServer);
                                    System.out.println("\n[INFO] notification de duplication recu");
                                    updateMapFile();
                                    System.out.println("Map mise à jour pour la duplication : " + partName + " -> " + newStorageServer);
                                    System.out.println(fileMap  + "\n");
                                }
                            }
                            
                            
                        }
                        
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static synchronized void updateStorageFile() {
        File storageFile = new File(CONF_DIR, STORAGE_FILE);
    
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(storageFile))) {
            for (String entry : STORAGE_ADDRESSES) {
                writer.write(entry);
                writer.newLine();
            }
            System.out.println("Fichier storage_addresses.txt mis à jour.");
            loadStorageAddresses();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    private static boolean isServerAlive(String address, int port) {
        try (Socket socket = new Socket(address, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void handleClient(Socket clientSocket) {
        System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
    
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
    
                try {
                    String action = dis.readUTF().toUpperCase();
                    System.out.println("Client request: " + action);
                    if (action.equalsIgnoreCase("DOWNLOAD")) {
                        handleDownload(dis, dos);
                    } if (action.equalsIgnoreCase("UPLOAD")) {
                        handleUpload(dis, dos);
                    } 
                    
                    if ("LIST".equals(action)) {
                        synchronized (fileMap) {
                            dos.writeUTF("OK");
                            if (fileMap.isEmpty()) {
                                dos.writeUTF("EMPTY");
                                System.out.println("Aucun fichier présent dans map.ini");
                            } else {
                                dos.writeUTF(String.join(",", fileMap.keySet()));
                                System.out.println("Liste des fichiers envoyée au client.");
                            }
                        }
                    }
                    
                    if ("REMOVE".equals(action)) {
                        String fileName = dis.readUTF();
                    
                        if (!fileMap.containsKey(fileName)) {
                            dos.writeUTF("ERROR: File not found");
                            return;
                        }
                    
                        Map<String, List<String>> parts = fileMap.get(fileName);
                    
                        // Envoyer une demande de suppression à chaque StorageServer
                        for (Map.Entry<String, List<String>> entry : parts.entrySet()) {
                            String partName = entry.getKey();
                            List<String> servers = entry.getValue();
                    
                            for (String server : servers) {
                                String[] addressPort = server.split(":");
                                String address = addressPort[0];
                                int port = Integer.parseInt(addressPort[1]);
                    
                                if (isServerAlive(address, port)) {
                                    try (Socket storageSocket = new Socket(address, port);
                                         DataOutputStream storageDos = new DataOutputStream(storageSocket.getOutputStream())) {
                                        storageDos.writeUTF("DELETE");
                                        storageDos.writeUTF(partName);
                                        System.out.println("Demande de suppression envoyée : " + partName + " -> " + address + ":" + port);
                                    } catch (IOException e) {
                                        System.err.println("Erreur lors de la suppression du morceau : " + partName + " sur " + address + ":" + port);
                                    }
                                }
                            }
                        }
                    
                        // Supprimer les métadonnées du fichier dans fileMap et map.ini
                        synchronized (fileMap) {
                            fileMap.remove(fileName);
                            updateMapFile();
                            loadFileMap();
                        }
                    
                        dos.writeUTF("REMOVE_COMPLETE");
                        System.out.println("Fichier supprimé et lignes supprimées de map.ini : " + fileName);
                    }
                    
                } catch (IOException e) {
                    System.err.println("Erreur lors du traitement de la requête client : " + e.getMessage());
                   
                }
          
    
        } catch (IOException e) {
            System.err.println("Erreur lors de la communication avec le client : " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture de la socket client : " + e.getMessage());
            }
        }
    }
    
   
    private static void handleDownload(DataInputStream dis, DataOutputStream dos) throws IOException {
        String fileName = dis.readUTF();
        if (!fileMap.containsKey(fileName)) {
            dos.writeUTF("ERROR: File not found");
            System.err.println("Fichier non trouvé : " + fileName);
            return;
        }
    
        ByteArrayOutputStream assembledFile = new ByteArrayOutputStream();
        Map<String, List<String>> parts = fileMap.get(fileName);

        // Trier les parties par ordre croissant (.part0, .part1, ...)
        List<String> sortedPartNames = new ArrayList<>(parts.keySet());
        sortedPartNames.sort(Comparator.comparingInt(part -> Integer.parseInt(part.replaceAll(".*\\.part", ""))));

        // boolean allPartsRetrieved = true;

        // Télécharger les parties du fichier
        for (String partName : sortedPartNames) {
            List<String> servers = parts.get(partName);
            boolean partRetrieved = false;

            for (String server : servers) {
                String[] addressPort = server.split(":");
                String address = addressPort[0];
                int port = Integer.parseInt(addressPort[1]);

                if (isServerAlive(address, port)) {
                    try (Socket storageSocket = new Socket(address, port);
                         DataOutputStream storageDos = new DataOutputStream(storageSocket.getOutputStream());
                         DataInputStream storageDis = new DataInputStream(storageSocket.getInputStream())) {
            
                        storageDos.writeUTF("RETRIEVE");
                        storageDos.writeUTF(partName);
            
                        int chunkSize = storageDis.readInt();
                        byte[] buffer = new byte[chunkSize];
                        storageDis.readFully(buffer);
            
                        assembledFile.write(buffer);
                        partRetrieved = true;
                        break;
                    }
                } else {
                    System.out.println("Serveur inactif : " + address + ":" + port);
                }
            }

            if (!partRetrieved) {
                dos.writeUTF("ERROR: Failed to retrieve part: " + partName);
                System.err.println("Impossible de récupérer la partie : " + partName);
                return;
            }
        }

        dos.writeUTF("OK");
        byte[] fileBytes = assembledFile.toByteArray();
        dos.writeLong(fileBytes.length);
        dos.write(fileBytes);
        System.out.println("Fichier envoyé au client : " + fileName);
    }
    
    
    private static void handleUpload(DataInputStream dis, DataOutputStream dos) throws IOException {
        String fileName = dis.readUTF();
        long fileSize = dis.readLong();
    
        int numServers = STORAGE_ADDRESSES.size();
        long baseChunkSize = fileSize / numServers;
        long remainder = fileSize % numServers;
    
        byte[] buffer = new byte[4096];
        int bytesRead;
        int serverIndex = 0;
        long remaining = fileSize;
    
        Map<String, List<String>> parts = new HashMap<>();
    
        while (remaining > 0) {
            long currentChunkSize = baseChunkSize + (serverIndex < remainder ? 1 : 0);
            ByteArrayOutputStream chunkData = new ByteArrayOutputStream();
    
            while (currentChunkSize > 0 &&
                    (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, currentChunkSize))) > 0) {
                chunkData.write(buffer, 0, bytesRead);
                currentChunkSize -= bytesRead;
                remaining -= bytesRead;
            }
    
            String[] storage = STORAGE_ADDRESSES.get(serverIndex).split(":");
            String host = storage[0].trim();
            int port = Integer.parseInt(storage[1].trim());
    
            String partName = fileName + ".part" + serverIndex;
    
            try (Socket storageSocket = new Socket(host, port);
                 DataOutputStream storageDos = new DataOutputStream(storageSocket.getOutputStream())) {
    
                storageDos.writeUTF("STORE");
                storageDos.writeUTF(partName);
                byte[] chunkBytes = chunkData.toByteArray();
                storageDos.writeInt(chunkBytes.length);
                storageDos.write(chunkBytes);
    
                parts.computeIfAbsent(partName, k -> new ArrayList<>()).add(host + ":" + port);
                System.out.println("Morceau envoyé : " + partName + " -> " + host + ":" + port);
    
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi du morceau : " + partName + " au serveur " + host + ":" + port);
            }
    
            serverIndex = (serverIndex + 1) % numServers;
        }
    
        synchronized (fileMap) {
            fileMap.put(fileName, parts);
            updateMapFile();
            loadFileMap();
        }
    
        dos.writeUTF("UPLOAD_COMPLETE");
        System.out.println("Fichier uploadé et enregistré dans map.ini : " + fileName);
    }
    

    private static void updateMapFile() {
        File mapFile = new File(CONF_DIR, MAP_FILE);
    
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mapFile))) {
            for (Map.Entry<String, Map<String, List<String>>> entry : fileMap.entrySet()) {
                writer.write("[" + entry.getKey() + "]");
                writer.newLine();
    
                Map<String, List<String>> parts = entry.getValue();
                for (Map.Entry<String, List<String>> partEntry : parts.entrySet()) {
                    writer.write(partEntry.getKey() + " = " + String.join(", ", partEntry.getValue()));
                    writer.newLine();
                }
                
            }
            System.out.println("Fichier map.ini mis à jour.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        loadFileMap();

    }
    
}

