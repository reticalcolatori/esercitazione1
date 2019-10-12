package com;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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

        while (true) {
            packet.setData(buf, 0, buf.length);

            try {
                socket.receive(packet); //attendo una richiesta da un client
            } catch (IOException e) {
            	//non dovrebbe entrare se non impostato timeout.
                e.printStackTrace();
                System.exit(RS_RECEIVE_ERR);
            }

            biStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            diStream = new DataInputStream(biStream);

            try {
                richiesta = diStream.readUTF(); //leggo le due righe separate da virgola
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(RS_READ_UTF_ERR);
            }

//			try {
//				diStream.close();
//				biStream.close();
//			} catch (IOException e) {
//				e.printStackTrace(); System.exit(IO_ERROR);
//			}

            st = new StringTokenizer(richiesta, ","); //splitto per trovare le due righe da scambiare
            riga1 = Integer.parseInt(st.nextToken());
            riga2 = Integer.parseInt(st.nextToken());

            //Scambio le righe e ritorno l'esito.
            String esito = swap(riga1, riga2);

            boStream = new ByteArrayOutputStream();
            doStream = new DataOutputStream(boStream);

			try {
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
				socket.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(RS_SEND_ERR);
			}

		}
    }

    private String swap(int riga1, int riga2) {

    	final String esitoOK = "OK";

    	if(riga1 == riga2) return esitoOK;

        //Controllo sulle righe.
        if (riga1 > struct.getFileCount() || riga2 > struct.getFileCount()) {
            return "Riga 1 o Riga 2 supera la dimensione del file. (" + struct.getFileCount() + ")";
        }

        //Leggo tutto il file e mi salvo le righe da swappare
        String inDaSwap1 = null;
        String inDaSwap2 = null;

        try (BufferedReader bufferedReader = Files.newBufferedReader(struct.getPath())) {
            //Leggo le righe.
            for (int i = 0; i < struct.getFileCount(); i++) {
                String tmpLine = bufferedReader.readLine();

                if (i == riga1) inDaSwap1 = tmpLine;
                if (i == riga2) inDaSwap2 = tmpLine;
            }
        } catch (IOException e) {
            String err = "Errore nell'aprire il file: " + e.getMessage();
            System.err.println(err);
            return err;

            //Non possiamo uscire dobbiamo inviare risposta al client.
//			System.exit(IO_ERR);
        }

        //Buffer temporaneo del file temporaneo.
		Path tmpPath = Path.of(new File(getId() + ".tmp").toURI());

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(tmpPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            try (BufferedReader bufferedReader = Files.newBufferedReader(struct.getPath(), StandardCharsets.UTF_8)) {
                //Leggo le righe.
                for (int i = 0; i < struct.getFileCount(); i++) {
                    String tmpLine = bufferedReader.readLine();

                    if (i == riga1) {
                        bufferedWriter.write(inDaSwap2);
                    } else if (i == riga2) {
                        bufferedWriter.write(inDaSwap1);
                    } else {
                        bufferedWriter.write(tmpLine);
                    }

                    bufferedWriter.newLine();
                }
            } catch (IOException e) {
                String err = "Errore nell'aprire il file: " + e.getMessage();
                System.err.println(err);
                return err;

//				System.exit(IO_ERR);
            }

        } catch (IOException e) {

            String err = "Impossibile creare il file temporaneo: " + e.getMessage();
            System.err.println(err);
            return err;

//			System.exit(TMP_FILE_ERR);
        }

		try {
			Files.move(tmpPath, struct.getPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			String err = "Impossibile spostare il file temporaneo: " + e.getMessage();
			System.err.println(err);
			return err;
		}


		return esitoOK;
    }

}
