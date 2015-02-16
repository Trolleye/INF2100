package no.uio.ifi.alboc.chargenerator;

/*
 * module CharGenerator
 */

import java.io.*;
import no.uio.ifi.alboc.alboc.AlboC;
import no.uio.ifi.alboc.error.Error;
import no.uio.ifi.alboc.log.Log;

/**
 * Module for reading single characters.
 * @author Uy Tran
 * @version 15.09.2014
 */
public class CharGenerator {
	public static char curC, nextC;
	
	private static LineNumberReader sourceFile = null;
	private static String sourceLine;
	private static int sourcePos;
	public static boolean foundComment;

	/**
	 * Metoden initialiserer alle de nødvendige variablene som brukes i CharGenerator og Scanner
	 */

	public static void init() {
		try {
			sourceFile = new LineNumberReader(new FileReader(AlboC.sourceName));
		} catch (FileNotFoundException e) {
			Error.error("Cannot read " + AlboC.sourceName + "!");
		}
		sourceLine = "";  sourcePos = 0;  curC = nextC = ' '; foundComment = false;
		readNext();  readNext();
	}

	/**
	 * Metoden avslutter CharGenerator ved å lukke sourceFile
	 */
	
	public static void finish() {
		if (sourceFile != null) {
			try {
				sourceFile.close();
			} catch (IOException e) {
				System.out.println();
				System.err.println(e.getMessage());
			}
		}
	}

	/**
	 * Metoden skjekker om sourceLne er like null, hvis sourcFile.readLine() har kommet til eof så blir null returnert
	 * @return false hvis vi har nådd eof, ellers true
	 */
	
	public static boolean isMoreToRead() {
		if(sourceLine == null)
			return false;
		return true;
	}

    /**
     * Metoden returnerer linjen som nextC er på
     * @return linjen som nextC er på
     */
    
    public static int curLineNum() {
    	return (sourceFile == null ? 0 : sourceFile.getLineNumber());
    }

	/**
	 * 
	 */
	
	public static void readNext() {
		curC = nextC;
		if (!isMoreToRead()) return;

		if(sourcePos == 1 && sourceLine.length() > 1)
			Log.noteSourceLine(curLineNum(), sourceLine);

		try {
			if (sourcePos == sourceLine.length()) {
				sourceLine = sourceFile.readLine();
				if (!isMoreToRead()) return;
				foundComment = sourceLine.startsWith("#");

				while (sourceLine.startsWith("#") && sourceLine != null) {
					Log.noteSourceLine(curLineNum(), sourceLine);	
					sourceLine = sourceFile.readLine();		
				}

				/*
				 * Jeg hadde noen problemer med at Scanner spyttet ut tokens før CharGenerator rakk å skrive ut
				 * kodelinjen, jeg fikset dette ved å gjøre linjene lengere slik at curC og nextC sin tokens
				 * alltid kommer på samme linje samtidig. 
				 * Dette gjør det også lettere å spytte ut en feilmelding og terminere programmet ved eventuelle
				 * 'c\n' feil
				 * Jeg vet at dette er en veldig skitten måte å løse problemet på...
				 */
				sourceLine += sourceLine != null ? (sourceLine.length() == 0 ? "  " : " ") : "";

				if (!isMoreToRead()) return;

				sourcePos = 0;
			}

			if(sourcePos == 0 && sourceLine.length() <= 1)
				Log.noteSourceLine(curLineNum(), sourceLine);
			nextC = sourceLine.charAt(sourcePos);
			sourcePos++;
		} catch(IOException e) {
			System.out.println();
			System.err.println(e.getMessage());
		}
	}
}
