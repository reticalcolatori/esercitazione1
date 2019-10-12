package model;

public class FilePortStruct {
	
	private static final int INVALID_PORT = 11;
	
	private String fileName;
	private int port;
	
	public FilePortStruct(String fileName, String port) {
		
		try {
			this.port = Integer.parseInt(port);
		} catch (NumberFormatException e) {
			System.out.println("Invalid port: must be int 1024 < port < 64k");
			System.exit(INVALID_PORT);
		}
		
		// DEVO CONTROLLARE OGNI VOLTA CHE LA PORTA NON SIA GIA' UTILIZZATA!!!!!!
		if(this.port < 1024 || this.port > 65536) {
			System.out.println("Invalid port: must be int 1024 < port < 64k");
			System.exit(INVALID_PORT);
		}
		
		this.fileName = fileName;
	}

	public String getFileName() {
		return this.fileName;
	}

	public int getPort() {
		return this.port;
	}
	
}
