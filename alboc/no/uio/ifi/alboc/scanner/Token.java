package no.uio.ifi.alboc.scanner;

/*
 * class Token
 */

/*
 * The different kinds of tokens read by Scanner.
 */
public enum Token { 
    addToken, ampToken, assignToken, 
    commaToken, 
    divideToken,
    elseToken, eofToken, equalToken, 
    forToken, 
    greaterEqualToken, greaterToken, 
    ifToken, intToken, 
    leftBracketToken, leftCurlToken, leftParToken, lessEqualToken, lessToken, 
    nameToken, notEqualToken, numberToken, 
    returnToken, rightBracketToken, rightCurlToken, rightParToken, 
    semicolonToken, starToken, subtractToken, 
    whileToken;

    public static boolean isFactorOperator(Token t) {
	//-- Must be changed in part 0:
        if(t == divideToken || t == starToken)
           return true;
       return false;
   }

   public static boolean isTermOperator(Token t) {
     if(t == addToken || t == subtractToken)
      return true;
    return false;
   }

   public static boolean isPrefixOperator(Token t) {
    if(t == subtractToken || t == starToken)
      return true;
       return false;
   }

   public static boolean isRelOperator(Token t) {
    if(t == equalToken || t == notEqualToken || t == lessToken || t == lessEqualToken || t == greaterToken || t == greaterEqualToken)
      return true;
    return false;
}

public static boolean isOperand(Token t) {
	//-- Must be changed in part 0:
    if(t == numberToken || t == nameToken || t == leftParToken || t == ampToken)
        return true;
    return false;
}
}
