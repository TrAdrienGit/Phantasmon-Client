package com.mystaria.phantasmon.client.network;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendUrlFileTest {

	@TempDir
	Path dir;

	@Test
	void missingFileIsCreatedWithTheDefault() throws IOException {
		Path file = dir.resolve("config/phantasmon.json");

		assertEquals(URI.create(BackendUrlFile.DEFAULT_URL), BackendUrlFile.load(file));
		assertTrue(Files.readString(file).contains("\"backend_url\": \"" + BackendUrlFile.DEFAULT_URL + "\""));
	}

	@Test
	void configuredUrlIsUsed() throws IOException {
		Path file = write("{\"backend_url\": \"https://phantasmon.example.org:8443/\"}");

		assertEquals(URI.create("https://phantasmon.example.org:8443"), BackendUrlFile.load(file));
	}

	@Test
	void invalidContentFallsBackToTheDefault() throws IOException {
		URI fallback = URI.create(BackendUrlFile.DEFAULT_URL);
		assertEquals(fallback, BackendUrlFile.load(write("not json {")));
		assertEquals(fallback, BackendUrlFile.load(write("{}")));
		assertEquals(fallback, BackendUrlFile.load(write("{\"backend_url\": \"ftp://host\"}")));
		assertEquals(fallback, BackendUrlFile.load(write("{\"backend_url\": 42}")));
	}

	@Test
	void onlyBareHttpOrHttpsOriginsAreAccepted() {
		assertEquals(URI.create("http://127.0.0.1:8080"), BackendUrlFile.parse(" http://127.0.0.1:8080 "));
		assertNull(BackendUrlFile.parse("localhost:8080"));
		assertNull(BackendUrlFile.parse("http://host:8080/api"));
		assertNull(BackendUrlFile.parse("http://host:8080?x=1"));
		assertNull(BackendUrlFile.parse("http://"));
		assertNull(BackendUrlFile.parse(""));
	}

	private Path write(String content) throws IOException {
		Path file = dir.resolve("phantasmon.json");
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}
}
