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
package de.voot.encfsanywhere.fs.shared.providers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.JsArray;

import de.voot.dropboxgwt.client.DropboxWrapper;
import de.voot.dropboxgwt.client.overlay.ApiError;
import de.voot.dropboxgwt.client.overlay.ArrayBuffer;
import de.voot.dropboxgwt.client.overlay.Stat;
import de.voot.encfsanywhere.fs.shared.io.DropboxInputStream;
import de.voot.encfsgwt.shared.jre.InputStream;
import de.voot.encfsgwt.shared.jre.OutputStream;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSFileInfo;

public class DropboxFileProvider implements FileProvider {

	private static final Logger LOG = Logger.getLogger("de.voot.encfsanywhere.fs.shared.providers.DropboxFileProvider");

	private DropboxWrapper dropboxWrapper;

	public DropboxFileProvider(DropboxWrapper dropboxWrapper) {
		this.dropboxWrapper = dropboxWrapper;
	}

	@Override
	public void isDirectory(String srcPath, final Callback<Boolean, IOException> callback) {
		getFileInfo(srcPath, new Callback<EncFSFileInfo, IOException>() {
			@Override
			public void onFailure(IOException reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(EncFSFileInfo result) {
				callback.onSuccess(result.isDirectory());
			}
		});
	}

	@Override
	public void exists(String srcPath, final Callback<Boolean, IOException> callback) {
		dropboxWrapper.metadata(srcPath, new Callback<Stat, ApiError>() {
			@Override
			public void onFailure(ApiError reason) {
				if (reason.getStatus() == 404) {
					callback.onSuccess(Boolean.FALSE);
				} else {
					callback.onFailure(new IOException(reason.getResponseText()));
				}
			}

			@Override
			public void onSuccess(Stat result) {
				callback.onSuccess(Boolean.TRUE);
			}
		});
	}

	@Override
	public String getFilesystemRootPath() {
		return "/";
	}

	@Override
	public void getFileInfo(String srcPath, final Callback<EncFSFileInfo, IOException> callback) {
		dropboxWrapper.metadata(srcPath, new Callback<Stat, ApiError>() {
			@Override
			public void onFailure(ApiError reason) {
				callback.onFailure(new IOException(reason.getResponseText()));
			}

			@Override
			public void onSuccess(Stat result) {
				EncFSFileInfo info = statToFileInfo(result);
				callback.onSuccess(info);
			}
		});
	}

	@Override
	public void listFiles(String dirPath, final Callback<List<EncFSFileInfo>, IOException> callback) {
		dropboxWrapper.readdir(dirPath, new Callback<JsArray<Stat>, ApiError>() {
			@Override
			public void onFailure(ApiError reason) {
				callback.onFailure(new IOException(reason.getResponseText()));
			}

			@Override
			public void onSuccess(JsArray<Stat> result) {
				List<EncFSFileInfo> list = new ArrayList<EncFSFileInfo>();
				for (int i = 0; i < result.length(); i++) {
					Stat stat = result.get(i);
					list.add(statToFileInfo(stat));
				}
				callback.onSuccess(list);
			}
		});
	}

	@Override
	public void openInputStream(String srcFilePath, final Callback<InputStream, IOException> callback) {
		dropboxWrapper.readFile(srcFilePath, new Callback<ArrayBuffer, ApiError>() {
			@Override
			public void onFailure(ApiError reason) {
				callback.onFailure(new IOException(reason.getResponseText()));
			}

			@Override
			public void onSuccess(ArrayBuffer result) {
				InputStream in = new DropboxInputStream(result);
				callback.onSuccess(in);
			}
		});
	}

	@Override
	public boolean move(String srcPath, String dstPath) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean delete(String srcPath) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean mkdir(String dirPath) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean mkdirs(String dirPath) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public EncFSFileInfo createFile(String dstFilePath) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean copy(String srcFilePath, String dstFilePath) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public OutputStream openOutputStream(String dstFilePath, long outputLength) throws IOException {
		throw new UnsupportedOperationException();
	}

	private EncFSFileInfo statToFileInfo(Stat result) {
		String path = result.getPath();
		int offset = path.lastIndexOf("/") + 1;
		String name = path.substring(offset);
		String parentPath = path.substring(0, offset);
		EncFSFileInfo info = new EncFSFileInfo(name, parentPath, result.isFolder(), 0, result.getSize(), true, false, false);
		return info;
	}

	@Override
	public void disconnect() {
		dropboxWrapper.signOut();
	}

}
