package com.github.ktj.compiler;

import java.util.Arrays;

final class TokenHandler{

    private final Token[][] token;
    private final String file;
    private final int lineOffset;
    private int line;
    private int i;

    TokenHandler(Token[][] token, String file, int lineOffset){
        this.token = token;
        this.file = file;
        this.lineOffset = lineOffset;
        line = 0;
        i = -1;
    }

    public Token next(){
        if(i + 1 < token[line].length){
            i++;
            return token[line][i];
        }else{
            while(++line < token.length){
                if(token[line].length > 0){
                    i = 0;
                    return token[line][i];
                }
            }
        }
        err("Expected Token got nothing");
        return new Token(null, null); // unreachable statement
    }

    public Token current(){
        if(i == -1 || i >= token[line].length) err("Expected Token got nothing");
        return token[line][i];
    }

    public Token last(){
        if(i > 0){
            i--;
            return token[line][i];
        }else{
            while(--line >= 0){
                if(token[line].length > 0){
                    i = token[line].length - 1;
                    return token[line][i];
                }
            }
        }
        i = -1;
        line = 0;
        return new Token(null, null);
    }

    public Token assertToken(String...strings){
        Token t = next();

        for(String string:strings) if(t.equals(string)) return t;

        err("Expected one of "+Arrays.toString(strings)+" got "+t.s);
        return new Token(null, null); // unreachable statement
    }

    public Token assertTokenTypes(Token.Type...types){
        Token t = next();

        for(Token.Type type:types) if(t.equals(type)) return t;

        err("Expected one of "+Arrays.toString(types)+" got "+t.t);
        return new Token(null, null); // unreachable statement
    }

    public Token assertToken(Token.Type type, String...strings){
        Token t = next();

        if(t.equals(type)) return t;

        for(String string:strings) if(t.equals(string)) return t;

        err("Expected one of "+type+(strings.length != 0 ? ", "+Arrays.toString(strings) : "")+" got "+t.s);
        return new Token(null, null); // unreachable statement
    }

    public Token assertToken(Token.Type type1, Token.Type type2,String...strings){
        Token t = next();

        if(t.equals(type1)) return t;
        if(t.equals(type2)) return t;

        for(String string:strings) if(t.equals(string)) return t;

        err("Expected one of "+type1+", "+type2+(strings.length != 0 ? ", "+Arrays.toString(strings) : "")+" got "+t.s);
        return new Token(null, null); // unreachable statement
    }

    public void assertEndOfStatement(){
        if(i + 1 != token[line].length) assertToken(";");
    }

    public void assertHasNext(){
        next();
        last();
    }

    public boolean isNext(String string){
        if(!hasNext()) return false;
        if(next().equals(string)) return true;

        last();
        return false;
    }

    public boolean isNext(Token.Type type){
        if(next().equals(type)) return true;

        last();
        return false;
    }

    public boolean isEndOfStatement(){
        if(i + 1 == token[line].length) return true;

        return isNext(";");
    }

    public boolean hasNext(){
        String index = getIndex();
        if(line < token.length && i + 1 < token[line].length){
            setIndex(index);
            return true;
        }else{
            while(++line < token.length){
                if(token[line].length > 0){
                    setIndex(index);
                    return true;
                }
            }
        }
        setIndex(index);
        return false;
    }

    public int getLine(){
        if(lineOffset > 0) return lineOffset + line;
        return line + 1;
    }

    public String getIndex(){
        return line +":"+i;
    }

    public void setIndex(String index){
        String[] s = index.split(":");
        line = Integer.parseInt(s[0]);
        i = Integer.parseInt(s[1]);
    }

    public String getInBracket(){
        String openingBracket = current().s;
        String closingBracket;
        switch(openingBracket){
            case "(":
                closingBracket = ")";
                break;
            case "[":
                closingBracket = "]";
                break;
            case "{":
                closingBracket = "}";
                break;
            case "<":
                closingBracket = ">";
                break;
            default:
                throw new RuntimeException();
        }
        StringBuilder sb = new StringBuilder();

        int line = getLine();
        int b = 1;

        while(b > 0 && hasNext()){
            next();
            while(getLine() != line){
                line++;
                sb.append("\n");
            }
            if(current().equals(openingBracket)) b++;
            else if(current().equals(closingBracket)) b--;
            if(b > 0) sb.append(current()).append(" ");
        }

        if(b > 0) err("Expected "+closingBracket);

        return sb.toString();
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();

        for(Token[] line:token){
            for(Token token:line) sb.append(token).append(" ");
            sb.append("\n");
        }

        return sb.toString();
    }

    private void err(String message) throws ParsingException{
        int line = getLine();

        StringBuilder sb = new StringBuilder(message);
        sb.append(" near ");

        String index = getIndex();
        try{
            if(last() == null) throw new ParsingException("", "", 0);
            sb.append(current());
            next();
        }catch(ParsingException ignored){
            setIndex(index);
        }

        if(line < 0 || line >= token.length || i == -1 || i >= token[line].length) throw new ParsingException(sb.toString(), file, line);

        sb.append(" ").append(current()).append(" ");
        if(hasNext()) sb.append(next());

        throw new ParsingException(sb.toString(), file, line);
    }
}
