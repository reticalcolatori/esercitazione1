package com;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class RowSwapServer extends Thread {

    private static final int RS_RECEIVE_ERR = 11;
    private static final int RS_READ_UTF_ERR = 12;
    private static final int RS_WRITE_UTF_ERR = 13;
    private static final int RS_SEND_ERR = 14;

    private FilePortStruct struct;
    private DatagramSocket socket;

    public RowSwapServer(FilePortStruct struct) throws SocketException {
        this.struct = struct;
        this.socket = new DatagramSocket(struct.getPort());
    }

    @Override
    public void run() {

        //preparo packet per ricezione/invio
        byte buf[] = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, 0, buf.length);

        //preparo strutture per lettura/scrittura dati
        ByteArrayInputStream biStream = null;
        DataInputStream diStream = null;
        ByteArrayOutputStream boStream = null;
        DataOutputStream doStream = null;
        String richiesta = null;
        int riga1 = -1;
        int riga2 = -1;
        StringTokenizer st = null;
        String esito = null;

        while (true) {
            //ciclicamente risetto il buffer del pacchetto
            packet.setData(buf, 0, buf.length);

            try {
                socket.receive(packet); //attendo una richiesta da un client
            } catch (IOException e) {
                //non dovrebbe entrare se non impostato timeout.
                e.printStackTrace();
//                System.exit(RS_RECEIVE_ERR);
                esito = "Impossibile ricevere messaggio";
            }

            biStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            diStream = new DataInputStream(biStream);

            try {
                richiesta = diStream.readUTF(); //leggo le due righe separate da virgola
            } catch (IOException e) {
                e.printStackTrace();
//                System.exit(RS_READ_UTF_ERR);
                esito = "richiesta malformata";
            }


            //Continuo solo la decodifica è andata a buon fine.
            if (esito == null) {
                st = new StringTokenizer(richiesta, ","); //splitto per trovare le due righe da scambiare

                try {
                    riga1 = Integer.parseInt(st.nextToken());
                    riga2 = Integer.parseInt(st.nextToken());

                    //Scambio le righe e ritorno l'esito.
                    esito = swap(riga1, riga2);

                } catch (NumberFormatException | NoSuchElementException e) {
                    esito = "righe malformate";
                }
            }

            boStream = new ByteArrayOutputStream();
            doStream = new DataOutputStream(boStream);

            try {
                //rispondo con esito dell'operazione di swap
                doStream.writeUTF(esito);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(RS_WRITE_UTF_ERR);
            }

            packet.setData(boStream.toByteArray());

            try {
                doStream.close();
                boStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                //non importa uscire ho già la risposta.
            }

            try {
                //invio la risposta con esito dello swap
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(RS_SEND_ERR);
            }

        }
    }

    private String swap(int riga1, int riga2) {

        final String esitoOK = "OK";

        //Giustamente il controllo viene fatto a livello client...
        //In questo caso isolato può anche andare, ma in un contesto più generale
        //dove il client viene implementato da terze parti, non sappiamo se hanno fatto il controllo.
        //if(riga1 == riga2) return esitoOK;

        //Controllo sulle righe (se superano la dimensione del file su cui insisto non ci provo nemmeno ritorno stringa errore
        if (riga1 > struct.getFileCount() || riga2 > struct.getFileCount()) {
            return "Riga 1 o Riga 2 supera la dimensione del file. (" + struct.getFileCount() + ")";
        }

        //Cerco la riga più in basso:
        //Nel ciclo mi fermerò lì.
        int maxLine = riga1 > riga2 ? riga1 : riga2;

        //Leggo tutto il file e mi salvo le righe da swappare
        String inDaSwap1 = null;
        String inDaSwap2 = null;

        try (BufferedReader bufferedReader = Files.newBufferedReader(struct.getPath())) {
            //Leggo le righe.
            for (int i = 0; i < struct.getFileCount(); i++) {
                String tmpLine = bufferedReader.readLine();

                if (i == riga1) inDaSwap1 = tmpLine;
                if (i == riga2) inDaSwap2 = tmpLine;
                if (i == maxLine) break;

            }
        } catch (IOException e) {
            String err = "Errore nell'aprire il file: " + e.getMessage();
            System.err.println(err);
            return err;
            //non esco ma rispondo con una stringa che rappresenta il problema
        }

        //Buffer temporaneo del file temporaneo.
        //Path tmpPath = Paths.get(new File(getId() + ".tmp").toURI());
        //Disponibile da Java 11
        Path tmpPath = Path.of(new File(getId() + ".tmp").toURI());

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(tmpPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            try (BufferedReader bufferedReader = Files.newBufferedReader(struct.getPath(), StandardCharsets.UTF_8)) {
                //Leggo le righe.
                for (int i = 0; i < struct.getFileCount(); i++) {
                    String tmpLine = bufferedReader.readLine();

                    if (i == riga1) { //se la riga letta è quella di indice riga1 allora ci scrivo la seconda
                        bufferedWriter.write(inDaSwap2);
                    } else if (i == riga2) { //se la riga letta è quella di indice riga2 allora ci scrivo la prima
                        bufferedWriter.write(inDaSwap1);
                    } else {
                        bufferedWriter.write(tmpLine);
                    }
                    bufferedWriter.newLine(); //dopo aver scritto la riga stampo il fine linea
                }
            } catch (IOException e) {
                String err = "Errore nell'aprire il file: " + e.getMessage();
                System.err.println(err);
                return err;
            }

        } catch (IOException e) {
            String err = "Impossibile creare il file temporaneo: " + e.getMessage();
            System.err.println(err);
            return err;
        }

        try {
            //sposto il file tmp in quello finale
            Files.move(tmpPath, struct.getPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            String err = "Impossibile spostare il file temporaneo: " + e.getMessage();
            System.err.println(err);
            return err;
        }
        //ritorno esito in formato di stringa dello swap
        return esitoOK;
    }

}
