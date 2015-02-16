package no.uio.ifi.alboc.scanner;

/*
 * module Scanner 
 */

import no.uio.ifi.alboc.chargenerator.CharGenerator;
import no.uio.ifi.alboc.error.Error;
import no.uio.ifi.alboc.log.Log;
import static no.uio.ifi.alboc.scanner.Token.*;

/**
 * Module for forming characters into tokens.
 * @author Uy Tran
 * @version 10.10.2014
 */
public class Scanner {
	public static Token curToken, nextToken;
	public static String curName, nextName;
	public static int curNum, nextNum;
	public static int curLine, nextLine;
	public static String state;
	private static String curSourceLine;
	public static int startCommentLine;

	/**
	 * Metoden initialiserer alle variblene som skal brukes.
	 * Dette må gjøres spesielt for String slik at man kan begynne å konkatenere med en gang.
	 */
	
	public static void init() {
	//-- Must be changed in part 0:
		curToken = null; nextToken = null;
		curName = ""; nextName = "";
		curNum = nextNum = 0;
		curLine = nextLine = 0;
		startCommentLine = 0;

		state = "start"; curSourceLine = "";
		readNext(); readNext();
	}

	/**
	 * Metoden "dreper" Scanner slik at den ikke kan brukes videre
	 */
	
	public static void finish() {
		state = "dead";
	}

	/**
	 * Metoden setter alle cur variable til next verdiene også henter inn nye verdier til next verdiene.
	 * Jeg tester om tegnet som jeg får fra CharGenerator er et tall, symbol eller en bokstav og starter
	 * de forskjellige metodene for å komme frem til den riktige token.
	 * Ved eofToken hadde jeg et problem der jeg ikke fikk spyttet ut den siste token i filen.
	 * Dette løste jeg med å gjøre alle testene om igjen før eofToken blir notert.
	 */
	
	public static void readNext() {
		if(state == "dead") return;
		curToken = nextToken;  curName = nextName;  curNum = nextNum;
		curLine = nextLine;

		nextToken = null;
		while (nextToken == null) {
			while(CharGenerator.curC == '\t')
				CharGenerator.readNext();
			nextLine = CharGenerator.curLineNum();

			if (! CharGenerator.isMoreToRead()) {
				if(isLetterAZ(CharGenerator.curC) || Character.isDigit(CharGenerator.curC)){
					setTokenForWordOrNumber();
				}else if (!isLetterAZ(CharGenerator.curC)){
					setTokenForSingle();
				}
				Log.noteToken();
				nextToken = eofToken;
			}else if(isLetterAZ(CharGenerator.curC) || Character.isDigit(CharGenerator.curC)){
				setTokenForWordOrNumber();
			}else if (!isLetterAZ(CharGenerator.curC)){
				setTokenForSingle();
			}else{
				Error.error(nextLine,
					"Illegal symbol: '" + CharGenerator.curC + "'!");
			}
		}
		Log.noteToken();
	}

	/**
	 * Metoden sender en feilmelding til Error omt at vi har funnet et tegn som vi ikke kan knytte til en token
	 * @param c Et tegn som Scanner ikke kan oversette til en token
	 */

	private static void foundWeirdCharacter(char c){
		Error.error(nextLine, "Illegal symbol: '" + CharGenerator.curC + "'!");
	}

	/**
	 * Metoden sender en feilmelding til Error om at vi har funnet et tall på begynnelsen av et variabelnavn
	 */

	private static void foundNumberAtStart(){
		Error.error(nextLine, "Found number at start of nameToken");
	}

	/**
	 * Metoden gir nextToken en verdi som stemmer overens med et innlest ord eller tall fra CharGenerator
	 * Metoden skjekker om Scanner er i kommenteringsstatus for så å se om kommenteringen er feilfritt.
	 * Hvis Scanner ikke er i kommenteringsstatus så setter metoden Scanner sin status til "wordOrNumberToken",
	 * at tokenene som blir spyttet ut kan være et ord eller tall med et vilkårlig antall tegn.
	 * Etter at metoden har spyttet ut en token går den et tegn videre i filen med CharGenerator.
	 */

	private static void setTokenForWordOrNumber(){
		if(state.equals("comment")){
			if(CharGenerator.foundComment){
				Error.error("Comment starting on line " + startCommentLine + " never ends!");
				System.exit(1);
			}else if(CharGenerator.curC == '/' && CharGenerator.nextC == '*'){
				Error.error("Found inner comment at line " + CharGenerator.curLineNum());
				System.exit(2);
			}else if(CharGenerator.curC == '*' && CharGenerator.nextC == '/'){
				state = "outOfComment";
				CharGenerator.readNext();
			}
			CharGenerator.readNext();
		}else{
			state = "wordOrNumberToken";
			String word = "";
			boolean isNumber = true;
			while(isLetterAZ(CharGenerator.curC) || Character.isDigit(CharGenerator.curC) || CharGenerator.curC == '_'){
				if(isLetterAZ(CharGenerator.curC))
					isNumber = false;
				word += CharGenerator.curC;
				CharGenerator.readNext();
			}

			if(Character.isDigit(word.indexOf(0)))
				foundNumberAtStart();


			if(word.equals("else"))
				nextToken = Token.elseToken;
			else if(word.equals("for"))
				nextToken = Token.forToken;
			else if(word.equals("if"))
				nextToken = Token.ifToken;
			else if(word.equals("int")){
				nextToken = Token.intToken;
			}else if(word.equals("return"))
			nextToken = Token.returnToken;
			else if(word.equals("while"))
				nextToken = Token.whileToken;
			else if(isNumber){
				nextToken = Token.numberToken;
				nextNum = Integer.parseInt(word);
			}else{
				nextToken = Token.nameToken;
				nextName = word;
			}
		}
	}

