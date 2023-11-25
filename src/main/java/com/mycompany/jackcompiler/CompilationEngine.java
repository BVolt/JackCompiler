package com.mycompany.jackcompiler;
import java.io.*;

public class CompilationEngine {
    private JackTokenizer tokenizer;
    private VMWriter writer;
    private SymbolTable symTable;
    private String className;
    private String currSubName;
    private boolean subScope;
    private int ifLabelIndex = 0;
    private int whileLabelIndex = 0;
    
    
    public CompilationEngine(BufferedReader iFile, BufferedWriter ofile){
        tokenizer = new JackTokenizer(iFile);
        writer = new VMWriter(ofile);
        symTable = new SymbolTable();
        subScope = false;
    }
    
    public void CompileClass(){
        tokenizer.advance(); //keyword class
        tokenizer.advance(); // identifier class name
        className = tokenizer.identifier();
        tokenizer.advance(); //{
        tokenizer.advance();
        while(tokenizer.keyWord().equals("static") || tokenizer.keyWord().equals("field")){
            CompileClassVarDec();
        }

        while(tokenizer.keyWord().equals("constructor") || tokenizer.keyWord().equals("function") || tokenizer.keyWord().equals("method")){
            CompileSubroutine();
        }
        tokenizer.advance();
    }
    
    public void CompileClassVarDec(){
        String kind, type, name;
            kind = tokenizer.keyWord();//Static or field
            tokenizer.advance();
            type = tokenizer.keyWord();//type
            tokenizer.advance();
            name = tokenizer.identifier();//identifier
            symTable.Define(name, type, kind);
            tokenizer.advance();
            while(tokenizer.symbol() == ','){
                tokenizer.advance();  //,
                name = tokenizer.identifier(); 
                tokenizer.advance();
                symTable.Define(name, type, kind);
            }

            tokenizer.advance();//;
    }
    
    
    public void CompileSubroutine() {
        ifLabelIndex = 0; whileLabelIndex = 0;
        symTable.startSubroutine();
        String funcType = tokenizer.keyWord();
        if(funcType.equals("method")) {
            symTable.Define("this", className, "argument");
        }
        tokenizer.advance(); //method or function or constructor
        tokenizer.advance(); // type
        currSubName = tokenizer.identifier();
        tokenizer.advance(); // identifier
        tokenizer.advance(); // (
        CompileParameterList();
        tokenizer.advance(); // )
        tokenizer.advance(); // {
        while (tokenizer.keyWord().equals("var") ) {
            CompileVarDec();
        }
        writer.WriteFunction(className + "." + currSubName, symTable.VarCount("var"));
        if(funcType.equals("constructor")){
            writer.writePush("constant", symTable.VarCount("field"));
            writer.WriteCall("Memory.alloc", 1);
            writer.writePop("pointer", 0);
        }
        if(funcType.equals("method")){ //Pointer to class
            writer.writePush("argument", 0); //Push this onto stack
            writer.writePop("pointer", 0); 
        }
        while(tokenizer.keyWord().equals("let") || tokenizer.keyWord().equals("if") || tokenizer.keyWord().equals("while") || tokenizer.keyWord().equals("do") || tokenizer.keyWord().equals("return")){
            CompileStatements();
        }
        tokenizer.advance();//}
    }
    
    public void CompileParameterList() {
        String name, type;
        if (tokenizer.symbol() != ')') {
            while (tokenizer.keyWord().equals("int") || tokenizer.keyWord().equals("char") || tokenizer.keyWord().equals("boolean") || tokenizer.tokenType().equals("IDENTIFIER")) {
                type = tokenizer.keyWord();
                tokenizer.advance(); // type
                name = tokenizer.identifier();
                tokenizer.advance(); // identifier
                symTable.Define(name, type, "argument");
                if (tokenizer.symbol() == ',') {
                    tokenizer.advance(); // ,
                }
            }
        }
    }
    
    public void CompileVarDec(){
        String type, name;
        tokenizer.advance(); //var
        type = tokenizer.keyWord();
        tokenizer.advance(); //type
        name = tokenizer.identifier();
        tokenizer.advance(); //identifier
        symTable.Define(name, type, "var");
        while(tokenizer.symbol() == ','){
            tokenizer.advance(); //,
            name = tokenizer.identifier();
            tokenizer.advance(); //identifier
            symTable.Define(name, type, "var");
        }
        tokenizer.advance(); //;
    }
    
