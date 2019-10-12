package model;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.StringTokenizer;

public class RowSwapServer extends Thread {
	
	private static final int RS_RECEIVE_ERR = 11;
	private static final int RS_READ_UTF_ERR = 12;
	
	private String fileName;
	private DatagramSocket socket;

	public RowSwapServer(String fileName, DatagramSocket port) {
		this.fileName = fileName;
		this.socket = port;
	}

	@Override
	public void run() {
		
		//preparo packet per ricezione/invio
		byte buf[] = new byte[256];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		
		//preparo strutture per lettura/scrittura dati
		ByteArrayInputStream biStream = null;
		DataInputStream diStream = null;
		String richiesta = null;
		String riga1 = null;
		String riga2 = null;
		StringTokenizer st = null;
		
		while(true) {
			
			packet.setData(buf);
			try {
				socket.receive(packet); //attendo una richiesta da un client
			} catch (IOException e) {
				e.printStackTrace(); System.exit(RS_RECEIVE_ERR);
			}
			
			biStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
			diStream = new DataInputStream(biStream);
			
			try {
				richiesta = diStream.readUTF(); //leggo le due righe separate da virgola
			} catch (IOException e) {
				e.printStackTrace(); System.exit(RS_READ_UTF_ERR);
			}
			
			st = new StringTokenizer(richiesta, ","); //splitto per trovare le due righe da scambiare
			riga1 = st.nextToken();
			riga2 = st.nextToken();
			
			//devo implementare la logica di scambio righe
			//controllo che esistano le righe
			//devo rispondere al client
			//devo stampare su stdout l'esito dell'operazione
			
		}
	}
	
	private String swap(String riga1, String riga2) {
		return "";
	}
	
}
