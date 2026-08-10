package com.openrsc.server.util;

import com.openrsc.server.Server;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PersistenceManager {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private static final XStream xstream = new XStream();

	private final Server server;

	public PersistenceManager(Server server) {
		this.server = server;
		xstream.addPermission(AnyTypePermission.ANY);
		setupAliases();
	}

	public Object load(String filename) {
		File theFile = new File(getServer().getConfig().CONFIG_DIR, filename);
		boolean isGzipped = false;
		if (!theFile.exists()) {
			// fallback for old servers using .gz definitions
			theFile = new File(getServer().getConfig().CONFIG_DIR, filename + ".gz");
			isGzipped = true;
		}
		try (InputStream fileStream = new FileInputStream(theFile);
			 InputStream is = isGzipped ? new GZIPInputStream(fileStream) : fileStream) {
			return xstream.fromXML(is);
		} catch (IOException ioe) {
			LOGGER.catching(ioe);
		}
		return null;
	}

	protected void setupAliases() {
		try (FileInputStream fis = new FileInputStream(new File(getServer().getConfig().CONFIG_DIR, "aliases.xml"))) {
			Properties aliases = new Properties();
			aliases.loadFromXML(fis);
			for (Enumeration<?> e = aliases.propertyNames(); e.hasMoreElements(); ) {
				String alias = (String) e.nextElement();
				Class<?> c = Class.forName((String) aliases.get(alias));
				xstream.alias(alias, c);
			}
		} catch (Exception ioe) {
			LOGGER.catching(ioe);
		}
	}

	public void write(String filename, Object o) {
		try (OutputStream fileStream = new FileOutputStream(new File(getServer().getConfig().CONFIG_DIR, filename));
			 OutputStream os = filename.endsWith(".gz") ? new GZIPOutputStream(fileStream) : fileStream) {
			xstream.toXML(o, os);
		} catch (IOException ioe) {
			LOGGER.catching(ioe);
		}
	}

	public Server getServer() {
		return server;
	}
}
