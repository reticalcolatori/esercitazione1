package com;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FilePortStruct {
	
	private static final int INVALID_PORT = 11;


	private String fileName;
	private Path path;
	private int port;
	private int fileCount = -1;
	
	public FilePortStruct(String fileName, String port) {

		this.fileName = fileName;

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
		
		this.path = Path.of(new File(fileName).toURI());
	}

	public String getFilename() {
		return this.fileName;
	}

	public Path getPath(){
		return this.path;
	}

	public int getPort() {
		return this.port;
	}

	public int getFileCount() {
		return fileCount;
	}

	public void setFileCount(int fileCount) {
		this.fileCount = fileCount;
	}
}
