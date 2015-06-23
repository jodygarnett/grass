/* Copyright (c) 2013 - 2014 Boundless - http://boundlessgeo.com All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package com.boundlessgeo.wps.grass;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.wps.process.RawData;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.geotools.util.KVP;
import org.opengis.coverage.grid.GridCoverageWriter;

public class GrassProcesses extends StaticMethodsProcessFactory<GrassProcesses> {

	static String EXEC;

	public GrassProcesses() {
		super(Text.text("Geographic Resources Analysis Support System"),
				"grass", GrassProcesses.class);
		String system = System.getProperty("os.name").toLowerCase();
		String grass = GeoServerExtensions.getProperty("GRASS");
		if (grass != null) {
			EXEC = grass;
		} else if (system.contains("nix")) {
			EXEC = "/user/local/grass70";
		} else if (system.contains("mac")) {
			EXEC = "/Applications/GRASS-7.0.app/Contents/MacOS/grass70";
		} else if (system.contains("win")) {
			EXEC = "C:\\GRASS64\\GRASS70.exe";
		} else {
			EXEC = null;
		}
	}

	@DescribeProcess(title = "GRASS Verion", description = "Retreive the version of GRASS used for computation")
	@DescribeResult(description = "Version")
	public static String version() {
		if (EXEC == null ){
			return "unavailable";
		}
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("-v");
		
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			executor.setStreamHandler(new PumpStreamHandler(outputStream));
			int exitValue = executor.execute(cmd);
			return outputStream.toString();
		} catch (ExecuteException huh){
			return "unavailable: "+huh.getMessage();
		} catch (IOException e) {
			return "unavailable: "+e.getMessage();
		}
	}
	
	@DescribeProcess(title = "r.viewshed", description = "Computes the viewshed of a point on an elevation raster map.")
	@DescribeResult(name="viewshed",description="area visible from provided location")
	public static RawData viewshed(
			@DescribeParameter(name = "dem", description = "digitial elevation model") GridCoverage2D dem,
            @DescribeParameter(name = "x", description = "x location in map units") double x,
            @DescribeParameter(name = "y", description = "y location in map units") double y) throws Exception{
		
		File init[] = location( "viewshed", dem );
		
		File geodb = init[0];
		File location = init[1];
		File file = init[2];
		KVP kvp = new KVP(
				"geodb",geodb,
				"location",location,
				"file",file);
		
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("r.viewshed");
		cmd.addArgument("-c");
		
		
		cmd = new CommandLine(EXEC);
		cmd.addArgument("r.viewport");
		cmd.addArgument("--overwrite");
		cmd.addArgument("--verbose");
		cmd.addArgument("input="+file.getName());
		cmd.addArgument("output=viewshed.tiff");
		cmd.addArgument("coordinates="+x+","+y);
	
		cmd.addArgument("${location}");
		cmd.setSubstitutionMap(kvp);
	
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		executor.setWatchdog(new ExecuteWatchdog(60000));		
		executor.setStreamHandler(new PumpStreamHandler(System.out));
		
		executor.setWorkingDirectory(location);
		int exitValue = executor.execute(cmd);
		
		System.out.println("GRASS Enviornment:"+location);
		
		return null;
	}
	
	static File[] location( String operation, GridCoverage2D dem ) throws Exception {
		
		File geodb = new File(System.getProperty("user.home"),"grassdata");		
		File location = Files.createTempDirectory(geodb.toPath(),operation).toFile();
		location.delete();
		File file = new File( geodb, "dem.tiff");
		String mapset = "PERMANENT";		
		KVP kvp = new KVP(
				"geodb",geodb,
				"location",location,
				"file",file);
		/*
		// grass70 + ' -c epsg:' + myepsg + ' -e ' + location_path
		CoordinateReferenceSystem crs = dem.getCoordinateReferenceSystem();
		String code = CRS.toSRS(crs, true);
		
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("-c");
		cmd.addArgument("epsg:"+code);
		cmd.addArgument("-e");
		cmd.addArgument("${location}");
		cmd.setSubstitutionMap(kvp);

		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);
		
		executor.setStreamHandler(new PumpStreamHandler(System.out));
		int exitValue = executor.execute(cmd);
		*/
		
		final GeoTiffFormat format = new GeoTiffFormat();
		GridCoverageWriter writer = format.getWriter(file);
		writer.write(dem, null);
		System.out.println("Staging file:"+file);
		
		// grass70 + ' -c ' + myfile + ' -e ' + location_path
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("-c");
		cmd.addArgument("${file}");
		cmd.addArgument("-e");
		cmd.addArgument("${location}");
		cmd.setSubstitutionMap(kvp);
		
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);		
		executor.setStreamHandler(new PumpStreamHandler(System.out));
		int exitValue = executor.execute(cmd);

		System.out.println("GRASS Enviornment:"+location);
		
		File origional = file;
		file = new File(location, file.getName());
		Files.move(origional.toPath(),  file.toPath() );
		
		return new File[]{geodb,location,file};
	}
}
