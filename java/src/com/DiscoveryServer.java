package com;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DiscoveryServer {
	
	private static final int INV_ERR = 1;
	private static final int INVALID_DS_PORT = 2;
	private static final int SOCKET_ERR = 3;
	private static final int RECEIVE_ERR = 4;
	private static final int UTF_ERR = 5;
	private static final int WRITEINT_ERR = 6;
	private static final int SEND_ERR = 7;
	private static final int FILE_NOT_EXISTS = 8;
	private static final int FILE_NOT_RDWR = 9;
	private static final int IO_ERROR = 10;
	private static final int CHK_DS_PORT = 11;
	private static final int CHK_FILENAME = 12;
	private static final int CHK_PORT = 12;
	
	private static FilePortStruct fps[] = null;

	public static void main(String[] args) {
		
		//DiscoveryServer portaDiscoveryServer nomefile1 port1 ... nomefileN portN
		
		// numero coppie file/port
		int nCoppie = (args.length - 1) / 2;
		
		//controllo che l'utente abbia inserito portaDS e almeno un file e una porta
		if(args.length < 3 || ((args.length - 1) % 2) != 0) {
			System.out.println("Usage: DiscoveryServer portaDiscoveryServer nomefile1 port1 ... nomefileN portN");
			System.exit(INV_ERR);
		}
		
		//controllo porta DiscoveryServer
		int dsPort = -1;

		try {
			dsPort = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			System.out.println("Invalid port: must be int 1024 < port < 64k");
			System.exit(INVALID_DS_PORT);
		}
		
		//se la porta del discovery server è out of range errore
		if(dsPort < 1024 || dsPort > 65536) {
			System.out.println("Invalid port: must be int 1024 < port < 64k");
			System.exit(INVALID_DS_PORT);
		}

		//Controllo le coppie file:porta.
		for (int i = 0, j = 1; i < nCoppie; i++, j+=2){

			//Coppia da controllare
			String filenameCheck = args[j];
			String portaCheck = args[j+1];

			if(args[0].equals(portaCheck)){
				System.err.println("La porta per RowSwapServer è già in uso dal discovery!");
				System.exit(CHK_DS_PORT);
			}

			for (int k = i+1, l = j+2; k < nCoppie; k++, l+=2){
				//Coppia da controllare (questa è quella che scorre tutte le restanti coppie)
				String filenameCheckLoop = args[l];
				String portaCheckLoop = args[l+1];

				//non prendiamo in ingresso file con lo stesso nome
				if(filenameCheck.equals(filenameCheckLoop)){
					System.err.println("filename già inserito");
					System.exit(CHK_FILENAME);
				}

				//non posso avere porte duplicate --> questo comporta che più RSS sarebbero in ascolto dietro alla stessa porta!
				if(portaCheck.equals(portaCheckLoop)){
					System.err.println("porta già inserita");
					System.exit(CHK_PORT);
				}
			}
		}
		
		//predispongo struttura per coppie file/port
		fps = new FilePortStruct[nCoppie];
		int argsIdx = 1;
				
		for (int i = 0; i < nCoppie; i++) {

			fps[i] = new FilePortStruct(args[argsIdx], args[argsIdx + 1]);
			argsIdx += 2;

			//Controllo che i file esistano: inoltre colgo l'occasione per contare le righe.
			//Controllo che il file esite.
			if(fps[i].isValid())
				if(!Files.exists(fps[i].getPath())){
					System.err.println("Il file non esite.");
					//Il prof ha detto che non si butta via nulla.
					//System.exit(FILE_NOT_EXISTS);
					fps[i].setValid(false);
				}

			//Controllo che il file sia accessibile sia in lettura sia in scrittura.
			if(fps[i].isValid())
				if(!Files.isReadable(fps[i].getPath()) || !Files.isWritable(fps[i].getPath())){
					System.err.println("Il file "+fps[i].getFilename()+" non è leggibile/scrivibile.");
					//Il prof ha detto che non si butta via nulla.
					//System.exit(FILE_NOT_RDWR);
					fps[i].setValid(false);
				}

			//Conto le righe.
			if(fps[i].isValid())
				try (BufferedReader bufferedReader = Files.newBufferedReader(fps[i].getPath(), StandardCharsets.UTF_8)){
					int tmp = 0;

					while(bufferedReader.readLine() != null) tmp++;
					//setto all'interno della struttura il numero di righe per ciascun file
					fps[i].setFileCount(tmp);

				} catch (IOException e) {
					System.err.println("Errore nell'aprire il file: "+e.getMessage());
					//Il prof ha detto che non si butta via nulla.
					//System.exit(IO_ERROR);
					fps[i].setValid(false);
				}

		}

		//creo array di RowSwapServer (thread figli) quanti il numero delle coppie passate
		RowSwapServer rss[] = new RowSwapServer[nCoppie];
		
		for (int i = 0; i < rss.length; i++) {
			//Avvio RowSwapServer solo se il file è valido.
			if(fps[i].isValid()){
				try {
					rss[i] = new RowSwapServer(fps[i]);
				} catch (SocketException e) {
					e.printStackTrace(); System.exit(SOCKET_ERR);
				}
				//attivo i vari RowSwapServer
				rss[i].start();
			}
		} 
		
		// Creo socket per comunicazione con il client
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(dsPort);
		} catch (SocketException e) {
			e.printStackTrace(); System.exit(SOCKET_ERR);
		}
		
		// preparo array di byte per ricezione dati dalla socket
		byte buf[] = new byte[256];
		DatagramPacket packet = new DatagramPacket(buf, 0, buf.length);
		
		//preparo strutture per lettura dati
		ByteArrayInputStream biStream = null;
		DataInputStream diStream = null;
		ByteArrayOutputStream boStream = null;
		DataOutputStream doStream = null;
		
		/*
		 *Ciclicamente il DS:
		 * 1) si pone in attesa della richiesta dei vari Client
		 * 2) ricava se possibile la porta corrispondente al file 
		 * 3) prepara e invia la risposta
		 */
		while (true) {
			packet.setPort(dsPort);
			packet.setData(buf, 0, buf.length); //devo risettare ciclicamente il buffer del pacchetto

			try {
				//mi pongo in attesa di un packet da parte di un client 
				socket.receive(packet); 
			} catch (IOException e) {
				e.printStackTrace(); System.exit(RECEIVE_ERR);
			}
			
			biStream = new ByteArrayInputStream(packet.getData());
			diStream = new DataInputStream(biStream);
			
			String richiesta = null;
			try {
				//leggo nome file inviato dal client --> risponderò con la corrispettiva porta (se corretto)
				richiesta = diStream.readUTF(); 
			} catch (IOException e) {
				e.printStackTrace(); System.exit(UTF_ERR);
			}

			boStream = new ByteArrayOutputStream();
			doStream = new DataOutputStream(boStream);
			
			try {
				int porta = getPortByFilename(richiesta); //trovo porta corrisp. se esiste
				if (porta == -1) { //se il file non esiste lo comunico
					doStream.writeUTF("Il file richiesto non esiste, quindi non c'è una porta corrispondente\n");
				} else { //altrimenti restituisco la porta corrisp.
					doStream.writeUTF(""+porta);
				}
			} catch (IOException e) {
				e.printStackTrace(); System.exit(WRITEINT_ERR);
			}
			
			//setto il contenuto della risposta
           		packet.setData(boStream.toByteArray());

			try {
				socket.send(packet); //invio risposta
			} catch (IOException e) {
				e.printStackTrace(); System.exit(SEND_ERR);
			}
		}
		
		// Se ipotizzassi di ammettere al massimo X richieste da parte dei client
		// allora al termine di questo ciclo (finito!) dovrei chiudere la socket
		// socket.close();
	}

	private static int getPortByFilename(String richiesta) {
		for (int i = 0; i < fps.length ; i++) {
			if(fps[i].getFilename().equals(richiesta))
				return fps[i].getPort();
		}
		return -1;
	}
	
}
