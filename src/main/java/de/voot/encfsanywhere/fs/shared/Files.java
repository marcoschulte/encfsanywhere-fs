package de.voot.encfsanywhere.fs.shared;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.google.gwt.core.client.Callback;

import de.voot.encfsanywhere.fs.shared.providers.FileProvider;
import de.voot.encfsanywhere.fs.shared.providers.PathRewritingWrapper;
import de.voot.encfsgwt.shared.jre.InputStream;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSFile;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSFileInfo;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSFileProvider;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSInputStream;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSVolume;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSVolumeBuilder;
import de.voot.encfsgwt.shared.mrpdaemon.EncFSVolumeBuilder.PasswordBuilder;

public class Files {

	private static final Logger LOG = Logger.getLogger("Files");

	private class PathInfo {
		Path path;
		long lastModified;
		boolean isDirectory;
		Boolean isEncFSRoot;
		long size;
		Path[] children;
	}

	private class EncFSPath {
		String root;
		String remaining;
		String full;
	}

	private Map<String, PathInfo> cache = new HashMap<String, PathInfo>();
	private Map<String, EncFSFile> encFSFileCache = new HashMap<String, EncFSFile>();
	private Map<String, EncFSVolume> encfsVolumes = new HashMap<String, EncFSVolume>();
	private FileProvider provider;

	public Files(FileProvider provider) {
		this.provider = provider;
	}

	public void disconnect() {
		LOG.info("Disconnecting");
		provider.disconnect();
	}

	public String getName(Path path) {
		String s = path.toString();
		return s.substring(s.lastIndexOf("/") + 1);
	}

	public long getSize(Path path) {
		PathInfo info = cache.get(path.toString());
		return info.size;
	}

	public long getLastModified(Path path) {
		PathInfo info = cache.get(path.toString());
		return info.lastModified;
	}

	public boolean isDirectory(Path path) {
		PathInfo info = cache.get(path.toString());
		return info.isDirectory;
	}

	/**
	 * Returns <code>null</code> if information is not available. Information is
	 * not available until <code>listFiles</code> has been called on given path.
	 * 
	 * @param path
	 * @return
	 */
	public Boolean isEncFSRoot(Path path) {
		PathInfo info = cache.get(path.toString());
		return info.isEncFSRoot;
	}

	public boolean isEncFSRootUnlocked(Path path) {
		return encfsVolumes.containsKey(path.toString());
	}

	public String getParentPath(Path path) {
		PathInfo info = cache.get(path.toString());
		String p = info.path.toString();
		String parent = p.substring(0, p.lastIndexOf("/"));
		return parent.isEmpty() ? "/" : parent;
	}

