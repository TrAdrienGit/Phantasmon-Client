package com.mystaria.phantasmon.client.version;

/**
 * Pure version-comparison logic for the {@code GET /version} handshake (CAD
 * Partie 3 §E) — no Minecraft dependency, so it's covered by a plain JUnit
 * test rather than manual QA (per the client's testing convention).
 */
public final class VersionCompatibility {

	public enum Status {
		/** {@code client_version < min_supported_version} — connection must be refused. */
		INCOMPATIBLE,
		/** Compatible but behind {@code current_version} — non-blocking warning. */
		OUTDATED,
		UP_TO_DATE
	}

	private VersionCompatibility() {
	}

	public static Status evaluate(String clientVersion, String minSupportedVersion, String currentVersion) {
		if (compare(clientVersion, minSupportedVersion) < 0) {
			return Status.INCOMPATIBLE;
		}
		if (compare(clientVersion, currentVersion) < 0) {
			return Status.OUTDATED;
		}
		return Status.UP_TO_DATE;
	}

	/** Compares two dot-separated numeric versions (e.g. {@code "1.4.2"}); a trailing non-numeric suffix per segment is ignored. */
	static int compare(String a, String b) {
		int[] partsA = parse(a);
		int[] partsB = parse(b);
		int length = Math.max(partsA.length, partsB.length);
		for (int i = 0; i < length; i++) {
			int valueA = i < partsA.length ? partsA[i] : 0;
			int valueB = i < partsB.length ? partsB[i] : 0;
			if (valueA != valueB) {
				return Integer.compare(valueA, valueB);
			}
		}
		return 0;
	}

	private static int[] parse(String version) {
		String[] segments = version.split("\\.");
		int[] result = new int[segments.length];
		for (int i = 0; i < segments.length; i++) {
			String digitsOnly = segments[i].replaceAll("[^0-9].*$", "");
			result[i] = digitsOnly.isEmpty() ? 0 : Integer.parseInt(digitsOnly);
		}
		return result;
	}
}
