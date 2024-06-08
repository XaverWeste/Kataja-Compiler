package com.github.ktj.compiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

final class Parser {

    private HashMap<String, Compilable> classes;
    private HashMap<String, String> uses;
    private Scanner sc;
    private TokenHandler th;
    private String path;
    private String name;
    private Compilable current = null;
    private KtjClass statik;
    private int line;

    public HashMap<String, Compilable> parseFile(File file) throws IllegalArgumentException{
        if(!file.exists()) throw new IllegalArgumentException(STR."File \{file.getPath()} did not exist");

        try{
            path = file.getPath().substring(0, file.getPath().length() - file.getName().length() - 1);
            name = file.getName().substring(0, file.getName().length() - 4);
            classes = new HashMap<>();
            uses = new HashMap<>();
            sc = new Scanner(file);
            statik = new KtjClass(new Modifier(AccessFlag.ACC_PUBLIC), uses, STR."\{path}\\\{name}", -1);
            line = 0;
        }catch(FileNotFoundException ignored){
            throw new IllegalArgumentException(STR."Unable to find \{file.getPath()}");
        }

        while(sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                try {
                    switch (th.next().s()) {
                        case "#" -> {}
                        case "use" -> parseUse();
                        case "main" -> parseMain();
                        default -> parseModifier(null);
                    }
                }catch(RuntimeException e){
                    if(e.getMessage().contains(" at ")) throw e;
                    else{
                        try {
                            err(e.getMessage());
                        }catch(RuntimeException err){
                            err.setStackTrace(e.getStackTrace());
                            throw err;
                        }
                    }
                }
            }
        }

        if(!statik.isEmpty()) classes.put(classes.isEmpty() ? name : STR."_\{name}", statik);

        if(classes.size() == 1 && name.equals(classes.keySet().toArray(new String[0])[0])){
            String name = classes.keySet().toArray(new String[0])[0];
            Compilable clazz = classes.remove(name);
            classes.put(CompilerUtil.validateClassName(STR."\{path}.\{name}"), clazz);

            if(uses.containsKey(name)) err(STR.".\{name} is already defined");
            uses.put(name, STR."\{CompilerUtil.validateClassName(path)}.\{name}");
        }else{
            HashMap<String, Compilable> help = new HashMap<>();

            for(String clazz: classes.keySet()){
                help.put(CompilerUtil.validateClassName(STR."\{path}.\{name}.\{clazz}"), classes.get(clazz));

                path = CompilerUtil.validateClassName(path);
                if(uses.containsKey(clazz)) err(STR.".\{clazz} is already defined");
                uses.put(clazz, STR."\{path}.\{name}.\{clazz}");
            }

            classes = help;
        }

