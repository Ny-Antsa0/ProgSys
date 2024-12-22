import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StorageServer {
    private static int storagePort;
    private static String intermediateServerAddress;
    private static int intermediateServerPort;
    private static File storageDir;

    public static void main(String[] args) {
        try {
            // Demander le port du serveur de stockage
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            // Charger la configuration du port par défaut
            File configFile = new File("intermediate_adress.conf");
            if (!configFile.exists()) {
                createDefaultPortConfig(configFile);
            }
            loadIntermediateServerConfig(configFile);

            System.out.println("Port du server intermediaire:" + intermediateServerPort);
            System.out.print("Entrez le port du serveur de stockage : ");
            String storagePortInput = reader.readLine();

            while (storagePortInput.isEmpty()) {
                System.out.println("Veuillez choisir un port");
                storagePortInput = reader.readLine();
            }
            while (Integer.parseInt(storagePortInput) == intermediateServerPort) {
                System.out.println("Veuillez choisir un port different de celui du server intermediaire:" + intermediateServerPort);
                storagePortInput = reader.readLine();
            }
            storagePort = Integer.parseInt(storagePortInput);

            // Envoyer un message au serveur intermédiaire
            notifyIntermediateServer();

            // Lancer le serveur de stockage
            startStorageServer(storagePort);

        }  catch (EOFException e) {
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefaultPortConfig(File configFile) throws Exception {
        System.out.println("Création du fichier de configuration par défaut : " + configFile.getAbsolutePath());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("localhost:1235");
        }
    }

    private static void loadIntermediateServerConfig(File configFile) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String[] parts = br.readLine().split(":");
            intermediateServerAddress = parts[0];
            intermediateServerPort = Integer.parseInt(parts[1]);
            System.out.println("Configuration chargée : " + intermediateServerAddress + ":" + intermediateServerPort);
        }
    }

    private static void notifyIntermediateServer() {
        try (Socket socket = new Socket(intermediateServerAddress, intermediateServerPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("REGISTER_STORAGE");
            dos.writeUTF(InetAddress.getLocalHost().getHostAddress());
            dos.writeInt(storagePort);
            System.out.println("Notification envoyée au serveur intermédiaire : " + intermediateServerAddress + ":" + intermediateServerPort);
        }  catch (EOFException e) {
            
        } catch (Exception e) {
            System.err.println("Impossible de notifier le serveur intermédiaire : " + e.getMessage());
        }
    }

    public static void startStorageServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Storage Server démarré sur le port : " + port);

            // Ajoute une fermeture du serveur propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Storage Server sur le port " + port + " est en cours d'arrêt.");
                notifyIntermediateServerShutdown();
            }));

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClientRequest(clientSocket)).start();
            }
        }  catch (EOFException e) {
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void notifyIntermediateServerShutdown() {
        try (Socket socket = new Socket(intermediateServerAddress, intermediateServerPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("UNREGISTER_STORAGE");
            dos.writeUTF(InetAddress.getLocalHost().getHostAddress());
            dos.writeInt(storagePort);

            System.out.println("Notification d'arrêt envoyée au serveur intermédiaire.");
        }  catch (EOFException e) {
            
        } catch (Exception e) {
            System.err.println("Impossible d'envoyer l'arrêt au serveur intermédiaire : " + e.getMessage());
        }
    }

    private static void handleClientRequest(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            String action = dis.readUTF();
            action = action.toUpperCase();       
            
            // Créer un répertoire de stockage spécifique
            storageDir = new File("storage_" + storagePort);
            if (!storageDir.exists()) storageDir.mkdirs();

            System.out.println("Action reçue : " + action);

            switch (action) {
                case "STORE":
                    storeAndDuplicate(dis, dos);
                    break;
                case "DUPLICATE":
                    storeOnly(dis, dos);
                    break;
                case "RETRIEVE":
                    handleRetrieve(dis, dos);
                    break;
                case "DELETE":
                    handleDelete(dis, dos);
                    break;
                default:
                    System.out.println("Action inconnue : " + action);
                    break;
            }

        }  catch (EOFException e) {
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void storeOnly(DataInputStream dis, DataOutputStream dos) {
        try {
            String fileName = dis.readUTF();
            int fileSize = dis.readInt();
            byte[] buffer = new byte[fileSize];
            dis.read(buffer);


            File file = new File(storageDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(buffer);
            }
            System.out.println("Fichier reçu et stocké : " + file.getAbsolutePath());

        }  catch (EOFException e) {
            
        } catch (Exception e) {
            System.out.println("Erreur lors de la tentative de stockage : " + e.getMessage());
        }
    }


    public static String getRandomServer() throws Exception{
        
        try (Socket intermediateSocket = new Socket(intermediateServerAddress, intermediateServerPort);
            DataOutputStream intermediateDos = new DataOutputStream(intermediateSocket.getOutputStream());
            DataInputStream intermediateDis = new DataInputStream(intermediateSocket.getInputStream())) {

            System.out.println("[INFO] Connexion au serveur intermédiaire pour récupérer la liste des serveurs actifs...");
            intermediateDos.writeUTF("GET_ACTIVE_SERVERS");

            int activeServerCount = intermediateDis.readInt();
            List<String> activeServers = new ArrayList<>();
            for (int i = 0; i < activeServerCount; i++) {
                activeServers.add(intermediateDis.readUTF());
            }

            System.out.println("[INFO] Liste des serveurs actifs reçue : " + activeServers);
            
            // Retirer l'adresse du serveur actuel de la liste
            String storageAddress = InetAddress.getLocalHost().getHostAddress();
            String currentServer = storageAddress + ":" + storagePort;
            activeServers.remove(currentServer);

            
            if (!activeServers.isEmpty()) {
                // Choisir un serveur aléatoire pour la duplication
                Random random = new Random();
                String duplicateServer = activeServers.get(random.nextInt(activeServers.size()));
                System.out.println("[INFO] Serveur choisi pour la duplication : " + duplicateServer);
                return duplicateServer;
            } else throw new Exception("[WARNING] Aucun autre serveur actif disponible pour effectuer la duplication.");
        
        }  catch (EOFException e) {
            
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }

    public static void informServerOfDuplication(String partName, String duplicateServer) throws Exception {
        try (Socket intermediateSocket = new Socket(intermediateServerAddress, intermediateServerPort);
            DataOutputStream intermediateDos = new DataOutputStream(intermediateSocket.getOutputStream());
            DataInputStream intermediateDis = new DataInputStream(intermediateSocket.getInputStream())) {
            try {
                // String duplicateServer = getRandomServer();
                // Informer le serveur intermédiaire de la duplication
                String initialFileName = partName.split(".part")[0];
                intermediateDos.writeUTF("DUPLICATION_COMPLETE");
                intermediateDos.writeUTF(partName);
                intermediateDos.writeUTF(initialFileName);
                intermediateDos.writeUTF(duplicateServer);
                System.out.println("[INFO] Le serveur intermédiaire a été informé de la duplication.");
            }  catch (EOFException e) {
                
            } catch (Exception e) {
                // TODO: handle exception
                throw e;
            }
        }  catch (EOFException e) {
            
        } catch (Exception e) {
            throw e;
        }
    }

    public static void storeAndDuplicate(DataInputStream dis, DataOutputStream dos) {
        try {
            String fileName = dis.readUTF();
            int fileSize = dis.readInt();
            byte[] buffer = new byte[fileSize];
            dis.readFully(buffer);

            File file = new File(storageDir, fileName);
            
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(buffer);
                }
                 catch (Exception e) {}
            System.out.println("[INFO] Fichier reçu et stocké : " + file.getAbsolutePath());
                
            String duplicateServer = getRandomServer();
            String[] addressPort = duplicateServer.split(":");
            String duplicateAddress = addressPort[0];
            int duplicatePort = Integer.parseInt(addressPort[1]);
            try (Socket duplicateSocket = new Socket(duplicateAddress, duplicatePort);
                DataOutputStream duplicateDos = new DataOutputStream(duplicateSocket.getOutputStream())) {

                duplicateDos.writeUTF("DUPLICATE");
                duplicateDos.writeUTF(fileName);
                duplicateDos.writeInt(buffer.length);
                duplicateDos.write(buffer);

                System.out.println("[INFO] Duplication du fichier envoyée au serveur : " + duplicateServer);
                informServerOfDuplication(fileName, duplicateServer);
            }  catch (EOFException e) {
                
            } catch (Exception e) {
                System.err.println("[ERROR] Échec de la duplication vers le serveur : " + duplicateServer + ". Détails : " + e.getMessage());
            }
        } catch (EOFException e) {
            
        }  catch (IOException e) {
            System.out.println("[ERROR] erreur lors de la tentative de duplicate: "+ e.getMessage());
        } catch (Exception e) {
            System.out.println("[ERROR] "+ e.getMessage());
        }
    }

    public static void handleRetrieve(DataInputStream dis, DataOutputStream dos) {
        try {
            String fileName = dis.readUTF();
            File file = new File(storageDir, fileName);

            if (file.exists()) {
                byte[] fileBytes = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(fileBytes);
                }

                dos.writeInt(fileBytes.length);
                dos.write(fileBytes);
                System.out.println("Fichier envoyé : " + file.getAbsolutePath());
            } else {
                dos.writeInt(0);
                System.err.println("Fichier non trouvé : " + file.getAbsolutePath());
            }

        }  catch (EOFException e) {
            
        } catch (Exception e) {
            System.out.println("Erreur lors de la récupération : " + e.getMessage());
        }
    }

    public static void handleDelete(DataInputStream dis, DataOutputStream dos) {
        try {
            String partName = dis.readUTF();
            File fileToDelete = new File(storageDir, partName);

            if (fileToDelete.exists() && fileToDelete.delete()) {
                dos.writeUTF("DELETE_SUCCESS");
                System.out.println("Fichier supprimé : " + fileToDelete.getAbsolutePath());
            } else {
                dos.writeUTF("DELETE_FAILURE");
                System.err.println("Échec de suppression du fichier : " + partName);
            }

        }  catch (EOFException e) {
            
        } catch (Exception e) {
            System.out.println("Erreur lors de la suppression : " + e.getMessage());
        }
    }
}