    public void CompileStatements(){
        if(tokenizer.symbol() != '}'){
            while(tokenizer.keyWord().equals("let") || tokenizer.keyWord().equals("if") || tokenizer.keyWord().equals("while")|| tokenizer.keyWord().equals("do") || tokenizer.keyWord().equals("return")){
                switch(tokenizer.keyWord()){
                    case "let" -> {CompileLet();}
                    case "do" -> {CompileDo();}
                    case "if" -> CompileIf();
                    case "while" -> CompileWhile();
                    case "return" -> CompileReturn();
                    default -> {}
                }
            }
        }
    }
    
    public void CompileDo(){
        tokenizer.advance(); //do
        String name = tokenizer.identifier();
        tokenizer.advance(); //Subroutine name or className or varName
        CompileSubroutineCall(name);
        writer.writePop("temp", 0);
        tokenizer.advance(); //;
    }
    
    public void CompileLet(){
        boolean isArray = false;
        tokenizer.advance();//advance past let
        String identifier = tokenizer.identifier();
        String segment;
        switch(symTable.KindOf(identifier)){
            case "var" -> segment = "local";
            case "field" ->segment = "this";
            default -> segment = symTable.KindOf(identifier) ;
        }
        tokenizer.advance();// advance past identifier
        if(tokenizer.symbol()== '['){
            isArray = true;
            tokenizer.advance(); //Advance Past [
            CompileExpression(); 
            writer.writePush(segment, symTable.IndexOf(identifier));
            writer.writeArithmetic("add");
            tokenizer.advance(); //Advance Past ]
        }
        tokenizer.advance();//advance past =
        CompileExpression();
        tokenizer.advance();//advnace past ;
        if(!isArray)
            writer.writePop(segment, symTable.IndexOf(identifier));
        else{
            writer.writePop("temp", 0);
            writer.writePop("pointer", 1);
            writer.writePush("temp", 0);
            writer.writePop("that", 0);
        }
    }
    
    public void CompileWhile(){
        int ind = whileLabelIndex;
        whileLabelIndex++;
        writer.WriteLabel("WHILE_EXP"+Integer.toString(ind));
        tokenizer.advance();//while
        tokenizer.advance(); // (
        CompileExpression();
        tokenizer.advance(); // )
        writer.writeArithmetic("not");
        writer.WriteIf("WHILE_END"+Integer.toString(ind));
        tokenizer.advance(); // {
        CompileStatements();
        writer.WriteGoto("WHILE_EXP"+Integer.toString(ind));
        tokenizer.advance(); // }
        writer.WriteLabel("WHILE_END"+Integer.toString(ind));
    }
    
    public void CompileReturn(){
        tokenizer.advance(); // return
        if(tokenizer.symbol() != ';'){
            CompileExpression();
        }
        else{
            writer.writePush("constant", 0);
        }
        tokenizer.advance(); //;
        writer.writeReturn();
    }
    
    public void CompileIf(){
        int ind = ifLabelIndex;
        ifLabelIndex++;
        tokenizer.advance(); //if
        tokenizer.advance();//(
        CompileExpression(); //
        writer.WriteIf("IF_TRUE"+Integer.toString(ind));
        writer.WriteGoto("IF_FALSE"+Integer.toString(ind));
        tokenizer.advance();//)
        tokenizer.advance();//{
        writer.WriteLabel("IF_TRUE"+Integer.toString(ind));
        CompileStatements();
        tokenizer.advance();//}
        if(tokenizer.keyWord().equals("else")){
            writer.WriteGoto("IF_END"+Integer.toString(ind));
        }
        writer.WriteLabel("IF_FALSE"+Integer.toString(ind));
        if(tokenizer.keyWord().equals("else")){
            tokenizer.advance();//else
            tokenizer.advance();//{
            CompileStatements();
            tokenizer.advance();//}
            writer.WriteLabel("IF_END"+Integer.toString(ind));
        }
    }
    
    public void CompileExpression(){
        char operator;
        CompileTerm();
        while(isOp()){
            operator = tokenizer.symbol();
            tokenizer.advance();
            CompileTerm();
            switch(operator){
                case '+'-> writer.writeArithmetic("add"); 
                case '-'-> writer.writeArithmetic("sub");
                case '*'-> writer.writeArithmetic("mult");
                case '/'-> writer.writeArithmetic("div"); 
                case '&'-> writer.writeArithmetic("and");
                case '|'-> writer.writeArithmetic("or"); 
                case '<'-> writer.writeArithmetic("lt"); 
                case '>'-> writer.writeArithmetic("gt");
                case '='-> writer.writeArithmetic("eq");
                default -> {}
            }

        }
    }
    
