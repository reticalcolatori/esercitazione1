package com;

import java.io.*;
import java.net.*;

public class RSClient {

    private static final String DEFAULT_serverIP = "127.0.0.1";
    private static final int DEFAULT_serverPort = 6666;
    private static final int DEFAULT_bufferSize = 256;
//    private static final int DEFAULT_timeout = 30000;

    private static boolean isPortValid(int port) {
        return 0x400 < port && port <= 0xFFFF;
    }

    private InetAddress serverAddress;
    private final int serverPort;
    private int servicePort = -1;

    private boolean networkState = false;

    private DatagramSocket socket;
    private DatagramPacket packet;

    private byte[] emptyBuffer = new byte[DEFAULT_bufferSize];

    public RSClient() throws UnknownHostException {
        this(DEFAULT_serverIP, DEFAULT_serverPort);
    }

    public RSClient(String serverIP, int serverPort) throws UnknownHostException, IllegalArgumentException {
        this(InetAddress.getByName(serverIP), serverPort);
    }

    public RSClient(InetAddress serverAddress, int serverPort) throws IllegalArgumentException {
        this.serverAddress = serverAddress;

        //Controllo che la porta sia non standard e nel range di 16-bit.
        if (!(isPortValid(serverPort))) {
            throw new IllegalArgumentException("Porta non valida");
        }

        this.serverPort = serverPort;
    }

