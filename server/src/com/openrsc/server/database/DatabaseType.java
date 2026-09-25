package com.openrsc.server.database;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum DatabaseType {
	MYSQL(0),
	SQLITE(1);

	private static final Map<Integer, DatabaseType> byType = new HashMap<Integer, DatabaseType>();
	public static final DatabaseType DEFAULT = SQLITE;

	static {
		for (DatabaseType type : DatabaseType.values()) {
			if (byType.put(type.getType(), type) != null) {
				throw new IllegalArgumentException("duplicate id: " + type.getType());
			}
		}
	}

	public static DatabaseType getByType(Integer type) {
		return byType.getOrDefault(type, DatabaseType.DEFAULT);
	}

	DatabaseType(int type) {
		this.type = type;
	}

	private final int type;

	/**
	 * Resolve a database type while retaining the historic permissive behaviour
	 * for callers outside server startup. Invalid values fall back to SQLite.
	 */
	public static DatabaseType resolveType(String type) {
		try {
			return resolveTypeStrict(type);
		} catch (IllegalArgumentException e) {
			return DatabaseType.DEFAULT;
		}
	}

	/**
	 * Resolve a database type without silently falling back to SQLite. A missing
	 * value still selects the default, but a malformed production value should
	 * stop startup instead of opening the wrong database.
	 */
	public static DatabaseType resolveTypeStrict(String type) {
		if (type == null || type.trim().isEmpty()) {
			return DatabaseType.DEFAULT;
		}

		String normalized = type.trim().toUpperCase(Locale.ENGLISH);
		try {
			return DatabaseType.valueOf(normalized);
		} catch (IllegalArgumentException e) {
			try {
				int numericType = Integer.parseInt(normalized);
				DatabaseType resolved = byType.get(numericType);
				if (resolved == null) {
					throw new IllegalArgumentException("Unsupported database type: " + type, e);
				}
				return resolved;
			} catch (NumberFormatException numberFormatException) {
				throw new IllegalArgumentException("Unsupported database type: " + type, e);
			}
		}
	}

    public int getType() {
		return this.type;
	}
}