    public void CompileTerm(){
        switch (tokenizer.symbol()) {
            case '(' -> {
                tokenizer.advance(); // (
                CompileExpression();
                tokenizer.advance(); // )
            }
            case '~' -> {
                tokenizer.advance(); // ~ 
                CompileTerm();
                writer.writeArithmetic("not");

            }
            case '-' ->{
                tokenizer.advance(); // -
                CompileTerm();
                writer.writeArithmetic("neg");
            }
            default -> {
                switch(tokenizer.tokenType()){
                    case "INT_CONST" -> {
                        writer.writePush("constant", tokenizer.intVal());
                        tokenizer.advance();
                    }
                    case "STRING_CONST" ->{
                        String str = tokenizer.stringVal();
                        writer.writePush("constant", str.length());
                        writer.WriteCall("String.new", 1);
                        for(int i = 0; i < str.length(); i++){
                            int asci = str.charAt(i);
                            writer.writePush("constant", asci);
                            writer.WriteCall("String.appendChar", 2);
                        }
                        tokenizer.advance();
                    }
                    case "KEYWORD"-> {
                        switch (tokenizer.keyWord()) {
                            case "this" -> writer.writePush("pointer", 0);
                            case "true" -> {
                                writer.writePush("constant", 0);
                                writer.writeArithmetic("not");
                            }
                            case "false", "null" -> writer.writePush("constant", 0);
                            default -> {
                            }
                        }
                        tokenizer.advance();
                        }
                    case "IDENTIFIER" -> { 
                        String name = tokenizer.identifier();
                        tokenizer.advance(); 
                        switch (tokenizer.symbol()) {
                            case '(', '.' -> CompileSubroutineCall(name);
                            case '[' -> {
                                String segment;
                                String kind = symTable.KindOf(name);
                                segment = switch (kind) {
                                case "var" -> "local";
                                case "field" -> "this";
                                default -> symTable.KindOf(name);
                            };
                                tokenizer.advance();// [
                                CompileExpression();
                                tokenizer.advance(); // ]
                                writer.writePush(segment, symTable.IndexOf(name));
                                writer.writeArithmetic("add");
                                writer.writePop("pointer", 1);
                                writer.writePush("that", 0);
                            }
                            default -> {
                                String segment;
                                String kind = symTable.KindOf(name);
                                segment = switch (kind) {
                                case "var" -> "local";
                                case "field" -> "this";
                                default -> symTable.KindOf(name);
                            };
                                writer.writePush(segment,symTable.IndexOf(name));       
                            }
                        }
                        }
                    default ->{}
                }
            }
        }
    }
    
    public void CompileSubroutineCall(String name){
        int nArgs = 0;
        String subIdentifier;
        boolean isMethod = false; 
        if(tokenizer.symbol() == '.'){
            tokenizer.advance(); //.
            if(symTable.TypeOf(name) != null) {
                String segment;
                String kind = symTable.KindOf(name);
                segment = switch (kind) {
                    case "var" -> "local";
                    case "field" -> "this";
                    default -> symTable.KindOf(name);
                };
                writer.writePush(segment, symTable.IndexOf(name));
                nArgs++;
                name = symTable.TypeOf(name);
                }
            subIdentifier = name + "." + tokenizer.identifier();
            tokenizer.advance(); //subRoutineName
        }else{
            subIdentifier = className + "." + name;
            isMethod = true;
        }
        if(isMethod){
            writer.writePush("pointer", 0);
            nArgs++;
        }
        tokenizer.advance(); //(
        if(tokenizer.symbol() != ')'){
            CompileExpression();
            nArgs++;
            while(tokenizer.symbol() == ','){
                tokenizer.advance(); //,
                CompileExpression();
                nArgs++;
            }
        }
        tokenizer.advance();// )
        writer.WriteCall(subIdentifier, nArgs);
    }
    

    public void closeFiles(){
        try{
            tokenizer.close();
            writer.close();
        }catch(IOException e){
        
        }
    }
    
    public boolean isOp(){
        return switch(tokenizer.symbol()){
            case '+', '-', '*', '/', '&', '|', '<', '>', '=' -> true;
            default -> false;
        };
    }
    
}
