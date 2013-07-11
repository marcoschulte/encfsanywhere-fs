package de.voot.encfsanywhere.fs.shared.providers;

import de.voot.encfsgwt.shared.mrpdaemon.EncFSFileProvider;

public interface FileProvider extends EncFSFileProvider {
	public void disconnect();
}
