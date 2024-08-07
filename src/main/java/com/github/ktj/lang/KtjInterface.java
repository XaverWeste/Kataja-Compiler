package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.ArrayList;
import java.util.HashMap;

public class KtjInterface extends Compilable{

    public HashMap<String, KtjMethod> methods;

    public KtjInterface(Modifier modifier, ArrayList<GenericType> genericTypes, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, genericTypes, uses, statics, file, line);
        methods = new HashMap<>();
    }

    public boolean addMethod(String desc, KtjMethod method){
        if(methods.containsKey(desc)) return true;

        methods.put(desc, method);
        return false;
    }

    @Override
    public void validateTypes(){
        if(genericTypes != null) for(GenericType genericType:genericTypes) genericType.type = validateType(genericType.type, true);

        HashMap<String, KtjMethod> help = new HashMap<>();

        for(String methodName:methods.keySet()){
            KtjMethod method = methods.get(methodName);
            method.validateTypes();

            String[] args = methodName.split("%");
            StringBuilder desc = new StringBuilder(args[0]);

            for(int i = 1;i < args.length;i++) desc.append("%").append(validateType(args[i], true));

            help.put(desc.toString(), method);
        }

        methods = help;
    }

    public int getAccessFlag(){
        return super.getAccessFlag() + AccessFlag.INTERFACE + AccessFlag.ABSTRACT;
    }
}