        return classes;
    }

    private void parseUse(){
        String current = th.assertToken(Token.Type.IDENTIFIER).s();

        if(th.assertToken("/", "from", ",").equals("/")){
            StringBuilder path = new StringBuilder(current);
            th.assertHasNext();
            th.last();

            while (th.hasNext()){
                if(th.assertToken("/", "as").equals("as")){
                    if(uses.containsKey(th.assertToken(Token.Type.IDENTIFIER).s())) err(STR."\{th.current()} is already defined");
                    uses.put(th.current().s(), path.toString());
                }else{
                    current = th.assertToken(Token.Type.IDENTIFIER).s();
                    path.append("/").append(current);
                }
            }

            if(uses.containsKey(current)) err(STR."\{current} is already defined");
            uses.put(current, path.toString().replace("/", "."));
        }else{
            ArrayList<String> classes = new ArrayList<>();
            classes.add(current);

            if(th.current().equals(",")){
                classes.add(th.assertToken(Token.Type.IDENTIFIER).s());
                th.assertToken(",", "from");
            }

            StringBuilder path = new StringBuilder(th.assertToken(Token.Type.IDENTIFIER).s());

            while (th.hasNext()){
                if(th.assertToken("/", "as").equals("as")){
                    if(classes.size() != 1) err("illegal argument");
                    if(uses.containsKey(th.assertToken(Token.Type.IDENTIFIER).s())) err(STR."\{th.current()} is already defined");
                    uses.put(th.current().s(), (path + classes.getFirst()).replace("/", "."));
                }else path.append("/").append(th.assertToken(Token.Type.IDENTIFIER).s());
            }

            for(String clazz:classes){
                if(uses.containsKey(clazz)) err(STR."\{clazz} is already defined");
                uses.put(clazz, path.toString().replace("/", "."));
            }
        }
    }

    private void parseMain(){
        th.assertToken("{");
        th.assertNull();

        int _line = line;

        StringBuilder code = new StringBuilder();
        int i = 1;

        while (sc.hasNextLine()) {
            nextLine();

            if (th.isEmpty()) code.append("\n");
            else if (th.next().s().equals("}")) {
                if(!th.hasNext() || !th.next().equals("else")) {
                    i--;

                    if (i > 0) {
                        if (!code.isEmpty()) code.append("\n");
                        code.append(th.toStringNonMarked());
                    } else {
                        Modifier mod = new Modifier(AccessFlag.ACC_PUBLIC);
                        mod.statik = true;
                        addMethod("main%[_String", new KtjMethod(mod, "void", code.toString(), new KtjMethod.Parameter[]{new KtjMethod.Parameter("[_String", "args")}, uses, STR."\{path}\\\{name}", _line));
                        uses.put("_String", "java.lang.String");
                        return;
                    }
                }else{
                    if (!code.isEmpty()) code.append("\n");
                    code.append(th.toStringNonMarked());
                }
            }else{
                if(Set.of("if", "while").contains(th.current().s())) i++;
                if (!code.isEmpty()) code.append("\n");
                code.append(th.toStringNonMarked());
            }
        }

        err("Expected '}'");
    }

    private void parseModifier(String clazzName){
        Modifier mod = switch (th.current().s()){
            case "public" -> new Modifier(AccessFlag.ACC_PUBLIC);
            case "private" -> new Modifier(AccessFlag.ACC_PRIVATE);
            case "protected" -> new Modifier(AccessFlag.ACC_PROTECTED);
            default -> {
                th.last();
                yield new Modifier(AccessFlag.ACC_PACKAGE_PRIVATE);
            }
        };
        Set<String> modifier = Set.of("final", "abstract", "static", "synchronised", "const");

        while (modifier.contains(th.next().s())){
            switch (th.current().s()){
                case "final" -> {
                    if(mod.finaly) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.finaly = true;
                }
                case "abstract" -> {
                    if(mod.abstrakt) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.abstrakt = true;
                }
                case "static" -> {
                    if(mod.statik) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.statik = true;
                }
                case "synchronised" -> {
                    if(mod.synchronised) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.synchronised = true;
                }
                case "const" -> {
                    if(mod.constant) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.constant = true;
                }
            }
        }
        th.last();

        if(clazzName == null) {
            switch (th.assertToken(Token.Type.IDENTIFIER).s()) {
                case "class" -> parseClass(mod);
                case "interface" -> parseInterface(mod);
                case "data" -> parseData(mod);
                case "type" -> parseType(mod);
                default -> {
                    th.last();
                    parseMethodAndField(mod, null);
                }
            }
        }else parseMethodAndField(mod, clazzName);
    }

    private void parseClass(Modifier modifier){
        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(classes.containsKey(name)) err(STR."Class \{name} is already defined");

        th.assertToken("{");
        th.assertNull();

        KtjClass clazz = new KtjClass(modifier, uses, STR."\{path}\\\{name}", line);
        current = clazz;

        while (sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                if (th.next().s().equals("}")) {
                    current = null;
                    th.assertNull();
                    classes.put(name, clazz);
                    return;
                } else {
                    parseModifier(name);
                }
            }
        }

        err("Expected '}'");
    }

    private void parseData(Modifier modifier){
        if(!modifier.isValidForData()) err("illegal modifier");

        String name = parseName();
        KtjDataClass clazz = new KtjDataClass(modifier, uses, STR."\{path}\\\{name}", line);

        th.assertToken("=");
        th.assertToken("(");

        if(!th.next().equals(")")) {
            th.last();

            while (th.hasNext()) {
                if (clazz.addField(th.assertToken(Token.Type.IDENTIFIER).s(), th.assertToken(Token.Type.IDENTIFIER).s(), line))
                    err("field is already defined");

                if (th.hasNext()){
                    if(th.assertToken(")", ",").equals(")")) break;
                    th.assertHasNext();
                }
            }
        }

        if(!th.current().equals(")")) err("Expected ')'");
        th.assertNull();

        classes.put(name, clazz);
    }

    private void parseType(Modifier modifier){
        if(!modifier.isValidForType()) err("illegal modifier");

        String name = parseName();

        th.assertToken("=");

        ArrayList<String> types = new ArrayList<>();
        types.add(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext()){
            th.assertToken("|");
            String type = th.assertToken(Token.Type.IDENTIFIER).s();

            if(!types.contains(type)) types.add(type);
        }

        classes.put(name, new KtjTypeClass(modifier, types.toArray(new String[0]), uses, STR."\{path}\\\{name}", line));
    }

    private void parseInterface(Modifier modifier){
        if(!modifier.isValidForInterface()) err("illegal modifier");

        String name = parseName();

        th.assertToken("{");

        KtjInterface clazz = new KtjInterface(modifier, uses, STR."\{path}\\\{name}", line);
        current = clazz;

        while (sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                if(th.next().s().equals("}")){
                    current = null;
                    th.assertNull();
                    classes.put(name, clazz);
                    return;
                }else parseModifier(name);
            }
        }

        err("Expected '}'");
    }

    private String parseName(){
        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(classes.containsKey(name)) err(STR."Type Class \{name} is already defined");

        return name;
    }

    private void parseMethodAndField(Modifier mod, String clazzName){
        String type = th.assertToken(Token.Type.IDENTIFIER).s();
        String name = th.assertToken(Token.Type.IDENTIFIER, "[", "(").s();

        while(name.equals("[")){
            th.assertToken("]");
            type = STR."[\{type}";
            name = th.assertToken(Token.Type.IDENTIFIER, "[").s();
        }

        if(name.equals("(")){
            if(type.equals(clazzName)) type = "<init>";
            parseMethod(mod, "void", type);
        }else{
            if(th.hasNext() && th.assertToken("=", "(").equals("(")) parseMethod(mod, type, name);
            else parseField(mod, type, name);
        }
    }

    private void parseField(Modifier mod, String type, String name){
        if(!mod.isValidForField()) err("illegal modifier");

        String initValue = null;

        if(th.current().equals("=")){
            StringBuilder sb = new StringBuilder();
            th.assertHasNext();

            while(th.hasNext()){
                sb.append(th.next().s()).append(" ");
                if(th.current().equals(";") && th.hasNext()) throw new RuntimeException("illegal argument");
            }

            initValue = sb.toString();
        }

        if(current == null){
            mod.statik = true;
            if(statik.addField(name, new KtjField(mod, type, initValue, uses, STR."\{path}\\\{name}", line))) err("field is already defined");
            return;
        }

        if(current instanceof KtjClass){
            if(((KtjClass) current).addField(name, new KtjField(mod, type, initValue, uses, STR."\{path}\\\{name}", line))) err("field is already defined");
        }
    }

    private void parseMethod(Modifier mod, String type, String name){
        if(name.equals("<init>")){
            if(!mod.isValidForInit()) err("illegal modifier");
        }else if(!mod.isValidForMethod()) err("illegal modifier");

        TokenHandler parameterList = th.getInBracket();
        ArrayList<KtjMethod.Parameter> parameter = new ArrayList<>();
        int _line = line;

        while(parameterList.hasNext()){
            String pType = parameterList.assertToken(Token.Type.IDENTIFIER).s();
            String pName = parameterList.assertToken(Token.Type.IDENTIFIER, "[").s();

            while(pName.equals("[")){
                parameterList.assertToken("]");
                pType = STR."[\{pType}";
                pName = parameterList.assertToken(Token.Type.IDENTIFIER, "[").s();
            }

            for(KtjMethod.Parameter p:parameter) if(pName.equals(p.name())) err(STR."Method \{pName} is already defined");

            parameter.add(new KtjMethod.Parameter(pType, pName));

            if(parameterList.hasNext()){
                parameterList.assertToken(",");
                parameterList.assertHasNext();
            }
        }

        StringBuilder desc = new StringBuilder(name);
        for(KtjMethod.Parameter p:parameter) desc.append(STR."%\{p.type()}");

        if(mod.abstrakt){
            th.assertNull();
            addMethod(desc.toString(), new KtjMethod(mod, type, null, new KtjMethod.Parameter[0], uses, STR."\{path}\\\{name}", _line));
        }else {
            th.assertToken("{");
            th.assertNull();

            StringBuilder code = new StringBuilder();
            int i = 1;

            while (sc.hasNextLine()) {
                nextLine();

                if (th.isEmpty()) code.append("\n");
                else if (th.next().s().equals("}")) {
                    if(!th.hasNext() || !th.next().equals("else")) {
                        i--;

                        if (i > 0) {
                            if (!code.isEmpty()) code.append("\n");
                            code.append(th.toStringNonMarked());
                        } else {
                            addMethod(desc.toString(), new KtjMethod(mod, type, code.toString(), parameter.toArray(new KtjMethod.Parameter[0]), uses, STR."\{path}\\\{name}", _line));
                            return;
                        }
                    }else{
                        if (!code.isEmpty()) code.append("\n");
                        code.append(th.toStringNonMarked());
                    }
                }else{
                    if(Set.of("if", "while").contains(th.current().s())) i++;
                    if (!code.isEmpty()) code.append("\n");
                    code.append(th.toStringNonMarked());
                }
            }

            err("Expected '}'");
        }
    }

    private void addMethod(String desc, KtjMethod method){
        if(current == null){
            method.modifier.statik = true;
            if (statik.addMethod(desc, method)) err("method is already defined");
            return;
        }

        if (current instanceof KtjClass) {
            if (((KtjClass) current).addMethod(desc, method)) err("method is already defined");
        } else if (current instanceof KtjInterface) {
            if(!method.isAbstract()) err("expected abstract Method");
            if (((KtjInterface) current).addMethod(desc, method)) err("method is already defined");
        }
    }

    private void nextLine() throws RuntimeException{
        if(sc.hasNextLine()){
            th = Lexer.lex(sc.nextLine());
            line ++;
        }else throw new RuntimeException();
    }

    private void err(String message) throws RuntimeException{
        throw new RuntimeException(STR."\{message} at \{path}\\\{name}:\{line}");
    }
}
