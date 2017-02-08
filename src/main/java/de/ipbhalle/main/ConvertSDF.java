package de.ipbhalle.main;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import javax.imageio.ImageIO;

import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.renderer.AtomContainerRenderer;
import org.openscience.cdk.renderer.RendererModel;
import org.openscience.cdk.renderer.font.AWTFontManager;
import org.openscience.cdk.renderer.generators.BasicAtomGenerator;
import org.openscience.cdk.renderer.generators.BasicAtomGenerator.AtomRadius;
import org.openscience.cdk.renderer.generators.BasicBondGenerator;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator;
import org.openscience.cdk.renderer.generators.IGenerator;
import org.openscience.cdk.renderer.visitor.AWTDrawVisitor;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableImage;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;

public class ConvertSDF {

	public static String fileSep = System.getProperty("file.separator");
	public static String sdfFile = "";
	public static String resultspath = "";
	public static String fileName = "";
	public static String format = "csv";
	public static boolean fast = false;
	public static boolean withImages = false;
	public static Map<String, Integer> labels = new HashMap<String, Integer>(); 
	public static java.util.Vector<String> skipWhenMissing;
	
	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if(args.length <= 1) {
			System.out.println("usage: command sdf='sdf file' out='output folder' [img='with images'] [format='format'] [fast=false] [skipEntry=PROPERTY1[,PROPERTY2,...]]");
			System.out.println("\t\tfast mode only available for csv format");
			System.exit(1);
		}
		
		String arg_string = args[0];
		for(int i = 1; i < args.length; i++) {
			arg_string += " " + args[i];
		}
		arg_string = arg_string.replaceAll("\\s*=\\s*", "=").trim();
		
		
		String[] args_spaced = arg_string.split("\\s+");
		for(int i = 0; i < args_spaced.length; i++) {
			String[] tmp = args_spaced[i].split("=");
			if(tmp[0].equals("sdf")) sdfFile = tmp[1];
			else if(tmp[0].equals("out")) resultspath = tmp[1];
			else if(tmp[0].equals("img") && tmp[1].charAt(0) == '1') withImages = true;
			else if(tmp[0].equals("format") && (tmp[1].equals("xls") || tmp[1].equals("csv"))) format = tmp[1];
			else if(tmp[0].equals("fast") && (tmp[1].equals("true"))) fast = true;
			else if(tmp[0].equals("skipEntry")) {
				String[] propertyNames = tmp[1].split(",");
				skipWhenMissing = new java.util.Vector<String>();
				for(String property : propertyNames)
					skipWhenMissing.add(property);
			}
			else {
				System.err.println("Parameter unknown " + args_spaced[i]);
				System.exit(1);
			}
		}

		String inputSDF = "";

		if(sdfFile.equals("STDIN")) {
			inputSDF = getInputStringSDF();
			if(inputSDF.length() == 0) {
				System.err.println("No input given by STDIN");
				System.exit(1);
			}
		}
		
		// get filereader for the sdf file
		File file = new File(sdfFile);
		
