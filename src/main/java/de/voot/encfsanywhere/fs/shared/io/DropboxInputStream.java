package de.voot.encfsanywhere.fs.shared.io;

import java.io.IOException;

import de.voot.dropboxgwt.client.overlay.ArrayBuffer;
import de.voot.encfsgwt.shared.jre.InputStream;

public class DropboxInputStream extends InputStream {

	private ArrayBuffer arrayBuffer;
	private int pos = 0;
	private int size;

	public DropboxInputStream(ArrayBuffer arrayBuffer) {
		this.arrayBuffer = arrayBuffer;
		size = arrayBuffer.size();
	}

	@Override
	public int read() throws IOException {
		if (pos < size) {
			return arrayBuffer.getByte(pos++);
		}
		return -1;
	}

}
