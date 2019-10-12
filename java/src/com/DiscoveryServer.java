package model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class DiscoveryServer {
	
	private static final int INV_ERR = 1;
	private static final int INVALID_DS_PORT = 2;
	private static final int SOCKET_ERR = 3;
	private static final int RECEIVE_ERR = 4;
	private static final int UTF_ERR = 5;
	private static final int WRITEINT_ERR = 6;
	private static final int SEND_ERR = 7;
	
	private static FilePortStruct fps[] = null;

	public static void main(String[] args) {
		
		//DiscoveryServer portaDiscoveryServer nomefile1 port1 ... nomefileN portN
		
		// numero coppie file/port
		int nCoppie = (args.length - 1) / 2;
		
		//controllo che l'utente abbia inserito portaDS e almeno un file e una porta
		if(args.length < 3 || (nCoppie % 2) != 0) {
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
		
		//predispongo struttura per coppie file/port
		fps = new FilePortStruct[nCoppie];
		int argsIdx = 1;
				
		for (int i = 0; i < nCoppie; i++) {
			fps[i] = new FilePortStruct(args[argsIdx], args[argsIdx + 1]);
			argsIdx += 2;
		}
		
		//controllo immissione dati
		for (int i = 0; i < fps.length; i++) {
			System.out.println(fps[i].getFileName() + " " + fps[i].getPort());
		}
		
		//creo array di RowSwapServer (thread figli)
		RowSwapServer rss[] = new RowSwapServer[nCoppie];
		
		for (int i = 0; i < rss.length; i++) {
			try {
				rss[i] = new RowSwapServer(fps[i].getFileName(), new DatagramSocket(fps[i].getPort()));
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
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		
		//preparo strutture per lettura dati
		ByteArrayInputStream biStream = null;
		DataInputStream diStream = null;
		ByteArrayOutputStream boStream = null;
		DataOutputStream doStream = null;
		
		while (true) {
			packet.setData(buf); //i dati verranno scritti qui
			try {
				socket.receive(packet); //mi pongo in attesa di un packet da parte di un client
			} catch (IOException e) {
				e.printStackTrace(); System.exit(RECEIVE_ERR);
			}
			
			biStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
			diStream = new DataInputStream(biStream);
			
			String richiesta = null;
			try {
				richiesta = diStream.readUTF(); //leggo nome file inviato dal client
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
					doStream.writeUTF("" + porta);
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
			if(richiesta == fps[i].getFileName())
				return fps[i].getPort();
		}
		return -1;
	}
	
	
}
