package de.voot.encfsanywhere.fs.shared.providers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.Callback;

import de.voot.encfsgwt.shared.jre.InputStream;
import de.voot.encfsgwt.shared.jre.OutputStream;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSFileInfo;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSFileProvider;

public class PathRewritingWrapper implements EncFSFileProvider {

	private final String rootFolder;
	private final EncFSFileProvider provider;

	public PathRewritingWrapper(String rootFolder, EncFSFileProvider provider) {
		this.rootFolder = rootFolder;
		this.provider = provider;
	}

	private EncFSFileInfo convertFileInfo(EncFSFileInfo info) {
		String path = info.getPath().substring(rootFolder.length());
		if (path.isEmpty()) {
			path = "/";
		}

		String name = null;
		String parentPath = null;
		if ("/".equals(path)) {
			name = "/";
			parentPath = "";
		} else {
			name = path.substring(path.lastIndexOf("/") + 1);
			parentPath = path.substring(0, path.lastIndexOf("/") + 1);
		}
		return new EncFSFileInfo(name, parentPath, info.isDirectory(), info.getLastModified(), info.getSize(), info.isReadable(), info.isWritable(),
				info.isExecutable());
	}

	@Override
	public void isDirectory(String srcPath, Callback<Boolean, IOException> callback) {
		provider.isDirectory(rootFolder + srcPath, callback);
	}

	@Override
	public void exists(String srcPath, Callback<Boolean, IOException> callback) {
		provider.exists(rootFolder + srcPath, callback);
	}

	@Override
	public String getFilesystemRootPath() {
		return provider.getFilesystemRootPath();
	}

	@Override
	public void getFileInfo(String srcPath, final Callback<EncFSFileInfo, IOException> callback) {
		provider.getFileInfo(rootFolder + srcPath, new Callback<EncFSFileInfo, IOException>() {
			@Override
			public void onFailure(IOException reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(EncFSFileInfo result) {
				callback.onSuccess(convertFileInfo(result));
			}
		});
	}

	@Override
	public void listFiles(String dirPath, final Callback<List<EncFSFileInfo>, IOException> callback) {
		provider.listFiles(rootFolder + dirPath, new Callback<List<EncFSFileInfo>, IOException>() {
			@Override
			public void onFailure(IOException reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(List<EncFSFileInfo> result) {
				List<EncFSFileInfo> list = new ArrayList<EncFSFileInfo>(result.size());
				for (EncFSFileInfo info : result) {
					list.add(convertFileInfo(info));
				}
				callback.onSuccess(list);
			}
		});
	}

	@Override
	public boolean move(String srcPath, String dstPath) throws IOException {
		return provider.move(rootFolder + srcPath, rootFolder + dstPath);
	}

	@Override
	public boolean delete(String srcPath) throws IOException {
		return provider.delete(rootFolder + srcPath);
	}

	@Override
	public boolean mkdir(String dirPath) throws IOException {
		return provider.mkdir(rootFolder + dirPath);
	}

	@Override
	public boolean mkdirs(String dirPath) throws IOException {
		return provider.mkdirs(rootFolder + dirPath);
	}

	@Override
	public EncFSFileInfo createFile(String dstFilePath) throws IOException {
		return provider.createFile(rootFolder + dstFilePath);
	}

	@Override
	public boolean copy(String srcFilePath, String dstFilePath) throws IOException {
		return provider.copy(rootFolder + srcFilePath, rootFolder + dstFilePath);
	}

	@Override
	public void openInputStream(String srcFilePath, Callback<InputStream, IOException> callback) {
		provider.openInputStream(rootFolder + srcFilePath, callback);
	}

	@Override
	public OutputStream openOutputStream(String dstFilePath, long outputLength) throws IOException {
		return provider.openOutputStream(rootFolder + dstFilePath, outputLength);
	}

}