		FileReader fileReader = null;
		if(inputSDF.length() == 0) {
			try {
				fileReader = new FileReader(file);
				if(!file.getName().endsWith(".sdf")) {
					System.out.println("sdf file extension missing");
					fileReader.close();
					throw new Exception();
				}
				// determine xls file name
				fileName = file.getName().split("\\.sdf")[0];
				if(fileName.length() == 0) {
					System.err.println("No valid name for xls/csv file.");
					fileReader.close();
					throw new Exception();
				}
			} catch (FileNotFoundException e) {
				System.err.println("Could not read sdf file. Is it valid?");
				System.exit(1);
			} catch(Exception e) {
				System.err.println("Valid sdf file?");
				System.exit(1);
			}
		}
		if(fast || inputSDF.length() != 0) {
			try {
				if(inputSDF.length() == 0) fastCSVWriter(file);
				else fastCSVWriter(inputSDF);
				return;
			} catch (IOException e1) {
				e1.printStackTrace();
				return;
			}
		}
		// read in sdf file
		List<IAtomContainer> containersList = new ArrayList<IAtomContainer>();
		IteratingSDFReader reader = new IteratingSDFReader(fileReader, DefaultChemObjectBuilder.getInstance());
		while(reader.hasNext()) {
			containersList.add((IAtomContainer)reader.next());
		}
		try {
			reader.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if(containersList == null || containersList.size() == 0) {
			System.out.println("No molecules found in sdf file.");
			System.exit(1);
		}
		
		System.out.println("Read " + containersList.size() + " molecules");
		// write xls file
		try {
			if(format.equals("xls")) writeXLSFile(containersList);
			else if(format.equals("csv")) writeCSVFile(containersList);
			else {
				System.err.println("File format " + format + " not known");
				System.exit(3);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * writes out xls file containing all molecules of containersList
	 * 
	 * @param containersList
	 * @throws CloneNotSupportedException 
	 */
	public static void writeXLSFile(List<IAtomContainer> containersList) throws CloneNotSupportedException {
		
		File xlsFile = new File(resultspath, fileName+".xls");
		WritableSheet sheet = null;
		WritableWorkbook workbook = null;
		
		try {
			xlsFile.createNewFile();
			workbook = Workbook.createWorkbook(xlsFile);
		} catch (IOException e) {
			System.out.println("Could not create xls file.");
			System.exit(1);
		}
		
		sheet = workbook.createSheet("SDFtoXLS@rforrocks.de", 0);
		
		// add images if selected
		int columnWidthAdd = withImages ? 3 : 0;
		int rowHeightAdd = withImages ? 9 : 1;
		List<RenderedImage> molImages = null;
		if(withImages) {
			
			molImages = convertMoleculesToImages(containersList);
			for(int i = 0; i < molImages.size(); i++) {
				//File imageFile = new File(resultspath + fileSep + fileName+ "_" +i+".png");
				try {
					File imageFile = File.createTempFile(fileName, ".png", new File(resultspath));
					imageFile.deleteOnExit();
					if(ImageIO.write(molImages.get(i), "png", imageFile)) {
						WritableImage wi = new WritableImage(0, (i * rowHeightAdd) + 1, columnWidthAdd, rowHeightAdd, imageFile);
						sheet.addImage(wi);					}
				} catch (IOException e) {
					e.printStackTrace();
				}

			}	
		}
		
		WritableFont arial10font = new WritableFont(WritableFont.ARIAL, 10);
		WritableCellFormat arial10format = new WritableCellFormat(arial10font);
		try {
			arial10font.setBoldStyle(WritableFont.BOLD);
		} catch (WriteException e1) {
			System.out.println("Warning: Could not set WritableFont");
		}

		int numberCells = 0;
		for(int i = 0; i < containersList.size(); i++) {
			// write header
			Map<Object, Object> properties = containersList.get(i).getProperties();
			Iterator<Object> propNames = properties.keySet().iterator();
			while(propNames.hasNext()) {
				String propName = (String)propNames.next();
				if(!labels.containsKey(propName)) {
					labels.put(propName, new Integer(numberCells));
					try {
						sheet.addCell(new Label(labels.get(propName) + columnWidthAdd, 0, propName, arial10format));
					} catch (RowsExceededException e) {
						e.printStackTrace();
					} catch (WriteException e) {
						e.printStackTrace();
					}
					numberCells++;
				}
				try {
					sheet.addCell(new Label(labels.get(propName) + columnWidthAdd, (i * rowHeightAdd) + 1, (String)properties.get(propName)));
				} catch (RowsExceededException e) {
					e.printStackTrace();
				} catch (WriteException e) {
					e.printStackTrace();
				}
			}
		}
		
		try {
			workbook.write();
			workbook.close();
			System.out.println("Wrote xls file to "+xlsFile.getAbsolutePath());
		} catch (IOException e) {
			System.out.println("Could not write xls file.");
			e.printStackTrace();
			System.exit(1);
		}
			
	}
	
	/**
	 * writes out xls file containing all molecules of containersList
	 * 
	 * @param containersList
	 * @throws IOException 
	 */
	public static void writeCSVFile(List<IAtomContainer> containersList) {
		
		java.util.Vector<java.util.Map<Object,Object>> values = new java.util.Vector<java.util.Map<Object,Object>>();
		
		java.util.Vector<String> headerValues = new java.util.Vector<String>();
		for(int i = 0; i < containersList.size(); i++) {
			// write header
			Map<Object, Object> properties = containersList.get(i).getProperties();
			values.add(properties);
			Iterator<Object> propNames = properties.keySet().iterator();
			while(propNames.hasNext()) {
				String propName = ((String)propNames.next()).trim();
				if(!headerValues.contains(propName)) headerValues.add(propName);
			}
		}
		
		java.util.Vector<String> lines = new java.util.Vector<String>();
		if(headerValues.size() < 1) return;
		String header = headerValues.get(0);
		for(int i = 1; i < headerValues.size(); i++)
		  header += "|" + headerValues.get(i);
		lines.add(header);
		
		for(int j = 0; j < values.size(); j++) {
			Map<Object, Object> properties = values.get(j);
			Object obj = properties.get(headerValues.get(0));
			String line = (obj == null ? "" : (String)obj); 
			for(int k = 1; k < headerValues.size(); k++) {
				obj = properties.get(headerValues.get(k));
				line += "|" + (obj == null ? "" : (String)obj); 
			}
			lines.add(line);
		}

		try {
			File csvFile = new File(resultspath, fileName + ".csv");
			java.io.BufferedWriter bwriter = new java.io.BufferedWriter(new java.io.FileWriter(csvFile));
			
			for(int i = 0; i < lines.size(); i++) {
				bwriter.write(lines.get(i));
				bwriter.newLine();
			}
			
			bwriter.close();
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
	}
	
	public static void fastCSVWriter(File sdfFile) throws IOException {
		BufferedReader breader = new BufferedReader(new FileReader(sdfFile));
		String line = "";
		java.util.TreeSet<String> propertyNames = new java.util.TreeSet<String>();
		int numberMolecules = 0;
		while((line = breader.readLine()) != null) {
			line = line.trim();
			// check if property
			if(line.startsWith("> <")) {
				String propertyName = line.replaceFirst("^>\\s<", "").replaceFirst(">", "");
				propertyNames.add(propertyName);
			}
			else if(line.startsWith("$$$$")) {
				numberMolecules++;
			}
		}
		breader.close();
		
		java.util.Hashtable<String, Integer> propertyNameToIndex = new java.util.Hashtable<String, Integer>();
		java.util.Iterator<String> it = propertyNames.iterator();
		int index = 0;
		String[] header = new String[propertyNames.size()];
		while(it.hasNext()) {
			String property = it.next();
			propertyNameToIndex.put(property, index);
			header[index] = property;
			index++;
		}
		java.util.Vector<Integer> indexMissingToSkip = null;
		if(skipWhenMissing != null) {
			indexMissingToSkip = new java.util.Vector<Integer>();
			for(int i = 0; i < skipWhenMissing.size(); i++) {
				for(int j = 0; j < header.length; j++) {
					if(skipWhenMissing.get(i).equals(header[j])) {
						indexMissingToSkip.add(j);
						break;
					}
				}
			}
		}
		breader = new BufferedReader(new FileReader(sdfFile));
		String[][] moleculeProperties = new String[numberMolecules][propertyNames.size()];
		int moleculeIndex = 0;
		while((line = breader.readLine()) != null) {
			line = line.trim();
			// check if property
			if(line.startsWith("> <")) {
				String propertyName = line.replaceFirst("^>\\s<", "").replaceFirst(">", "");
				moleculeProperties[moleculeIndex][propertyNameToIndex.get(propertyName)] = breader.readLine().trim();
			}
			else if(line.startsWith("$$$$")) {
				moleculeIndex++;
			}
		}
		breader.close();
		try {
			if(!resultspath.equals("STDOUT")) {
				File csvFile = new File(resultspath, fileName + ".csv");
				java.io.BufferedWriter bwriter = new java.io.BufferedWriter(new java.io.FileWriter(csvFile));
				
				bwriter.write(header[0]);
				for(int i = 1; i < header.length; i++) {
					bwriter.write("|" + header[i]);
				}
				bwriter.newLine();

				for(int i = 0; i < moleculeProperties.length; i++) {
					boolean skipEntry = false;
					if(indexMissingToSkip != null) {
						for(int j = 0; j < moleculeProperties[0].length; j++) {
							//check whether necessary values are missing
							if(moleculeProperties[i][j] == null) {
								if(indexMissingToSkip.contains(j)) {
									skipEntry = true;
									break;
								}
							}
						}
					}
					if(skipEntry) continue;
					bwriter.write(moleculeProperties[i][0] == null ? "" : moleculeProperties[i][0]);
					for(int j = 1; j < moleculeProperties[0].length; j++) {
						bwriter.write("|" + (moleculeProperties[i][j] == null ? "" : moleculeProperties[i][j]));
					}
					bwriter.newLine();
				}
				bwriter.close();
			}
			else {
				System.out.print(header[0]);
				for(int i = 1; i < header.length; i++) {
					System.out.print("|" + header[i]);
				}
				System.out.println();
				for(int i = 0; i < moleculeProperties.length; i++) {
					System.out.print(moleculeProperties[i][0] == null ? "" : moleculeProperties[i][0]);
					for(int j = 1; j < moleculeProperties[0].length; j++) {
						System.out.print("|" + (moleculeProperties[i][j] == null ? "" : moleculeProperties[i][j]));
					}
					System.out.println();
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
		
	}
	

	public static void fastCSVWriter(String inputSDF) throws IOException {
		String[] tmp = inputSDF.split("\n");
		String line = "";
		java.util.TreeSet<String> propertyNames = new java.util.TreeSet<String>();
		int numberMolecules = 0;
		for(int i = 0; i < tmp.length; i++) {
			line = tmp[i].trim();
			// check if property
			if(line.startsWith("> <")) {
				String propertyName = line.replaceFirst("^>\\s<", "").replaceFirst(">", "");
				propertyNames.add(propertyName);
			}
			else if(line.startsWith("$$$$")) {
				numberMolecules++;
			}
		}
		
		java.util.Hashtable<String, Integer> propertyNameToIndex = new java.util.Hashtable<String, Integer>();
		java.util.Iterator<String> it = propertyNames.iterator();
		int index = 0;
		String[] header = new String[propertyNames.size()];
		while(it.hasNext()) {
			String property = it.next();
			propertyNameToIndex.put(property, index);
			header[index] = property;
			index++;
		}

		String[][] moleculeProperties = new String[numberMolecules][propertyNames.size()];
		int moleculeIndex = 0;
		for(int i = 0; i < tmp.length; i++) {
			line = tmp[i].trim();
			// check if property
			if(line.startsWith("> <")) {
				String propertyName = line.replaceFirst("^>\\s<", "").replaceFirst(">", "");
				moleculeProperties[moleculeIndex][propertyNameToIndex.get(propertyName)] = tmp[i + 1].trim();
			}
			else if(line.startsWith("$$$$")) {
				moleculeIndex++;
			}
		}
		
		try {
			if(!resultspath.equals("STDOUT")) {
				File csvFile = new File(resultspath, fileName + ".csv");
				java.io.BufferedWriter bwriter = new java.io.BufferedWriter(new java.io.FileWriter(csvFile));
				
				bwriter.write(header[0]);
				for(int i = 1; i < header.length; i++) {
					bwriter.write("|" + header[i]);
				}
				bwriter.newLine();
				
				for(int i = 0; i < moleculeProperties.length; i++) {
					bwriter.write(moleculeProperties[i][0] == null ? "" : moleculeProperties[i][0]);
					for(int j = 1; j < moleculeProperties[0].length; j++) {
						bwriter.write("|" + (moleculeProperties[i][j] == null ? "" : moleculeProperties[i][j]));
					}
					bwriter.newLine();
				}
				bwriter.close();
			}
			else {
				System.out.print(header[0]);
				for(int i = 1; i < header.length; i++) {
					System.out.print("|" + header[i]);
				}
				System.out.println();
				for(int i = 0; i < moleculeProperties.length; i++) {
					System.out.print(moleculeProperties[i][0] == null ? "" : moleculeProperties[i][0]);
					for(int j = 1; j < moleculeProperties[0].length; j++) {
						System.out.print("|" + (moleculeProperties[i][j] == null ? "" : moleculeProperties[i][j]));
					}
					System.out.println();
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
		
	}
	
	/**
	 * generate images of chemical structures
	 * 
	 * @param mol
	 * @return
	 * @throws CloneNotSupportedException 
	 * @throws Exception
	 */
    	private static List<RenderedImage> convertMoleculesToImages(List<IAtomContainer> mols) throws CloneNotSupportedException {

    		List<RenderedImage> molImages = new ArrayList<RenderedImage>();
    	
    		int width = 200;
    		int height = 200;
    	
    		for(int i = 0; i < mols.size(); i++) {
	    		IAtomContainer mol = AtomContainerManipulator.removeHydrogens(mols.get(i));
	    		IAtomContainer molSource = mol.clone();
		
    		try {	    		
				AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molSource);
			} catch(CDKException e1) {
				e1.printStackTrace();
			}
	    	Rectangle drawArea = new Rectangle(width, height);
			Image image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	    	
	    	
			StructureDiagramGenerator sdg = new StructureDiagramGenerator();
			sdg.setMolecule(molSource);
			try {
		       		sdg.generateCoordinates();
			} catch (Exception e) { 
				System.out.println("Warning: Could not draw molecule number "+(i+1)+".");
				molImages.add(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
				continue;
			}
			molSource = sdg.getMolecule();
	    	
	    	
			List<IGenerator<IAtomContainer>> generators = new ArrayList<IGenerator<IAtomContainer>>();
			generators.add(new BasicSceneGenerator());
			generators.add(new BasicBondGenerator());
			generators.add(new BasicAtomGenerator());
			
			AtomContainerRenderer renderer = new AtomContainerRenderer(generators, new AWTFontManager());
			RendererModel rm = renderer.getRenderer2DModel();
	        	rm.set(AtomRadius.class, 0.4);
			
			renderer.setup(molSource, drawArea);
			   
			Graphics2D g2 = (Graphics2D)image.getGraphics();
		   	g2.setColor(Color.WHITE);
		   	g2.fillRect(0, 0, width, height);

		   	renderer.paint(molSource, new AWTDrawVisitor(g2), drawArea, true);
	
		   	molImages.add((RenderedImage)image);
		   	
    		}
	   	return molImages;
    	}
    	
    	
    private static String getInputStringSDF() {
		String input = "";
		int num = 0;
    	try {
			if (System.in.available() != 0) {
				BufferedReader in = null;
				try {
					in = new BufferedReader(new InputStreamReader(System.in));
					String line;
					while ((line = in.readLine()) != null) {
						input += line + "\n";
						System.out.println(++num);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (in != null) {
						in.close();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
    	return input;
    }
}