	public void unlock(final Path encFSRoot, String password, final Callback<Void, Exception> callback) {
		EncFSFileProvider newProvider = new PathRewritingWrapper(encFSRoot.toString(), provider);
		new EncFSVolumeBuilder().withFileProvider(newProvider).withPassword(password, new Callback<EncFSVolumeBuilder.PasswordBuilder, Exception>() {
			@Override
			public void onFailure(Exception reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(PasswordBuilder result) {
				try {
					result.buildVolume(new Callback<EncFSVolume, Exception>() {
						@Override
						public void onFailure(Exception reason) {
							callback.onFailure(reason);
						}

						@Override
						public void onSuccess(EncFSVolume result) {
							removeChildrenFromCache(encFSRoot.toString());
							encfsVolumes.put(encFSRoot.toString(), result);
							callback.onSuccess(null);
						}
					});
				} catch (Exception e) {
					callback.onFailure(e);
				}
			}
		});
	}

	public void pathForName(String path, final Callback<Path, Exception> callback) {
		LOG.info("Determining path object for path <" + path + ">");
		PathInfo info = cache.get(path.toString());

		if (info != null) {
			LOG.info("Found cached pathinfo");
			callback.onSuccess(info.path);

		} else {
			EncFSPath encfsPath = unlockedRootForPath(path);
			if (encfsPath != null) {
				pathFromEncFS(encfsPath, callback);
			} else {
				pathFromUnencryptedFS(path, callback);
			}
		}
	}

	private void pathFromUnencryptedFS(String path, final Callback<Path, Exception> callback) {
		LOG.info("Determining path object for path <" + path + ">, treated as unencrypted path");
		provider.getFileInfo(path, new Callback<EncFSFileInfo, IOException>() {
			@Override
			public void onFailure(IOException reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(EncFSFileInfo result) {
				final Path newPath = new Path(result.getPath());
				createCacheEntry(newPath, result);
				callback.onSuccess(newPath);
			}
		});
	}

	private void pathFromEncFS(final EncFSPath path, final Callback<Path, Exception> callback) {
		LOG.info("Determining path object for path <" + path + ">, treated as encrypted path");
		EncFSVolume volume = volumeForPath(path.root);
		volume.getFile(path.remaining, new Callback<EncFSFile, Exception>() {
			@Override
			public void onFailure(Exception reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(EncFSFile result) {
				final Path newPath = new Path(path.full);
				createCacheEntry(newPath, result);
				callback.onSuccess(newPath);
			}
		});
	}

	public void listFiles(Path path, final Callback<Path[], Exception> callback) {
		LOG.info("Listing files of path <" + path + ">");
		if (!isDirectory(path)) {
			callback.onFailure(new IOException("Not a directory"));
		}

		final PathInfo info = cache.get(path.toString());

		if (info.children == null) {
			EncFSPath encfsPath = unlockedRootForPath(path.toString());
			if (encfsPath != null) {
				listFilesFromEncryptedPath(encfsPath, info, callback);
			} else {
				listFilesFromUnencryptedPath(path, info, callback);
			}
		} else {
			callback.onSuccess(info.children);
		}
	}

	private void listFilesFromUnencryptedPath(Path path, final PathInfo info, final Callback<Path[], Exception> callback) {
		LOG.info("Listing from unencrypted path");
		provider.listFiles(path.toString(), new Callback<List<EncFSFileInfo>, IOException>() {
			@Override
			public void onFailure(IOException reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(List<EncFSFileInfo> result) {
				Path[] paths = new Path[result.size()];

				info.isEncFSRoot = false;
				for (int i = 0; i < result.size(); i++) {
					EncFSFileInfo encFSFileInfo = result.get(i);
					Path newPath = new Path(encFSFileInfo.getPath());
					paths[i] = newPath;
					createCacheEntry(newPath, encFSFileInfo);
					if (EncFSVolume.CONFIG_FILE_NAME.equals(encFSFileInfo.getName())) {
						info.isEncFSRoot = true;
					}
				}
				info.children = paths;
				callback.onSuccess(info.children);
			}
		});
	}

	private void listFilesFromEncryptedPath(final EncFSPath path, final PathInfo info, final Callback<Path[], Exception> callback) {
		LOG.info("Listing from encrypted path");
		final Callback<EncFSFile[], Exception> innerCallback = new Callback<EncFSFile[], Exception>() {
			@Override
			public void onFailure(Exception reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(EncFSFile[] result) {
				Path[] paths = new Path[result.length];

				info.isEncFSRoot = false;
				for (int i = 0; i < result.length; i++) {
					EncFSFile encFSFile = result[i];
					Path newPath = new Path(path.root + encFSFile.getPath());
					paths[i] = newPath;
					createCacheEntry(newPath, encFSFile);
					if (EncFSVolume.CONFIG_FILE_NAME.equals(encFSFile.getName())) {
						info.isEncFSRoot = true;
					}
				}
				info.children = paths;
				callback.onSuccess(info.children);
			}
		};

		EncFSFile encFSFile = encFSFileCache.get(path.toString());
		if (encFSFile != null) {
			encFSFile.listFiles(innerCallback);
		} else {
			EncFSVolume volume = volumeForPath(path.root);
			String p = path.remaining.length() == 0 ? "/" : path.remaining;
			volume.listFilesForPath(p, innerCallback);
		}
	}

	/**
	 * Returns file content as InputStream
	 * 
	 * @param path
	 * @param callback
	 */
	public void getFileContent(Path path, final Callback<InputStream, Exception> callback) {
		LOG.info("Loading file content for path <" + path + ">");
		final Callback<InputStream, IOException> actualCallback = new Callback<InputStream, IOException>() {
			@Override
			public void onFailure(IOException reason) {
				callback.onFailure(reason);
			}

			@Override
			public void onSuccess(InputStream result) {
				callback.onSuccess(result);
			}
		};

		final Callback<EncFSInputStream, Exception> encfsCallback = new Callback<EncFSInputStream, Exception>() {
			@Override
			public void onFailure(Exception reason) {
				actualCallback.onFailure(new IOException(reason));
			}

			@Override
			public void onSuccess(EncFSInputStream result) {
				actualCallback.onSuccess(result);
			}
		};

		if (isDirectory(path)) {
			callback.onFailure(new IOException("Path is a directory"));
		}

		EncFSPath encfsPath = unlockedRootForPath(path.toString());
		if (encfsPath != null) {
			EncFSFile encFSFile = encFSFileCache.get(encfsPath.full);
			if (encFSFile != null) {
				encFSFile.openInputStream(encfsCallback);
			} else {
				EncFSVolume volume = volumeForPath(encfsPath.root);
				volume.openInputStreamForPath(encfsPath.remaining, encfsCallback);
			}
		} else {
			provider.openInputStream(path.toString(), actualCallback);
		}
	}

	private void createCacheEntry(Path path, EncFSFileInfo encFSFileInfo) {
		LOG.info("Creating cache entry for unencrypted path <" + path + ">");
		PathInfo info = new PathInfo();
		info.isDirectory = encFSFileInfo.isDirectory();
		info.lastModified = encFSFileInfo.getLastModified();
		info.path = path;
		info.size = encFSFileInfo.getSize();
		cache.put(path.toString(), info);
	}

	private void createCacheEntry(Path path, EncFSFile encFSFile) {
		LOG.info("Creating cache entry for encrypted path <" + path + ">");
		PathInfo info = new PathInfo();
		info.isDirectory = encFSFile.isDirectory();
		info.lastModified = encFSFile.getLastModified();
		info.path = path;
		info.size = encFSFile.getLength();

		cache.put(path.toString(), info);
		encFSFileCache.put(path.toString(), encFSFile);
	}

	/**
	 * Returns EncFSVolume for the given root-path. Throws an exception if no
	 * unlocked volume is existing with given root-path.
	 * 
	 * @param path
	 * @return
	 */
	private EncFSVolume volumeForPath(String rootPath) {
		EncFSVolume volume = encfsVolumes.get(rootPath);
		if (volume != null) {
			return volume;
		}

		throw new RuntimeException("Invalid encfs-path");
	}

	/**
	 * Returns the root-path of an unlocked encfs volume to a given path.
	 * Returns <code>null</code> if the path is not within an encfs volume or if
	 * it is not unlocked yet.
	 * 
	 * @param path
	 * @return
	 */
	private EncFSPath unlockedRootForPath(String path) {
		String root = null;
		for (Entry<String, EncFSVolume> entry : encfsVolumes.entrySet()) {
			if (path.startsWith(entry.getKey())) {
				root = entry.getKey();
				break;
			}
		}
		if (root != null) {
			EncFSPath result = new EncFSPath();
			result.full = path;
			result.root = root;
			result.remaining = path.substring(root.length());
			return result;
		}

		return null;
	}

	private void removeChildrenFromCache(String path) {
		LOG.info("Removing cache entries for path <" + path + "> and its children");
		PathInfo info = cache.get(path);
		info.children = null;

		List<String> toRemove = new ArrayList<String>();
		for (String key : cache.keySet()) {
			if (key.startsWith(path)) {
				toRemove.add(key);
			}
		}
		for (String key : toRemove) {
			cache.remove(key);
		}

		cache.put(path, info);
	}

	/**
	 * Return the decrypted path for the given encrypted path. Only looks within
	 * the cache, so the given path must have appeared before (aka a Path object
	 * must have been created for this path).
	 * 
	 * @param path
	 * @return
	 */
	public String decryptCachedPathname(String path) {
		for (Entry<String, EncFSFile> entry : encFSFileCache.entrySet()) {
			// TODO: a bit dodgy with path.endsWith. Unfortunately entry.getValue().getEncryptedPath() does not contain the path to its encfs root.
			if (path.endsWith(entry.getValue().getEncryptedPath())) {
				return entry.getKey();
			}
		}
		return null;
	}
}
