/*
  	Copyright (C) 2013 Marco Schulte

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
