package com.mycompany.jackcompiler;
import java.util.*;

public class SymbolTable {
    private HashMap<String, Identifier> classTable;
    private HashMap<String, Identifier> subTable;
    private int staticIndex, fieldIndex, argIndex, varIndex;
    private boolean subroutine;
    
    
    public SymbolTable(){
        classTable = new HashMap<>();
        subTable = new HashMap<>();
        staticIndex = 0;
        fieldIndex = 0;
        argIndex = 0;
        varIndex = 0;
        subroutine = false;
    }
    
    public void startSubroutine(){
        subTable.clear();
        argIndex = 0;
        varIndex = 0;
        subroutine = true;
    }
    
    public void Define(String name, String type, String kind){
        switch(kind){
            case "static"->{
                classTable.put(name, new Identifier(staticIndex, type, kind));
                staticIndex++;
            }
            case "field"->{
                classTable.put(name, new Identifier(fieldIndex, type, kind));
                fieldIndex++;
            }
            case "argument"-> {
                subTable.put(name, new Identifier(argIndex, type, kind));
                argIndex++;
            }
            case "var" -> {
                subTable.put(name, new Identifier(varIndex, type, kind));
                varIndex++;
            }
            default -> {}
        }
    }
    
    public int VarCount(String kind){
        HashMap<String, Identifier> symbolTable = new HashMap();
        switch(kind){
            case "static", "field"-> symbolTable = classTable;
            case "argument", "var" ->  symbolTable = subTable;
            default -> {}
        }
        int varCount = 0;
        Set<String> keys = symbolTable.keySet();
        for(String key: keys){
            if(symbolTable.get(key) != null && symbolTable.get(key).getKind().equals(kind))
                varCount++;
        }
        return varCount;
    }
    
    public String KindOf(String name){
        if(subroutine && subTable.get(name) != null) return subTable.get(name).getKind();
        else return classTable.get(name).getKind();
    }
    
    public String TypeOf(String name){
        if(subTable.get(name) == null && classTable.get(name) ==null) return null;
        if(subroutine && subTable.get(name) != null ) return subTable.get(name).getType();
        else return classTable.get(name).getType();
    }
    
    public int IndexOf(String name){
        if(subroutine && subTable.get(name) != null) return subTable.get(name).getIndex();
        else return classTable.get(name).getIndex();
    }
}