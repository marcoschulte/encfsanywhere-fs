package de.voot.encfsanywhere.fs.shared;

public class Path {

	private String path;

	protected Path(String path) {
		this.path = path;
	}

	@Override
	public String toString() {
		return path;
	}
}
