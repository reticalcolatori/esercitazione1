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
		
		if(dsPort < 1024 || dsPort > 65536) {
			System.out.println("Invalid port: must be int 1024 < port < 64k");
			System.exit(INVALID_DS_PORT);
		}

		//Controllo le coppie file:porta.
		for (int i = 0, j = 1; i < nCoppie; i++, j+=2){

			//Coppia di check
			String filenameCheck = args[j];
			String portaCheck = args[j+1];

			if(args[0].equals(portaCheck)){
				System.err.println("La porta DS già in uso per RowSwapServer");
				System.exit(CHK_DS_PORT);
			}

			for (int k = i+1, l = j+2; k < nCoppie; k++, l+=2){
				//Coppia di check secondo
				String filenameCheckLoop = args[l];
				String portaCheckLoop = args[l+1];

				if(filenameCheck.equals(filenameCheckLoop)){
					System.err.println("filename già inserito");
					System.exit(CHK_FILENAME);
				}

				if(portaCheck.equals(portaCheckLoop)){
					System.err.println("porta già inserito");
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
			if(!Files.exists(fps[i].getPath())){
				System.err.println("Il file non esite.");
				System.exit(FILE_NOT_EXISTS);
			}

			//Controllo che il file sia accessibile sia in lettura sia in scrittura.
			if(!Files.isReadable(fps[i].getPath()) || !Files.isWritable(fps[i].getPath())){
				System.err.println("Il file non è leggibile/scrivibile.");
				System.exit(FILE_NOT_RDWR);
			}

			//Conto le righe.
			try (BufferedReader bufferedReader = Files.newBufferedReader(fps[i].getPath(), StandardCharsets.UTF_8)){
				int tmp = 0;

				while(bufferedReader.readLine() != null) tmp++;
				fps[i].setFileCount(tmp);

			} catch (IOException e) {
				System.err.println("Errore nell'aprire il file: "+e.getMessage());
				System.exit(IO_ERROR);
			}

		}
		
		//controllo immissione dati
		for (int i = 0; i < fps.length; i++) {
			System.out.println(fps[i].getFilename() + " " + fps[i].getPort());
		}

		//creo array di RowSwapServer (thread figli)
		RowSwapServer rss[] = new RowSwapServer[nCoppie];
		
		for (int i = 0; i < rss.length; i++) {
			try {
				rss[i] = new RowSwapServer(fps[i]);
			} catch (SocketException e) {
				e.printStackTrace(); System.exit(SOCKET_ERR);
			}
			rss[i].start();
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
		
		while (true) {
//			packet.setPort(dsPort);
			packet.setData(buf, 0, buf.length); //i dati verranno scritti qui

			try {
				socket.receive(packet); //mi pongo in attesa di un packet da parte di un client
			} catch (IOException e) {
				e.printStackTrace(); System.exit(RECEIVE_ERR);
			}
			
			biStream = new ByteArrayInputStream(packet.getData());
			diStream = new DataInputStream(biStream);
			
			String richiesta = null;
			try {
				richiesta = diStream.readUTF(); //leggo nome file inviato dal client
			} catch (IOException e) {
				e.printStackTrace(); System.exit(UTF_ERR);
			}

//			try {
//				diStream.close();
//				biStream.close();
//			} catch (IOException e) {
//				e.printStackTrace(); System.exit(IO_ERROR);
//			}

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
			
			buf = boStream.toByteArray(); //converto lo stream in un byte di array
			packet.setData(buf, 0, buf.length);
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