    public InetAddress getServerAddress() {
        return serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getServicePort() {
        return servicePort;
    }

    /**
     * Inizializza gli oggetti di rete.
     *
     * @throws SocketException Non è stato possibile creare la socket
     */
    public void initNetwork() throws SocketException {
        socket = new DatagramSocket();
        //Se voglio imposto il timeout:
        //socket.setSoTimeout(DEFAULT_timeout);
        packet = new DatagramPacket(emptyBuffer, 0, emptyBuffer.length, serverAddress, serverPort);
        networkState = true;
    }

    /**
     * Richede al discovery server il servizio collegato al file.
     *
     * @param filename nome del file in qui fare lo swap
     */
    public boolean requestService(String filename) throws IOException, IllegalArgumentException {
        //Controllo argomenti
        if (filename.isBlank()) {
            throw new IllegalArgumentException("Filename vuoto");
        }

        //Devo verificare che la rete sia inizializzata.
        if (!networkState) throw new IllegalStateException("Bisogna inizializzare la rete prima");

        //Imposto il pacchetto volto al Discovery.
        packet.setAddress(serverAddress);
        packet.setPort(serverPort);

        //Imposto la richiesta.
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            try (DataOutputStream dataStream = new DataOutputStream(byteStream)) {
                //Creo il messaggio per il discovery e lo salvo nel pacchetto.
                dataStream.writeUTF(filename);
                packet.setData(byteStream.toByteArray());
            }
        }

        //Invio la richiesta.
        socket.send(packet);

        //Attendo risposta.
        packet.setData(emptyBuffer);
        socket.receive(packet);

        //Decodifico la risposta:
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData())) {
            try (DataInputStream dataStream = new DataInputStream(byteStream)) {
                //Ricavo la porta del servizio.
                int tmpPort = dataStream.readInt();

                //Verifico che la porta sia valida.
                //Controllo che la porta sia non standard e nel range di 16-bit.
                //Se il nome file non fosse fra quelli noti al DiscoveryServer, il
                //DiscoveryServer invia esito negativo (-1 ????) e il client termina.
                if (!(isPortValid(serverPort))) {
                    //throw new IllegalArgumentException("Porta servizio non valida");
                    return false;
                }

                this.servicePort = tmpPort;
                return true;
            }
        }
    }


    /**
     * Chiede al server di swappare due righe.
     *
     * @param line1 linea 1 da swappare
     * @param line2 linea 2 da swappare
     * @return Stringa con l'esito del server.
     * @throws IOException Errore dovuto alla socket, stream
     */
    public String swapLines(int line1, int line2) throws IOException {
        //Controllo che le linee siano valide
        if (line1 < 0 || line2 < 0) {
            throw new IllegalArgumentException("Linee inserite non valide (< 0)");
        }

        //Linee uguali non devo richiedere il server.
        if (line1 == line2) {
            return "Esito POSITIVO (Local Check)";
        }

        //Devo verificare che la rete sia inizializzata.
        if (!networkState) throw new IllegalStateException("Bisogna inizializzare la rete prima");

        //Devo verificare che sia già stato trovato il servizio.
        if (servicePort < 0) throw new IllegalStateException("Bisogna cercare il servizio prima");

        //Ora posso chidere al servizio di swappare le righe:

        //Imposto la richiesta.
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            try (DataOutputStream dataStream = new DataOutputStream(byteStream)) {
                //Creo il messaggio per il discovery e lo salvo nel pacchetto.
                dataStream.writeUTF(line1 + "," + line2);
                packet.setData(byteStream.toByteArray());
            }
        }

        //Invio la richiesta
        packet.setPort(servicePort);
        socket.send(packet);

        //Attendo risposta.
        packet.setData(emptyBuffer);
        socket.receive(packet);

        //Decodifico la risposta:
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData())) {
            try (DataInputStream dataStream = new DataInputStream(byteStream)) {
                //Ricavo il risultato e lo mostro.
                String result = dataStream.readUTF();
                return result;
            }
        }

    }

    public static void main(String[] args) {
        //Programma client.

        //Controllo argomenti inline
        //RSClient IPDS portDS fileName

        if (args.length != 3) {
            System.out.println("RSClient IPDS portDS fileName");
            System.exit(1);
        }

        //Parsing degli argomenti
        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String filename = args[2];

        //Controllo delgli arogmenti
        //La porta viene comunque controllata. Ma la ricontrolliamo...
        if (!isPortValid(serverPort)) {
            System.err.println("Porta server non valida");
            System.exit(1);
        }

        //Controllo inutile: se la stringa fosse vuota non ci sarebbe l'argomento.
        //Lo sposto direttamente nel metodo per eccezione.
//        if(filename.isBlank()){
//            System.err.println("File name non valido");
//            System.exit(1);
//        }

        RSClient client = null;

        //Creo l'oggetto client.
        try {
            client = new RSClient(serverIP, serverPort);
        } /*catch (IllegalArgumentException e) {
            System.err.println(e.getLocalizedMessage());
            System.exit(1);
        } */catch (UnknownHostException e) {
            System.err.println("Server ip non valido");
            System.exit(1);
        }

        //Mi connetto al server, richiedo il servizio e avvio REPL.

        try {
            client.initNetwork();
        } catch (SocketException e) {
            System.err.println("Impossibile inizializzare la rete: " + e.getLocalizedMessage());
            System.exit(2);
        }

        System.out.println("Rete inizializzata: " + serverIP + ":" + serverPort);

        try {
            client.requestService(filename);
        } catch (IOException e) {
            System.err.println("Impossibile richiedere il servizio relativo a: " + filename);
            System.exit(3);
        }

        System.out.println("Servizio trovato: " + client.getServicePort());

        //REPL while

        //Roba per il REPL
        int line1 = -1, line2 = -1;
        String tmpString = null;
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

        try {
            while (true) {
                //Chiedo le linee da swappare:

                //LINEA 1

                System.out.println("Linea 1: ");
                tmpString = stdIn.readLine();

                if(tmpString == null){
                    //EOF. Termino.
                    System.exit(0);
                }

                try{
                    line1 = Integer.parseInt(tmpString);
                }catch (NumberFormatException ex){
                    System.out.println("Linea 1 malformata");
                    continue;
                }

                //LINEA 2

                System.out.println("Linea 2: ");
                tmpString = stdIn.readLine();

                if(tmpString == null){
                    //EOF. Termino.
                    System.exit(0);
                }

                try{
                    line2 = Integer.parseInt(tmpString);
                }catch (NumberFormatException ex){
                    System.out.println("Linea 2 malformata");
                    continue;//Nuovo ciclo REPL.
                }

                //Controllo delle linee.
                if(line1 <0 | line2 < 0){
                    System.out.println("Linee non valide (<0)");
                    continue;//Nuovo ciclo REPL.
                }

                //Posso fare lo swapping.
                String esito = client.swapLines(line1, line2);

                System.out.println(esito);
            }
        } catch (IOException e) {
            System.err.println("Errore nella REPL: " + e.getLocalizedMessage());
            System.exit(4);
        }


    }

}
