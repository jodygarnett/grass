package com.boundlessgeo.wps.grass;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.geoserver.data.util.IOUtils;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class GrassProcessTest {

	static GrassProcesses grass;
	private static GeoTiffReader sfdem;

	@BeforeClass
	public static void init() {
		grass = new GrassProcesses();
	}

	@AfterClass
	public static void tearDown() {
		grass = null;
	}

	@Before
	public void before() throws IOException {
		File file = Files.createTempFile("sfdem", "tiff").toFile();
		InputStream resource = GrassProcessTest.class.getResourceAsStream("sfdem.tiff");
		IOUtils.copy(resource, file);

		GeoTiffFormat format = new GeoTiffFormat();
		sfdem = format.getReader(file);
	}

	public void after() {
		if (sfdem != null) {
			sfdem.dispose();
		}
	}

	@Ignore
	public void testVersion() {
		String version = GrassProcesses.version();
		System.out.println(version);
		assertTrue(version.contains("7.0.0"));
	}
	
	@Test
	public void testDEM() throws Exception {
		assertNotNull(sfdem);
		
		GridCoverage2D dem = sfdem.read(null);
		try {
			GrassProcesses.viewshed(dem, 599909.340659, 4923108.95604);
		} finally {
			dem.dispose(false);
		}
	}
}
