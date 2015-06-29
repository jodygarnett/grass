/* Copyright (c) 2013 - 2014 Boundless - http://boundlessgeo.com All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package com.boundlessgeo.wps.grass;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.referencing.CRS;
import org.geotools.text.Text;
import org.geotools.util.KVP;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.grid.GridCoverageWriter;
import org.opengis.feature.type.Name;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class GrassProcesses extends StaticMethodsProcessFactory<GrassProcesses> {
	private static final Logger LOGGER = Logging.getLogger("org.geoserver.wps.grass");
	static String EXEC;

	public GrassProcesses() {
		super(Text.text("Geographic Resources Analysis Support System"),
				"grass", GrassProcesses.class);
		String system = System.getProperty("os.name").toLowerCase();
		String grass = GeoServerExtensions.getProperty("GRASS");
		if (grass != null) {
			LOGGER.info("defined GRASS="+grass);
			EXEC = grass;
		} else if (system.contains("nix") || system.contains("nux") || system.contains("aix")) {
			EXEC = "/usr/local/bin/grass70";
			LOGGER.info("default GRASS="+EXEC);
		} else if (system.contains("mac")) {
			EXEC = "/Applications/GRASS-7.0.app/Contents/MacOS/grass70";
			LOGGER.info("default GRASS="+EXEC);
		} else if (system.contains("win")) {
			EXEC = "C:\\GRASS64\\GRASS70.exe";
			LOGGER.info("default GRASS="+EXEC);
		} else {
			LOGGER.warning(
				"GRASS default executable unavailable for '"+system+
				"'. Please use GRASS environmental variable, context parameter or system property"
			);
			EXEC = null;
		}
		if( EXEC != null ){
			File exec = new File(EXEC);
			if( !exec.exists()){
				LOGGER.warning(EXEC+" does not exists");
				EXEC = null;
			}
			if( !exec.canExecute()){
				LOGGER.warning(EXEC+" not executable");
				EXEC = null;
			}
		}
	}
	
	@Override
	public Set<Name> getNames() {
		if( EXEC == null ){
			// do not advertise grass processes if executable is not available
			return Collections.emptySet();
		}
		return super.getNames();
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
			
			LOGGER.info("exec: "+cmd.toString());
			int exitValue = executor.execute(cmd);
			return outputStream.toString();
		} catch (ExecuteException huh){
			return "exit code: "+huh.getExitValue()+ " ("+huh.getMessage()+")";
		} catch (IOException e) {
			return "unavailable: "+e.getClass().getSimpleName()+":"+e.getMessage();
		}
	}
	
	@DescribeProcess(title = "r.viewshed", description = "Computes the viewshed of a point on an elevation raster map.")
	@DescribeResult(name="viewshed",description="area visible from provided location")
	public static GridCoverage2D viewshed(
			@DescribeParameter(name = "dem", description = "digitial elevation model")
			GridCoverage2D dem,
            @DescribeParameter(name = "x", description = "x location in map units")
			double x,
            @DescribeParameter(name = "y", description = "y location in map units")
			double y) throws Exception{
		
		File init[] = location( "viewshed", dem );
		File geodb = init[0];
		File location = init[1];
		File file = init[2];
		KVP kvp = new KVP(
				"geodb",geodb,
				"location",location,
				"file",file);
		System.out.println("GRASS Enviornment:"+location);
		
		CommandLine cmd = new CommandLine(EXEC);
		
		cmd = new CommandLine(EXEC);
		cmd.addArgument("r.viewshed");
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
		
		// see: http://grasswiki.osgeo.org/wiki/GRASS_and_Shell
		Map<String,String> env = EnvironmentUtils.getProcEnvironment();
		File GISBASE = new File(EXEC).getParentFile();
		EnvironmentUtils.addVariableToEnvironment(env, "GISBASE="+GISBASE);
		
		int exitValue = executor.execute(cmd,env);
		
		
		return dem;
	}
	
	/**
	 * Define a GISBASE/LOCATION_NAME for the provided dem.
	 * 
	 * @param operation Name used for the location on disk
	 * @param dem File used to establish CRS and Bounds for the location
	 * @return
	 * @throws Exception
	 */
	static File location( CoordinateReferenceSystem crs ) throws Exception {
		String code = CRS.toSRS(crs, true);
		File geodb = new File(System.getProperty("user.home"),"grassdata");
		File location = Files.createTempDirectory(geodb.toPath(),code).toFile();
		KVP kvp = new KVP(
				"geodb",geodb,
				"location",location);
		
		// grass70 + ' -c epsg:' + myepsg + ' -e ' + location_path
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("-c");
		cmd.addArgument("epsg:"+code);
		cmd.addArgument("-e");
		cmd.addArgument("${location}");
		cmd.setSubstitutionMap(kvp);

		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		executor.setWatchdog(new ExecuteWatchdog(60000));		
		executor.setStreamHandler(new PumpStreamHandler(System.out));
		
		int exitValue = executor.execute(cmd);		
		return location;
	}
	
	/**
	 * Define a GISBASE/LOCATION_NAME/PERMANENT for the provided dem.
	 * 
	 * @param operation Name used for the location on disk
	 * @param dem File used to establish CRS and Bounds for the location
	 * @return
	 * @throws Exception
	 */
	static File[] location( String operation, GridCoverage2D dem ) throws Exception {
		File geodb = new File(System.getProperty("user.home"),"grassdata");
		File location = Files.createTempDirectory(geodb.toPath(),operation).toFile();		
		File file = new File( geodb, "dem.tiff");
		
		String mapset = "PERMANENT";		
		KVP kvp = new KVP(
				"geodb",geodb,
				"location",location,
				"file",file);
		
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
