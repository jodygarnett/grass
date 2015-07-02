/* Copyright (c) 2013 - 2014 Boundless - http://boundlessgeo.com All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package com.boundlessgeo.wps.grass;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import static java.io.File.pathSeparator;
import java.io.FileWriter;
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
import org.geotools.gce.geotiff.GeoTiffReader;
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
	enum Env { LINUX, MAC, WINDOWS, UNKNOWN }
	
	private static final Logger LOGGER = Logging.getLogger("org.geoserver.wps.grass");
	static String EXEC;
	final static Env SYSTEM;
	static {
		String system = System.getProperty("os.name").toLowerCase();
		if (system.contains("nix") || system.contains("nux") || system.contains("aix")) {
			SYSTEM = Env.LINUX;
		}
		else if (system.contains("mac")) {
			SYSTEM = Env.MAC;
		}
		else if (system.contains("win")) {
			SYSTEM = Env.WINDOWS;
		}
		else {
			SYSTEM = Env.UNKNOWN;
		}
		
	}
	
	public GrassProcesses() {
		super(Text.text("Geographic Resources Analysis Support System"),
				"grass", GrassProcesses.class);
		
		String grass = GeoServerExtensions.getProperty("GRASS");
		if (grass != null) {
			LOGGER.info("defined GRASS="+grass);
			EXEC = grass;
		} else if (SYSTEM == Env.LINUX){
			EXEC = "/usr/local/bin/grass70";
			LOGGER.info("default GRASS="+EXEC);
		} else if (SYSTEM == Env.MAC){
			EXEC = "/Applications/GRASS-7.0.app/Contents/MacOS/grass70";
			LOGGER.info("default GRASS="+EXEC);
		} else if(SYSTEM == Env.WINDOWS){
			EXEC = "C:\\GRASS64\\GRASS70.exe";
			LOGGER.info("default GRASS="+EXEC);
		} else {
			LOGGER.warning(
				"GRASS default executable unavailable for '"+System.getProperty("os.name")+
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

	
	@DescribeProcess(title = "GRASS Version", description = "Retreive the version of GRASS used for computation")
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
		
		String COMMAND = "viewshed";
		File init[] = location( COMMAND, dem );
		File geodb = init[0];
		File location = init[1];
		File mapset = init[2];
		File file = init[3];		
				
		// see: http://grasswiki.osgeo.org/wiki/GRASS_and_Shell
		Map<String,String> env = EnvironmentUtils.getProcEnvironment();

		// GRASS ENV
		File GISBASE = new File(EXEC).getParentFile();
		String GRASS_VERSION = "7.0.0";
		EnvironmentUtils.addVariableToEnvironment(env, "GISBASE="+GISBASE);
		EnvironmentUtils.addVariableToEnvironment(env, "GRASS_VERSION="+GRASS_VERSION);
		
		File GISRC = new File(System.getProperty("user.home"),".grassrc."+GRASS_VERSION+"."+location.getName());
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(GISRC))) {
			writer.write("GISDBASE: " + geodb);
			writer.newLine();
			writer.write("LOCATION_NAME: " + location.getName());
			writer.newLine();
			writer.write("MAPSET: PERMANENT");
			writer.newLine();
			writer.write("GRASS_GUI: text");
			writer.newLine();
		}
		EnvironmentUtils.addVariableToEnvironment(env, "GISRC="+GISRC);

		// SYSTEM ENV
		String bin = new File(GISBASE,"bin").getAbsolutePath();
		String scripts = new File(GISBASE,"scripts").getAbsolutePath();				
		String lib = new File(GISBASE,"lib").getAbsolutePath();
		if (SYSTEM == Env.WINDOWS) {
			String PATH = env.get("PATH") + pathSeparator + bin + pathSeparator + scripts + pathSeparator + lib;
			EnvironmentUtils.addVariableToEnvironment(env, "PATH=" + PATH);
		} else {
			String PATH = env.get("PATH") + pathSeparator + bin + pathSeparator + scripts;
			EnvironmentUtils.addVariableToEnvironment(env, "PATH=" + PATH);
			if (SYSTEM == Env.LINUX) {
				String LD_LIBRARY_PATH = (env.containsKey("LD_LIBRARY_PATH")
						? env.get("LD_LIBRARY_PATH") + pathSeparator : "") + lib;
				EnvironmentUtils.addVariableToEnvironment(env, "LD_LIBRARY_PATH=" + LD_LIBRARY_PATH);
			} else if (SYSTEM == Env.MAC) {
				String DYLD_LIBRARY_PATH = (env.containsKey("DYLD_LIBRARY_PATH")
						? env.get("DYLD_LIBRARY_PATH") + pathSeparator : "") + lib;
				EnvironmentUtils.addVariableToEnvironment(env, "DYLD_LIBRARY_PATH=" + DYLD_LIBRARY_PATH);
			}
		}		
		// EXECUTE COMMAND
		File VIEWSHED = new File( bin, "r.viewshed");
		if( !VIEWSHED.exists()){
			throw new IllegalStateException("r.viewshed not found:"+VIEWSHED);
		}
		if( !VIEWSHED.canExecute()){
			throw new IllegalStateException("r.viewshed not executable:"+VIEWSHED);
		}
		File output = new File( mapset, "viewshed.tif");
		
		CommandLine cmd = new CommandLine( VIEWSHED );
		cmd.addArgument("input="+file.getName());
		cmd.addArgument("output="+output.getName());
		cmd.addArgument("coordinates=${x},${y}");	
		// cmd.addArgument("${mapset}");
		cmd.addArgument("--overwrite");
		cmd.setSubstitutionMap(new KVP("mapset", mapset,"output",output,"x",x,"y",y));
		LOGGER.info(cmd.toString());
			
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		executor.setWatchdog(new ExecuteWatchdog(60000));		
		executor.setStreamHandler(new PumpStreamHandler(System.out));		
		executor.setWorkingDirectory(mapset);
		
		int exitValue = executor.execute(cmd,env);
		
		File viewshed = new File( location, "viewshed.tif");
		if( !viewshed.exists() ){
			throw new IOException("Generated viweshed.tif not found");
		}
		final GeoTiffFormat format = new GeoTiffFormat();
		GeoTiffReader reader = format.getReader( viewshed );
		GridCoverage2D coverage = reader.read(null);
		
		return coverage;
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
	 * The dem is staged in GISBASE/dem.tif and then moved to
	 * GISBASE/LOCATION_NAME/PERMANENT/dem.tif
	 * 
	 * @param operation
	 *            Name used for the location on disk
	 * @param dem
	 *            File used to establish CRS and Bounds for the location
	 * @return Array of files consisting of {GISBASE, LOCATION, MAPSET, dem.tif}
	 * @throws Exception
	 */
	static File[] location( String operation, GridCoverage2D dem ) throws Exception {
		File geodb = new File(System.getProperty("user.home"),"grassdata");
		//File location = Files.createTempDirectory(geodb.toPath(),operation).toFile();
		File location = new File( geodb, operation );
		File mapset = new File( location, "PERMANENT" ); 
		File file = new File( geodb, "dem.tif");
		
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
		cmd.setSubstitutionMap(new KVP("file", file, "location", location));		
		LOGGER.info( cmd.toString() );
		
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);		
		executor.setStreamHandler(new PumpStreamHandler(System.out));
		int exitValue = executor.execute(cmd);
		
		File origional = file;
		file = new File(mapset, file.getName());
		Files.move(origional.toPath(),  file.toPath() );
		
		// r.in.gdal input=~/grassdata/viewshed/PERMANENT/dem.tif output=dem --overwrite

		
		return new File[]{geodb,location,mapset,file};
	}
}