	/**
	 * Metoden gir nextToken en verdi som stemmer overens med en eller to innleste karakterer
	 * Metoden skjekker om Scanner er i kommenteringsstatus for så å se om kommenteringen er feilfritt.
	 * Hvis Scanner ikke er i kommenteringsstatus så setter metoden Scanner sin status til "singleToken",
	 * at tokenene som blir spyttet ut er veldig korte.
	 * Hvis vi finner et tegn som vi ikke har token til så blir en feilmelding skrevet ut, men bare hvis
	 * forrige tegn ikke var '''.
	 * Etter at metoden har spyttet ut en token går den et tegn videre i filen med CharGenerator.
	 */

	private static void setTokenForSingle() {
		if(state.equals("comment")){
			if(CharGenerator.foundComment){
				Error.error("Comment starting on line " + startCommentLine + " never ends!");
				System.exit(1);
			}else if(CharGenerator.curC == '/' && CharGenerator.nextC == '*'){
				Error.error("Found inner comment at line " + CharGenerator.curLineNum());
				System.exit(2);
			}else if(CharGenerator.curC == '*' && CharGenerator.nextC == '/'){
				state = "outOfComment";
				CharGenerator.readNext();
			}
			CharGenerator.readNext();
		}else{
			state = "singleToken";
			switch(CharGenerator.curC){
				case '+': 
				nextToken = Token.addToken;
				break;
				case '&':
				nextToken = Token.ampToken;
				break;
				case '=':
				if(CharGenerator.nextC == '='){
					nextToken = Token.equalToken;
					CharGenerator.readNext();
				}else
					nextToken = Token.assignToken;
				break;
				case ',':
				nextToken = Token.commaToken;
				break;
				case '/':
				if(CharGenerator.nextC == '*'){
					state = "comment";
					startCommentLine = CharGenerator.curLineNum();
					CharGenerator.readNext();
					CharGenerator.readNext();
				}else{
					nextToken = Token.divideToken;
				}
				break;
				case '>':
				if(CharGenerator.nextC == '='){
					nextToken = Token.greaterEqualToken;
					CharGenerator.readNext();
				}else
					nextToken = Token.greaterToken;
				break;
				case '[':
				nextToken = Token.leftBracketToken;
				break;
				case '{':
				nextToken = Token.leftCurlToken;
				break;
				case '(':
				nextToken = Token.leftParToken;
				break;
				case '<':
				if(CharGenerator.nextC == '='){
					nextToken = Token.lessEqualToken;
					CharGenerator.readNext();
				}
				else
					nextToken = Token.lessToken;
				break;
				case '!':
				if(CharGenerator.nextC == '='){
					nextToken = notEqualToken;
					CharGenerator.readNext();
				}
				break;
				case ']':
				nextToken = Token.rightBracketToken;
				break;
				case '}':
				nextToken = Token.rightCurlToken;
				break;
				case ')':
				nextToken = Token.rightParToken;
				break;
				case ';':
				nextToken = Token.semicolonToken;
				break;
				case '*':
				nextToken = Token.starToken;
				break;
				case '-':
				nextToken = Token.subtractToken;
				break;
				case 39:
				nextToken = numberToken;
				nextNum = CharGenerator.nextC;
				CharGenerator.readNext();
				if(CharGenerator.nextC != 39){
					Error.error("in line " + CharGenerator.curLineNum() + ": Illegal character constant!");
					System.exit(3);
				}
				CharGenerator.readNext();
				break;
				default:
				if(CharGenerator.curC != ' ')
					foundWeirdCharacter(CharGenerator.curC);
				break;

			}
			CharGenerator.readNext();
		}
	}

	private static boolean isLetterAZ(char c) {
		if(c > 64 && c < 91 || c > 96 && c < 123)
			return true;
		return false;
	}

	public static void check(Token t) {
		if (curToken != t)
			Error.expected("A " + t);
	}

	public static void check(Token t1, Token t2) {
		if (curToken != t1 && curToken != t2)
			Error.expected("A " + t1 + " or a " + t2);
	}

	public static void skip(Token t) {
		check(t);  readNext();
	}

	public static void skip(Token t1, Token t2) {
	check(t1,t2);  readNext();
	}
}
