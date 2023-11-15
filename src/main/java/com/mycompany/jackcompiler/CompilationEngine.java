package com.mycompany.jackcompiler;
import java.io.*;


//Keep working on subroutinecall -> compileExpression() writeTerm();
//Currently printing in too many situations


public class CompilationEngine {
    private JackTokenizer tokenizer;
    private VMWriter writer;
    private SymbolTable symTable;
    private String className;
    private String currSubName;
    private boolean subScope;
    
    
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
            symTable.Define(name, type, kind.toUpperCase());
            tokenizer.advance();
            while(tokenizer.symbol() == ','){
                tokenizer.advance();  //,
                name = tokenizer.identifier(); 
                tokenizer.advance();
                symTable.Define(name, type, kind.toUpperCase());
            }

            tokenizer.advance();//;
    }
    
    
    public void CompileSubroutine() {
        boolean isConstruct = false;
        boolean isMethod = false;
        if(tokenizer.keyWord().equals("method")) {symTable.startSubroutine(1); isMethod = true;}
        else symTable.startSubroutine(0);
        if(tokenizer.keyWord().equals("constructor")){
            isConstruct = true;

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
        writer.WriteFunction(className + "." + currSubName, symTable.VarCount("VAR"));
        if(!subScope){
            writer.writePush("constant", symTable.VarCount("STATIC")+symTable.VarCount("FIELD"));
            subScope = true;
        }
        if(isConstruct){
            writer.WriteCall("Memory.alloc", 1);
            writer.writePop("pointer", 0);
        }
        if(isMethod){
            writer.writePush("argument", 0);
            writer.writePop("pointer", 0);
        }
        for(int i = 0; i < symTable.VarCount("ARG");i++){
                writer.writePush("argument", i);
                writer.writePop("this", i);
        }
        writer.writePush("pointer", 0);
        while(tokenizer.keyWord().equals("let") || tokenizer.keyWord().equals("if") || tokenizer.keyWord().equals("while") || tokenizer.keyWord().equals("do")){
            CompileStatements();
        }
        CompileReturn();
        tokenizer.advance();//}
    }
    
    public void CompileParameterList() {
        String name, type;
        int args = 0;
        if (tokenizer.symbol() != ')') {
            while (tokenizer.keyWord().equals("int") || tokenizer.keyWord().equals("char") || tokenizer.keyWord().equals("boolean") || tokenizer.tokenType().equals("indentifier")) {
                type = tokenizer.keyWord();
                tokenizer.advance(); // type
                name = tokenizer.identifier();
                tokenizer.advance(); // identifier
                symTable.Define(name, type, "ARG");
                if (tokenizer.symbol() == ',') {
                    tokenizer.advance(); // ,
                }
            }
        }
    }
    
    public void CompileVarDec(){
        String type, name;
        String kind = "VAR";
        tokenizer.advance(); //var
        type = tokenizer.keyWord();
        tokenizer.advance(); //type
        name = tokenizer.identifier();
        tokenizer.advance(); //identifier
        symTable.Define(name, type, kind.toUpperCase());
        while(tokenizer.symbol() == ','){
            tokenizer.advance(); //,
            name = tokenizer.identifier();
            tokenizer.advance(); //identifier
            symTable.Define(name, type, kind.toUpperCase());
        }
        tokenizer.advance(); //;
    }
    
    public void CompileStatements(){
        if(tokenizer.symbol() != '}'){
            while(tokenizer.keyWord().equals("let") || tokenizer.keyWord().equals("if") || tokenizer.keyWord().equals("while")|| tokenizer.keyWord().equals("do")){
                switch(tokenizer.keyWord()){
                    case "let" -> {CompileLet();}
                    case "do" -> {CompileDo();}
                    case "if" -> CompileIf();
                    case "while" -> CompileWhile();
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
        tokenizer.advance(); //;
    }
    
    public void CompileLet(){
        while(tokenizer.symbol() != ';'){
            if(tokenizer.symbol() == '[' || tokenizer.symbol() == '='){
                tokenizer.advance(); // [ | =
                CompileExpression();
            }
            else tokenizer.advance(); // let identifier
        }
        tokenizer.advance(); //;
        
    }
    
    public void CompileWhile(){
        tokenizer.advance();//while
        tokenizer.advance(); // (
        CompileExpression();
        tokenizer.advance(); // )
        tokenizer.advance(); // {
        CompileStatements();
        tokenizer.advance(); // }
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
        tokenizer.advance(); //if
        tokenizer.advance();//(
        CompileExpression(); //
        tokenizer.advance();//)
        tokenizer.advance();//{
        CompileStatements();
        tokenizer.advance();//}
        if(tokenizer.keyWord().equals("else")){
            tokenizer.advance();//else
            tokenizer.advance();//{
            CompileStatements();
            tokenizer.advance();//}
        }
    }
    
    public void CompileExpression(){
        CompileTerm();
        while(isOp()){
            switch(tokenizer.symbol()){
                case '+'-> writer.writeArithmetic("add"); 
                case '-'-> writer.writeArithmetic("sub");
//                case '*'-> writer.writeArithmetic("add");
//                case '/'-> writer.writeArithmetic("add"); 
                case '&'-> writer.writeArithmetic("and");
                case '|'-> writer.writeArithmetic("or"); 
                case '<'-> writer.writeArithmetic("lt"); 
                case '>'-> writer.writeArithmetic("gt");
                case '='-> writer.writeArithmetic("eq");
                default -> {}
            }
            tokenizer.advance(); // operator
            CompileTerm();
        }
    }
    
    public void CompileTerm(){
//        symTable.IndexOf("x");
        switch (tokenizer.symbol()) {
            case '(' -> {
                tokenizer.advance(); // (
                CompileExpression();
                tokenizer.advance(); // )
            }
            case '~', '-' -> {
                tokenizer.advance(); // ~ | -
                CompileTerm();
            }
            default -> {
                String name = tokenizer.identifier();
                System.out.println(name);
                if(tokenizer.tokenType().equals("IDENTIFIER")){
                    writer.writePush("this", symTable.IndexOf(name));
                }
                //push this (i) gets called where i is the location where indentifier x is on stack
                tokenizer.advance(); // identifier
                switch (tokenizer.symbol()) {
                    case '(', '.' -> CompileSubroutineCall(name);
                    case '[' -> {
                        tokenizer.advance();// [
                        CompileExpression();
                        tokenizer.advance(); // ]
                    }
                    default -> {
                    }
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
            if(symTable.TypeOf(name) != null) {name = symTable.TypeOf(name);isMethod = true;}
            subIdentifier = name + "." + tokenizer.identifier();
            tokenizer.advance(); //subRoutineName
        }else{
            subIdentifier = className + "." + name;
            isMethod = true;
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
        //sometin funky here pointer or local
        if(isMethod){writer.writePush("pointer", 0);nArgs++;}
        writer.WriteCall(subIdentifier, nArgs);
//        if(isMethod) 
            writer.writePop("temp", 0);
//        else writer.writePop("local", 0);
//        for(i<0; i <nArgs; i++){
//            writer.writePop("temp", nArgs);
//        }
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
